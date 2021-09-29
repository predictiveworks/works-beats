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
import de.kp.works.beats.file.{FileEvent, FileTransform}
import de.kp.works.beats.mqtt.MqttPublisher

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

  private var fileTransform:Option[FileTransform] = None

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

  def setFileTransform(transform:FileTransform):Unit = {
    fileTransform = Some(transform)
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

    val serialized = fileTransform.get.transform(event, namespace.get)
    /*
     * Check which output channel is configured
     */
    if (channel.isEmpty)
      throw new Exception(s"[OutputHandler] No output channel configured.")

    channel.get match {
      case "mqtt" =>
        if (mqttPublisher.isDefined && serialized.isDefined) {
          /*
           * The current implementation leverages the `namespace`
           * value and the file name ([FileEvent].eventType) to
           * define an MQTT topic.
           */
          val topics = Array(s"$namespace/${event.eventType}")
          mqttPublisher.get.publish(topics, serialized.get)

        }

      case "sse" =>
        if (queue.isDefined && serialized.isDefined)
          queue.get.offer(serialized.get)

        else {
          /*
           * An undefined queue can be useful for testing
           * and publishes received events to the console
           */
          println(serialized)
        }

      case _ =>
        println(serialized)

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
