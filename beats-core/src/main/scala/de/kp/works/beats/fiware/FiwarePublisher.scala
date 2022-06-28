package de.kp.works.beats.fiware
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

import com.google.gson.{JsonElement, JsonObject}
import de.kp.works.beats.BeatsConf

class FiwarePublisher(namespace:String) {

  def publish(eventData:JsonElement):Unit = {
    /*
     * `eventData` is a JSON object with the following
     * format:
     *
     * {
     *  "format": "...",
     *  "entity": {
     *    "id": "...",
     *    "type": "...",
     *    "timestamp": {...},
     *    "rows": [
     *      {
     *        "action": {...},
     *        "<column>": {...},
     *
     *      }
     *    ]
     *  }
     * }
     */
    namespace match {

      case BeatsConf.FIWARE_NAME =>
        /*
         * A FIWARE entity event is re-sent to
         * the context broker.
         */
        publishers.FIWARE.publish(eventData)

      case BeatsConf.FLEET_NAME =>
        /*
         * Osquery log results are transformed
         * into NGSI compliant entities
         */
        publishers.FLEET.publish(eventData)

      case BeatsConf.OPCUA_NAME =>
        /*
         * OPC-UA events are transformed into
         * NGSI compliant entities
         */
        publishers.OPCUA.publish(eventData)

      case BeatsConf.OPENCTI_NAME =>
        /*
         * STIX threat events are transformed into
         * NGSI compliant entities
         */
        publishers.OPENCTI.publish(eventData)

      case BeatsConf.OSQUERY_NAME =>
        /*
         * Osquery log results are transformed
         * into NGSI compliant entities
         */
        publishers.OSQUERY.publish(eventData)

      case BeatsConf.THINGS_NAME =>
        /*
         * ThingsBoard attribute events are transformed
         * into NGSI compliant entities
         */
        publishers.THINGS.publish(eventData)

      case BeatsConf.ZEEK_NAME =>
        /*
         * Zeek log events are transformed into
         * NGSI compliant entities
         */
        publishers.ZEEK.publish(eventData)

      case _ =>
        throw new Exception(s"Unknown namespace `$namespace` detected.")
    }
  }

  def transform(json:JsonObject):JsonElement = {

    namespace match {

      case BeatsConf.FIWARE_NAME =>
        /*
         * A FIWARE entity event is re-sent to
         * the context broker.
         */
        transformers.FIWARE.transform(json)

      case BeatsConf.FLEET_NAME =>
        /*
         * Osquery log results are transformed
         * into NGSI compliant entities
         */
        transformers.FLEET.transform(json)

      case BeatsConf.OPCUA_NAME =>
        /*
         * OPC-UA events are transformed into
         * NGSI compliant entities
         */
        transformers.OPCUA.transform(json)

      case BeatsConf.OPENCTI_NAME =>
        /*
         * STIX threat events are transformed into
         * NGSI compliant entities
         */
        transformers.OPENCTI.transform(json)

      case BeatsConf.OSQUERY_NAME =>
        /*
         * Osquery log results are transformed
         * into NGSI compliant entities
         */
        transformers.OSQUERY.transform(json)

      case BeatsConf.THINGS_NAME =>
        /*
         * ThingsBoard attribute events are transformed
         * into NGSI compliant entities
         */
        transformers.THINGS.transform(json)

      case BeatsConf.ZEEK_NAME =>
        /*
         * Zeek log events are transformed into
         * NGSI compliant entities
         */
        transformers.ZEEK.transform(json)

      case _ =>
        throw new Exception(s"Unknown namespace `$namespace` detected.")
    }

  }

}
