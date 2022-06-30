package de.kp.works.beats.transform.stix

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

import com.google.gson.{JsonArray, JsonObject}
import scala.collection.JavaConversions.iterableAsScalaIterable

object STIXObjects extends STIXBase {
  /**
   * A STIX object is either a STIX Domain Object (SDO)
   * or a STIX Cyber Observable (SCO). The following
   * field attributes are transformed into relations:
   *
   * - CREATED_BY_REF
   * - EXTERNAL_REFERENCES
   * - KILL_CHAIN_PHASES
   * - OBJECT_MARKING_REFS
   * - OBJECT_REFS
   */
  def createStixObject(entityId: String, entityType: String,
                       data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    /*
     * A certain STIX object is mapped onto sets
     * of NGSI entities and relationships
     */
    val entities = new JsonArray
    val relations = new JsonArray

    val entity = newEntity(entityId, entityType)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "create")
    val rowJson = new JsonObject
    /*
     * Ensure that all provided keys have underscores
     * instead of hyphens
     */
    var filteredData = data.map{
      case(k,v) => (k.replace("-", "_"),v)}

    /*
     * The remaining part of this method distinguishes
     * between fields that describe relations and those
     * that carry object properties.
     */

    /** CREATED BY
     *
     * This field contains the reference (identifier) of
     * the Identity object that created the STIX object.
     *
     * When creating a STIX entity, this reference is used
     * to also create a relationship from (STIX object) to
     * (Identity).
     */
    if (filteredData.contains(CREATED_BY_REF)) {
      /*
       * Creator is transformed to an edge
       */
      val e = STIXRelations.buildCreatedBy(entity, filteredData, "create")
      if (e.isDefined) relations.add(e.get)
      /*
       * Remove 'created_by_ref' from the provided dataset
       * to restrict further processing to object properties
       */
      filteredData = filteredData.filterKeys(k => !(k == CREATED_BY_REF))
    }

    /** EXTERNAL REFERENCES **/

    if (filteredData.contains(EXTERNAL_REFERENCES)) {
      /*
       * External references are mapped onto vertices
       * and edges
       */
      val (v, e) = STIXRelations.createExternalReferences(entity, filteredData)

      if (v.isDefined) v.get.foreach(entities.add)
      if (e.isDefined) e.get.foreach(relations.add)

      filteredData = filteredData.filterKeys(k => !(k == EXTERNAL_REFERENCES))
    }

    /** GRANULAR MARKINGS **/

    if (filteredData.contains(GRANULAR_MARKINGS)) {
      // TODO Granular markings are currently not supported
      filteredData = filteredData.filterKeys(k => !(k == GRANULAR_MARKINGS))
    }

    /** HASHES **/

    if (filteredData.contains(HASHES)) {

      val hashes = transformHashes(filteredData)
      if (hashes.nonEmpty) {

        STIX.STANDARD_HASHES.foreach(algorithm => {

          val hash = hashes.getOrElse(algorithm, "")

          val attrJson = new JsonObject
          attrJson.add(METADATA, new JsonObject)

          attrJson.addProperty(TYPE, "String")
          attrJson.addProperty(VALUE, hash)

          rowJson.add(algorithm, attrJson)

        })

       }

      filteredData = filteredData.filterKeys(k => !(k == HASHES))
    }

    /** KILL CHAIN PHASES
     *
     * This refers to Attack-Pattern, Indicator, Malware and Tools
     */
    if (filteredData.contains(KILL_CHAIN_PHASES)) {
      /*
       * Kill chain phases are mapped onto vertices
       * and edges
       */
      val (v, e) = STIXRelations.createKillChainPhases(entity, filteredData)

      if (v.isDefined) v.get.foreach(entities.add)
      if (e.isDefined) e.get.foreach(relations.add)
      /*
       * Remove 'kill_chain_phases' from the provided dataset
       * to restrict further processing to object properties
       */
      filteredData = filteredData.filterKeys(k => !(k == KILL_CHAIN_PHASES))
    }

