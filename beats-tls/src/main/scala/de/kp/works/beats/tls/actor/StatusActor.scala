package de.kp.works.beats.tls.actor
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

import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson._
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.tls.TLSConstants._
import de.kp.works.beats.tls.actor.StatusActor._
import de.kp.works.beats.tls.redis.OsqueryNode

import scala.collection.JavaConversions._

/*
 * This actor publishes status logs to a pre-configured
 * data sink
 */
class StatusActor(outputHandler:OutputHandler) extends BaseActor {

  private val namespace = BeatsConf.OSQUERY_NAME

  override def receive: Receive = {

    case request:StatusReq =>
      /*
       * Send response message to `origin` immediately
       * as the logging task may last some time:
       *
       * LogActor -- [StatusReq] --> StatusActor
       *
       */
      val origin = sender
      origin ! StatusRsp("Logging started", success = true)

      try {

        val batch = buildBatch(request)
        /*
         * Send each status message as log event to the
         * output channel; this approach is equivalent
         * to the Fleet based mechanism.
         */
        batch.foreach(batchObj => {

          val json = new JsonObject

          json.addProperty("type", s"beat/$namespace/osquery_status")
          json.addProperty("event", batchObj.toString)

          outputHandler.sendEvent(json)

        })

      } catch {
        case t:Throwable => origin ! StatusRsp("Status logging failed: " + t.getLocalizedMessage, success = false)
      }
  }

  private def buildBatch(request:StatusReq):JsonArray = {

    val batch = new JsonArray

    val node = request.node
    val data = request.data.iterator

    while (data.hasNext) {

      val oldObj = data.next.getAsJsonObject
      val batchObj = new JsonObject
      /*
       * Assign header to event
       */
      batchObj.addProperty(HOST_IDENTIFIER, node.hostIdentifier)
      batchObj.addProperty(NODE_IDENT, node.uuid)
      batchObj.addProperty(NODE_KEY, node.nodeKey)

      /*
       * Assign body to event
       */
      oldObj.entrySet.foreach(entry => {

        val k = entry.getKey
        val v = entry.getValue

        batchObj.add(k, v)

      })

      batch.add(batchObj)

    }

    batch

  }

  override def execute(request: HttpRequest): String = {
    throw new Exception("Not implemented.")
  }

}

object StatusActor {

  case class StatusReq(node:OsqueryNode, data:JsonArray)
  case class StatusRsp(message:String, success:Boolean)

}
