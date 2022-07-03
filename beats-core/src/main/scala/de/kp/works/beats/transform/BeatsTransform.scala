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
import com.google.gson.{JsonArray, JsonObject}
import de.kp.works.beats.BeatsLogging

import java.time.{ZoneId, ZoneOffset}
import java.util.UUID
import scala.collection.JavaConversions.iterableAsScalaIterable

trait BeatsTransform extends BeatsLogging {

  protected val ACTION    = "action"
  protected val BASE_TYPE = "baseType"
  protected val EVENT     = "event"
  protected val ENTITY    = "entity"
  protected val ENTITIES  = "entities"
  protected val FORMAT    = "format"
  protected val ID        = "id"
  protected val METADATA  = "metadata"
  protected val MODE      = "mode"
  protected val RELATIONS = "relations"
  protected val ROWS      = "rows"
  protected val TIMESTAMP = "timestamp"
  protected val TYPE      = "type"
  protected val VALUE     = "value"

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  /**
   * A private helper method to transform the type
   * of a STIX V2.1 domain object or cyber observable
   * into an NGSI complaint representation.
   */
  def toCamelCase(text:String):String = {

    val tokens = text.split("-")
    tokens
      .map(token => token.head.toUpper + token.tail)
      .mkString

  }

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
   */
  protected def fillRow(payload: Map[String, Any], keys: Set[String],
                        rowJson: JsonObject, action:String="create"): Unit = {

    keys.foreach(key => {
      try {
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
              /*
               * The current implementation does not support
               * a list of complex properties like LIST, MAP
               *
               * Infer component data type from first element
               * of the list attribute value
               */
              val basicType = getBasicType(list.head)
              if (list.size == 1) {
                putValue(
                  attrName = key,
                  attrType = basicType,
                  attrVal  = list.head,
                  rowJson  = rowJson,
                  action   = action)

               } else {
                putList(
                  attrName = key,
                  attrType = basicType,
                  attrVal  = list,
                  rowJson  = rowJson,
                  action   = action)
              }

            }
          case _: Map[_, _] =>
            val map = value.asInstanceOf[Map[String, Any]]
            if (map.nonEmpty) {

              putMap(
                attrName = key,
                attrVal  = map,
                rowJson  = rowJson,
                action   = action)

            }
          case _ =>
            /*
             * The `value` is expected to have a [Basic]
             * data type
             */
            if (value != null) {

              val basicType = getBasicType(value)
              putValue(
                attrName = key,
                attrType = basicType,
                attrVal  = value,
                rowJson  = rowJson,
                action   = action)

            }
        }

      } catch {
        case t:Throwable =>
          val message = s"Extracting value for `$key` failed: ${t.getLocalizedMessage}"
          error(message)
      }

    })

  }
  /**
   * This helper method supports a map of values
   * that refer to a basic data type.
   *
   * The content of the map is flattened and added
   * to the row with concatenated attribute names
   */
  protected def putMap(attrName:String, attrVal:Map[String,Any],
                       rowJson:JsonObject, action:String="create"):Unit = {

    attrVal.foreach{case(k,v) =>
      try {

        val subName = s"$attrName:$k"
        val subType = getBasicType(v)

        putValue(
          attrName = subName,
          attrType = subType,
          attrVal  = v,
          rowJson  = rowJson,
          action   = action)

      } catch {
        case t:Throwable =>
          val message = s"Extracting `$k` from Map failed: ${t.getLocalizedMessage}"
          error(message)
      }
    }

  }
  /**
   * This helper method supports a list of values
   * that refer to a basic data type
   */
  protected def putList(attrName: String, attrType: String, attrVal: List[Any],
                        rowJson: JsonObject, action:String="create"): Unit = {

    val metaJson = new JsonObject
    metaJson.addProperty(ACTION, action)
    metaJson.addProperty(BASE_TYPE, attrType)

    val attrJson = new JsonObject
    attrJson.add(METADATA, metaJson)

    attrJson.addProperty(TYPE, "List")
    val valueJson = new JsonArray

    attrType match {
      /*
       * Basic data types
       */
      case "BigDecimal" =>
        attrVal
          .map(_.asInstanceOf[BigDecimal])
          .foreach(x => valueJson.add(x))

      case "Boolean" =>
        attrVal
          .map(_.asInstanceOf[Boolean])
          .foreach(x => valueJson.add(x))

      case "Byte" =>
        attrVal
          .map(_.asInstanceOf[Byte])
          .foreach(x => valueJson.add(x))

      case "Double" =>
        attrVal
          .map(_.asInstanceOf[Double])
          .foreach(x => valueJson.add(x))

      case "Float" =>
        attrVal
          .map(_.asInstanceOf[Float])
          .foreach(x => valueJson.add(x))

      case "Int" =>
        attrVal
          .map(_.asInstanceOf[Int])
          .foreach(x => valueJson.add(x))

      case "Long" =>
        attrVal
          .map(_.asInstanceOf[Long])
          .foreach(x => valueJson.add(x))

      case "Short" =>
        attrVal
          .map(_.asInstanceOf[Short])
          .foreach(x => valueJson.add(x))

      case "String" =>
        attrVal
          .map(_.asInstanceOf[String])
          .foreach(x => valueJson.add(x))

      case "Date" =>
        attrVal.head match {
          case _: java.sql.Date =>
            attrVal
              .map(_.asInstanceOf[java.sql.Date])
              .foreach(x => valueJson.add(x.getTime))

          case _: java.util.Date =>
            attrVal
              .map(_.asInstanceOf[java.util.Date])
              .foreach(x => valueJson.add(x.getTime))

          case _: java.time.LocalDate =>
            attrVal.map(_.asInstanceOf[java.time.LocalDate])
              .foreach(x =>
                valueJson.add(
                  x
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant.toEpochMilli))

          case _: java.time.LocalDateTime =>
            attrVal
              .map(_.asInstanceOf[java.time.LocalDateTime])
              .foreach(x => valueJson.add(
                x
                  .toInstant(ZoneOffset.UTC).toEpochMilli))

          case _ => /* Do nothing */
        }
      case "Timestamp" =>
        attrVal.head match {
          case _: java.sql.Timestamp =>
            attrVal
              .map(_.asInstanceOf[java.sql.Timestamp])
              .foreach(x => valueJson.add(x.getTime))

          case _ => /* Do nothing */
        }
      /*
       * Handpicked data types
       */
      case "UUID" =>
        attrVal
          .map(_.asInstanceOf[java.util.UUID])
          .foreach(x => valueJson.add(x.toString))

      case _ => /* Do nothing */

    }

    if (valueJson.nonEmpty) {
      attrJson.add(VALUE, valueJson)
      rowJson.add(attrName, attrJson)
    }

  }

  protected def putValue(attrName: String, attrType: String, attrVal: Any,
                         rowJson: JsonObject, action:String="create"): Unit = {

    val dataType = attrType.head.toString + attrType.tail.map(x => x.toLower)

    val metaJson = new JsonObject
    metaJson.addProperty(ACTION, action)

    val attrJson = new JsonObject
    attrJson.add(METADATA, metaJson)

    attrJson.addProperty(TYPE, dataType)

    attrType match {
      /*
       * Basic data types
       */
      case "BigDecimal" =>
        val value = attrVal.asInstanceOf[BigDecimal].toString
        attrJson.addProperty(VALUE, value)

      case "Boolean" =>
        val value = attrVal.asInstanceOf[Boolean]
        attrJson.addProperty(VALUE, value)

      case "Byte" =>
        val value = attrVal.asInstanceOf[Byte]
        attrJson.addProperty(VALUE, value)

      case "Double" =>
        val value = attrVal.asInstanceOf[Double]
        attrJson.addProperty(VALUE, value)

      case "Float" =>
        val value = attrVal.asInstanceOf[Float]
        attrJson.addProperty(VALUE, value)

      case "Int" =>
        val value = attrVal.asInstanceOf[Int]
        attrJson.addProperty(VALUE, value)

      case "Long" =>
        val value = attrVal.asInstanceOf[Long]
        attrJson.addProperty(VALUE, value)

      case "Short" =>
        val value = attrVal.asInstanceOf[Short]
        attrJson.addProperty(VALUE, value)

      case "String" =>
        val value = attrVal.asInstanceOf[String]
        attrJson.addProperty(VALUE, value)

      /*
       * Datetime support
       */
      case "Date" =>
        attrVal match {
          case _: java.sql.Date =>
            val value = attrVal.asInstanceOf[java.sql.Date]
            attrJson.addProperty(VALUE, value.getTime)

          case _: java.util.Date =>
            val value = attrVal.asInstanceOf[java.util.Date]
            attrJson.addProperty(VALUE, value.getTime)

          case _: java.time.LocalDate =>
            val value = attrVal
              .asInstanceOf[java.time.LocalDate]
              .atStartOfDay(ZoneId.of("UTC"))
              .toInstant.toEpochMilli
            attrJson.addProperty(VALUE, value)

          case _: java.time.LocalDateTime =>
            val value = attrVal
              .asInstanceOf[java.time.LocalDateTime]
              .toInstant(ZoneOffset.UTC).toEpochMilli
            attrJson.addProperty(VALUE, value)

          case _ => /* Do nothing */
        }
      case "Timestamp" =>
        attrVal match {
          case _: java.sql.Timestamp =>
            val value = attrVal.asInstanceOf[java.sql.Timestamp]
            attrJson.addProperty(VALUE, value.getTime)

          case _ => /* Do nothing */
        }
      /*
       * Handpicked data types
       */
      case "UUID" =>
        val value = attrVal.asInstanceOf[java.util.UUID]
        attrJson.addProperty(VALUE, value.toString)

      case _ => /* Do nothing */
    }

    if (attrJson.has(VALUE)) {
      rowJson.add(attrName, attrJson)
    }

  }

  protected def getBasicType(attrVal: Any): String = {
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
        throw new Exception(s"Basic data type not supported.")
    }

  }

}
