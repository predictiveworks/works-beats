package de.kp.works.beats.openems

/**
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

import de.kp.works.beats.BaseBeat

/**
 * The [EMSBeat] is an Akka based Http(s) service that manages
 * a WebSocket client based OpenEMS connector. Retrieved events
 * are transformed and published to the SSE output queue.
 */
object EMSBeat extends BaseBeat {

  override var programName: String = "EMSBeat"
  override var programDesc: String = "Publish energy events as SSE."

  override def launch(args: Array[String]): Unit = {

    //val service = new CTIService()
    //start(args, service)

  }

}