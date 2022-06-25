package de.kp.works.beats.fiware
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

import com.google.gson.JsonObject

class FiwarePublisher {
  /**
   * The broker endpoint to create & update
   * entities
   */
  private val entityCreateUrl = "/v2/entities"
  private val entityGetUrl    = "/v2/entities/{id}"
  private val entityUpdateUrl = "/v2/entities/{id}/attrs"

  def publish(event:JsonObject):Unit = {

  }

  def transform(json:JsonObject):JsonObject = ???

}
