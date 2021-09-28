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

import akka.NotUsed
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import com.typesafe.config.Config
import de.kp.works.beats.{BeatsConf, BeatsService}

class OpcUaService extends BeatsService(BeatsConf.OPCUA_CONF) {

  override def onStart(queue: SourceQueueWithComplete[String], opcUaCfg:Config):Unit = {

    val receiverCfg = opcUaCfg.getConfig("receiver")
    val numThreads = receiverCfg.getInt("numThreads")

    val receiver = new OpcUaReceiver(Some(queue), numThreads)
    receiver.start()

  }

}
