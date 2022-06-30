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
import java.nio.charset.StandardCharsets

object STIXRelations extends STIXBase {

  /**
   * OpenCTI publishes this event object with basic fields
   * of the internal `from` object and a `to` reference
   * that is derived from entity_type
   */
  def createObservableRelationship(entityId: String, entityType: String,
                                   data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val relations = new JsonArray

    try {
      /*
       * Ensure that all provided keys have underscores
       * instead of hyphens
       */
      val filteredData = data.map{
        case(k,v) => (k.replace("-", "_"),v)}

      /** FROM
       *
       * The basic fields contain `source_ref` and also
       * `x_opencti_source_ref`. It is expected that one
       * of these fields contains the `from` identifier.
       */
      val fromId =
        if (filteredData.contains("source_ref")) {
          filteredData("source_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_source_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_source_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `from` identifier.")
        }

      val fromType = fromId.split("--").head

      val relationJson = newNGSIRelation(fromId, fromType)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")

      /** TO **/

      val toField = entityType.replace("-", "_") + "_ref"
      val toId =
        if (filteredData.contains(toField)) {
          filteredData(toField).asInstanceOf[String]
        }
        else
          throw new Exception(s"Relationship does not contain a `to` identifier.")

      refJson.addProperty(VALUE, toId)

      val toType = toId.split("--").head
      val refAttr = s"ref${toCamelCase(toType)}"

      relationJson.add(refAttr, refJson)
      relations.add(relationJson)

      (None, Some(relations))

    } catch {
      case t:Throwable =>
        val message = s"Creating an observable relationship failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)
    }

  }
  /**
   * A helper method to create a STIX relation
   * as an NGSI entity and 2 assigned relations
   */
  def createRelationship(entityId: String, entityType: String,
                         data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    /*
     * A certain STIX relationship is mapped onto
     * sets of NGSI entities and relationships
     */
    val entities = new JsonArray
    val relations = new JsonArray
    /*
     * NGSI compliant relations do not contain
     * any attributes: Therefore, a STIX relation
     * is built as an entity with 2 NGSI relations:
     *
     *      Source --> Relation --> Target
     */
    try {

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
      val filteredData = data.map{
        case(k,v) => (k.replace("-", "_"),v)}

      /*
       * The following attributes are added to the
       * Relationship entity
       */
      Seq(
        "description",
        "name",
        "relationship_type",
        "start_time",
        "stop_time"
      ).foreach(attrName => {

        try {

          if (filteredData.contains(attrName)) {

            val attrVal = filteredData(attrName)
            val attrType = getBasicType(attrVal)

            putValue(
              attrName = attrName,
              attrType = attrType,
              attrVal  = attrVal,
              rowJson  = rowJson)
          }

        } catch {
          case _:Throwable => /* Do nothing */
        }

      })

      val rowsJson = new JsonArray
      rowsJson.add(rowJson)

      entity.add(ROWS, rowsJson)
      entities.add(entity)

      /** FROM: Source --> Relationship (entity)
       *
       * The reference, source_ref or x_opencti_source_ref,
       * contains the ID of the (from) SDO.
       *
       * This implementation defines `Relation` as an [Edge] that
       * points from the SDO to another one.
       *
       * In contrast to `Sighting`, OpenCTI leverages [String]
       * instead of a List[String]
       *
       */
      val sourceId =
        if (filteredData.contains("source_ref")) {
          filteredData("source_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_source_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_source_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `from` identifier.")
        }

      /*
       * All identifiers, excluding those used in the deprecated Cyber
       Observable Container, MUST follow the form object-type--UUID, where
       * object-type is the exact value from the type property of the object
       * being identified or referenced.
       */
      val sourceType = sourceId.split("--").head
      val sourceJson = newNGSIRelation(sourceId, sourceType)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      sourceJson.addProperty(ACTION, "create")

      val sourceRef = new JsonObject
      sourceRef.addProperty(TYPE, "Relationship")
      sourceRef.addProperty(VALUE, entity.get(ID).getAsString)

      sourceJson.add("refRelationship", sourceRef)
      relations.add(sourceJson)

      /** TO: Relationship (entity) --> Target
       *
       * In contrast to `Sighting`, OpenCTI leverages [String]
       * instead of a List[String]
       */
      val targetId =
        if (filteredData.contains("target_ref")) {
          filteredData("target_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_target_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_target_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `to` identifier.")
        }

      val targetType = targetId.split("--").head
      val targetJson = new JsonObject

      targetJson.addProperty(ID, entity.get(ID).getAsString)
      targetJson.addProperty(TYPE, entity.get(TYPE).getAsString)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      targetJson.addProperty(ACTION, "create")

      val targetRef = new JsonObject
      targetRef.addProperty(TYPE, "Relationship")
      targetRef.addProperty(VALUE, targetId)

      val targetRefAttr = s"ref${toCamelCase(targetType)}"
      targetJson.add(targetRefAttr, targetRef)

      relations.add(targetJson)

      (Some(entities), Some(relations))

    } catch {
      case t:Throwable =>
        val message = s"Creating relationship failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)
    }

  }

