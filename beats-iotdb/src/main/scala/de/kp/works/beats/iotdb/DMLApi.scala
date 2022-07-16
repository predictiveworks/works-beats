package de.kp.works.beats.iotdb

/**
 * Copyright (c) 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.JsonObject
import org.apache.iotdb.session.Session
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType

import java.time.Instant
import scala.collection.mutable

object DMLApi extends DBConstants {
  /**
   * This method inserts the data of certain
   * device, represented as a JSON object.
   *
   *
   */
  def insertNGSIRecord(session:Session, deviceId:String, recordJson:JsonObject):Unit = {

    val dataTypes = mutable.ArrayBuffer.empty[TSDataType]
    val measurements = mutable.ArrayBuffer.empty[String]

    val values = mutable.ArrayBuffer.empty[Any]

    /* entity_id */
    val entityId = recordJson.remove(ID).getAsString
    dataTypes += TSDataType.TEXT

    measurements += "entity_id"
    values += entityId

    /* entity_type */
    val entityType = recordJson.get(TYPE).getAsString
    dataTypes += TSDataType.TEXT

    measurements += "entity_type"
    values += entityType

    /* timestamp */
    val timestamp = {
      if (recordJson.has(TIMESTAMP)) {
        recordJson.remove(TIMESTAMP).getAsJsonObject
          .get(VALUE).getAsLong

      } else Instant.now.toEpochMilli

    }
  }
}
/*

 */