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

import com.google.gson.{JsonElement, JsonObject}

object OPENCTI extends BaseTransformer {

  override def transform(json: JsonObject): JsonElement = {
    /*
     * The `json` object has the following format:
     *
     * {
     *  "type": "",   The `type` is specified as beat/opencti/{create | delete | update}
     *
     *  "event": ""   The `event` contains the serialized JSON representation of the
     *                associated `create | delete | update` event.
     * }
     */
    val (eventType, eventData) = deserialize(json)

    throw new Exception("not implemented yet")
  }

}
