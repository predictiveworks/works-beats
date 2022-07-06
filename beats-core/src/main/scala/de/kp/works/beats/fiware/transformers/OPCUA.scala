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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.{JsonElement, JsonNull, JsonObject}
import de.kp.works.beats.events.OpcUaEvent

object OPCUA extends BaseTransformer {

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  /**
   * A serialized [OpcUaEvent] is deserialized and
   * transformed into an NGSI compliant entity format.
   *
   * The `entityId` and `entityType` is derived from
   * the system name and the respective address.
   */
  override def transform(json: JsonObject): JsonElement = {
    /*
     * The provided `json` object has a common data format
     * that is used by all Works. Beats.
     *
     * {
     *    "type": "..",
     *    "event": ".."
     * }

     * Deserialize the event type and the respective data.
     * For OPC-UA events, the event type does not contain
     * any additional information.
     *
     * The event data represent the JSON description
     * of the [OpcUaEvent]
     */
    val event = json.get("event").getAsString
    val opcUaEvent = mapper.readValue(event, classOf[OpcUaEvent])
    /*
     * The main task of this transformation step is
     * to turn the [OpcUaEvent] into an NGSI compliant
     * representation.
     *
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     */
    val model = getModel(opcUaEvent)
    if (model.isEmpty) return JsonNull.INSTANCE

    val (entityType, attrName) = (model.get.head, model.get.last)
    val ngsiType = toCamelCase(entityType)
    /*
     * The system name specifies the unique identifier
     * of the OPC-UA receiver that provided the event
     */
    val systemName = opcUaEvent.systemName
    val ngsiId = s"urn:ngsi-ld:$ngsiType:${cleanText(systemName)}"

    val ngsiJson = new JsonObject
    ngsiJson.addProperty(ID, ngsiId)
    ngsiJson.addProperty(TYPE, ngsiType)
    /*
     * The current implementation assigns the event
     * attributes `sourceTime`, `serverTime` and
     * `statusCode` as metadata to the event value
     */
    val sourceTime = opcUaEvent.sourceTime
    val serverTime = opcUaEvent.serverTime
    val statusCode = opcUaEvent.statusCode

    val metaJson = new JsonObject
    metaJson.addProperty("sourceTime", sourceTime)
    metaJson.addProperty("serverTime", serverTime)
    metaJson.addProperty("statusCode", statusCode)

    try {

      val attrJson = new JsonObject

      val attrVal = opcUaEvent.dataValue
      val attrType = getBasicType(attrVal)

      putValue(attrType, attrVal, attrJson)
      if (attrJson.has(VALUE)) {

        ngsiJson.add(attrName, attrJson)
        ngsiJson

      } else JsonNull.INSTANCE

    } catch {
      case t:Throwable =>
        val message = s"Transforming OPC-UA event into NGSI entity failed: ${t.getLocalizedMessage}"
        error(message)

        JsonNull.INSTANCE
    }

  }

  /**
   * A helper method to evaluate the `address` of the
   * [OpcUaEvent] and extract the model info, i.e.
   * entity type and attribute name.
   *
   * This implementation currently ignores the OPC-UA
   * namespace, as it is expected that the address
   * specifies entity type and attribute name.
   *
   * The unique (entity) identifier is derived from
   * the system name (see above).
   */
  private def getModel(event:OpcUaEvent):Option[Seq[String]] = {
    try {
      /*
       * The identifier type is extracted from the
       * `address` attributes of the [OpcUaEvent[
       */
      val address = event.address
      /*
       * The format of the `address` value depends
       * on the value of the `topicType` attribute
       */
      val topicType = event.topicType
      topicType match {
        case "NodeId" =>
          val tokens = address.split(";")
          val identifier =
            if (tokens.length == 2)
              tokens.last.replace("s=", "")

            else tokens.head
          /*
           * We expect that the identifier specifies the
           * entity type and the attribute name of the
           * provided attribute value:
           *
           * <entity-type>.<attribute>
           */
          val model = identifier.split("\\.")
          if (model.length == 2) Some(model) else None

        case "Path" =>
          /*
           * We expect that the identifier specifies the
           * entity type and the attribute name of the
           * provided attribute value:
           *
           * <entity-type>/<attribute>
           */
          val model = address.split("\\/")
          if (model.length == 2) Some(model) else None

        case _ => None

      }

    } catch {
      case t:Throwable =>
        val message = s"Transforming an OPC-UA event into an NGSI entity failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }
  }

}
