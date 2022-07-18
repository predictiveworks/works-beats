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

import com.google.gson.JsonElement
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import scala.collection.mutable

object MitreGraph extends MitreConnect {
  /*
   * The following object types are
   * not taken into account:
   *
   * - x-mitre-collection
   * - x-mitre-matrix
   */
  val NODE_TYPES = List(
    "attack-pattern",
    "course-of-action",
    "identity",
    "intrusion-set",
    "malware",
    "marking-definition",
    "tool",
    "x-mitre-data-component",
    "x-mitre-data-source",
    "x-mitre-tactic")

  val EDGES_TYPES = List(
    "relationship"
  )

  val COMMON_NODE_PROPERTIES = List(
    "description",
    "id",
    "name",
    "type")
  /**
   * This method extracts the identifiers that
   * defines MITRE based STIX objects.
   *
   * This list is used as a lookup to determine
   * whether a certain referenced object is part
   * of the provided domain.
   */
  def extractNodeIds(domain:MitreDomain):Seq[String] = {

    val nodeIds = mutable.ArrayBuffer.empty[String]

    val objects = getObjects(domain)
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      if (NODE_TYPES.contains(objType)) {
        nodeIds += objJson.get("id").getAsString
      }

    })

    nodeIds

  }
  /**
   * Extracting nodes from a domain-specific knowledge
   * base is mapped onto nodes and edges, as certain
   * node properties are used to reference another node.
   */
  def extractNodes(domain:MitreDomain):(Seq[JsonElement], Seq[JsonElement]) = {

    val nodes = Seq.empty[JsonElement]
    val edges = Seq.empty[JsonElement]
    /*
     * STEP #1: Extract the unique identifiers
     * of the nodes provided by the domain
     * specific knowledge base
     */
    val nodeId = extractNodeIds(domain)

    // TODO

    (nodes, edges)

  }
}
