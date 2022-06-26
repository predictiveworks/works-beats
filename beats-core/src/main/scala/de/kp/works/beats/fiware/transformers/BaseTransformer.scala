package de.kp.works.beats.fiware.transformers

/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.{JsonElement, JsonObject, JsonParser}

trait BaseTransformer {

  def deserialize(json:JsonObject): (String, JsonElement) = {
    /*
     * The provided `json` object has a common data format
     * that is used by all Works. Beats.
     *
     * {
     *    "type": "..",
     *    "event": ".."
     * }
     */

    val eventType = json.get("type").getAsString
    val eventData = JsonParser.parseString(json.get("event").getAsString)

    (eventType, eventData)

  }

  def transform(json:JsonObject):JsonElement

}
