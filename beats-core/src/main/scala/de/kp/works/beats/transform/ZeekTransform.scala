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

import com.google.gson.{JsonObject, JsonParser}
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.events.FileEvent
import de.kp.works.beats.transform.BeatsFormats.{BeatFormat, NGSI}
import de.kp.works.beats.transform.zeek.{StructType, ZeekReplace, ZeekSchema, ZeekUtil}

/**
 * Zeek sensor platform generates and updates 35+ log files
 * (conn.log, dns.log, http.log, etc.) that are monitored by
 * the [FileMonitor].
 *
 * The file name can be used as a semantic (content) indicator,
 * and no additional transformation must be applied to create
 * meaningful event types or topics.
 */
class ZeekTransform(format:BeatFormat=NGSI) extends FileTransform {

  private val zeekCfg = BeatsConf.getBeatCfg(BeatsConf.ZEEK_CONF)

  override def transform(fileEvent:FileEvent, namespace:String):Option[JsonObject] = {

    val eventType = fileEvent.eventType

    if (!eventType.endsWith(".log")) {
      val message = s"The provided event type `$eventType` does not describe a Zeek log file."
      error(message)

      return None
    }

    try {
      /*
       * Determine method to replace Zeek specific names
       * to harmonize field names
       */
      val table = eventType.replace(".log", "")
      /*
       * Flatten the Zeek log event and harmonize the
       * specified attribute names
       */
      val zeekJson = prepareEvent(table, fileEvent.eventData)
      /*
       * Transform the Zeek event into an NGSI compliant
       * entity. This is achieved by leveraging the Zeek
       * schema definitions
       */
      val schema = findSchema(table)
      val ngsiEntity = buildNGSIEntity(table, schema, zeekJson)

      val json = new JsonObject
      /*
       * In case of a [FileEvent], the `eventType` specifies
       * the file name. It is enriched with the namespace,
       *
       * e.g `beat/zeek/dns.log`.
       */
      json.addProperty("type", s"beat/$namespace/${fileEvent.eventType}")
      json.addProperty("event", ngsiEntity.toString)

      Some(json)

    }  catch {
      case t:Throwable =>
        val message = s"Transforming a Zeek log file failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }

  }

  private def buildNGSIEntity(table:String, schema:StructType, event:JsonObject):JsonObject = {
    /*
     * This implementation assigns each Zeek sensor receiver
     * a unique identifier to distinguish it from all other
     * sensors
     */
    val receiverCfg = zeekCfg.getConfig("receiver")
    val zeekIdent = receiverCfg.getString("zeekIdent")
    /*
     * Each identifier according to the NGSI-LD specification
     * is a URN follows a standard format:
     *
     * urn:ngsi-ld:<entity-type>:<entity-id>
     *
     * Mapping STIX objects or observables to NGSI entities
     * and relations is faced with the fact, that references
     * in STIX (identifiers) follow the STIX naming convention,
     * but it is not guaranteed that the object and observable
     * type can be inferred correctly.
     *
     * Therefore, STIX related NGSI entity identifier do not
     * specify the <entity-type> as expected above.
     */
    val ngsiType = toCamelCase(table, separator="_")
    val ngsiId = s"urn:ngsi-ld:$ngsiType:$zeekIdent"

    val ngsiJson = new JsonObject
    ngsiJson.addProperty(ID, ngsiId)
    ngsiJson.addProperty(TYPE, ngsiType)

    ZeekUtil.json2NGSI(ngsiJson, event, schema)
    ngsiJson

  }

  private def findSchema(table:String):StructType = {

    val methods = ZeekSchema.getClass.getMethods

    val method = methods.filter(m => m.getName == table).head
    val schema = method.invoke(ZeekSchema).asInstanceOf[StructType]

    schema

  }
  private def prepareEvent(table:String, event:String):JsonObject = {

    val methods = ZeekReplace.getClass.getMethods
    val method = methods.filter(m => m.getName == s"replace_$table").head
    /*
     * Flatten the Zeek log event and harmonize the
     * specified attribute names
     */
    val log = JsonParser.parseString(event).getAsJsonObject
    val zeekJson = method.invoke(ZeekReplace, log).asInstanceOf[JsonObject]

    zeekJson

  }
}
