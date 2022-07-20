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

import com.typesafe.config.ConfigObject
import de.kp.works.beats.BeatsConf

import scala.collection.JavaConversions.asScalaBuffer

object PlcOptions {

  private val plcCfg = BeatsConf.getBeatCfg(BeatsConf.PLC_CONF)
  private val receiverCfg = plcCfg.getConfig("receiver")

  /**
   * Method to retrieve the list of PLC fields that
   * must be used to retrieve data
   */
  def getPlcFields:Seq[PlcField] = {

    val values = receiverCfg.getList("fields")
    values.map {
      case configObject: ConfigObject =>

        val field = configObject.toConfig
        PlcField(fieldAddress = field.getString("address"), fieldName = field.getString("name"))

      case _ =>
        throw new Exception(s"PLC fields are not configured properly")
    }

  }
  /**
   * Method to retrieve the configured unique
   * identifier of the supported PLC
   */
  def getPlcId:String = receiverCfg.getString("ident")
  /**
   * Method to retrieve the configured NGSI
   * entity type of the supported PLC
   */
  def getPlcType:String = receiverCfg.getString("type")
  /**
   * Method to retrieve the endpoint of the
   * configured and supported PLC
   */
  def getPlcUrl:String = receiverCfg.getString("endpoint")

}