  /**
   * This method transforms a STIX v2.1 `Sighting`, which connects
   * a STIX Domain Object or Cyber Observable with an instance that
   * recognized this object or observable.
   *
   * From an NGSI perspective, a `sighting` primarily is an entity
   * with a variety of relations
   */
  def createSighting(entityId: String, entityType: String,
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
    val filteredData = data.map{
      case(k,v) => (k.replace("-", "_"),v)}

    /*
     * The remaining part of this method distinguishes
     * between fields that describe relations and those
     * that carry object properties.
     */

    /** SIGHTING OF REF
     *
     * The reference, sighting_of_ref, contains the ID of the SDO
     * that was sighted, which e.g. can be an indicator or cyber
     * observable.
     *
     * This implementation defines `Sighting` as an [Edge] that
     * points from the SDO to the (identity) that also sighted
     * the indicator or cyber observable.
     */
    val sightingOfRef = buildSightingRef(entity, filteredData)
    if (sightingOfRef.isDefined) relations.add(sightingOfRef.get)

    /** WHERE SIGHTED REFS
     *
     * A list of ID references to the Identity or Location objects
     * describing the entities or types of entities that saw the sighting.
     */
    val sightedRefs = getWhereSightedRefs(filteredData)
    sightedRefs.foreach(sightedRef => {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      relationJson.add(ID, entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, sightedRef)

      val sightedRefType = sightedRef.split("--").head
      val refAttr = s"ref${toCamelCase(sightedRefType)}"

      relationJson.add(refAttr, refJson)

    })
    /** OBSERVED DATA REFS
     *
     * A list of ID references to the Observed Data objects that
     * contain the raw cyber data for this Sighting.
     */
    val observedDataRefs = getObservedDataRefs(filteredData)
    observedDataRefs.foreach(observedDataRef => {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      relationJson.add(ID, entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, observedDataRef)

      val observedDataRefType = observedDataRef.split("--").head
      val refAttr = s"ref${toCamelCase(observedDataRefType)}"

      relationJson.add(refAttr, refJson)

    })

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

    /*
     * The following attributes are added as sighting
     * properties
     */
    Seq(
      "attribute_count",
      "confidence",
      "count",
      "created",
      "description",
      "first_seen",
      "last_seen",
      "modified",
      "name").foreach(attrName => {

      try {

        if (filteredData.contains(attrName)) {

          val attrVal = filteredData(attrName)
          val attrType = getBasicType(attrVal)

          putValue(
            attrName = attrName,
            attrType = attrType,
            attrVal  = attrVal,
            rowJson  = rowJson)
        }

      } catch {
        case _:Throwable => /* Do nothing */
      }

    })

    val rowsJson = new JsonArray
    rowsJson.add(rowJson)

    entity.add(ROWS, rowsJson)
    entities.add(entity)

    (Some(entities), Some(relations))

  }

  /** WHERE SIGHTED REFS
   *
   * A list of ID references to the Identity or Location objects
   * describing the entities or types of entities that saw the sighting.
   */
  def getWhereSightedRefs(data:Map[String,Any]):List[String] = {

    if (data.contains("where_sighted_refs"))
      data("where_sighted_refs").asInstanceOf[List[String]]

    else if (data.contains("x_opencti_where_sighted_refs"))
      data("x_opencti_where_sighted_refs").asInstanceOf[List[String]]

    else
      List.empty[String]
  }
  /** OBSERVED DATA REFS
   *
   * A list of ID references to the Observed Data objects that
   * contain the raw cyber data for this Sighting.
   */
  def getObservedDataRefs(data:Map[String,Any]):List[String] = {

    if (data.contains("observed_data_refs"))
      data("observed_data_refs").asInstanceOf[List[String]]

    else if (data.contains("x_opencti_observed_data_refs"))
      data("x_opencti_observed_data_refs").asInstanceOf[List[String]]

    else
      List.empty[String]
  }

