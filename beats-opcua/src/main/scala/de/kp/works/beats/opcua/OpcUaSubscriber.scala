package de.kp.works.beats.opcua
/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */

import com.typesafe.config.Config
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.{BeatsConf, BeatsLogging}
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.{UaMonitoredItem, UaSubscription}
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.{DataValue, ExtensionObject, NodeId, QualifiedName}
import org.eclipse.milo.opcua.stack.core.types.enumerated.{DataChangeTrigger, DeadbandType, MonitoringMode, TimestampsToReturn}
import org.eclipse.milo.opcua.stack.core.types.structured.{DataChangeFilter, MonitoredItemCreateRequest, MonitoringParameters, ReadValueId}

import java.util
import java.util.function.Consumer
import scala.collection.JavaConversions._
/**
 * The [OpcUaSubscriber] controls the publishing
 * of OPC-UA events
 */
class OpcUaSubscriber(
   client: OpcUaClient,
   subscription: UaSubscription,
   outputHandler: OutputHandler) extends BeatsLogging {

  private val caches = new OpcUaCache(client)
  private val opcUaCfg: Config = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)
  /*
   * Unpack monitoring configuration
   */
  private val monitoringCfg: Config = opcUaCfg.getConfig("monitoring")

  private val bufferSize = monitoringCfg.getInt("bufferSize")
  private val samplingInterval = monitoringCfg.getDouble("samplingInterval")

  private val discardOldest = monitoringCfg.getBoolean("discardOldest")
  private val dataChangeTrigger = DataChangeTrigger.valueOf(monitoringCfg.getString("dataChangeTrigger"))
  /**
   * Subscribe a list of nodes (in contrast to paths)
   */
  private def subscribeNodes(topics: List[OpcUaTopic]):Boolean = {

    if (topics.isEmpty) return true
    /*
     * Optimistic approach
     */
    var success = true

    var requests = List.empty[MonitoredItemCreateRequest]
    val nodeIds = topics.map(topic => NodeId.parseOrNull(topic.address))

    val filter: ExtensionObject = ExtensionObject.encode(
      client.getStaticSerializationContext,
      new DataChangeFilter(dataChangeTrigger, UInteger.valueOf(DeadbandType.None.getValue), 0.0))

    nodeIds.foreach(nodeId => {
      /*
       * IMPORTANT: The client handle must be unique per item within
       * the context of a subscription. The UaSubscription's client
       * handle sequence is provided as a convenience.
       */
      val clientHandle: UInteger = subscription.nextClientHandle
      val readValueId: ReadValueId = new ReadValueId(nodeId, AttributeId.Value.uid, null, QualifiedName.NULL_VALUE)

      val parameters = new MonitoringParameters(clientHandle, samplingInterval, filter, UInteger.valueOf(bufferSize), discardOldest)
      val request = new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters)

      requests ++= List(request)

    })
    /*
     * When creating items in MonitoringMode. Reporting this callback is where
     * each item needs to have its value/event consumer hooked up. The alternative
     * is to create the item in sampling mode, hook up the consumer after the creation
     * call completes, and then change the mode for all items to reporting.
     */
    val onItemCreated: UaSubscription.ItemCreationCallback = new UaSubscription.ItemCreationCallback {
      override def onItemCreated(item: UaMonitoredItem, id: Int): Unit = {

        val topic: OpcUaTopic = topics.get(id)
        if (item.getStatusCode.isGood) {
          OpcUaRegistry.addMonitoredItem(MonitoredItem(item), topic)
        }
        val valueConsumer = new Consumer[DataValue] {
          override def accept(data: DataValue): Unit = {
            consumeValue(topic, data)
          }
        }
        item.setValueConsumer(valueConsumer)

      }
    }

    subscription
      .createMonitoredItems(TimestampsToReturn.Both, requests, onItemCreated)
      .thenAccept(new Consumer[util.List[UaMonitoredItem]] {
        override def accept(monitoredItems: util.List[UaMonitoredItem]): Unit = {
          try {
            monitoredItems.foreach(monitoredItem => {
              if (monitoredItem.getStatusCode.isGood) {
                info("Monitored item created for nodeId: " + monitoredItem.getReadValueId.getNodeId)
              }
              else {
                warn("Failed to create monitored item for nodeId: " + monitoredItem.getReadValueId.getNodeId + " with status code: " + monitoredItem.getStatusCode.toString)
              }

            })

          } catch {
            case t: Throwable =>
              error(t.getLocalizedMessage)
              success = false
          }

        }

      }).get

    success

  }

  /**
   * The support to subscribe by (browse) path is implemented
   * in 2 steps: first the address cache is used to retrieve
   * the [NodeId]s that refer to the provided path-based
   * `address`.
   *
   * Then address and browse path is modified.
   */
  private def subscribePath(topics: List[OpcUaTopic]):Boolean = {

    if (topics.isEmpty) return true
    try {

      var resolvedTopics: List[OpcUaTopic] = List.empty[OpcUaTopic]
      topics.foreach(topic => {
        resolvedTopics ++= caches.resolveTopic(topic)
      })

      if (resolvedTopics.isEmpty) return false
      subscribeNodes(resolvedTopics)

    } catch {
      case t: Throwable =>
        error(t.getLocalizedMessage)
        false
    }

  }
  /**
   * This method supports the starting phase of the
   * [OpcUAConnector] where an existing [UaSubscription]
   * learns about the configured topics.
   */
  def subscribeTopic(clientId: String, topic: OpcUaTopic):Boolean = {

    try {

      val (totalClients, added) = OpcUaRegistry.addClient(clientId, topic)
      /*
       * If `added` = true, client & topic are
       * registered already. This indicates that
       * the [UaSubscription] already knows the
       * topic.
       */
      if (!added) return true
      if (totalClients == 1) {
        /*
         * This is the first time, the topic is
         * registered, so also assign the topic
         * to the [UaSubscription].
         */
        subscribeTopics(List(topic))
      }
      else true

    } catch {
      case t: Throwable =>
        error(t.getLocalizedMessage)
        false
    }

  }
  /**
   * This is a common method that supports
   * the initial registration of topics, and
   * also the re-attempt to do so.
   */
  def subscribeTopics(topics: List[OpcUaTopic]):Boolean = {

    var success = false
    /*
     * Distinguish between node (id) based and
     * browse path based topics
     */
    val nodeTopics = topics.filter(topic => topic.topicType == OpcUaTopicTypes.NodeId)
    val pathTopics = topics.filter(topic => topic.topicType == OpcUaTopicTypes.Path)

    try {

      if (!subscribeNodes(nodeTopics))
        throw new Exception("Node")

      if (!subscribePath(pathTopics))
        throw new Exception("Path")

      success = true

    } catch {
      case t:Throwable =>
        val message = s"Attempt to assign ${t.getLocalizedMessage} topics failed."
        error(message)
    }

    success

  }

  def unsubscribeTopics(topics: List[OpcUaTopic], items: List[MonitoredItem]):Boolean = {

    try {

      if (items.nonEmpty) {

        val opcUaItems: List[UaMonitoredItem] = items.map(item => item.item)
        subscription.deleteMonitoredItems(opcUaItems)

      }

      true

    } catch {
      case t: Throwable =>
        error(s"Unsubscribing topics failed: ${t.getLocalizedMessage}")
        false
    }

  }
  /**
   * This is the main method that controls
   * publication of OPC-UA events
   */
  private def consumeValue(dataTopic: OpcUaTopic, dataValue: DataValue): Unit = {

    try {

      val jsonObject = OpcUaTransform.transform(dataTopic, dataValue)
      outputHandler.sendOpcUaEvent(Some(jsonObject))

    } catch {
      case _: Exception =>
        outputHandler.sendOpcUaEvent(None)

    }
  }

}
