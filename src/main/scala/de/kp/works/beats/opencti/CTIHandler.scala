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

class CTIHandler(
    /*
     * The queue is the output queue of the Akka based SSE mechanism
     */
    queue:Option[SourceQueueWithComplete[String]]) {

    def write(eventId:String, eventType:String, data:String):Unit = {
      /*
       * Guard to filter irrelevant messages
       */
      if (eventId == null || eventType == null || data == null) return
      if (eventId.isEmpty || eventType.isEmpty || data.isEmpty) return
      /*
       * Transform the received event and republish
       * as serialized [JsonObject]
       */
      val serialized = CTITransform.transform(eventId, eventType, data)
      if (queue.isDefined)
        queue.get.offer(serialized)

      else {
        /*
         * An undefined queue can be useful for testing
         * and publishes received events to the console
         */
        println(serialized)
      }

    }
}
