package de.kp.works.beats
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.{JsonArray, JsonObject}

import java.util.UUID

trait BeatsTransform {

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  /**
   * Sample processing from ThingsBoard
   *
   * Subscribing to topic v1/gateway/attributes results
   * in messages of the format:
   *
   * {
   *   "device": "Device A",
   *   "data": {
   *     "attribute1": "value1",
   *     "attribute2": 42
   *   }
   * }
   *
   * In this case, the `payload` parameter refers to `data`
   */
  protected def fillEntity(payload:Map[String, Any], keys:Set[String], entityJson:JsonObject):Unit = {
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
              /*
               * The attribute value is a list of objects
               */
              val valueJson = new JsonArray
              list.foreach(entry => {

                val entryMap = entry.asInstanceOf[Map[String, Any]]
                if (entryMap.nonEmpty) {

                  val entryJson = new JsonObject
                  /*
                   * {
                   *    "name1" : { "type1": "...", "value1": "..."},
                   *    "name2" : { "type2": "...", "value2": "..."},
                   * }
                   */
                  entryMap.foreach{case (k,v) =>

                    val itemJson = new JsonObject

                    val basicType = getBasicType(v)
                    itemJson.addProperty("type", basicType)

                    putValue(v, basicType, itemJson)
                    entryJson.add(k, itemJson)
                  }

                  valueJson.add(entryJson)

                }
              })

              val attrType = "List[Map]"
              attrJson.addProperty("type", attrType)

              attrJson.add("value", valueJson)

            }
            else {
              /*
               * A basic data type is expected in
               * this case
               */
              val basicType = getBasicType(head)

              val attrType = s"List[$basicType]"
              attrJson.addProperty("type", attrType)

              putValues(list, basicType, attrJson)

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

            val valueJson = new JsonObject

            map.foreach{case (k,v) =>

              val itemJson = new JsonObject

              val basicType = getBasicType(v)
              itemJson.addProperty("type", basicType)

              putValue(v, basicType, itemJson)
              valueJson.add(k, itemJson)

            }

            val attrType = "Map"
            attrJson.addProperty("type", attrType)

            attrJson.add("value", valueJson)

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

            putValue(value, basicType, attrJson)

            val attrName = key
            entityJson.add(attrName, attrJson)

          }
      }

    })

  }

  protected def putValue(attrVal:Any, attrType:String, attrJson:JsonObject):Unit = {
    attrType match {
      /*
       * Basic data types
       */
      case "BigDecimal" =>
        val value = attrVal.asInstanceOf[BigDecimal]
        attrJson.addProperty("value", value)
      case "Boolean" =>
        val value = attrVal.asInstanceOf[Boolean]
        attrJson.addProperty("value", value)
      case "Byte" =>
        val value = attrVal.asInstanceOf[Byte]
        attrJson.addProperty("value", value)
      case "Double" =>
        val value = attrVal.asInstanceOf[Double]
        attrJson.addProperty("value", value)
      case "Float" =>
        val value = attrVal.asInstanceOf[Float]
        attrJson.addProperty("value", value)
      case "Int" =>
        val value = attrVal.asInstanceOf[Int]
        attrJson.addProperty("value", value)
      case "Long" =>
        val value = attrVal.asInstanceOf[Long]
        attrJson.addProperty("value", value)
      case "Short" =>
        val value = attrVal.asInstanceOf[Short]
        attrJson.addProperty("value", value)
      case "String" =>
        val value = attrVal.asInstanceOf[String]
        attrJson.addProperty("value", value)
      /*
       * Datetime support
       */
      case "Date" =>
        attrVal match {
          case _: java.sql.Date =>
            val value = attrVal.asInstanceOf[java.sql.Date].toString
            attrJson.addProperty("value", value)
          case _: java.util.Date =>
            val value = attrVal.asInstanceOf[java.util.Date].toString
            attrJson.addProperty("value", value)
          case _: java.time.LocalDate =>
            val value = attrVal.asInstanceOf[java.time.LocalDate].toString
            attrJson.addProperty("value", value)
          case _ =>
            val now = new java.util.Date().toString
            throw new Exception(s"[ERROR] $now - Date data type not supported.")
        }
      case "Timestamp" =>
        attrVal match {
          case _: java.sql.Timestamp =>
            val value = attrVal.asInstanceOf[java.sql.Timestamp].toString
            attrJson.addProperty("value", value)
          case _: java.time.LocalTime =>
            val value = attrVal.asInstanceOf[java.time.LocalTime].toString
            attrJson.addProperty("value", value)
          case _ =>
            val now = new java.util.Date().toString
            throw new Exception(s"[ERROR] $now - Timestamp data type not supported.")
        }
      case "Datetime" =>
        val value = attrVal.asInstanceOf[java.time.LocalDateTime].toString
        attrJson.addProperty("value", value)

      /*
       * Handpicked data types
       */
      case "UUID" =>
        val value = attrVal.asInstanceOf[java.util.UUID].toString
        attrJson.addProperty("value", value)
      case _ =>
        val now = new java.util.Date().toString
        throw new Exception(s"[ERROR] $now - Basic data type not supported.")
    }
  }

  protected def putValues(attrVal:List[Any], attrType:String, attrJson:JsonObject):Unit = {

    val arrayJson = new JsonArray
    attrType match {
      /*
       * Basic data types
       */
      case "BigDecimal" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[BigDecimal]
          arrayJson.add(value)
        })
      case "Boolean" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Boolean]
          arrayJson.add(value)
        })
      case "Byte" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Byte]
          arrayJson.add(value)
        })
      case "Double" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Double]
          arrayJson.add(value)
        })
      case "Float" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Float]
          arrayJson.add(value)
        })
      case "Int" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Int]
          arrayJson.add(value)
        })
      case "Long" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Long]
          arrayJson.add(value)
        })
      case "Short" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[Short]
          arrayJson.add(value)
        })
      case "String" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[String]
          arrayJson.add(value)
        })
      /*
       * Datetime support
       */
      case "Date" =>
        attrVal.head match {
          case _: java.sql.Date =>
            attrVal.foreach(item => {
              val value = item.asInstanceOf[java.sql.Date].toString
              arrayJson.add(value)
            })
          case _: java.util.Date =>
            attrVal.foreach(item => {
              val value = item.asInstanceOf[java.util.Date].toString
              arrayJson.add(value)
            })
          case _: java.time.LocalDate =>
            attrVal.foreach(item => {
              val value = item.asInstanceOf[java.time.LocalDate].toString
              arrayJson.add(value)
            })
          case _ =>
            val now = new java.util.Date().toString
            throw new Exception(s"[ERROR] $now - Date data type not supported.")
        }
      case "Timestamp" =>
        attrVal.head match {
          case _: java.sql.Timestamp =>
            attrVal.foreach(item => {
              val value = item.asInstanceOf[java.sql.Timestamp].toString
              arrayJson.add(value)
            })
          case _: java.time.LocalTime =>
            attrVal.foreach(item => {
              val value = item.asInstanceOf[java.time.LocalTime].toString
              arrayJson.add(value)
            })
          case _ =>
            val now = new java.util.Date().toString
            throw new Exception(s"[ERROR] $now - Timestamp data type not supported.")
        }
      case "Datetime" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[java.time.LocalDateTime].toString
          arrayJson.add(value)
        })

      /*
       * Handpicked data types
       */
      case "UUID" =>
        attrVal.foreach(item => {
          val value = item.asInstanceOf[java.util.UUID].toString
          arrayJson.add(value)
        })
      case _ =>
        val now = new java.util.Date().toString
        throw new Exception(s"[ERROR] $now - Basic data type not supported.")
    }

    attrJson.add("value", arrayJson)

  }

  protected def getBasicType(attrVal:Any): String = {
    attrVal match {
      /*
       * Basic data types
       */
      case _: BigDecimal => "BigDecimal"
      case _: Boolean    => "Boolean"
      case _: Byte       => "Byte"
      case _: Double     => "Double"
      case _: Float      => "Float"
      case _: Int        => "Int"
      case _: Long       => "Long"
      case _: Short      => "Short"
      case _: String     => "String"
      /*
       * Datetime support
       */
      case _: java.sql.Date           => "Date"
      case _: java.sql.Timestamp      => "Timestamp"
      case _: java.util.Date          => "Date"
      case _: java.time.LocalDate     => "Date"
      case _: java.time.LocalTime     => "Timestamp"
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
