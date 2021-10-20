package de.kp.works.beats.opcua
/*
 * Copyright (c) 2020 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.JsonObject
import com.typesafe.config.Config
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.handler.OutputHandler
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.{UaMonitoredItem, UaSubscription}
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.{DataValue, ExtensionObject, NodeId, QualifiedName}
import org.eclipse.milo.opcua.stack.core.types.enumerated.{DataChangeTrigger, DeadbandType, MonitoringMode, TimestampsToReturn}
import org.eclipse.milo.opcua.stack.core.types.structured.{DataChangeFilter, MonitoredItemCreateRequest, MonitoringParameters, ReadValueId}
import org.slf4j.LoggerFactory

import java.util
import java.util.concurrent.{CompletableFuture, Future}
import java.util.function.{BiConsumer, Consumer}
import scala.collection.JavaConversions._

class OpcUaSubscriber(
   client: OpcUaClient,
   subscription: UaSubscription,
   outputHandler: OutputHandler) {

  private val LOGGER = LoggerFactory.getLogger(classOf[OpcUaSubscriber])

  private val caches = new OpcUaCaches(client)
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
  private def subscribeNodes(topics: List[OpcUaTopic]): CompletableFuture[Boolean] = {

    val future: CompletableFuture[Boolean] = new CompletableFuture[Boolean]
    if (topics.isEmpty) {
      future.complete(true)

    }
    else {

      var requests: List[MonitoredItemCreateRequest] = List.empty[MonitoredItemCreateRequest]

      val nodeIds: List[NodeId] = topics
        .map(topic => NodeId.parseOrNull(topic.address))

      val filter: ExtensionObject =
        ExtensionObject.encode(
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
       * When creating items in MonitoringMode.Reporting this callback is where
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
                  LOGGER.debug("Monitored item created for nodeId: " + monitoredItem.getReadValueId.getNodeId)
                }
                else {
                  LOGGER.warn("Failed to create monitored item for nodeId: " + monitoredItem.getReadValueId.getNodeId + " with status code: " + monitoredItem.getStatusCode.toString)
                }

              })

              future.complete(true)

            } catch {
              case e: Exception =>
                LOGGER.error(e.getLocalizedMessage)
                future.complete(false)
            }

          }
        })

    }
    future
  }

  /**
   * The support to subscribe by (browse) path is implemented
   * in 2 steps: first the address cache is used to retrieve
   * the [NodeId]s that refer to the provided path-based
   * `address`.
   *
   * Then address and browse path is modified.
   */
  private def subscribePath(topics: List[OpcUaTopic]): CompletableFuture[Boolean] = {

    val future: CompletableFuture[Boolean] = new CompletableFuture[Boolean]
    if (topics.isEmpty) {
      future.complete(true)
    }
    else {

      try {

        var resolvedTopics: List[OpcUaTopic] = List.empty[OpcUaTopic]
        topics.foreach(topic => {
          resolvedTopics ++= caches.resolveTopic(topic)
        })

        if (resolvedTopics.isEmpty) {
          future.complete(false)
        }
        else {
          val res: Boolean = subscribeNodes(resolvedTopics).get
          future.complete(res)
        }
      } catch {
        case e: Exception =>
          LOGGER.error(e.getLocalizedMessage)
          future.complete(false)
      }
    }

    future
  }

  def subscribeTopic(clientId: String, topic: OpcUaTopic): Future[Boolean] = {

    val future: CompletableFuture[Boolean] = new CompletableFuture[Boolean]
    try {

      val tuple = OpcUaRegistry.addClient(clientId, topic)

      val added: Boolean = tuple._2.asInstanceOf[Boolean]
      val count: Int = tuple._1.asInstanceOf[Int]

      if (!added) {
        future.complete(true)
      }
      else {
        if (count == 1) {
          val res: Boolean = subscribeTopics(List(topic)).get
          future.complete(res)
        }
        else {
          future.complete(true)
        }
      }
    } catch {
      case e: Exception =>
        LOGGER.error(e.getLocalizedMessage)
        future.complete(false)
    }
     future
  }

  def subscribeTopics(topics: List[OpcUaTopic]): Future[Boolean] = {

    val future: CompletableFuture[Boolean] = new CompletableFuture[Boolean]
    /*
     * Distinguish between node (id) based and
     * browse path based topics
     */
    val nodeTopics = topics.filter(topic => topic.topicType == OpcUaTopicType.NodeId)
    val pathTopics = topics.filter(topic => topic.topicType == OpcUaTopicType.Path)

    CompletableFuture
      .allOf(subscribeNodes(nodeTopics), subscribePath(pathTopics))
      .whenComplete(new BiConsumer[Void, Throwable] {
        override def accept(ignore: Void, error: Throwable): Unit = {
          if (error == null) {
            future.complete(true)
          }
          else {
            future.complete(false)
          }

        }
      })

    future

  }

  def unsubscribeTopics(topics: List[OpcUaTopic], items: List[MonitoredItem]): Future[Boolean] = {

    val future: CompletableFuture[Boolean] = new CompletableFuture[Boolean]
    try {

      if (items.nonEmpty) {

        val opcUaItems: List[UaMonitoredItem] = items.map(item => item.item)
        subscription.deleteMonitoredItems(opcUaItems)

      }

      future.complete(true)

    } catch {
      case e: Exception =>
        LOGGER.error(e.getLocalizedMessage)
        future.complete(false)
    }

    future

  }

  private def consumeValue(dataTopic: OpcUaTopic, dataValue: DataValue): Unit = {

    try {

      val jsonTopic = OpcUaTransform.dataTopicToJson(dataTopic)
      val jsonValue = OpcUaTransform.dataValueToJson(dataValue)

      val opcUaMessage = new JsonObject
      opcUaMessage.add("topic", jsonTopic)
      opcUaMessage.add("value", jsonValue)
      /*
       * This mechanism defines the bridge to subsequent
       * data computation
       */
      outputHandler.sendOpcUaEvent(Some(opcUaMessage))

    } catch {
      case _: Exception =>
        outputHandler.sendOpcUaEvent(None)

    }
  }


}
