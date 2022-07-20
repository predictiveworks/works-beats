package de.kp.works.beats.plc

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

import de.kp.works.beats.BeatsScheduledReceiver
import de.kp.works.beats.handler.OutputHandler

import java.util.concurrent.TimeUnit

class PlcReceiver(outputHandler:OutputHandler, interval:Int, numThreads:Int = 1)
  extends BeatsScheduledReceiver(interval, TimeUnit.MILLISECONDS, numThreads) {

  private val connector = new PlcConnector(outputHandler)
  /*
   * Connect to the PLC with retry; the respective
   * configuration is pre-defined and currently can
   * not be changed
   */
  connector.connectWithRetry()
  /*
   * Build the respective read request to retrieve
   * field values on a scheduled basis
   */
  private val request = connector.buildReadRequest

  def getWorker:Runnable = new Runnable {

    override def run(): Unit = {

      if (request.isEmpty) {
        val message = s"PLC Receiver worker could not be started"
        error(message)

        throw new Exception(message)

      } else {
        val message = s"PLC Receiver worker started."
        info(message)

        connector.readAndPublish(request.get)

      }

    }
  }

}
