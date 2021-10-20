package de.kp.works.beats.things

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

import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.ssl.SslOptions

import java.util.concurrent.{ExecutorService, Executors}

class ThingsReceiver(
    brokerUrl:String,
    mqttCreds: MqttCreds,
    sslOptions: Option[SslOptions] = None,
    outputHandler:OutputHandler,
    /* The number of threads to use for processing */
    numThreads:Int = 1) {

  private var executorService:ExecutorService = _

  def start():Unit = {
    /*
     * Wrap connector and output handler in a runnable
     */
    val worker = new Runnable {
      /*
       * Initialize the connector to the
       * OpenCTI server
       */
      private val connector = new MqttConnector(brokerUrl, outputHandler, mqttCreds, sslOptions)

      override def run(): Unit = {

        val now = new java.util.Date().toString
        println(s"[ThingsReceiver] $now - Receiver worker started.")

        connector.start()

      }
    }

    try {

      /* Initiate stream execution */
      executorService = Executors.newFixedThreadPool(numThreads)
      executorService.execute(worker)

    } catch {
      case _:Exception => executorService.shutdown()
    }

  }

  def stop():Unit = {

    /* Stop listening to the Mqtt events stream  */
    executorService.shutdown()
    executorService.shutdownNow()

  }

}
