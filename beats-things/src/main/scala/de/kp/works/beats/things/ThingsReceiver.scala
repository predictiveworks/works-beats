package de.kp.works.beats.things

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

import de.kp.works.beats.BeatsReceiver
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.ssl.SslOptions

class ThingsReceiver(
    brokerUrl:String,
    mqttCreds: MqttCreds,
    sslOptions: Option[SslOptions] = None,
    outputHandler:OutputHandler,
    /* The number of threads to use for processing */
    numThreads:Int = 1) extends BeatsReceiver(numThreads) {

  def getWorker: Runnable = new Runnable {

    private val connector = new MqttConnector(brokerUrl, outputHandler, mqttCreds, sslOptions)

    override def run(): Unit = {
      val message = s"Things Receiver worker started."
      info(message)

      connector.start()

    }
  }

}
