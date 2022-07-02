package de.kp.works.beats.fiware.publishers

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

import com.google.gson.{JsonArray, JsonElement, JsonObject}

import scala.collection.JavaConversions.iterableAsScalaIterable

object OPENCTI extends BasePublisher {
  /**
   * `eventData` is a JSON object with the following
   * format:
   *
   * {
   * "action": "...",
   * "format": "...",
   * "entities": [],
   * "relations": [],
   * }
   */
  override def publish(eventData: JsonElement): Unit = {
    /*
     * The `eventData` element is either a JsonObject
     * or a JsonNull
     */
    if (eventData.isJsonNull) {
      val message = s"OpenCTI event is empty and will not be published."
      info(message)

    } else {

      val eventJson = eventData.getAsJsonObject

      val entities = eventJson.get(ENTITIES).getAsJsonArray
      val relations = eventJson.get(RELATIONS).getAsJsonArray

      if (entities.isEmpty && relations.isEmpty) {
        val message = s"OpenCTI event neither contains entities nor relations to publish."
        warn(message)

      } else {

        val action = eventJson.get(ACTION).getAsString
        action match {
          case "create" =>
            publishCreate(entities, relations)

          case "delete" =>
            /*
             * The relations provided, specify associations
             * where the also provided entities are source
             * entities.
             *
             * The FIWARE context broker manages a relation
             * as a `ref` attribute of the respective entity.
             *
             * Therefore, there is no need to handle relations.
             */
            publishDelete(entities)

          case "update" =>
            publishUpdate(entities, relations)

          case _ =>
            val message = s"Event action `$action` found. OpenCTI event either creates, deletes of updates context."
            warn(message)
        }

      }

    }

  }

  /**
   * An OpenCTI event covers STIX domain objects,
   * STIX cyber observables, relations and sightings
   * as NGSI v2 entities.
   *
   * In addition, references to other STIX objects
   * and observables are specified as NGSI compliant
   * relations.
   */
  private def publishCreate(entities:JsonArray, relations:JsonArray):Unit = {

    try {
      /*
       * Publishing a certain OpenCTI event exposes new entities
       * first, and then sends new relations to the FIWARE context
       * broker
       */
      entities.foreach(entity => {
        val entityJson = entity.getAsJsonObject
        /*
         * {
         *  "id": "..",
         *  "type": ".."
         *  "timestamp": {
         *    "metadata": {},
         *    "type": "Long",
         *    "value": ...
         *  },
         *  "rows": [
         *    {...}
         *  ]
         * }
         *
         * As a first step, the provided rows with
         * a single row element are extracted and
         * added as NGSI attributes to the entity.
         *
         * Thereby the specified `action` attribute
         * is removed.
         */
        flattenRow(entityJson)
        /*
         * As a second step, send post request to
         * FIWARE context broker create `entity`
         */
        entityCreate(entityJson)
      })

      relations.foreach(relation => {
        val relationJson = relation.getAsJsonObject
        /*
         * {
         *  --- source entity ---
         *
         *  "id": "...",
         *  "type": "...",
         *
         * --- target entity ---
         *
         * "ref<target-type>: {
         *    "type": "Relationship,
         *    "value": "..." // identifier of the target
         * }
         *
         * Send post request to the `entities` endpoint
         * to create a STIX-base relation as NGSI relation
         */
        entityCreate(relationJson)

      })

    } catch {
      case t:Throwable =>
        val message = s"Publishing an OpenCTI (create) event failed: ${t.getLocalizedMessage}"
        error(message)
    }

  }
  private def extractRow(entityJson:JsonObject):Option[JsonObject] = {

    val rows = entityJson.remove(ROWS)
    val row = {
      val rowsJson = rows.getAsJsonArray
      if (rowsJson.nonEmpty)
        Some(rowsJson.head.getAsJsonObject)

      else
        None
    }

    row

  }
  /**
   * OpenCTI event handling is synchronized with other
   * event sources, i.e. the set of attributes is defined
   * as a single row in rows.
   */
  private def flattenRow(entityJson:JsonObject):Unit = {

    val row = extractRow(entityJson)
    if (row.nonEmpty) {
      val rowJson = row.get
      rowJson.keySet
        .foreach(key => {
          /*
           * Extract the NGSI attribute from the
           * row and determine whether to remove
           * the `action` field
           */
          val attrJson = rowJson.get(key).getAsJsonObject
          if (attrJson.has(ACTION)) attrJson.remove(ACTION)
          /*
           * Assign optionally cleaned attribute
           * to the entity JSON representation
           */
          entityJson.add(key, attrJson)
        })
    }

  }

