package de.kp.works.beats.fiware
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
 * The [FiwareBeat] is an Akka based Http(s) services that
 * sends subscriptions to a Fiware Context Broker and receives
 * NGSI notifications. These notifications are sent to the SSE
 * output queue.
 * <p>
 * An SSE client like [Works. Stream] listens to the published
 * events and initiates subsequent data processing.
 */
object FiwareBeat extends BaseBeat {

  override var programName:String = "FiwareBeat"
  override var programDesc:String = "Publish Fiware notifications as SSE."

  override def launch(args:Array[String]):Unit = {

    val service = new FiwareService()
    start(args, service)

    }

}
