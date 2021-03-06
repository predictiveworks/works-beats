package de.kp.works.beats.opencti
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

import de.kp.works.beats.BaseBeat

/**
 * The [CTIBeat] is an Akka based Http(s) service that manages
 * an SSE client based OpenCTI connector. Retrieved events are
 * transformed into an NGSI compliant format and then published
 * to a FIWARE context broker, an MQTT broker and via the SSE
 * output queue.
 *
 * An SSE client like [Works. Stream] listens to the published
 * events and initiates subsequent data processing.
 */
object CTIBeat extends BaseBeat {

  override var programName: String = "CTIBeat"
  override var programDesc: String = "Publish threat events via multiple output channels."

  override def launch(args: Array[String]): Unit = {

    val service = new CTIService()
    start(args, service)

  }

}
