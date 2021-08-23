package de.kp.works.beats.opencti.transform
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

object CreateTransform extends BeatsTransform {
  /*
   * SAMPLE CREATE EVENT
   *
   *   data: {
   *      name: 'JULIEN',
   *      description: '',
   *      identity_class: 'individual',
   *      id: 'identity--d969b177-497f-598d-8428-b128c8f5f819',
   *      x_opencti_id: '3ae87124-b240-42b7-b309-89d8eb66e9cc',
   *      type: 'identity',
   *    }
   *
   */
  def transform(payload:Map[String,Any]):Option[String] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object; this object
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id",   entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "create")

    entityJson.add("action", attrJson)
    /*
     * Extract other attributes from the
     * provided payload
     */
    val filter = Seq("id", "type")
    val keys = payload.keySet.filter(key => !filter.contains(key))

    fillEntity(payload, keys, entityJson)
    Some(entityJson.toString)

  }

}


