package de.kp.works.beats.fleet
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
import com.typesafe.config.Config
import de.kp.works.beats.file.FileMonitor
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.transform.FleetTransform
import de.kp.works.beats.{BeatsConf, BeatsService}

class FleetService extends BeatsService(BeatsConf.FLEET_CONF) {

  override def onStart(queue: SourceQueueWithComplete[String], cfg: Config): Unit = {
    /*
     * INPUT (READ) DIRECTION
     *
     * Fleet Beat leverages a [FileMonitor] to receive Osquery log
     * events; this monitor surveys a certain file system folder
     * and is started as independent thread.
     */
    val receiverCfg = cfg.getConfig("receiver")

    val fleetFolder = receiverCfg.getString("fleetFolder")
    val numThreads = receiverCfg.getInt("numThreads")
    /*
     * OUTPUT (WRITE) DIRECTION
     *
     * The current implementation of [FleetBeat] supports
     * `mqtt` and `sse` as output channel
     */
    val eventHandler:OutputHandler = new OutputHandler
    eventHandler.setNamespace(BeatsConf.FLEET_NAME)

    val channel = getOutputCfg.getString("channel")
    eventHandler.setChannel(channel)
    /*
     * Configure the [OutputHandler] to transform incoming
     * [FileEvent]s prior to publishing with [FleetTransform]
     */
    eventHandler.setFileTransform(new FleetTransform)

    channel match {
      case "mqtt" =>
        /*
         * Do nothing as the [OutputHandler] initiates the
         * [MqttPublisher] when setting the respective channel
         */
      case "sse" =>
        /*
         * Configure the [OutputHandler] to write incoming
         * [FileEvent]s to the SSE output queue
         */
        eventHandler.setSseQueue(queue)

      case _ =>
        throw new Exception(s"The configured output channel `$channel` is not supported.")
    }
    /*
     * File Monitor to listen to log file changes on a Fleet platform
     */
    val monitor = new FileMonitor(BeatsConf.FLEET_CONF, fleetFolder, eventHandler)

    val receiver = new FleetReceiver(monitor, numThreads)
    receiver.start()

  }

}