    /** OBJECT LABELS **/

    if (filteredData.contains(LABELS)) {

      val labels = transformLabels(filteredData)
      if (labels.nonEmpty) {

        val metaJson = new JsonObject
        metaJson.addProperty(BASE_TYPE, "String")

        val attrJson = new JsonObject
        attrJson.add(METADATA, metaJson)

        attrJson.addProperty(TYPE, "List")
        attrJson.add(VALUE, labels.get)

        rowJson.add(LABELS, attrJson)
      }
      /*
       * Remove 'labels' from the provided dataset to restrict
       * further processing to object properties
       */
      filteredData = filteredData.filterKeys(k => !(k == LABELS))

    }

    /** OBJECT MARKINGS
     *
     * This field contains a list of references (identifiers) of
     * Object Markings that are associated with the STIX object.
     *
     * When creating the STIX object also a list of associated
     * edges is created, pointing from the STIX object to the
     * respective Object Marking.
     */
    if (filteredData.contains(OBJECT_MARKING_REFS)) {
      /*
       * Object markings are transformed (in contrast to external
       * reference) to edges only
       */
      val e = STIXRelations.createObjectMarkings(entity, filteredData)
      if (e.isDefined) e.get.foreach(relations.add)
      /*
       * Remove 'object_marking_refs' from the provided dataset
       * to restrict further processing to object properties
       */
      filteredData = filteredData.filterKeys(k => !(k == OBJECT_MARKING_REFS))
    }

    /** OBJECT REFERENCES * */

    if (filteredData.contains(OBJECT_REFS)) {
      /*
       * Object references are transformed (in contrast to external
       * reference) to edges only
       */
      val e = STIXRelations.createObjectReferences(entity, filteredData)
      if (e.isDefined) e.get.foreach(relations.add)
      /*
       * Remove 'object_refs' from the provided dataset
       * to restrict further processing to object properties
       */
      filteredData = filteredData.filterKeys(k => !(k == OBJECT_REFS))
    }
    /*
     * Add remaining properties to the SDO or SCO; the current
     * implementation accepts properties of a basic data type
     * or a list where the components specify basic data types.
     */
    val keys = filteredData.keySet
    fillRow(payload = filteredData, keys = keys, rowJson = rowJson)

    val rowsJson = new JsonArray
    rowsJson.add(rowJson)

    entity.add(ROWS, rowsJson)
    entities.add(entity)

