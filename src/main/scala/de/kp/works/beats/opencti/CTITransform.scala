package de.kp.works.beats.opencti
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
/**
 * OpenCTI events do not respect the NGSI format.
 *
 * This transformer can be used to harmonize the
 * OpenCTI events to a standard format.
 */
object CTITransform {

  def transform(eventId:String, eventType:String, data:String):String = {

    val json = new JsonObject

    // TODO Introduce customized transformation before
    // emitting the event again

    json.addProperty("id", eventType)

    json.addProperty("type", eventType)
    json.addProperty("data", data)

    json.toString

  }
}
