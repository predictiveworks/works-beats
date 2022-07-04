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
import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.fiware.Fiware

abstract class BasePublisher extends Fiware with BeatsLogging {

  val ACTION    = "action"
  val ENTITIES  = "entities"
  val FORMAT    = "format"
  val ID        = "id"
  val RELATIONS = "relations"
  val ROWS      = "rows"
  val TYPE      = "type"
  /**
   * The time interval the publisher waits
   * until the next request is sent to the
   * FIWARE context broker.
   */
  var THREAD_SLEEP = 200

  def publish(eventData:JsonElement):Unit
  /**
   * A helper method to remove a certain attribute
   * from a specific entity managed by the context
   * broker
   */
  protected def attributeDelete(entityId:String, attrName:String):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + attributeDeleteUrl
      .replace("{id}", entityId)
      .replace("{attribute}", attrName)

    try {

      delete(endpoint, headers)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }
  /**
   * This method overwrites multiple attributes of
   * a certain entity
   */
  protected def attributesReplace(entityId:String, attrs:JsonObject):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + attributesReplaceUrl.replace("{id}", entityId)

    try {

      patch(endpoint=endpoint, headers=headers, body=attrs)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }
  /**
   * This method create or updates multiple attributes
   * of a certain entity
   */
  protected def attributesAppend(entityId:String, attrs:JsonObject):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + attributesAppendUrl.replace("{id}", entityId)

    try {

      post(endpoint=endpoint, headers=headers, body=attrs)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }
  /**
   * This method create or updates multiple attributes
   * of a certain entity
   */
  protected def attributesUpdate(entityId:String, attrs:JsonObject):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + attributesUpdateUrl.replace("{id}", entityId)

    try {

      post(endpoint=endpoint, headers=headers, body=attrs)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }

  /**
   * This method retrieves NGSI entities that point
   * to the provided entity, i.e. respective source
   * entities.
   */
  protected def entityRefs(entity:JsonObject):Option[JsonArray] = {

    val entityId = entity.get(ID).getAsString
    val entityType = entity.get(TYPE).getAsString

    val condition = s"ref$entityType==$entityId"
    val params = Map(
      /*
       * A query expression, composed of a list of statements
       * separated by `;`
       */
      "q" -> condition)

    val query = params
      .map{case(k,v) => s"$k=$v"}.mkString("&")

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entitiesGetUrl + s"?$query"

    try {

      val bytes = get(endpoint=endpoint, headers=headers)
      val result = extractJsonBody(bytes)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      Some(result.getAsJsonArray)

    } catch {
      case t:Throwable =>
        val message = s"Retrieving referencing entities failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  protected def entityCreate(entityJson:JsonObject):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityCreateUrl

    try {
      post(endpoint=endpoint, headers=headers, body=entityJson)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }
  /**
   * A helper method to delete an entity, identified by
   * its `id` attribute from the context broker
   */
  protected def entityDelete(entity:JsonObject):Boolean = {

    val entityId = entity.get("id").toString

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityDeleteUrl.replace("{id}", entityId)

    try {

      delete(endpoint, headers)
      /*
       * Wait between every sent request to balance
       * the access rate
       */
      Thread.sleep(THREAD_SLEEP)
      true

    } catch {
      case _:Throwable => false
    }

  }

  protected def entityExists(entityId:String):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityGetUrl.replace("{id}", entityId)

    try {

      val bytes = get(endpoint, headers, pooled = true)
      extractJsonBody(bytes)

      true

    } catch {
      case _:Throwable => false
    }

  }

}
