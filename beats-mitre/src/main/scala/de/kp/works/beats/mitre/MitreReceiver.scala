package de.kp.works.beats.mitre

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
import de.kp.works.beats.mitre.data.MitreLoader

import java.util.concurrent.TimeUnit

class MitreReceiver(outputHandler:OutputHandler, interval:Int, numThreads:Int = 1)
  extends BeatsScheduledReceiver(interval, TimeUnit.DAYS, numThreads) {

  def getWorker:Runnable = new Runnable {
    /*
     * Initialize the loader for the set of MITRE
     * domain knowledge bases
     */
    private val loader = new MitreLoader(outputHandler)

    override def run(): Unit = {

      val message = s"MITRE Receiver worker started."
      info(message)

      loader.start()

    }
  }

}
