package de.kp.works.beats.mitre.api

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.SourceQueueWithComplete
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.actors.BeatsActor

/**
 * [DataActor] is a MitreBeat API actor that offers
 * domain specific data sources and components.
 *
 * Data represent one of the top-level search dimensions
 * of the Beat's API.
 */
class DataActor(queue: SourceQueueWithComplete[String])
  extends BeatsActor(BeatsConf.MITRE_CONF, queue) {

  override def execute(request: HttpRequest): String = ???

}

