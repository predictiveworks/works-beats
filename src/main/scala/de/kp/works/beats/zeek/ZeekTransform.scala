package de.kp.works.beats.zeek
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
import de.kp.works.beats.file.{FileEvent, FileTransform}

class ZeekTransform extends FileTransform {

  override def transform(event:FileEvent, namespace:String):Option[String] = {

    val json = new JsonObject
    /*
     * In case of a [FileEvent], the `eventType` specifies
     * the file name. It is enriched with the namespace,
     *
     * e.g `zeek/dns.log`.
     */
    json.addProperty("type", s"$namespace/${event.eventType}")
    json.addProperty("event", event.eventData)

    Some(json.toString)

  }

}
