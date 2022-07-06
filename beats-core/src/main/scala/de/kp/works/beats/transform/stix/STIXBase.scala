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
import de.kp.works.beats.transform.BeatsTransform

import scala.collection.mutable

trait STIXBase extends BeatsTransform {

  val CREATED_BY_REF:String       = "created_by_ref"
  val EXTERNAL_REFERENCE:String   = "external_reference"
  val EXTERNAL_REFERENCES:String  = "external_references"
  val GRANULAR_MARKINGS:String    = "granular_markings"
  val HASHES:String               = "hashes"
  val KILL_CHAIN_PHASE:String     = "kill_chain_phase"
  val KILL_CHAIN_PHASES:String    = "kill_chain_phases"
  val LABELS:String               = "labels"
  val OBJECT_LABEL:String         = "object_label"
  val OBJECT_MARKING_REFS:String  = "object_marking_refs"
  val OBJECT_REFS:String          = "object_refs"
  /**
   * INTERNAL EDGE LABELS
   */
  val HAS_CREATED_BY: String = "has-created-by"
  val HAS_EXTERNAL_REFERENCE: String = "has-external-reference"
  val HAS_KILL_CHAIN_PHASE: String = "has-kill-chain-phase"
  val HAS_OBJECT_LABEL: String = "has-object-label"
  val HAS_OBJECT_MARKING: String = "has-object-marking"
  val HAS_OBJECT_REFERENCE: String = "has-object-reference"
  /**
   * A helper method to create an initial NGSI compliant
   * entity from the provided STIX entity identifier and
   * type
   */
  protected def newNGSIEntity(entityId:String, entityType:String):JsonObject = {
    /*
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     *
     * Mapping STIX objects or observables to NGSI entities
     * and relations is faced with the fact, that references
     * in STIX (identifiers) follow the STIX naming convention,
     * but it is not guaranteed that the object and observable
     * type can be inferred correctly.
     *
     * Therefore, STIX related NGSI entity identifier do not
     * specify the <entity-type> as expected above.
     */
    val ngsiType = toCamelCase(entityType)
    val ngsiId = s"urn:ngsi-ld:$entityId"

    val ngsiJson = new JsonObject
    ngsiJson.addProperty(ID, ngsiId)
    ngsiJson.addProperty(TYPE, ngsiType)

    ngsiJson

  }
  /**
   * A helper method to create an initial NGSI compliant
   * relation from the provided STIX identifier and type
   */
  protected def newNGSIRelation(entityId:String, entityType:String):JsonObject = {
    /*
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     *
     * Mapping STIX objects or observables to NGSI entities
     * and relations is faced with the fact, that references
     * in STIX (identifiers) follow the STIX naming convention,
     * but it is not guaranteed that the object and observable
     * type can be inferred correctly.
     *
     * Therefore, STIX related NGSI entity identifier do not
     * specify the <entity-type> as expected above.
     */
    val ngsiType = toCamelCase(entityType)
    val ngsiId = s"urn:ngsi-ld:$entityId"

    val ngsiJson = new JsonObject
    ngsiJson.addProperty(ID, ngsiId)
    ngsiJson.addProperty(TYPE, ngsiType)

    ngsiJson

  }

  /**
   * A helper method to create an NGSI entity and assign
   * identifier and type.
   */
  protected def newEntity(entityId: String, entityType: String): JsonObject = {

    val ngsiEntity = newNGSIEntity(entityId, entityType)
    /*
     * Add timestamp as NGSI compliant attribute
     */
    val timestamp = System.currentTimeMillis

    val timestampJson = new JsonObject
    timestampJson.add(METADATA, new JsonObject)

    timestampJson.addProperty(TYPE, "Long")
    timestampJson.addProperty(VALUE, timestamp)

    ngsiEntity.add(TIMESTAMP, timestampJson)
    ngsiEntity

  }

  protected def transformHashes(data:Map[String,Any]):Map[String, String] = {

    val hashes = data(HASHES)
    hashes match {
      case _: List[Any] =>
        hashes.asInstanceOf[List[Map[String, String]]]
          .map(hash => {
            (hash("algorithm"),hash("hash"))
          })
          .toMap

      case _ =>
        try {
          hashes.asInstanceOf[Map[String, String]]

        } catch {
          case _: Throwable => Map.empty[String,String]
        }
    }

  }

