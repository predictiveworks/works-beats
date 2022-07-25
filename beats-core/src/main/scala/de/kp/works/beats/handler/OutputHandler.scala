package de.kp.works.beats.handler
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

import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson.JsonObject
import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.events.{FileEvent, FiwareEvent, MqttEvent, SseEvent}
import de.kp.works.beats.fiware.FiwarePublisher
import de.kp.works.beats.mqtt.MqttPublisher
import de.kp.works.beats.transform.{CTITransform, FileTransform, FiwareTransform, ThingsTransform}

class OutputHandler extends BeatsLogging {

  private var channel:Option[String] = None
  /*
   * The `namespace` variable is used to inform the receiver
   * of a certain event about the sender
   */
  private var namespace:Option[String] = None
  /*
   * The Fiware publisher to use. The [FiwarePublisher] is initiated
   * automatically when configuring the `fiware` output channel.
   */
  private var fiwarePublisher:Option[FiwarePublisher] = None
  /*
   * The MQTT publisher to use. The [MqttPublisher] is automatically
   * initiated when configuring the `mqtt` output channel.
   */
  private var mqttPublisher:Option[MqttPublisher] = None
  /*
   * The queue is the output queue of the Akka based SSE mechanism.
   * It is used, if the [OutHandler] is configured to use the `sse`
   * channel.
   */
  private var queue:Option[SourceQueueWithComplete[String]] = None
  /*
   * [CTITransform] supports OpenCTI Beat
   */
  private var ctiTransform:Option[CTITransform] = None
  /*
   * [FileTransform] supports Fleet & Zeek Beats
   */
  private var fileTransform:Option[FileTransform] = None
  /*
   * [FiwareTransform] supports Fiware Beat
   */
  private var fiwareTransform:Option[FiwareTransform] = None
  /*
   * [ThingsTransform] supports Things Beat (ThingsBoard)
   */
  private var thingsTransform:Option[ThingsTransform] = None

  def getChannel:String = channel.get

  /** OUTPUT (WRITE) SUPPORT **/

  def setChannel(outputChannel:String):Unit = {

    outputChannel match {
      case "fiware" =>
        setFiwarePublisher()

      case "mqtt" =>
        /*
         * Select HiveMQ or Eclipse Paho MQTT client and
         * try to connect. If a connection is successfully
         * established, the `mqttPublisher` variable is set
         */
        setMqttPublisher()

      case "sse" =>
        /*
         * Do nothing as the SSE queue is provided externally
         * (see `setSseQueue`)
         */
      case _ =>

        val message = s"The configured output channel `$channel` is not supported."
        error(message)

    }

    channel = Some(outputChannel)

  }
  def setNamespace(ns:String):Unit = {
    namespace = Some(ns)
  }

  def setSseQueue(sseQueue:SourceQueueWithComplete[String]):Unit = {
    queue = Some(sseQueue)
  }

  /** INPUT SUPPORT **/

  def setCTITransform(transform:CTITransform):Unit = {
    ctiTransform = Some(transform)
  }

  def setFileTransform(transform:FileTransform):Unit = {
    fileTransform = Some(transform)
  }

  def setFiwareTransform(transform:FiwareTransform):Unit = {
    fiwareTransform = Some(transform)
  }

  def setThingsTransform(transform:ThingsTransform):Unit = {
    thingsTransform = Some(transform)
  }

  /** OUTPUT SUPPORT **/

  /**
   * This method defines the OpenCTI Beat specific
   * output handling
   */
  def sendCTIEvent(ctiEvent: SseEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (ctiTransform.isEmpty) {
      val message = s"[OutputHandler] No transformer configured to transform a [SseEvent]."
      error(message)
    }

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [SseEvent]."
      error(message)
    }

    val jsonObject = ctiTransform.get.transform(ctiEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }
  /**
   * A [FileEvent] originates either from the FleetBeat
   * or the ZeekBeat and contains cyber monitoring events
   */
  def sendFileEvent(event:FileEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (fileTransform.isEmpty) {
      val message = s"[OutputHandler] No transformer configured to transform a [FileEvent]."
      error(message)
    }

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [FileEvent]."
      error(message)
    }

