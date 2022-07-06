package de.kp.works.beats.fiware.transformers

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

import com.google.gson.{JsonElement, JsonObject, JsonParser}
import de.kp.works.beats.BeatsLogging

import java.time.{ZoneId, ZoneOffset}
import java.util.UUID

trait BaseTransformer extends BeatsLogging {

  protected val ID        = "id"
  protected val METADATA  = "metadata"
  protected val TIMESTAMP = "timestamp"
  protected val TYPE      = "type"
  protected val VALUE     = "value"

  def cleanText(text:String):String = {
    text.replace(".", "_")
  }

  def deserialize(json:JsonObject): (String, JsonElement) = {
    /*
     * The provided `json` object has a common data format
     * that is used by all Works. Beats.
     *
     * {
     *    "type": "..",
     *    "event": ".."
     * }
     */

    val eventType = json.get("type").getAsString
    val eventData = JsonParser.parseString(json.get("event").getAsString)

    (eventType, eventData)

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

  protected def putValue(attrType: String, attrVal: Any, attrJson:JsonObject): Unit = {

    val dataType = attrType.head.toString + attrType.tail.map(x => x.toLower)

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

  }

  def toCamelCase(text:String, separator:String="-"):String = {

    val tokens = text.split(separator)
    tokens
      .map(token => token.head.toUpper + token.tail)
      .mkString

  }

  def transform(json:JsonObject):JsonElement

}
