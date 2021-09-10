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

import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors

class OpcUaReceiver(
   /*
    * The SSE output queue to publish the
    * incoming OPC-UA events
    */
   queue:Option[SourceQueueWithComplete[String]] = None,
   /* The number of threads to use for processing */
   numThreads:Int = 1) {

  private val LOGGER = LoggerFactory.getLogger(classOf[OpcUaReceiver])

  private val opcUaConnector = new OpcUaConnector()

  private val executorService = Executors.newFixedThreadPool(numThreads)

  def start():Unit = {
    /*
     * Wrap connector and output handler in a runnable
     */
    val worker: Runnable = new Runnable {

      val subscriptionCallback: OpcUaCallback = new OpcUaCallback {
        override def onMessage(message: JsonObject): Unit = {

          val serialized = message.toString
          if (queue.isDefined)
            queue.get.offer(serialized)

          else {
            /*
             * An undefined queue can be useful for testing
             * and publishes received events to the console
             */
            println(serialized)
          }
        }
      }
      /*
       * Initialize the connector to the
       * OPC-UA server
       */
      val connector = new OpcUaConnector()
      opcUaConnector.setSubscriptionCallback(subscriptionCallback)

      override def run(): Unit = {

        val now = new java.util.Date().toString
        println(s"[OpcUaReceiver] $now - Receiver worker started.")

        connector.start()

      }
    }

    try {

      /* Initiate stream execution */
      executorService.execute(worker)

    } catch {
      case _:Exception => executorService.shutdown()
    }

  }

  def stop():Unit = {

    /* Stop listening to the OpenCTI events stream  */
    executorService.shutdown()
    executorService.shutdownNow()

  }

}
