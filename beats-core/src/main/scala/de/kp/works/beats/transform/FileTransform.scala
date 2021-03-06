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
import de.kp.works.beats.events.FileEvent

trait FileTransform extends BeatsTransform {

  def transform(event: FileEvent, namespace: String): Option[JsonObject]
  /**
   * A helper method to create an initial NGSI compliant
   * entity from the provided Osquery hostname and table
   */
  protected def newNGSIEntity(entityId:String, entityType:String):JsonObject = {
    /*
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     */
    val ngsiType = toCamelCase(entityType)
    val ngsiId = s"urn:ngsi-ld:$ngsiType:${cleanText(entityId)}"

    val ngsiJson = new JsonObject
    ngsiJson.addProperty(ID, ngsiId)
    ngsiJson.addProperty(TYPE, ngsiType)

    ngsiJson

  }

  private def cleanText(text:String):String = {
    text.replace(".", "_")
  }
}
