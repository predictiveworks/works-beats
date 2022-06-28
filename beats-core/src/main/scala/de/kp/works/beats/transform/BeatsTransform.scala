package de.kp.works.beats.transform

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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.JsonObject
import de.kp.works.beats.BeatsLogging

import java.util.UUID

trait BeatsTransform extends BeatsLogging {

  protected val ACTION    = "action"
  protected val EVENT     = "event"
  protected val ENTITY    = "entity"
  protected val FORMAT    = "format"
  protected val ID        = "id"
  protected val METADATA  = "metadata"
  protected val MODE      = "mode"
  protected val ROWS      = "rows"
  protected val TIMESTAMP = "timestamp"
  protected val TYPE      = "type"
  protected val VALUE     = "value"

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  /**
   * Sample processing from ThingsBoard
   *
   * Subscribing to topic v1/gateway/attributes results
   * in messages of the format:
   *
   * {
   * "device": "Device A",
   * "data": {
   * "attribute1": "value1",
   * "attribute2": 42
   * }
   * }
   *
   * In this case, the `payload` parameter refers to `data`
   */
  protected def fillEntity(payload: Map[String, Any], keys: Set[String], entityJson: JsonObject): Unit = {
    keys.foreach(key => {
      /*
       * Extract the respective value from payload
       */
      val value = payload(key)
      value match {
        /*
         * The `value` refers to a LIST attribute: the current
         * implementation supports component (or element) data
         * types that are either `primitive` or specify a map-
         */
        case _: List[Any] =>
          val list = value.asInstanceOf[List[Any]]
          if (list.nonEmpty) {

            val attrJson = new JsonObject
            /*
             * The attribute `metadata` field is introduced to
             * be compliant with the NGSI v2 specification, but
             * filled with an empty object
             */
            attrJson.add("metadata", new JsonObject)
            /*
             * Infer component data type from first element of
             * the list attribute value
             */
            val head = list.head
            if (head.isInstanceOf[Map[_, _]]) {

              val attrType = "List[Map]"
              attrJson.addProperty("type", attrType)

              val attrValu = mapper.writeValueAsString(list)
              attrJson.addProperty("value", attrValu)

            }
            else {
              /*
               * A basic data type is expected in
               * this case
               */
              val basicType = getBasicType(head)

              val attrType = s"List[$basicType]"
              attrJson.addProperty("type", attrType)

              val attrValu = mapper.writeValueAsString(list)
              attrJson.addProperty("value", attrValu)

            }
            /*
             * The contribution to the provided entity (entityJson):
             *
             * attribute = {
             *    "metadata" : {},
             *    "type": ...,
             *    "value": ...
             * }
             */
            entityJson.add(key, attrJson)

          }
        case _: Map[_, _] =>
          val map = value.asInstanceOf[Map[String, Any]]
          if (map.nonEmpty) {

            val attrJson = new JsonObject
            attrJson.add("metadata", new JsonObject)

            val attrType = "Map"
            attrJson.addProperty("type", attrType)

            val attrValu = mapper.writeValueAsString(map)
            attrJson.addProperty("value", attrValu)

            val attrName = key
            entityJson.add(attrName, attrJson)

          }
        case _ =>
          /*
           * The `value` is expected to have a [Basic]
           * data type
           */
          if (value != null) {

            val attrJson = new JsonObject
            attrJson.add("metadata", new JsonObject)

            val basicType = getBasicType(value)

            val attrType = basicType
            attrJson.addProperty("type", attrType)

            val attrValu = mapper.writeValueAsString(value)
            attrJson.addProperty("value", attrValu)

            val attrName = key
            entityJson.add(attrName, attrJson)

          }
      }

    })

  }

  protected def getBasicType(attrVal: Any): String = {
    attrVal match {
      /*
       * Basic data types
       */
      case _: BigDecimal => "BigDecimal"
      case _: Boolean => "Boolean"
      case _: Byte => "Byte"
      case _: Double => "Double"
      case _: Float => "Float"
      case _: Int => "Int"
      case _: Long => "Long"
      case _: Short => "Short"
      case _: String => "String"
      /*
       * Datetime support
       */
      case _: java.sql.Date => "Date"
      case _: java.sql.Timestamp => "Timestamp"
      case _: java.util.Date => "Date"
      case _: java.time.LocalDate => "Date"
      case _: java.time.LocalTime => "Timestamp"
      case _: java.time.LocalDateTime => "Datetime"
      /*
       * Handpicked data types
       */
      case _: UUID => "UUID"
      case _ =>
        val now = new java.util.Date().toString
        throw new Exception(s"[ERROR] $now - Basic data type not supported.")
    }

  }

}
