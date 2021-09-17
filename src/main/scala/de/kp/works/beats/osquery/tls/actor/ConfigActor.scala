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
import com.google.gson._
import de.kp.works.beats.osquery.tls.redis.RedisApi
import scala.collection.JavaConversions._

class ConfigActor extends BaseActor {

  import de.kp.works.beats.osquery.tls.OsqueryConstants._

  override def execute(request:HttpRequest):String = {
    /*
     * Retrieve node that refers to the provided
     * `node_key` and extract the respective config
     */
    val node = getNode(request)
    if (node == null)
      return buildInvalidResponse

    val config = RedisApi.nodeConfig(node.uuid)
    if (config == null) {

      log.error("Invalid node configuration detected.")
      return buildInvalidResponse

    }
    /*
     * Update node in RedisDB, unpack configuration
     * and send back to the remote node
     */
    RedisApi.nodeUpdate(node)

    val response = new JsonObject()
    response.addProperty(NODE_INVALID, false)

    config.entrySet.foreach(entry => {

      val k = entry.getKey
      val v = entry.getValue

      response.add(k, v)

    })

    response.toString

  }

}