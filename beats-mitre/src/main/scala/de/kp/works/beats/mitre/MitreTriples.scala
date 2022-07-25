package de.kp.works.beats.mitre
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

import de.kp.works.beats.mitre.MitreDomains.MitreDomain

object MitreTriples extends MitreConnect {

  def main(args:Array[String]):Unit = {

    //val result = attackPatterns(MitreDomains.ENTERPRISE)
    //result.foreach(println)

    val result = dataSources(MitreDomains.ENTERPRISE)
      .sortBy{case(_, source_name, _, _, _) => source_name}

    result.foreach(println)


  }

  /**
   * Method to retrieve all relations in triple format
   * that end in an `attack-pattern`.
   *
   * It is the starting point for further investigations,
   * e.g., "which malware instances" leverage the same
   * attack pattern.
   *
   */
  def attackPatterns(domain:MitreDomain):Map[String, Seq[(String,String)]] = {

    val relations = getObjects(domain, Some("relationship"))
      /*
       * STEP #1: Restrict to those relations
       * which have an attack-pattern specified
       * as target
       */
      .filter(obj => {

        val objJson = obj.getAsJsonObject

        val relationType = objJson.get("relationship_type").getAsString
        val isRevokedBy = relationType == "revoked-by"

        val target = objJson.get("target_ref").getAsString
        val targetName = target.split("--").head

        (targetName == "attack-pattern") && !isRevokedBy

      })
      /*
       * STEP #2: Restrict relations to triple
       * representation, source, type and target
       */
      .map(obj => {

        val objJson = obj.getAsJsonObject
        val relationType = objJson.get("relationship_type").getAsString

        val source = objJson.get("source_ref").getAsString
        val target = objJson.get("target_ref").getAsString

        (source, relationType, target)
      })
      /*
       * STEP #3: Group all relation triples by target
       */
      .groupBy{case (_, _, target) => target}
      .map{case(target, values) =>
        (target, values.map{case (source, predicate, _) => (source,predicate)})
      }

    relations

  }
  /**
   * Data Sources and Data Components represent data
   * which can be used to detect techniques (attack
   * patterns)
   */
  def dataSources(domain:MitreDomain):Seq[(String, String, String, String, String)] = {
    /*
     * STEP #1: Extract all MITRE data sources
     * and data components
     */
    val dataSources = getObjects(domain, Some("x-mitre-data-source"))
      /*
       * The extracted data sources are transformed
       * into a lookup data structured
       */
      .map(obj => {

        val objJson = obj.getAsJsonObject
        val id = objJson.get("id").getAsString

        (id, objJson)

      }).toMap

    val dataComponents = getObjects(domain, Some("x-mitre-data-component"))
    /*
     * STEP #2: Data components are transformed
     * into a named triple representation
     */
    val relations = dataComponents.map(obj => {

      val objJson = obj.getAsJsonObject

      val target_ref = objJson.get("id").getAsString
      val target_name = objJson.get("name").getAsString

      val source_ref = objJson.get("x_mitre_data_source_ref").getAsString
      val source_name = dataSources(source_ref).get("name").getAsString

      (source_ref, source_name, "includes", target_name, target_ref)
    })

    relations

  }
}