  def buildSightingRef(entity:JsonObject, data:Map[String,Any], action:String="create"):Option[JsonObject] = {

    try {
      /*
         * The NGSI compliant specification has the
         * following format:
         *
         * {
         *
         *  --- FROM ---
         *
         *  "id": "...",
         *  "type": "...",
         *
         *  --- TO ---
         *
         *  "ref<Identity>" : {
         *    "type": "Relationship",
         *    "value": "..."
         *  }
         * }
         */
      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID, entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")

      /** SIGHTING OF REF
       *
       * The reference, sighting_of_ref, contains the ID of the SDO
       * that was sighted, which e.g. can be an indicator or cyber
       * observable.
       *
       * This implementation defines `Sighting` as an [Edge] that
       * points from the SDO to the (identity) that also sighted
       * the indicator or cyber observable.
       */
      val reference =
        if (data.contains("sighting_of_ref")) {
          data("sighting_of_ref").asInstanceOf[String]
        }
        else if (data.contains("x_opencti_sighting_of_ref")) {
          data("x_opencti_sighting_of_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Sighting does not contain a `from` identifier.")
        }

      refJson.addProperty(VALUE, reference)

      val referenceType = reference.split("--").head
      val refAttr = s"ref${toCamelCase(referenceType)}"

      relationJson.add(refAttr, refJson)
      Some(relationJson)

    }catch {
      case t:Throwable =>
        val message = s"Creating sighting reference failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  /**
   * This method deletes an [Edge] object from the respective
   * Ignite cache. Removing selected edge properties is part
   * of the update implementation (with patch action `remove`)
   */
  def deleteObservableRelationship(entityId: String, entityType: String,
                                   data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val relations = new JsonArray

    try {
      /*
       * Ensure that all provided keys have underscores
       * instead of hyphens
       */
      val filteredData = data.map{
        case(k,v) => (k.replace("-", "_"),v)}

      /** FROM
       *
       * The basic fields contain `source_ref` and also
       * `x_opencti_source_ref`. It is expected that one
       * of these fields contains the `from` identifier.
       */
      val fromId =
        if (filteredData.contains("source_ref")) {
          filteredData("source_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_source_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_source_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `from` identifier.")
        }

      val fromType = fromId.split("--").head

      val relationJson = newNGSIRelation(fromId, fromType)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")

      /** TO **/

      val toField = entityType.replace("-", "_") + "_ref"
      val toId =
        if (filteredData.contains(toField)) {
          filteredData(toField).asInstanceOf[String]
        }
        else
          throw new Exception(s"Relationship does not contain a `to` identifier.")

      refJson.addProperty(VALUE, toId)

      val toType = toId.split("--").head
      val refAttr = s"ref${toCamelCase(toType)}"

      relationJson.add(refAttr, refJson)
      relations.add(relationJson)

      (None, Some(relations))

    } catch {
      case t:Throwable =>
        val message = s"Creating an observable relationship failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)
    }

  }
  /**
   * This method deletes an [Edge] object from the respective
   * Ignite cache. Removing selected edge properties is part
   * of the update implementation (with patch action `remove`)
   */
  def deleteRelationship(entityId: String, entityType: String,
                         data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    /*
     * A certain STIX relationship is mapped onto
     * sets of NGSI entities and relationships
     */
    val entities = new JsonArray
    val relations = new JsonArray
    /*
     * NGSI compliant relations do not contain
     * any attributes: Therefore, a STIX relation
     * is built as an entity with 2 NGSI relations:
     *
     *      Source --> Relation --> Target
     */
    try {

      val entity = newEntity(entityId, entityType)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      entity.addProperty(ACTION, "create")

      entity.add(ROWS, new JsonArray)
      entities.add(entity)
      /*
       * Ensure that all provided keys have underscores
       * instead of hyphens
       */
      val filteredData = data.map{
        case(k,v) => (k.replace("-", "_"),v)}

      /** FROM: Source --> Relationship (entity)
       *
       * The reference, source_ref or x_opencti_source_ref,
       * contains the ID of the (from) SDO.
       *
       * This implementation defines `Relation` as an [Edge] that
       * points from the SDO to another one.
       *
       * In contrast to `Sighting`, OpenCTI leverages [String]
       * instead of a List[String]
       *
       */
      val sourceId =
        if (filteredData.contains("source_ref")) {
          filteredData("source_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_source_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_source_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `from` identifier.")
        }

      /*
       * All identifiers, excluding those used in the deprecated Cyber
       Observable Container, MUST follow the form object-type--UUID, where
       * object-type is the exact value from the type property of the object
       * being identified or referenced.
       */
      val sourceType = sourceId.split("--").head
      val sourceJson = newNGSIRelation(sourceId, sourceType)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      sourceJson.addProperty(ACTION, "create")

      val sourceRef = new JsonObject
      sourceRef.addProperty(TYPE, "Relationship")
      sourceRef.addProperty(VALUE, entity.get(ID).getAsString)

      sourceJson.add("refRelationship", sourceRef)
      relations.add(sourceJson)

      /** TO: Relationship (entity) --> Target
       *
       * In contrast to `Sighting`, OpenCTI leverages [String]
       * instead of a List[String]
       */
      val targetId =
        if (filteredData.contains("target_ref")) {
          filteredData("target_ref").asInstanceOf[String]
        }
        else if (filteredData.contains("x_opencti_target_ref")) {
          /*
           * This is a fallback approach, but should not
           * happen in an OpenCTI event stream
           */
          filteredData("x_opencti_target_ref").asInstanceOf[String]
        }
        else {
          throw new Exception(s"Relationship does not contain a `to` identifier.")
        }

      val targetType = targetId.split("--").head
      val targetJson = new JsonObject

      targetJson.addProperty(ID, entity.get(ID).getAsString)
      targetJson.addProperty(TYPE, entity.get(TYPE).getAsString)
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      targetJson.addProperty(ACTION, "create")

      val targetRef = new JsonObject
      targetRef.addProperty(TYPE, "Relationship")
      targetRef.addProperty(VALUE, targetId)

      val targetRefAttr = s"ref${toCamelCase(targetType)}"
      targetJson.add(targetRefAttr, targetRef)

      relations.add(targetJson)

      (Some(entities), Some(relations))

    } catch {
      case t:Throwable =>
        val message = s"Deleting relationship failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)
    }

  }

  /**
   * This method deletes an [Edge] object from the respective
   * Ignite cache. Removing selected edge properties is part
   * of the update implementation (with patch action `remove`)
   */
  def deleteSighting(entityId: String, entityType: String,
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
    entity.add(ROWS, new JsonArray)

    entities.add(entity)
    /*
     * Ensure that all provided keys have underscores
     * instead of hyphens
     */
    val filteredData = data.map{
      case(k,v) => (k.replace("-", "_"),v)}

    /*
     * The remaining part of this method focuses on the
     * relations that refer to this STIX sighting, as these
     * it cannot be expected that the data destination
     * manages the deletion of relations properly.
     */

    /** SIGHTING OF REF
     *
     * The reference, sighting_of_ref, contains the ID of the SDO
     * that was sighted, which e.g. can be an indicator or cyber
     * observable.
     *
     * This implementation defines `Sighting` as an [Edge] that
     * points from the SDO to the (identity) that also sighted
     * the indicator or cyber observable.
     */
    val sightingOfRef = buildSightingRef(entity, filteredData)
    if (sightingOfRef.isDefined) relations.add(sightingOfRef.get)

    /** WHERE SIGHTED REFS
     *
     * A list of ID references to the Identity or Location objects
     * describing the entities or types of entities that saw the sighting.
     */
    val sightedRefs = getWhereSightedRefs(filteredData)
    sightedRefs.foreach(sightedRef => {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      relationJson.add(ID, entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, sightedRef)

      val sightedRefType = sightedRef.split("--").head
      val refAttr = s"ref${toCamelCase(sightedRefType)}"

      relationJson.add(refAttr, refJson)

    })
    /** OBSERVED DATA REFS
     *
     * A list of ID references to the Observed Data objects that
     * contain the raw cyber data for this Sighting.
     */
    val observedDataRefs = getObservedDataRefs(filteredData)
    observedDataRefs.foreach(observedDataRef => {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, "create")

      relationJson.add(ID, entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, observedDataRef)

      val observedDataRefType = observedDataRef.split("--").head
      val refAttr = s"ref${toCamelCase(observedDataRefType)}"

      relationJson.add(refAttr, refJson)

    })

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

    (Some(entities), Some(relations))

  }

  /**
   * A STIX (or OpenCTI) observable relationship does not
   * contain additional properties:
   *
   * Patch operations (add, remove or replace) must be ignored.
   *
   * Changing `from` or `to` identifiers via an update
   * request is currently not supported and must be mapped
   * onto (delete) -> (create) operations
   */
  def updateObservableRelationship(entityId: String, entityType: String,
                                   data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    (None, None)
  }

  /**
   * This method updates a STIX Relationship object,
   * and thereby focuses exclusively on the properties
   * of the object.
   *
   * Relations to other SDOs or SCOs cannot be changed
   */
  def updateRelationship(entityId: String, entityType: String,
                         data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities = new JsonArray
    val entity = newEntity(entityId, entityType)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "update")
    val rowJson = new JsonObject
    /*
     * Retrieve patch from data
     */
    val patch = getPatch(data)
    if (patch.isDefined) {

      val patchData = patch.get

      patchData.keySet.foreach(operation => {
        val properties = patchData(operation)
          .asInstanceOf[Map[String, List[Any]]]
          .map{
            case(k,v) => (k.replace("-", "_"),v)}

        /*
         * Update selected properties
         */
        val keys = Set(
          "description",
          "name",
          "relationship_type",
          "start_time",
          "stop_time")

        fillRow(payload = properties, keys = keys, rowJson = rowJson, action = operation)

      })

      val rowsJson = new JsonArray
      rowsJson.add(rowJson)

      entity.add(ROWS, rowsJson)
      entities.add(entity)

      (Some(entities), None)

    } else {
      (None, None)

    }

  }

