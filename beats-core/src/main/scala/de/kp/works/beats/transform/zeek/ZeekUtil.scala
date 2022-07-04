package de.kp.works.beats.transform.zeek

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.{JsonElement, JsonNull, JsonObject}
import de.kp.works.beats.transform.BeatsTransform

object ZeekUtil extends BeatsTransform {

  def json2NGSI(entityJson:JsonObject, rowJson:JsonObject, schema:StructType):Unit = {

    schema.fields.foreach(field => {

      val fieldName = field.name
      val fieldType = field.dataType

      fieldType match {
        case "Array[Long]" =>
          val attrJson = getLongArray(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "Array[String]" =>
          val attrJson = getStringArray(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "Boolean" =>
          val attrJson = getBoolean(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "Double" =>
          val attrJson = getDouble(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "Integer" =>
          val attrJson = getInt(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "Long" =>
          val attrJson = getLong(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case "String" =>
          val attrJson = getString(rowJson, fieldName, field.nullable)
          if (!attrJson.isJsonNull)
            entityJson.add(fieldName, attrJson)

        case _ => throw new Exception(s"Data type `$fieldType.toString` is not supported.")
      }

    })

  }

  def getBoolean(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "Boolean")
      attrJson.add(VALUE, rowJson.get(fieldName))

      attrJson

    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getDouble(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "Double")
      attrJson.add(VALUE, rowJson.get(fieldName))

      attrJson

    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getInt(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "Int")
      attrJson.add(VALUE, rowJson.get(fieldName))

      attrJson

    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getLong(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "Long")
      attrJson.add(VALUE, rowJson.get(fieldName))

      attrJson


    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getString(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val attrJson = new JsonObject
      attrJson.add(METADATA, new JsonObject)

      attrJson.addProperty(TYPE, "String")
      attrJson.add(VALUE, rowJson.get(fieldName))

      attrJson


    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getLongArray(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val metaJson = new JsonObject
      metaJson.addProperty(BASE_TYPE, "Long")

      val attrJson = new JsonObject
      attrJson.add(METADATA, metaJson)

      attrJson.addProperty(TYPE, "List")
      attrJson.add(VALUE, rowJson.get(fieldName).getAsJsonArray)

      attrJson

    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

  def getStringArray(rowJson:JsonObject, fieldName:String, nullable:Boolean):JsonElement = {

    try {

      val metaJson = new JsonObject
      metaJson.addProperty(BASE_TYPE, "String")

      val attrJson = new JsonObject
      attrJson.add(METADATA, metaJson)

      attrJson.addProperty(TYPE, "List")
      attrJson.add(VALUE, rowJson.get(fieldName).getAsJsonArray)

      attrJson

    } catch {
      case _:Throwable =>
        if (nullable) JsonNull.INSTANCE
        else
          throw new Exception(s"No value provided for field `$fieldName`.")
    }

  }

}