  /**
   * The labels property specifies a set of terms used to
   * describe a STIX object. The terms are user-defined or
   * trust-group defined and their meaning is outside the
   * scope of this specification and MAY be ignored.
   *
   * Where an object has a specific property defined in the
   * specification for characterizing subtypes of that object,
   * the labels property MUST NOT be used for that purpose.
   */
  protected def transformLabels(data:Map[String,Any]):Option[JsonArray] = {

    try {

      val labelsJson = new JsonArray

      val labels = data(LABELS).asInstanceOf[List[Any]]
      labels.foreach {
        /*
         * OpenCTI supports two different formats to describe
         * object labels:
         *
         * (1) [String]: the label value
         *
         * (2) fields: 'reference', 'value', 'x_opencti_internal_id'
         */
        case label@(_: Map[_, _]) =>
          val value = label.asInstanceOf[Map[String, Any]]
          /*
           * This data structures contains 2 fields, `value`
           * and `reference`: The `value` field contains the
           * identifier of the OpenCTI Object Label.
           *
           * The `reference` field specifies the label of the
           * Object Label
           */
          labelsJson.add(value("reference").asInstanceOf[String])

        /*
         * We expect this as the default approach to exchange
         * object labels of STIX objects. In this case, an extra
         * vertex is created to specify the respective label.
         */
        case label@(_: String) =>
          val value = label.asInstanceOf[String]
          labelsJson.add(value)

        case _ =>
          throw new Exception(s"The data type of the provided label is not supported.")
      }

      Some(labelsJson)

    } catch {
      case t: Throwable =>
        val message = s"Extracting labels failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  /*
   * SAMPLE UPDATE EVENT
   *
   *   data: {
   *     x_opencti_patch: {
   *       replace: { threat_actor_types: { current: ['competitor', 'crime-syndicate'], previous: ['competitor'] } },
   *     },
   *     id: 'threat-actor--b3486bf4-2cf8-527c-ab40-3fd2ff54de77',
   *     x_opencti_id: 'f499ceab-b3bf-4f39-827d-aea43beed391',
   *     type: 'threat-actor',
   *   }
   *
   */
  def getPatch(data: Map[String, Any]): Option[Map[String, Any]] = {

    val patch = {
      if (data.contains("x_opencti_patch")) {
        data("x_opencti_patch").asInstanceOf[Map[String, Any]]
      }
      else
        Map.empty[String, Any]
    }

    if (patch.isEmpty) return None
    /*
     * The patch contains a set of operations,
     * where an operation can be `add`, `remove`
     * or `replace`
     */
    val patchData = mutable.HashMap.empty[String, Any]

    val operations = patch.keySet
    /*
     * It is expected that OpenCTI specifies a maximum
     * of 3 update operations
     */
    operations.foreach {
      case "add" =>
        /*
         * The patch specifies a set of object properties
         * where values must be added
         */
        val filteredPatch = patch.get("add").asInstanceOf[Map[String, Any]]
        /*
         * Unpack reference properties and the associated
         * values that must be added; the result is a [Map]
         * with propKey -> propValues
         */
        val properties = filteredPatch.keySet.map(propKey => {

          val propVal = filteredPatch(propKey).asInstanceOf[List[Any]]
          /*
           * The content of the patch message can be a list
           * of attribute values or a list of reference maps
           *
           * Reference maps contain an internal OpenCTI id
           * and a value. As the purpose of this Beat is to
           * publish data to an external system, references
           * to internal identifiers are skipped
           */
          val values =
            if (propVal.head.isInstanceOf[Map[_, _]]) {
              propVal.map(value => {
                value.asInstanceOf[Map[String, Any]]("value")
              })
            }
            else
              propVal

          (propKey, values)

        }).toMap

        patchData += "add" -> properties

      case "remove" =>
        /*
         * The patch specifies a set of object properties
         * where values must be removed
         */
        val filteredPatch = patch.get("remove").asInstanceOf[Map[String, Any]]
        /*
         * Unpack reference properties and the associated
         * values that must be removed; the result is a [Map]
         * with propKey -> propValues
         */
        val properties = filteredPatch.keySet.map(propKey => {

          val propVal = filteredPatch(propKey).asInstanceOf[List[Any]]
          val values =
            if (propVal.head.isInstanceOf[Map[_, _]]) {
              propVal.map(value => {
                value.asInstanceOf[Map[String, Any]]("value")
              })
            }
            else
              propVal

          (propKey, values)

        }).toMap

        patchData += "remove" -> properties

      case "replace" =>
        /*
        * The patch specifies a set of object properties
        * where values must be replaced
        */
        val filteredPatch = patch.get("replace").asInstanceOf[Map[String, Any]]
        /*
         * Unpack reference properties and the current
         * values that must be set
         */
        val properties = filteredPatch.keySet.map(propKey => {

          val propVal = filteredPatch(propKey).asInstanceOf[Map[String, Any]]("current")
          /*
           * The referenced value(s) are represented as
           * a [List] to be compliant with the other patch
           * operations
           */
          val values =
            if (propVal.isInstanceOf[List[Any]]) {
              propVal
            }
            else
              List(propVal)

          (propKey, values)

        }).toMap

        patchData += "replace" -> properties

      case _ => /* Do nothing */

        val message = s"Unknown patch operation detected."
        error(message)

    }

    Some(patchData.toMap)

  }
}
