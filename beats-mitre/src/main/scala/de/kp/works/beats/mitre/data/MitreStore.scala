package de.kp.works.beats.mitre.data

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

import com.google.gson.JsonElement
import de.kp.works.beats.mitre.MitreDomains
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import scala.collection.mutable

object MitreStore {

  private val CAPEC_STORE = mutable.HashMap.empty[String, JsonElement]
  private val ENTERPRISE_STORE = mutable.HashMap.empty[String, JsonElement]

  private val ICS_STORE = mutable.HashMap.empty[String, JsonElement]
  private val MOBILE_STORE = mutable.HashMap.empty[String, JsonElement]

  def get(domain: MitreDomain): Seq[JsonElement] = {

    val store = getStore(domain)
    store.values.toSeq

  }

  def set(domain: MitreDomain, objects: Seq[JsonElement]): Unit = {

    val store = getStore(domain)
    objects.foreach(obj => {
      val id = obj.getAsJsonObject.get("id").getAsString
      store += id -> obj
    })

  }

  private def getStore(domain: MitreDomain): mutable.HashMap[String, JsonElement] = {

    domain match {
      case MitreDomains.CAPEC =>
        CAPEC_STORE

      case MitreDomains.ENTERPRISE =>
        ENTERPRISE_STORE

      case MitreDomains.ICS =>
        ICS_STORE

      case MitreDomains.MOBILE =>
        MOBILE_STORE
    }

  }
}
