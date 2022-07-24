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
import com.google.gson.JsonArray
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.actors.BeatsActor
import de.kp.works.beats.mitre.{MitreClient, MitreDomains}

/**
 * [DataActor] is a MitreBeat API actor that offers
 * domain specific data sources and components.
 *
 * Data represent one of the top-level search dimensions
 * of the Beat's API.
 */
class DataActor(queue: SourceQueueWithComplete[String])
  extends BeatsActor(BeatsConf.MITRE_CONF, queue) {

  private val emptyMessage = new JsonArray

  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(ApiMessages.invalidRequest)
      return emptyMessage.toString

    }

    val req = mapper.readValue(json.toString, classOf[DataReq])
    if (req.domain.isEmpty) {

      warn(ApiMessages.invalidDomain)
      return emptyMessage.toString

    }

    val dataTypes = Seq("both", "component", "source")
    if (!dataTypes.contains(req.dataType.toLowerCase)) {

      warn(ApiMessages.invalidDataType)
      return emptyMessage.toString

    }
    try {

      val result = new JsonArray

      val domain = MitreDomains.withName(req.domain)
      req.dataType.toLowerCase match {
        case "both" =>
          /*
           * Build source lookup and assign additional
           * field `x-mitre-data-components` to hold
           * all components of a certain source
           */
          val sources = MitreClient.getObjects(domain, Some("x-mitre-data-source"))
          val lookup = sources
            .map(obj => {

              val objJson = obj.getAsJsonObject
              objJson.add("x-mitre-data-components", new JsonArray)

              val id = objJson.get("id").getAsString
              (id, objJson)
            })
            .toMap
          /*
           * Retrieve all MITRE data components, determine
           * referenced data source and add component to
           * the source.
           */
          val components = MitreClient.getObjects(domain, Some("x-mitre-data-component"))
          components.foreach(component => {

            val componentJson = component.getAsJsonObject
            val source_ref = componentJson.get("x_mitre_data_source_ref").getAsString

            val source = lookup(source_ref)
            source.get("x-mitre-data-components").getAsJsonArray.add(componentJson)

          })

          lookup.values.foreach(result.add)

        case "component" =>
          val components = MitreClient.getObjects(domain, Some("x-mitre-data-component"))
          components.foreach(result.add)

        case "source" =>
          val sources = MitreClient.getObjects(domain, Some("x-mitre-data-source"))
          sources.foreach(result.add)
      }

      result.toString

    } catch {
      case t:Throwable =>
        error(ApiMessages.failedDataReq(t))
        emptyMessage.toString
    }

  }

}

