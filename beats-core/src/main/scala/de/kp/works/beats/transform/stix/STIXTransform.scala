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

import com.google.gson.JsonArray
import de.kp.works.beats.BeatsLogging

object STIXTransform extends BeatsLogging {

  def transformCreate(entityId: String, entityType: String,
                      data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    /*
     * The current implementation takes non-edges as nodes;
     * an edge can a `relationship` or `sighting`, and also
     * cyber observable relationships
     */
    val isEdge = STIX.isStixEdge(entityType.toLowerCase)
    if (isEdge) {
      if (STIX.isStixRelationship(entityType.toLowerCase)) {
        return STIXRelations.createRelationship(entityId, entityType, data)
      }

      if (STIX.isStixSighting(entityType.toLowerCase)) {
        return STIXRelations.createSighting(entityId, entityType, data)
      }
      /*
       * The creation of STIX observable and meta relationships
       * should not happen as OpenCTI delegates them to updates
       */
      if (STIX.isStixObservableRelationship(entityType.toLowerCase)) {
        return STIXRelations.createObservableRelationship(entityId, entityType, data)
      }

      val message = s"Unknown relation type detected."
      error(message)

      (None, None)

    }
    else {
      /*
       * A STIX object is either a STIX Domain Object
       * or a STIX Cyber Observable
       */
      STIXObjects.createStixObject(entityId, entityType, data)
    }

  }

  def transformDelete(entityId: String, entityType: String,
                      data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    /*
     * The current implementation takes non-edges as nodes;
     * an edge can a `relationship` or `sighting`, and also
     * cyber observable relationships
     */
    val isEdge = STIX.isStixEdge(entityType.toLowerCase)
    if (isEdge) {

      if (STIX.isStixRelationship(entityType.toLowerCase)) {
        return STIXRelations.deleteRelationship(entityId, entityType, data)
      }

      if (STIX.isStixSighting(entityType.toLowerCase)) {
        return STIXRelations.deleteSighting(entityId, entityType, data)
      }

      val message = s"Unknown relation type detected."
      error(message)

      (None, None)

    }
    else {
      STIXObjects.deleteStixObject(entityId, entityType, data)
    }
  }

  def transformUpdate(entityId: String, entityType: String,
                      data: Map[String, Any]):(Option[JsonArray], Option[JsonArray]) = {
    /*
     * The current implementation takes non-edges as nodes;
     * an edge can a `relationship` or `sighting`, and also
     * cyber observable relationships
     */
    val isEdge = STIX.isStixEdge(entityType.toLowerCase)
    if (isEdge) {
      if (STIX.isStixRelationship(entityType.toLowerCase)) {
        return STIXRelations.updateRelationship(entityId, entityType, data)
      }

      if (STIX.isStixSighting(entityType.toLowerCase)) {
        return STIXRelations.updateSighting(entityId, entityType, data)
      }
      /*
       * The creation of STIX observable and meta relationships
       * should not happen as OpenCTI delegates them to updates
       */
      if (STIX.isStixObservableRelationship(entityType.toLowerCase)) {
        return STIXRelations.updateObservableRelationship(entityId, entityType, data)
      }

      val message = s"Unknown relation type detected."
      error(message)

      (None, None)

    }
    else {
      /*
       * A STIX object is either a STIX Domain Object
       * or a STIX Cyber Observable
       */
      STIXObjects.updateStixObject(entityId, entityType, data)
    }
  }


}
