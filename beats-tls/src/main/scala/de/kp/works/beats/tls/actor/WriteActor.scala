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
import de.kp.works.beats.tls.TLSConstants._
import de.kp.works.beats.tls.redis.RedisApi

import scala.collection.JavaConversions._
/**
 * The [WriteActor] receives the results of distributed queries
 */
class WriteActor(queue:SourceQueueWithComplete[String]) extends BaseActor {

  override def execute(request:HttpRequest):String = {
    /*
     * Retrieve node that refers to the provided
     * `node_key`
     */
    val node = getNode(request)
    if (node == null)
      return buildInvalidResponse

    /*
     * We do not expect that the respective payload
     * is empty; therefore, no check is performed
     *
     * {
     * "node_key": "...",
     * "queries": {
     *   "id1": [
     *     {"column1": "value1", "column2": "value2"},
     *     {"column1": "value1", "column2": "value2"}
     *   ],
     *   "id2": [
     *     {"column1": "value1", "column2": "value2"},
     *     {"column1": "value1", "column2": "value2"}
     *   ],
     *   "id3": []
     * },
     * "statuses": {
     *   "id1": 0,
     *   "id2": 0,
     *   "id3": 2,
     *  }
    	 * }
     */
    val payload = getBodyAsJson(request).getAsJsonObject

    val queries = payload.get(QUERIES).getAsJsonObject
    val statuses = payload.get(STATUSES).getAsJsonObject

    /*
     * Normalize provided queries
     */
    val events = new JsonArray
    queries.entrySet.foreach(entry => {

      val qid = entry.getKey
      val rows = entry.getValue.getAsJsonArray

      /*
       * STEP #1: Check whether the query referenced by
       * `qid` is specified as pending
       */
      var task = RedisApi.taskById(qid)
      val valid = {
        /*
         * The query results must refer to a pending
         * distributed query task
         */
        if (task.status != "PENDING") {
          log.error(s"Distributed query `$qid` is not pending.")
          false

        }
        else
        /*
         * The query must refer to the provided node; in this case,
         * we must not update the QueryDB
         */
          if (task.node != node.uuid) {
            log.error(s"Distributed query task `$qid` does not refer to node `${node.uuid}`.")
            false
          }
          else {

            val status = try {
              statuses.get(qid).getAsInt

            } catch {
              case _:Throwable => -1
            }

            if (status == 0) {
              /* Update query */
              task = task.copy(status = "COMPLETE")
              RedisApi.updateTask(task)

              true
            }
            else {
              /* Update query */
              task = task.copy(status = "FAILED")
              RedisApi.updateTask(task)

              log.error(s"Distributed query `$qid` with non-zero status code.")
              false
            }
          }
      }

      if (valid) {

        val fields = buildFields(node.nodeKey, node.hostIdentifier, qid, rows)
        events.add(fields)

      }
    })

    queue.offer(events.toString)
    /*
     * Update node in NodesDB, unpack distributed
     * queries and send back to the remote node
     */
    RedisApi.nodeUpdate(node)
    buildResponse(nodeInvalid=false)

  }

  private def buildFields(node:String, host:String, qid:String, rows:JsonArray):JsonArray = {
    /*
     * [
     *   {"column1": "value1", "column2": "value2"},
     *   {"column1": "value1", "column2": "value2"}
     * ]
     */
    val fields = new JsonArray
    rows.foreach(columns => {

      val field = new JsonObject()

      /* Node meta information */

      field.addProperty(NODE, node)
      field.addProperty(HOST, host)

      /* Entry information */

      field.addProperty(NAME,   qid)
      field.addProperty(ACTION, "adhoc")

      field.addProperty(TIMESTAMP, System.currentTimeMillis)
      field.add(COLUMNS, columns.getAsJsonObject)

      fields.add(field)

    })

    fields

  }

}