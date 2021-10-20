package de.kp.works.beats.transform
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

import com.google.gson.JsonObject
import de.kp.works.beats.BeatsTransform

object CreateTransform extends BeatsTransform {
  /*
   * SAMPLE CREATE EVENT
   *
   *   data: {
   *      name: 'JULIEN',
   *      description: '',
   *      identity_class: 'individual',
   *      id: 'identity--d969b177-497f-598d-8428-b128c8f5f819',
   *      x_opencti_id: '3ae87124-b240-42b7-b309-89d8eb66e9cc',
   *      type: 'identity',
   *    }
   *
   */
  def transform(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    /*
     * SAMPLE TYPES
     *
     * - identity
     * - location
     * - file
     * - relationship
     * - sighting
     * - ...
     *
     * `relationship` and `sighting` explicitly indicates relations
     */
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object; this object
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id", entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "create")

    entityJson.add("action", attrJson)
    /*
     * Extract other attributes from the provided payload.

     * Basic fields are:
     * - id
     * - type
     *
     * - hashes
     * - source_ref
     * - spec_version
     * - start_time
     * - stop_time
     * - target_ref
     * - x_opencti_id
     * - x_opencti_source_ref
     * - x_opencti_target_ref
     */
    val filter = Seq("id", "type")
    val keys = payload.keySet.filter(key => !filter.contains(key))
    /*
     * Field rules used by OpenCTI (see stix.js)
     *
     * (1) Specify relation `from`
     *
     * If the specified type is `sighting` the field is named `sighting_of_ref`
     * and otherwise `source_ref`.  This field contains an identifier as
     * [String] and is accompanied by `x_opencti_* ` to specify the internal
     * counterpart
     *
     * (2) Specify relation `to`
     *
     * If the specified type is `sighting` the field is named `where_sighted_refs`
     * and otherwise `target_ref`. The field contains a list of identifiers
     * and is accompanied by `x_opencti_target_ref`, which contains the bare id.
     *
     * (3) Region specific input cases
     *
     * Field `x_opencti_stix_ids` contains a list of identifiers
     *
     * (4) Marking definition
     *
     * Field `name`             [String]
     * Field `definition_type`  [String]
     * Field `definition`       [Map[String,String]] where the key is equal to the
     * definition type
     *
     * (5) Object references
     *
     * Field `object_refs [List[Map[String,Any]] or List[String]
    *
     * In case of a simple list, the list items define identifiers, and
     * otherwise, it is Map of
     *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (6) Marking references
     *
     * Field `object_marking_refs` [List[Map[String,Any]] or List[String]
     *
     * In case of a simple list, the list items define identifiers, and
     * otherwise, it is Map of
     *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (6) Created By
     *
     * Field `created_by_ref` Map[String, Any] or [String]
     *
     * In case of a [String], this field defines an identifier and
     * otherwise, it is a Map of
      *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (7) Embedded relations
     *
     * Field `labels` [List[Map[String,Any]] or List[String]
     *
     * In case of a simple list, the list items define identifiers, and
     * otherwise, it is Map of
     *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (8) Kill chain phases
     *
     * Field `kill_chain_phases` [List[Map[String,Any]]
     *
     * The Map either specifies
     *
     * - kill_chain_name [String]
     * - phase_name      [String]
     *
     * or
     *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (9) External references
     *
     * Field `external_references` [List[Map[String,Any]]
     *
     * The Map either specifies
     *
     * - source_name [String]
     * - description [String]
     * - url         [String]
     * - hashes      ???
     * - external_id [String]
     *
     * or
     *
     * - reference [String]
     * - value     [String]
     * - x_opencti_internal_id [String]
     *
     * (10) Attribute filtering
     *
     * This is the final step where previous mappings are added
     * to the event as [key] = val
     *
     */

    // TODO Check whether `hashes` is covered by fillEntity
    fillEntity(payload, keys, entityJson)
    Some(entityJson)

  }

}
