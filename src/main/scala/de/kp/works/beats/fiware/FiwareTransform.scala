package de.kp.works.beats.fiware
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

class FiwareTransform {

  def transform(fiwareEvent:FiwareEvent, namespace:String):Option[JsonObject] = {

    val json = fiwareEvent.payload
    /*
     * {
        "data": [
            {
                "id": "Room1",
                "temperature": {
                    "metadata": {},
                    "type": "Float",
                    "value": 28.5
                },
                "type": "Room"
            }
        ],
        "subscriptionId": "57458eb60962ef754e7c0998"
       }
     */
    val sid = json.get("subscriptionId").getAsString
    if (FiwareSubscriptions.isRegistered(sid)) {
      /*
       * Send JSON representation of the Fiware
       * notification to the provided SSE queue
       */
      val eventType = s"beat/$namespace/notification"

      val sseEvent = new JsonObject
      sseEvent.addProperty("type", eventType)
      sseEvent.addProperty("event", fiwareEvent.toJson.toString)

      Some(sseEvent)

    } else None

  }

}