  private def publishDelete(entities:JsonArray):Unit = {

    try {
      /*
       * A certain OpenCTI delete event focuses on the
       * entities only, and is performed in 3 steps:
       *
       * (1) All entities are retrieved, that contain a
       * reference (attribute) that points to the current
       * entity.
       *
       * (2) All reference attributes are deleted.
       *
       * (3) The current object is deleted.
       */

      entities.foreach(entity => {
        val entityJson = entity.getAsJsonObject
        /*
         * Retrieve all entities that contain reference
         * attributes that point to the current entity
         * identifier.
         */
        val references = entityRefs(entityJson)
        if (references.nonEmpty && references.get.nonEmpty) {
          /*
           * Remove the reference attributes from the
           * parent entities
           */
          references.get.foreach(reference => {

            val referenceJson = reference.getAsJsonObject

            val entityId = referenceJson.get(ID).getAsString
            val attrName = s"ref${entityJson.get(TYPE).getAsString}"

            attributeDelete(entityId, attrName)

          })
        }
        /*
         * Finally delete the current entity
         */
        entityDelete(entityJson)

      })

    } catch {
      case t:Throwable =>
        val message = s"Publishing an OpenCTI (delete) event failed: ${t.getLocalizedMessage}"
        error(message)
    }

  }

  private def publishUpdate(entities:JsonArray, relations:JsonArray):Unit = {

    try {
      /*
       * Relations are handled as `ref` attributes of a certain
       * entity, and are assigned to the entities attributes.
       */
      var lookup = entities.map{entity => {

        val entityJson = entity.getAsJsonObject

        val entityId   = entityJson.get(ID).getAsString
        val entityType = entityJson.get(TYPE).getAsString

        val entityRow  = extractRow(entityJson)
        ((entityId, entityType), entityRow)

      }}.toMap
      /*
       * Assign each relation `ref` attribute to the specified
       * entity (source entity of the relation)
       */
      relations.foreach(relation => {
        val relationJson = relation.getAsJsonObject
        /*
         * {
         *   "id": "...",
         *   "type": "...",
         *   "action": "...",
         *   "ref<entity-type>": {
         *     "type": "Relationship",
         *     "value": "..."
         *   }
         * }
         */
        val entityId   = relationJson.remove(ID).getAsString
        val entityType = relationJson.remove(TYPE).getAsString

        val action = relationJson.remove(ACTION).getAsString
        /*
         * Determine the respective entity row, assign
         * the `ref` attribute to the entity row and
         * overwrite the respective entity row in the
         * `lookup` structure
         */
        if (lookup.contains((entityId,entityType))) {

          val row = lookup((entityId, entityType))
          val rowJson =
            if (row.isEmpty) new JsonObject else row.get

          val refAttrName = relationJson.keySet.head

          val refAttr = relationJson.get(refAttrName).getAsJsonObject
          refAttr.addProperty(ACTION, action)

          rowJson.add(refAttrName, refAttr)
          lookup = lookup ++ Map((entityId, entityType) -> Some(rowJson))

        }
        else {
          val message = s"Relation does not refer an entity: ${relationJson.toString}"
          warn(message)
        }

      })
      /*
       * Organize entity specific data with respect
       * to the operations `add`, `delete` and `replace`
       */
      val requests = lookup.map{entry => {

        val ((entityId, _), row) = entry
        val rowJson = row.get

        val addAttrs = new JsonObject
        val delAttrs = new JsonObject
        val repAttrs = new JsonObject

        val keys = rowJson.keySet
        keys.foreach(key => {

          val attrJson = rowJson.get(key).getAsJsonObject

          val action = attrJson.remove(ACTION).getAsString
          action match {
            case "add" =>
              addAttrs.add(key, attrJson)

            case "delete" =>
              delAttrs.add(key, attrJson)

            case "replace" =>
              repAttrs.add(key, attrJson)

            case _ => /* Do nothing */
          }

        })

        val operations = Map("add" -> addAttrs, "delete" -> delAttrs, "replace" -> repAttrs)
        (entityId, operations)

      }}.toSeq
      /*
       * Send update requests of different
       * flavors to the FIWARE context broker
       */
      requests.foreach{case(entityId, operations) =>

        operations.foreach{case(action, attrs) =>
          action match {
            case "add" =>
              attributesAppend(entityId, attrs)

            case "delete" =>
              attrs.keySet
                .foreach(attrName => attributeDelete(entityId, attrName))

            case "replace" =>
              attributesReplace(entityId, attrs)

            case _ => /* Do nothing */

          }
        }
      }

    } catch {
      case t:Throwable =>
        val message = s"Publishing an OpenCTI (update) event failed: ${t.getLocalizedMessage}"
        error(message)
    }

  }

}
