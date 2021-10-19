package de.kp.works.beats.osquery.tls.actor
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
import de.kp.works.beats.osquery.tls.actor.StatusActor._
import de.kp.works.beats.osquery.tls.redis.OsqueryNode

import scala.collection.JavaConversions._

/*
 * This actor publishes status logs to a pre-configured
 * data sink
 */
class StatusActor(queue:SourceQueueWithComplete[String]) extends BaseActor {

  import de.kp.works.beats.osquery.tls.OsqueryConstants._
  private val namespace = BeatsConf.OSQUERY_CONF

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
        /*
         * Repack request and send to REDIS instance leveraging
         * the `status` stream. Note, query results of all nodes
         * are collected in a single message stream.
         *
         * StatusActor -- [Publish] --> RedisProducer
         *
         */
        val eventType = s"beat/$namespace/status"
        val eventData = buildEvents(request)

        val sseEvent = new JsonObject
        sseEvent.addProperty("type", eventType)
        sseEvent.addProperty("event", eventData.toString)

        queue.offer(sseEvent.toString)

      } catch {
        case t:Throwable => origin ! StatusRsp("Status logging failed: " + t.getLocalizedMessage, success = false)
      }
  }

  private def buildEvents(request:StatusReq):JsonArray = {
    /*
     * Repack request and send to REDIS instance
     */
    val events = new JsonArray

    val node = request.node
    val data = request.data.iterator

    while (data.hasNext) {

      val item = data.next.getAsJsonObject
      val event = new JsonObject()
      /*
       * Assign header to event
       */
      event.addProperty(HOST_IDENTIFIER, node.hostIdentifier)
      event.addProperty(NODE_IDENT, node.uuid)
      event.addProperty(NODE_KEY, node.nodeKey)

      /*
       * Assign body to event
       */
      item.entrySet.foreach(entry => {

        val k = entry.getKey
        val v = entry.getValue

        event.add(k, v)

      })

      events.add(event)

    }

    events

  }

  override def execute(request: HttpRequest): String = {
    throw new Exception("Not implemented.")
  }

}

object StatusActor {

  case class StatusReq(node:OsqueryNode, data:JsonArray)
  case class StatusRsp(message:String, success:Boolean)

}