    (Some(entities), Some(relations))

  }

  /**
   * This method indicates the deletion of a certain STIX object.
   * The data provided in STIX format represents the object just
   * before deletion.
   */
  def deleteStixObject(entityId: String, entityType: String,
                       data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities = new JsonArray
    val relations = new JsonArray

    val entity = newEntity(entityId, entityType)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "delete")
    val rowJson = new JsonObject
    /*
     * Ensure that all provided keys have underscores
     * instead of hyphens
     */
    val filteredData = data.map{
      case(k,v) => (k.replace("-", "_"),v)}

    /*
     * The remaining part of this method focuses on the
     * relations that refer to this STIX object, as these
     * it cannot be expected that the data destination
     * manages the deletion of relations properly.
     */

    /** CREATED BY
     *
     * This field contains the reference (identifier) of
     * the Identity object that created the STIX object.
     *
     * When creating a STIX entity, this reference is used
     * to also create a relationship from (STIX object) to
     * (Identity).
     */
    if (filteredData.contains(CREATED_BY_REF)) {
      /*
       * Creator is transformed to an edge
       */
      val e = STIXRelations.buildCreatedBy(entity, filteredData, "create")
      if (e.isDefined) relations.add(e.get)

    }

    /** EXTERNAL REFERENCES **/

    if (filteredData.contains(EXTERNAL_REFERENCES)) {
      /*
       * External references are mapped onto vertices
       * and edges
       */
      val (v, e) = STIXRelations.createExternalReferences(entity, filteredData)

      if (v.isDefined) v.get.foreach(entities.add)
      if (e.isDefined) e.get.foreach(relations.add)

    }

    /** GRANULAR MARKINGS **/

    if (filteredData.contains(GRANULAR_MARKINGS)) {
      // TODO Granular markings are currently not supported
    }

    /** KILL CHAIN PHASES
     *
     * This refers to Attack-Pattern, Indicator, Malware and Tools
     */
    if (filteredData.contains(KILL_CHAIN_PHASES)) {
      /*
       * Kill chain phases are mapped onto vertices
       * and edges
       */
      val (v, e) = STIXRelations.createKillChainPhases(entity, filteredData)

      if (v.isDefined) v.get.foreach(entities.add)
      if (e.isDefined) e.get.foreach(relations.add)

    }

    /** OBJECT MARKINGS
     *
     * This field contains a list of references (identifiers) of
     * Object Markings that are associated with the STIX object.
     *
     * When creating the STIX object also a list of associated
     * edges is created, pointing from the STIX object to the
     * respective Object Marking.
     */
    if (filteredData.contains(OBJECT_MARKING_REFS)) {
      /*
       * Object markings are transformed (in contrast to external
       * reference) to edges only
       */
      val e = STIXRelations.createObjectMarkings(entity, filteredData)
      if (e.isDefined) e.get.foreach(relations.add)

    }

    /** OBJECT REFERENCES * */

    if (filteredData.contains(OBJECT_REFS)) {
      /*
       * Object references are transformed (in contrast to external
       * reference) to edges only
       */
      val e = STIXRelations.createObjectReferences(entity, filteredData)
      if (e.isDefined) e.get.foreach(relations.add)

     }

    val rowsJson = new JsonArray
    rowsJson.add(rowJson)

    entity.add(ROWS, rowsJson)
    entities.add(entity)

    (Some(entities), Some(relations))

  }

  def updateStixObject(entityId: String, entityType: String,
                       data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities = new JsonArray
    val relations = new JsonArray

    val entity = newEntity(entityId, entityType)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "update")
    val rowJson = new JsonObject
    /*
     * Retrieve patch data from data
     */
    val patch = getPatch(data)
    if (patch.isDefined) {

      val patchData = patch.get

      patchData.keySet.foreach(operation => {
        var properties = patchData(operation)
          .asInstanceOf[Map[String, List[Any]]]
          .map{
            case(k,v) => (k.replace("-", "_"),v)}
        /**
         * CREATED BY
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to Identity
         * objects
         */
        if (properties.contains(CREATED_BY_REF)) {
          val values = properties(CREATED_BY_REF)
          values match {
            case references: List[_] =>
              references.foreach(reference => {

                val e = STIXRelations.buildCreatedBy(entity, reference.asInstanceOf[String], operation)
                if (e.isDefined) relations.add(e.get)

              })
            case _ =>
              throw new Exception(s"The `created_by_ref` patch is not a List[String].")
          }

          properties = properties.filterKeys(k => !(k == CREATED_BY_REF))

        }
        /**
         * EXTERNAL REFERENCES
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to external
         * objects
         */
        if (properties.contains(EXTERNAL_REFERENCES)) {
          val values = properties(EXTERNAL_REFERENCE)
          values match {
            case references: List[_] =>
              references.foreach(reference => {

                val e = STIXRelations.buildExternalReference(entity, reference.asInstanceOf[String], operation)
                if (e.isDefined) relations.add(e.get)

              })
            case _ =>
              throw new Exception(s"The `external_references` patch is not a List[String].")
          }

          properties = properties.filterKeys(k => !(k == EXTERNAL_REFERENCES))

        }
        /**
         * KILL CHAIN PHASES
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to external
         * kill chain objects
         */
        if (properties.contains(KILL_CHAIN_PHASES)) {
          val values = properties(KILL_CHAIN_PHASES)
          values match {
            case references: List[_] =>
              references.foreach(reference => {

                val e = STIXRelations.buildKillChainPhase(entity, reference.asInstanceOf[String], operation)
                if (e.isDefined) relations.add(e.get)

              })
            case _ =>
              throw new Exception(s"The `kill_chain_phases` patch is not a List[String].")
          }

          properties = properties.filterKeys(k => !(k == KILL_CHAIN_PHASES))

        }
        /**
         * OBJECT LABELS
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to external
         * object label objects
         */
        if (properties.contains(LABELS)) {
          /*
           * The current implementation treats object labels
           * as `keywords` or `tags`. OpenCTI's approach to
           * describe each label as an entity is not supported
           * here.
           *
           * The consequence is, that update or patch requests
           * referring to `labels` cannot be executed.
           */
          properties = properties.filterKeys(k => !(k == LABELS))
        }
        /**
         * OBJECT MARKINGS
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to external
         * object marking objects
         */
        if (properties.contains(OBJECT_MARKING_REFS)) {
          val values = properties(OBJECT_MARKING_REFS)
          values match {
            case references: List[_] =>
              references.foreach(reference => {

                val e = STIXRelations.buildObjectMarking(entity, reference.asInstanceOf[String], operation)
                if (e.isDefined) relations.add(e.get)

              })
            case _ =>
               throw new Exception(s"The `object_marking_refs` patch is not a List[String].")
          }

          properties = properties.filterKeys(k => !(k == OBJECT_MARKING_REFS))

        }
        /**
         * OBJECT REFERENCES
         *
         * The current implementation expects that the values
         * provided by the patch are identifiers to external
         * object references
         */
        if (properties.contains(OBJECT_REFS)) {
         val values = properties(OBJECT_REFS)
          values match {
            case references: List[_] =>
              references.foreach(reference => {

                val e = STIXRelations.buildObjectReference(entity, reference.asInstanceOf[String], operation)
                if (e.isDefined) relations.add(e.get)

              })
            case _ =>
              throw new Exception(s"The `object_refs` patch is not a List[String].")
          }

          properties = properties.filterKeys(k => !(k == OBJECT_REFS))

        }
        /**
         * HASHES
         *
         * This implementation expects that `hashes` is an attribute
         * name within a certain patch; relevant, however, are the
         * hash values associated with the hash algorithm.
         */
        if (properties.contains(HASHES)) {
          val values = properties(HASHES)
          values.head match {
            case _: Map[_, _] =>
              val hashes = values.asInstanceOf[List[Map[String, String]]]
              /*
               * We expect the hash properties as [Map] with fields
               * `algorithm` and `hash`.
               */
              hashes.foreach(hash => {
                /*
                 * This implementation expects that a certain
                 * algorithm cannot be added, replaced or deleted
                 */
                val metaJson = new JsonObject
                metaJson.addProperty(ACTION, operation)

                val attrJson = new JsonObject
                attrJson.add(METADATA, metaJson)

                attrJson.addProperty(TYPE, "String")
                attrJson.addProperty(VALUE, hash("hash"))

                rowJson.add(hash("algorithm"), attrJson)

              })
            case _ =>
              throw new Exception(s"The `hashes` patch is not a List[Map[String,String].")
          }

          properties = properties.filterKeys(k => !(k == HASHES))

        }
        /*
         * Update remaining properties of the SDO or SCO
         */
        val keys = properties.keySet
        fillRow(payload = properties, keys = keys, rowJson = rowJson, action = operation)

      })

      val rowsJson = new JsonArray
      rowsJson.add(rowJson)

      entity.add(ROWS, rowsJson)
      entities.add(entity)

      (Some(entities), Some(relations))

    }
    else {
      (None, None)
    }
  }

}