  def updateSighting(entityId: String, entityType: String,
                     data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities = new JsonArray
    val entity = newEntity(entityId, entityType)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "update")
    val rowJson = new JsonObject
    /*
     * Retrieve patch from data
     */
    val patch = getPatch(data)
    if (patch.isDefined) {

      val patchData = patch.get
      patchData.keySet.foreach(operation => {
        val properties = patchData(operation)
          .asInstanceOf[Map[String, List[Any]]]
          .map{
            case(k,v) => (k.replace("-", "_"),v)}
        /*
         * Update selected properties
         */
        val keys = Set(
          "attribute_count",
          "confidence",
          "count",
          "created",
          "description",
          "first_seen",
          "last_seen",
          "modified",
          "name")

        fillRow(payload = properties, keys = keys, rowJson = rowJson, action = operation)

      })

      val rowsJson = new JsonArray
      rowsJson.add(rowJson)

      entity.add(ROWS, rowsJson)
      entities.add(entity)

      (Some(entities), None)

    } else {
      (None, None)

    }

  }

  def buildCreatedBy(entity: JsonObject, reference: String, action:String): Option[JsonObject] = {

    try {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, reference)

      val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_IDENTITY)}"
      relationJson.add(refAttr, refJson)

      Some(relationJson)

    } catch {
      case t: Throwable =>

        val message = s"Creating created_by failed: ${t.getLocalizedMessage}"
        error(message)

        None

    }

  }

  /**
   * This method creates an NGSI relation for an SDO
   * or SCO to describe the relationship to the `Identity`
   * that created the object or observable
   */
  def buildCreatedBy(entity: JsonObject, data: Map[String, Any], action:String): Option[JsonObject] = {

    val createdBy = data(CREATED_BY_REF)
    try {
      /*
       * The NGSI compliant specification has the
       * following format:
       *
       * {
       *
       *  --- FROM ---
       *
       *  "id": "...",
       *  "type": "...",
       *
       *  --- TO ---
       *
       *  "ref<Identity>" : {
       *    "type": "Relationship",
       *    "value": "..."
       *  }
       * }
       */
      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      /* TO
       *
       * OpenCTI supports two different formats to describe
       * the created_by_ref field:
       *
       * (1) [String]: identifier
       *
       * (2) [Map[String,String]: 'reference', 'value', 'x_opencti_internal_id'
       *
       * In both cases, an identifier is provided to reference
       * the creator (identity object)
       */
      val value = createdBy match {
        /*
         * This is the expected default description of references
         * to object markings
         */
        case v: String => v
        /*
         * The OpenCTI code base also specifies the subsequent format
         * to describe reference to the author or creator of a STIX
         * object.
         */
        case v: Map[_, _] =>
          v.asInstanceOf[Map[String, String]]("value")

        case _ =>
          throw new Exception("The data type of the created_by field is not supported.")

      }

      refJson.addProperty(VALUE, value)
      val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_IDENTITY)}"

      relationJson.add(refAttr, refJson)
      Some(relationJson)

    } catch {
      case t: Throwable =>
        val message = s"Building `created_by` relation failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }
  /**
   * A helper method to create an NGSI compliant
   * relationship between the provided entity and
   * the referenced (existing) kill chain phase.
   */
  def buildKillChainPhase(entity: JsonObject, reference: String, action:String): Option[JsonObject] = {

    try {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, reference)

      val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_KILL_CHAIN_PHASE)}"
      relationJson.add(refAttr, refJson)

      Some(relationJson)

    } catch {
      case t: Throwable =>

        val message = s"Creating kill chain phase failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  /**
   * This method transforms a list of kill chain phases
   * into a set of kill chain phase entities and relates
   * them to the provided entity
   */
  def createKillChainPhases(entity: JsonObject,
                            data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities  = new JsonArray
    val relations = new JsonArray

    try {

      val killChainPhases = data(KILL_CHAIN_PHASES).asInstanceOf[List[Any]]
      killChainPhases.foreach(killChainPhase => {

        val relationJson = new JsonObject
        /*
         * The current implementation adds the data
         * operation associated with this relation as
         * a temporary non-NGSI compliant field `action`
         */
        relationJson.addProperty(ACTION, "create")

        relationJson.add(ID,   entity.get(ID))
        relationJson.add(TYPE, entity.get(TYPE))

        val refJson = new JsonObject
        refJson.addProperty(TYPE, "Relationship")
        /* TO
         *
         * OpenCTI supports two different formats to describe
         * kill chain phases:
         *
         * (1) fields: 'kill_chain_name', 'phase_name'
         *
         * (2) fields: 'reference', 'value', 'x_opencti_internal_id'
         */
        killChainPhase match {
          case _: Map[_, _] =>
            val value = killChainPhase.asInstanceOf[Map[String, Any]]
            if (value.contains("value")) {
              /*
               * The `value` field is the reference identifier
               * of the Kill Chain object, that is required to
               * exist.
               */
              refJson.addProperty(VALUE, value("value").asInstanceOf[String])
              val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_KILL_CHAIN_PHASE)}"

              relationJson.add(refAttr, refJson)
              relations.add(relationJson)
            }
            else {
              /*
               * In this case, the processing of the kill chain phase
               * demands to create the kill chain phase as entity
               */
              val entityJson = newKillChainPhase(data)
              entities.add(entityJson)

              refJson.addProperty(VALUE, entityJson.get(ID).getAsString)
              val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_KILL_CHAIN_PHASE)}"

              relationJson.add(refAttr, refJson)
              relations.add(relationJson)

            }

          case _ =>
           throw new Exception(s"The data type of the provided kill chain phase is not supported.")
        }

      })

      (Some(entities), Some(relations))

    } catch {
      case t: Throwable =>

        val message = s"Creating external references failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)

    }

  }

  /**
   * Helper method to create an NGSI compliant
   * Kill Chain Phase entity
   */
  private def newKillChainPhase(data:Map[String,Any]):JsonObject = {

    val kill_chain_name = data.getOrElse("kill_chain_name", "").asInstanceOf[String]
    val phase_name = data.getOrElse("phase_name", "").asInstanceOf[String]

    val identifier = {
      val bytes = Seq(kill_chain_name, phase_name).mkString("|").getBytes(StandardCharsets.UTF_8)
      s"kill-chain-phase-${java.util.UUID.nameUUIDFromBytes(bytes).toString}"
    }

    val entity = newEntity(identifier, STIX.ENTITY_TYPE_KILL_CHAIN_PHASE)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "create")
    val rowJson = new JsonObject

    // KILL CHAIN PHASE
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, kill_chain_name)

      rowJson.add("killChainPhase", attrJson)
    }

    // PHASE NAME
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, phase_name)

      rowJson.add("phaseName", attrJson)
    }

    val rowsJson = new JsonArray
    rowsJson.add(rowJson)

    entity.add(ROWS, rowsJson)
    entity

  }

  def buildObjectMarking(entity: JsonObject, reference: String, action:String): Option[JsonObject] = {

    try {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, reference)

      val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_OBJECT_MARKING)}"
      relationJson.add(refAttr, refJson)

      Some(relationJson)

    } catch {
      case t: Throwable =>

        val message = s"Creating object marking failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  /**
   * This method creates edges between a STIX domain object
   * or cyber observable and assigned `object_marking_refs`.
   */
  def createObjectMarkings(entity: JsonObject, data: Map[String, Any]): Option[JsonArray] = {

    val relations = new JsonArray
    val markings  = data(OBJECT_MARKING_REFS).asInstanceOf[List[Any]]

    markings.foreach(marking => {
      try {

        val relationJson = new JsonObject
        /*
         * The current implementation adds the data
         * operation associated with this relation as
         * a temporary non-NGSI compliant field `action`
         */
        relationJson.addProperty(ACTION, "create")

        relationJson.add(ID,   entity.get(ID))
        relationJson.add(TYPE, entity.get(TYPE))

        val refJson = new JsonObject
        refJson.addProperty(TYPE, "Relationship")
        /*
         * OpenCTI ships with two different formats to describe
         * an object marking:
         *
         * (1) List[String] - identifiers
         *
         * (2) List[Map[String, String]]
         *     fields: 'reference', 'value', 'x_opencti_internal_id'
         */
        val value = marking match {
          /*
           * This is the expected default description of references
           * to object markings
           */
          case v: String => v
          /*
           * The OpenCTI code base also specifies the subsequent format
           * to describe reference to object markings for a STIX object.
           */
          case _: Map[_, _] =>
            marking.asInstanceOf[Map[String, Any]]("value").asInstanceOf[String]

          case _ =>
            throw new Exception(s"The data type of the provided object marking is not supported.")
        }

        relationJson.addProperty(VALUE, value)

        /* marking-definition */
        val referenceType = value.split("--").head
        val refAttr = s"ref${toCamelCase(referenceType)}"

        relationJson.add(refAttr, refJson)
        relations.add(relationJson)

      } catch {
        case t:Throwable =>
          val message = s"Creation of object marking failed: ${t.getLocalizedMessage}"
          error(message)

      }

     })

    Some(relations)

  }

  /** OBJECT REFERENCES * */

  def buildObjectReference(entity: JsonObject, reference: String, action:String): Option[JsonObject] = {

    try {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, reference)

      val referenceType = reference.split("--").head
      val refAttr = s"ref${toCamelCase(referenceType)}"

      relationJson.add(refAttr, refJson)
      Some(relationJson)

    } catch {
      case t: Throwable =>

        val message = s"Creating object reference failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  def createObjectReferences(entity: JsonObject, data: Map[String, Any]): Option[JsonArray] = {

    val relations = new JsonArray
    val references = data(OBJECT_REFS).asInstanceOf[List[Any]]

    references.foreach(reference => {

      try {

        val relationJson = new JsonObject
        /*
         * The current implementation adds the data
         * operation associated with this relation as
         * a temporary non-NGSI compliant field `action`
         */
        relationJson.addProperty(ACTION, "create")

        relationJson.add(ID,   entity.get(ID))
        relationJson.add(TYPE, entity.get(TYPE))

        val refJson = new JsonObject
        refJson.addProperty(TYPE, "Relationship")
        /*
         * OpenCTI ships with two different formats to describe
         * an object references:
         *
         * (1) List[String] - identifiers
         *
         * (2) List[Map[String, String]]
         *     fields: 'reference', 'value', 'x_opencti_internal_id'
         */
        val value = reference match {
          /*
           * This is the default case, where a STIX object contains
           * a list of references to other STIX objects.
           */
          case v: String => v

          case _: Map[_, _] =>
            reference.asInstanceOf[Map[String, Any]]("value").asInstanceOf[String]

          case _ =>
             throw new Exception(s"The data type of the provided object reference is not supported.")
        }

        relationJson.addProperty(VALUE, value)

        val referenceType = value.split("--").head
        val refAttr = s"ref${toCamelCase(referenceType)}"

        relationJson.add(refAttr, refJson)
        relations.add(relationJson)

      } catch {
        case t:Throwable =>

          val message = s"Creation of object reference failed: ${t.getLocalizedMessage}"
          error(message)

      }
    })

    Some(relations)

  }

  /**
   * A helper method to create an NGSI compliant
   * relationship between the provided entity and
   * the referenced (existing) external reference.
   */
  def buildExternalReference(entity: JsonObject, reference: String, action:String): Option[JsonObject] = {

    try {

      val relationJson = new JsonObject
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      relationJson.addProperty(ACTION, action)

      relationJson.add(ID,   entity.get(ID))
      relationJson.add(TYPE, entity.get(TYPE))

      val refJson = new JsonObject
      refJson.addProperty(TYPE, "Relationship")
      refJson.addProperty(VALUE, reference)

      val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_EXTERNAL_REFERENCE)}"
      relationJson.add(refAttr, refJson)

      Some(relationJson)

    } catch {
      case t: Throwable =>

        val message = s"Creating external reference failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  /**
   * External references are used to describe pointers to information
   * represented outside of STIX. For example, a Malware object could
   * use an external reference to indicate an ID for that malware in
   * an external database or a report could use references to represent
   * source material.
   */
  def createExternalReferences(entity: JsonObject,
                               data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {

    val entities = new JsonArray
    val relations = new JsonArray

    try {

      val references = data(EXTERNAL_REFERENCES).asInstanceOf[List[Any]]
      references.foreach(reference => {

        val relationJson = new JsonObject
        /*
         * The current implementation adds the data
         * operation associated with this relation as
         * a temporary non-NGSI compliant field `action`
         */
        relationJson.addProperty(ACTION, "create")

        relationJson.add(ID,   entity.get(ID))
        relationJson.add(TYPE, entity.get(TYPE))

        val refJson = new JsonObject
        refJson.addProperty(TYPE, "Relationship")
        /* TO
         *
         * OpenCTI supports two different formats to describe
         * external references:
         *
         * (1) fields: 'source_name', 'description', 'url', 'hashes', 'external_id'
         *
         * (2) fields: 'reference', 'value', 'x_opencti_internal_id'
         */
        reference match {
          case _: Map[_, _] =>
            val value = reference.asInstanceOf[Map[String, Any]]
            if (value.contains("value")) {
              /*
                * The `value` field contains the identifier
                * of the External Reference object, that is
                * required to exist.
                */
              refJson.addProperty(VALUE, value("value").asInstanceOf[String])
              val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_EXTERNAL_REFERENCE)}"

              relationJson.add(refAttr, refJson)
              relations.add(relationJson)

            }
            else {
              /*
               * In this case, the processing of the external reference
               * demands to create the external reference as entity
               */
              val entityJson = newExternalReference(data)
              entities.add(entityJson)

              refJson.addProperty(VALUE, entityJson.get(ID).getAsString)
              val refAttr = s"ref${toCamelCase(STIX.ENTITY_TYPE_EXTERNAL_REFERENCE)}"

              relationJson.add(refAttr, refJson)
              relations.add(relationJson)

           }
          case _ =>
            throw new Exception(s"The data type of the provided external reference is not supported.")
        }

      })

      (Some(entities), Some(relations))

    } catch {
      case t: Throwable =>

        val message = s"Creating external references failed: ${t.getLocalizedMessage}"
        error(message)

        (None, None)

    }
  }

  def newExternalReference(data:Map[String,Any]):JsonObject = {

    val source_name = data.getOrElse("source_name", "").asInstanceOf[String]
    val description = data.getOrElse("description", "").asInstanceOf[String]

    val url         = data.getOrElse("url", "").asInstanceOf[String]
    val external_id = data.getOrElse("external_id", "").asInstanceOf[String]
    /*
     * hashes is optional, but when provided specifies a dictionary of hashes
     * for the contents of the url:
     * hashes: {
     *  "SHA-256": "..."
     * }
     */
    val hashes =
      if (data.contains(HASHES)) transformHashes(data) else Map.empty[String,String]

    val identifier = {

      val bytes = (
        Seq(source_name, description, url, external_id) ++
        STIX.STANDARD_HASHES.map(algorithm => hashes.getOrElse(algorithm, ""))
        ).mkString("|").getBytes(StandardCharsets.UTF_8)

      s"external-reference-${java.util.UUID.nameUUIDFromBytes(bytes).toString}"

    }

    val entity = newEntity(identifier, STIX.ENTITY_TYPE_EXTERNAL_REFERENCE)
    /*
     * The current implementation adds the data
     * operation associated with this relation as
     * a temporary non-NGSI compliant field `action`
     */
    entity.addProperty(ACTION, "create")
    val rowJson = new JsonObject

    // SOURCE NAME
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, source_name)

      rowJson.add("sourceName", attrJson)
    }
    // DESCRIPTION
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, description)

      rowJson.add("description", attrJson)
    }
    // URL
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, url)

      rowJson.add("url", attrJson)
    }
    // EXTERNAL ID
    {
      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, external_id)

      rowJson.add("externalId", attrJson)
    }
    // HASHES
    STIX.STANDARD_HASHES.foreach(algorithm => {

      val hash = hashes.getOrElse(algorithm, "")

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.addProperty(VALUE, hash)

      rowJson.add(algorithm, attrJson)

    })

    val rowsJson = new JsonArray
    rowsJson.add(rowJson)

    entity.add(ROWS, rowsJson)
    entity

  }

}
