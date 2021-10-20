package de.kp.works.beats.handler
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

import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson.JsonObject
import de.kp.works.beats.file.{FileEvent, FileTransform}
import de.kp.works.beats.fiware.{FiwareEvent, FiwareTransform}
import de.kp.works.beats.mqtt.MqttPublisher
import de.kp.works.beats.opencti.{CTITransform, SseEvent}
import de.kp.works.beats.thingsboard.{MqttEvent, ThingsTransform}

class OutputHandler {

  private var channel:Option[String] = None
  /*
   * The `namespace` variable is used to inform the receiver
   * of a certain event about the sender
   */
  private var namespace:Option[String] = None
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
        throw new Exception(s"The configured output channel `$channel` is not supported.")
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
    if (ctiTransform.isEmpty)
      throw new Exception(s"[OutputHandler] No transformer configured to transform a [SseEvent].")

    if (namespace.isEmpty)
      throw new Exception(s"[OutputHandler] No namespace configured to transform a [SseEvent].")

    val jsonObject = ctiTransform.get.transform(ctiEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }

  def sendFileEvent(event:FileEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (fileTransform.isEmpty)
      throw new Exception(s"[OutputHandler] No transformer configured to transform a [FileEvent].")

    if (namespace.isEmpty)
      throw new Exception(s"[OutputHandler] No namespace configured to transform a [FileEvent].")

    val jsonObject = fileTransform.get.transform(event, namespace.get)
    sendEvent(jsonObject)

  }
  /**
   * This method defines the Fiware Beat specific
   * output handling
   */
  def sendFiwareEvent(fiwareEvent: FiwareEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (fiwareTransform.isEmpty)
      throw new Exception(s"[OutputHandler] No transformer configured to transform a [FiwareEvent].")

    if (namespace.isEmpty)
      throw new Exception(s"[OutputHandler] No namespace configured to transform a [FiwareEvent].")

    val jsonObject = fiwareTransform.get.transform(fiwareEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }

  def sendOpcUaEvent(opcUaEvent: Option[JsonObject]):Unit = {

    if (namespace.isEmpty)
      throw new Exception(s"[OutputHandler] No namespace configured to transform a [OpcUaEvent].")

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
   * This method defines the Things Beat specific
   * output handling
   */
  def sendThingsEvent(mqttEvent: MqttEvent):Unit = {
    /*
     * Transform the received event into a serialized
     * [JsonObject]
     */
    if (thingsTransform.isEmpty)
      throw new Exception(s"[OutputHandler] No transformer configured to transform a [MqttEvent].")

    if (namespace.isEmpty)
      throw new Exception(s"[OutputHandler] No namespace configured to transform a [MqttEvent].")

    val jsonObject = thingsTransform.get.transform(mqttEvent, namespace.get)
    if (jsonObject.isDefined) sendEvent(jsonObject.get)

  }

  private def sendEvent(jsonObject:JsonObject):Unit = {
    /*
     * Check which output channel is configured
     */
    if (channel.isEmpty)
      throw new Exception(s"[OutputHandler] No output channel configured.")

    channel.get match {
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
