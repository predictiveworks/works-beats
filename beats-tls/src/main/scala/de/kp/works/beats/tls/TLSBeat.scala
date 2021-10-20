package de.kp.works.beats.tls
/*
 * Copyright (c) 2021 Dr. Krusche & Partner PartG. All rights reserved.
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
 * [OsqueryBeat] is built as a TLS endpoint for node-based query `results`
 * and `status` messages, and also supports distributed (ad-hoc) queries
 * result.
 * <p>
 * The mission of OsqueryBeat is to consume osquery node information
 * in terms of SQL requests and publish these results into multiple
 * channels, with a focus on IoT-based channels to keep IoT payloads
 * and monitoring events as close as possible.
 * <p>
 * Use cases:
 * <p>
 * Monitoring IoT gateways (objective: high reliability)
 * <p>
 * Reliable operation of distributed small-sized devices and services
 * includes a variety of tasks, including identification of problematic
 * behaviour and security threats, finding the root cause in an anomaly
 * situation, and the selection and execution of an appropriate counter
 * measure.
 * <p>
 * At the scale of todays IoT systems, these tasks are impossible to perform
 * manually for human system administrators. Therefore, automatic anomaly
 * detection algorithms play an important role in the reliable operation of
 * IoT infrastructures.
 * <p>
 * Network (flow) attribution
 * <p>
 * Host information (processes, users etc) can be used to attribute network
 * flows of the network that contains the respective hosts
 * <p>
 * In contrast to other Works Beats, the Osquery Beat does not need any
 * receiver to obtain events or messages from connected Osquery agents.
 * <p>
 * This beat listens to Http(s) requests and (if necessary) transforms
 * and publishes them as SSE.
 */
object TLSBeat extends BaseBeat {

  override var programName: String = "OsqueryBeat"
  override var programDesc: String = "Publish Osquery results as SSE."

  override def launch(args: Array[String]): Unit = {

    val service = new TLSService()
    start(args, service)

  }

}