    val jsonObject = fileTransform.get.transform(event, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }
  /**
   * This method defines the FiwareBeat specific
   * output handling
   */
  def sendFiwareEvent(fiwareEvent: FiwareEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (fiwareTransform.isEmpty) {
      val message = s"[OutputHandler] No transformer configured to transform a [FiwareEvent]."
      error(message)
    }

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [FiwareEvent]."
      error(message)
    }

    val jsonObject = fiwareTransform.get.transform(fiwareEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }
  /**
   * This method publishes the current changes of the
   * MITRE domain knowledge bases, including CAPEC
   */
  def sendMitreEvent(mitreEvent: Option[JsonObject]):Unit = {

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [MitreEvent]."
      error(message)
    }

    if (mitreEvent.isDefined) {
      /*
       * Build unified SSE event format that is harmonized
       * with all other Beat event output formats
       */
      val eventType = s"beat/$namespace"

      val jsonObject = new JsonObject
      jsonObject.addProperty("type", eventType)
      jsonObject.addProperty("event", mitreEvent.get.toString)

      sendEvent(jsonObject)

    }

  }

  def sendOpcUaEvent(opcUaEvent: Option[JsonObject]):Unit = {

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [OpcUaEvent]."
      error(message)
    }

    if (opcUaEvent.isDefined) {
      /*
       * Build unified SSE event format that is harmonized
       * with all other Beat event output formats
       */
      val eventType = s"beat/$namespace"

      val jsonObject = new JsonObject
      jsonObject.addProperty("type", eventType)
      jsonObject.addProperty("event", opcUaEvent.get.toString)

      sendEvent(jsonObject)

    }

  }

  /**
   * This method defines the PLC Beat specific
   * output handling
   */
  def sendPlcEvent(plcEvent:JsonObject):Unit = {
    throw new Exception(s"Not supported yet")
  }

  /**
   * This method defines the Things Beat specific
   * output handling
   */
  def sendThingsEvent(mqttEvent: MqttEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (thingsTransform.isEmpty) {
      val message = s"[OutputHandler] No transformer configured to transform a [MqttEvent]."
      error(message)
    }

    if (namespace.isEmpty) {
      val message = s"[OutputHandler] No namespace configured to transform a [MqttEvent]."
      error(message)
    }

    val jsonObject = thingsTransform.get.transform(mqttEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }

  def sendEvent(jsonObject:JsonObject):Unit = {
    /*
     * Check which output channel is configured
     */
    if (channel.isEmpty) {
      val message = s"[OutputHandler] No output channel configured."
      error(message)
    }

    channel.get match {
      case "fiware" =>
        if (fiwarePublisher.isDefined) {
          /*
           * The JSON representation of the provided event
           * `jsonObject` is transformed into an NGSI compliant
           * entity representation
           */
          val event = fiwarePublisher.get.transform(jsonObject)
          fiwarePublisher.get.publish(event)

        }

      case "mqtt" =>
        if (mqttPublisher.isDefined) {
          /*
           * The current implementation leverages the `type` assigned
           * to the transformed [JsonObject] as semantic indicator
           * and topic
           */
          val topics = Array(jsonObject.get("type").getAsString)
          mqttPublisher.get.publish(topics, jsonObject.toString)

        }

      case "sse" =>
        if (queue.isDefined)
          queue.get.offer(jsonObject.toString)

        else {
          /*
           * An undefined queue can be useful for testing
           * and publishes received events to the console
           */
          println(jsonObject)
        }

      case _ =>
        println(jsonObject)

    }

  }
  /**
   * Helper method to retrieve the Fiware publisher
   * that transforms and sends Beat events to the
   * configured Fiware Context Broker
   */
  private def setFiwarePublisher():Unit = {
    val publisher = new FiwarePublisher(namespace.get)
    fiwarePublisher = Some(publisher)
  }

  private def setMqttPublisher():Unit = {

    try {
      /*
       * Build MQTT publisher based on the internal
       * or external configuration files
       */
      val publisher = MqttPublisher.build
      /*
       * Connect to the publisher and check whether
       * the connection is successfully established
       */
      publisher.connect()

      if (publisher.isConnected)
        mqttPublisher = Some(publisher)

    } catch {
      case _:Throwable =>
        /*
         * Do nothing as in this case the publisher
         * remains [None]
         */
    }

  }

}
