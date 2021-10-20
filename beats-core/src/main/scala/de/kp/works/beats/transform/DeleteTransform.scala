package de.kp.works.beats.transform
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

import com.google.gson.JsonObject
import de.kp.works.beats.BeatsTransform

object DeleteTransform extends BeatsTransform {
  def transform(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object; this object
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id", entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "delete")

    entityJson.add("action", attrJson)
    /*
     * Extract other attributes from the
     * provided payload
     */
    val filter = Seq("id", "type")
    val keys = payload.keySet.filter(key => !filter.contains(key))

    fillEntity(payload, keys, entityJson)
    Some(entityJson)

  }

}
