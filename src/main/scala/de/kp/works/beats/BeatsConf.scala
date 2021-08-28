package de.kp.works.beats
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

import com.typesafe.config.{Config, ConfigFactory}

object BeatsConf {

  private val path = "reference.conf"
  /**
   * This is the reference to the overall configuration
   * file that holds all configuration required for this
   * application
   */
  private var cfg: Option[Config] = None

  val FIWARE_CONF  = "fiware"
  val FIWARE_NAME  = "Fiware"

  val OPCUA_CONF = "opcua"
  val OPCUA_NAME = "OpcUA"

  val OPENCTI_CONF = "opencti"
  val OPENCTI_NAME = "OpenCTI"

  val THINGSBOARD_CONF = "thingsboard"
  val THINGSBOARD_NAME = "ThingsBoard"

  def init(config: Option[String] = None): Boolean = {

    if (cfg.isDefined) true
    else {
      try {

        cfg = if (config.isDefined) {
          /*
           * An external configuration file is provided
           * and must be transformed into a Config
           */
          Option(ConfigFactory.parseString(config.get))

        } else {
          /*
           * The internal reference file is used to
           * extract the required configurations
           */
          Option(ConfigFactory.load(path))

        }
        true

      } catch {
        case t: Throwable =>
          false
      }
    }
  }

  def isInit: Boolean = {
    cfg.isDefined
  }

  def getBeatCfg(name:String):Config = {

    val now = new java.util.Date().toString

    if (cfg.isEmpty) {
      throw new Exception(s"[ERROR] $now - Configuration not initialized.")

    }

    name match {
      case FIWARE_CONF      => cfg.get.getConfig(FIWARE_CONF)
      case OPCUA_CONF       => cfg.get.getConfig(OPCUA_CONF)
      case OPENCTI_CONF     => cfg.get.getConfig(OPENCTI_CONF)
      case THINGSBOARD_CONF => cfg.get.getConfig(THINGSBOARD_CONF)
      case _ =>
        throw new Exception(s"[ERROR] $now - Unknown configuration request.")
    }
  }
}
