package de.kp.works.beats.thingsboard
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

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import de.kp.works.beats.BeatsRoutes

class ThingsRoutes(actors:Map[String, ActorRef], source:Source[ServerSentEvent, NotUsed]) extends BeatsRoutes(source) {
  /**
   * The [ThingsBeat] is restricted to a Http(s) server
   * that supports GET requests only. Therefore, no actors
   * are needed to process incoming requests.
   */
  def this(source:Source[ServerSentEvent, NotUsed]) = this(Map.empty[String, ActorRef], source)

}
