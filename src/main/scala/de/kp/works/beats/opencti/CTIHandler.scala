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

import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson.JsonObject
import de.kp.works.beats.BeatsConf

class CTIHandler(queue:Option[SourceQueueWithComplete[String]]) {

  private val namespace = BeatsConf.OPENCTI_CONF

    def write(ctiEvent:SseEvent):Unit = {
      /*
       * Guard to filter irrelevant messages
       */
      if (ctiEvent.eventId == null || ctiEvent.eventType == null || ctiEvent.data == null) return
      if (ctiEvent.eventId.isEmpty || ctiEvent.eventType.isEmpty || ctiEvent.data.isEmpty) return
      /*
       * Transform the received event and republish
       * as serialized [JsonObject]
       */
      val jsonObject = CTITransform.transform(ctiEvent)
      /*
       * In case of event types that that are not
       * republished, [CTITransform] returns None
       */
      if (queue.isDefined && jsonObject.isDefined) {
        /*
         * Build unified SSE event format that is
         * harmonized with all other Beat event output
         * formats.
         *
         * OpenCTI distinguishes `create`, `delete`,
         * `update` etc events
         */
        val eventType = s"beat/$namespace/${ctiEvent.eventType}"

        val sseEvent = new JsonObject
        sseEvent.addProperty("type", eventType)
        sseEvent.addProperty("event", jsonObject.get.toString)

        queue.get.offer(sseEvent.toString)

      }

    }
}
