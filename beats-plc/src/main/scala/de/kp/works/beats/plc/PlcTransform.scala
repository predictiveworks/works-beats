package de.kp.works.beats.plc

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
import de.kp.works.beats.BeatsLogging
import org.apache.plc4x.java.api.messages.PlcReadResponse
import org.apache.plc4x.java.api.types.PlcResponseCode

import scala.collection.JavaConversions.collectionAsScalaIterable

object PlcTransform extends BeatsLogging {

  private val ARRAY          = "Array"
  private val BASE_TYPE      = "baseType"
  private val BIG_DECIMAL    = "BigDecimal"
  private val BIG_INTEGER    = "BigInteger"
  private val BOOLEAN        = "Boolean"
  private val BYTE           = "Byte"
  private val DOUBLE         = "Double"
  private val FLOAT          = "Float"
  private val ID             = "id"
  private val INTEGER        = "Int"
  private val LOCAL_DATE     = "LocalDate"
  private val LOCAL_DATETIME = "LocalDateTime"
  private val LOCAL_TIME     = "LocalTime"
  private val LONG           = "Long"
  private val METADATA       = "metadata"
  private val SHORT          = "Short"
  private val STRING         = "String"
  private val TYPE           = "type"
  private val VALUE          = "value"
  /**
   * This public method transforms the read
   * response of a certain PLC into an NGSI
   * compliant JSON entity
   */
  def transform(response: PlcReadResponse): Option[JsonObject] = {
    /*
     * The PlcEvent collects the field names
     * and its associated values; the respective
     * entity must be defined elsewhere
     */
    val plcEvent = new JsonObject
    /*
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     */
    val ngsiType = toCamelCase(PlcOptions.getPlcType)
    val ngsiId = s"urn:ngsi-ld:$ngsiType:${PlcOptions.getPlcId}"

    plcEvent.addProperty(ID, ngsiId)
    plcEvent.addProperty(TYPE, ngsiType)

    response.getFieldNames.foreach(fieldName => {

      try {

        val code = response.getResponseCode(fieldName)
        if (code != PlcResponseCode.OK) {
          throw new Exception(s"PLC returned `${code.name}`")
        }

        val attrJson = new JsonObject
        val metaJson = new JsonObject

        /*
         * The field name response either returns a
         * single value or an Array of values
         */
        val numValues = response.getNumberOfValues(fieldName)
        if (numValues == 1) {

          val attrJson = new JsonObject
          if (response.isValidBigDecimal(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, BIG_DECIMAL)
            attrJson.addProperty(VALUE, response.getBigDecimal(fieldName))

          }
          else if (response.isValidBigInteger(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, BIG_INTEGER)
            attrJson.addProperty(VALUE, response.getBigInteger(fieldName))

          }
          else if (response.isValidBoolean(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, BOOLEAN)
            attrJson.addProperty(VALUE, response.getBoolean(fieldName))

          }
          else if (response.isValidByte(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, BYTE)
            attrJson.addProperty(VALUE, response.getByte(fieldName))

          }
          else if (response.isValidDate(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, LOCAL_DATE)
            attrJson.addProperty(VALUE, response.getDate(fieldName).toString)

          }
          else if (response.isValidDateTime(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, LOCAL_DATETIME)
            attrJson.addProperty(VALUE, response.getDateTime(fieldName).toString)

          }
          else if (response.isValidDouble(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, DOUBLE)
            attrJson.addProperty(VALUE, response.getDouble(fieldName))

          }
          else if (response.isValidFloat(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, FLOAT)
            attrJson.addProperty(VALUE, response.getFloat(fieldName))

          }
          else if (response.isValidInteger(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, INTEGER)
            attrJson.addProperty(VALUE, response.getInteger(fieldName))

          }
          else if (response.isValidLong(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, LONG)
            attrJson.addProperty(VALUE, response.getLong(fieldName))

          }
          else if (response.isValidShort(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, SHORT)
            attrJson.addProperty(VALUE, response.getShort(fieldName))

          }
          else if (response.isValidString(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, STRING)
            attrJson.addProperty(VALUE, response.getString(fieldName))

          }
          else if (response.isValidTime(fieldName)) {

            attrJson.add(METADATA, metaJson)
            attrJson.addProperty(TYPE, LOCAL_TIME)
            attrJson.addProperty(VALUE, response.getTime(fieldName).toString)

          }

        } else {

          if (response.isValidBigDecimal(fieldName,0)) {

            metaJson.addProperty(BASE_TYPE, BIG_DECIMAL)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllBigDecimals(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidBigInteger(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, BIG_INTEGER)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllBigIntegers(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidBoolean(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, BOOLEAN)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllBooleans(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidByte(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, BYTE)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllBytes(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidDate(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, LOCAL_DATE)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllDates(fieldName).foreach(v => values.add(v.toString))

            attrJson.add(VALUE, values)

          }
          else if (response.isValidDateTime(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, LOCAL_DATETIME)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllDateTimes(fieldName).foreach(v => values.add(v.toString))

            attrJson.add(VALUE, values)

          }
          else if (response.isValidDouble(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, DOUBLE)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllDoubles(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidFloat(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, FLOAT)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllFloats(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidInteger(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, INTEGER)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllIntegers(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidLong(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, LONG)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllLongs(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidShort(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, SHORT)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllShorts(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidString(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, STRING)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllStrings(fieldName).foreach(values.add)

            attrJson.add(VALUE, values)

          }
          else if (response.isValidTime(fieldName, 0)) {

            metaJson.addProperty(BASE_TYPE, LOCAL_TIME)
            attrJson.add(METADATA, metaJson)

            attrJson.addProperty(TYPE, ARRAY)

            val values = new JsonArray()
            response.getAllTimes(fieldName).foreach(v => values.add(v.toString))

            attrJson.add(VALUE, values)

          }

        }

        if (attrJson.keySet.nonEmpty) plcEvent.add(fieldName, attrJson)

      } catch {
        case t: Throwable =>
          val message = s"Retrieving value for `$fieldName` failed: ${t.getLocalizedMessage}"
          error(message)
      }
    })

    if (plcEvent.keySet.isEmpty) None else Some(plcEvent)

  }

  /**
   * A private helper method to transform the type
   * of a PLC entity into an NGSI complaint representation.
   */
  def toCamelCase(text:String, separator:String="-"):String = {

    val tokens = text.split(separator)
    tokens
      .map(token => token.head.toUpper + token.tail)
      .mkString

  }

}