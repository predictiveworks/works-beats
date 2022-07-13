package de.kp.works.beats.opcua
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
import com.google.gson
import de.kp.works.beats.events.OpcUaEvent
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue

import java.time.Instant
import java.util.regex.Pattern

object OpcUaTransform {

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  /**
   * This is the main method to combine topic and value
   * that refer to the OPCUA server notification into a
   * single data format
   */
  def transform(topic: OpcUaTopic, value: DataValue): gson.JsonObject = {
    /*
     * Extract fields from data value, and fill
     * with default values if necessary
     */
    val sourceTime =
      if (value.getSourceTime.isNull)
        Instant.now()
      else
        value.getSourceTime.getJavaInstant

    val serverTime =
      if (value.getServerTime.isNull)
        Instant.now()
      else
        value.getServerTime.getJavaInstant

    val sourcePicoseconds = value.getSourcePicoseconds.intValue()
    val serverPicoseconds = value.getServerPicoseconds.intValue()

    val statusCode = value.getStatusCode.getValue
    val dataValue =
      if (value.getValue.isNotNull)
        value.getValue.getValue
      else null
    /*
     * As a first step, topic & value are organized
     * as [OpcUaEvent] and then this event is converted
     * into a JsonObject
     */
    val event = OpcUaEvent(
      /*
       * STEP #1: Extract fields from OPC-UA topic
       */
      address    = topic.address,
      browsePath = topic.browsePath,
      topicName  = topic.topicName,
      topicType  = topic.topicType.toString,
      systemName = topic.systemName,
      /*
       * STEP #2: Data value representation
       */
      sourceTime = sourceTime.toEpochMilli,
      sourcePicoseconds = sourcePicoseconds,
      serverTime = serverTime.toEpochMilli,
      serverPicoseconds = serverPicoseconds,
      statusCode = statusCode,
      dataValue = dataValue)

    val serialized = mapper.writeValueAsString(event)
    gson.JsonParser.parseString(serialized).getAsJsonObject

  }

  def splitAddress(address: String): Array[String] = {
    val regex = "(?<!\\\\)\\/"
    address.split(regex)
  }
  /**
   * This method transforms the OPC-UA data value
   * into a JSON object
   */
  def dataValueToJson(v: DataValue): gson.JsonObject = {

    val value = if (v.getValue.isNotNull) v.getValue.getValue else null
    val statusCode = v.getStatusCode.getValue

    val sourceTime = if (v.getSourceTime.isNull) Instant.now() else v.getSourceTime.getJavaInstant
    val serverTime = if (v.getServerTime.isNull) Instant.now() else v.getServerTime.getJavaInstant

    val sourcePicoseconds = v.getSourcePicoseconds.intValue()
    val serverPicoseconds = v.getServerPicoseconds.intValue()

    val topicValue = OpcUaTopicValue(
      sourceTime = sourceTime.toEpochMilli,
      sourcePicoseconds = sourcePicoseconds,
      serverTime = serverTime.toEpochMilli,
      serverPicoseconds = serverPicoseconds,
      statusCode = statusCode,
      value = value)

    val serialized = mapper.writeValueAsString(topicValue)
    gson.JsonParser.parseString(serialized).getAsJsonObject

  }
  /**
   * This method transforms the OPC-UA topic
   * into a JSON object
   */
  def dataTopicToJson(topic: OpcUaTopic): gson.JsonObject = {

    val serialized = mapper.writeValueAsString(topic)
    gson.JsonParser.parseString(serialized).getAsJsonObject

  }

  def parse(text: String): OpcUaTopic = {

    var topic = parse1(text)

    if (topic == null) topic = parse2(text)
    if (topic == null) topic = parse3(text)

    if (topic == null)
      throw new Exception(s"Could not create topic from text representation.")

    topic

  }

  private def parse1(text: String): OpcUaTopic = {

    try {
      /*
       * The node starts with the prefix opc/{id},
       * and the OPC-UA namespace reference (ns)
       * is specified as a number in [0..9], where
       * ns=0 describes the default namespace.
       */
      val regex = "opc\\/(\\w+)\\/Node\\/ns=([0-9].*);s=(.*)"

      val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
      val matcher = pattern.matcher(text)

      var systemName: String = null

      var addr1: String = null
      var addr2: String = null

      while (matcher.find) {

        systemName = matcher.group(1)

        addr1 = matcher.group(2)
        addr2 = matcher.group(3)

      }

      if (addr1 == null || addr2 == null || systemName == null)
        throw new Exception("Regex does not match.")

      /*
       * The address combines the namespace reference
       * index, the node type and the respective value
       *
       * ns=<namespace-index>;<type>=<value>

       * Currently there are 4 node types defined:
       *
       * - i: Numeric (value is a UInteger)
       * - s: String (value is a String)
       * - g: Guid (value is a Guid/UUID)
       * - b: Opaque (value is ByteString)
       *
       * This [OpcUaBeat] supports string types only
       */
      val address = s"ns=$addr1;s=$addr2"
      OpcUaTopic(address = address, topicName = text, topicType = OpcUaTopicTypes.NodeId, systemName = systemName)

    } catch {
      case _: Throwable => null
    }

  }

  private def parse2(text: String): OpcUaTopic = {

    try {
      val regex = "opc\\/(\\w+)\\/Node\\/(.*)"

      val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
      val matcher = pattern.matcher(text)

      var systemName: String = null
      var address: String = null

      while (matcher.find) {

        systemName = matcher.group(1)
        address = matcher.group(2)

      }

      if (address == null || systemName == null)
        throw new Exception("Regex does not match.")

      OpcUaTopic(address = address, topicName = text, topicType = OpcUaTopicTypes.NodeId, systemName = systemName)

    } catch {
      case _: Throwable => null
    }

  }

  private def parse3(text: String): OpcUaTopic = {

    try {
      val regex = "opc\\/(\\w+)\\/Path\\/(.*)\\/(.*)"

      val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
      val matcher = pattern.matcher(text)

      var systemName: String = null
      var addr1: String = null
      var addr2: String = null

      while (matcher.find) {

        systemName = matcher.group(1)
        addr1 = matcher.group(2)
        addr2 = matcher.group(3)

      }

      if (addr1 == null || addr2 == null || systemName == null)
        throw new Exception("Regex does not match.")

      val address = s"$addr1/$addr2"
      OpcUaTopic(address = address, topicName = text, topicType = OpcUaTopicTypes.Path, systemName = systemName)

    } catch {
      case _: Throwable => null
    }

  }
}
