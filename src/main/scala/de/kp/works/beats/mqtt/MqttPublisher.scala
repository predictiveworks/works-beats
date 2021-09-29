package de.kp.works.beats.mqtt
/*
 * Copyright (c) 2021 Dr. Krusche & Partner PartG. All rights reserved.
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

import de.kp.works.beats.BeatsConf
import de.kp.works.beats.ssl.SslOptions

object MqttPublisher {

  private val mqttCfg = BeatsConf.getOutputCfg.getConfig("mqtt")

  def build: MqttPublisher = {

    val client = mqttCfg.getString("client")
    client match {
      case "hive" =>
        buildHive

      case "paho" =>
        buildPaho

      case _ =>
        throw new Exception(s"The configured MQTT client `$client` is not support.")
    }

  }
  /** HIVEMQ SUPPORT **/

  /**
   * This method builds a HiveMQ MQTT publisher
   * either for MQTT v3.1.1 or MQTT v5
   */
  def buildHive: MqttPublisher = {

    val hiveCfg = mqttCfg.getConfig("hive")

    /* The host of the HiveMQ broker */
    val mqttHost = hiveCfg.getString("mqttHost")

    /* The port of the HiveMQ broker */
    val mqttPort = hiveCfg.getInt("mqttPort")

    /*
     * The name of a registered user of the HiveMQ broker.
     * This parameter is optional. Without providing a user
     * name, no authentication is executed.
     */
    val mqttUser = hiveCfg.getString("mqttUser")

    /* The password of the registered user */
    val mqttPass = hiveCfg.getString("mqttPass")

    /* The quality of service */
    val mqttQoS = {
      val v = hiveCfg.getInt("mqttQoS")
      Some(v)
    }

    /* The version of the MQTT protocol */
    val mqttVersion = {
      val v = hiveCfg.getInt("mqttVersion")
      Some(v)
    }

    /* The SSL configuration **/
    val mqttSsl = {
      val cfg = hiveCfg.getConfig("mqttSsl")
      if (!cfg.getBoolean("ssl")) None
      else
        Some(SslOptions.getSslOptions(cfg))
    }

    buildMqttHive(
      mqttHost,
      mqttPort,
      mqttUser,
      mqttPass,
      mqttSsl,
      mqttQoS,
      mqttVersion
    )

  }

  /**
   * A helper method to wrap the construction
   * of a HiveMQ MQTT publisher
   */
  private def buildMqttHive(
    mqttHost: String,
    mqttPort: Int,
    mqttUser: String,
    mqttPass: String,
    mqttSsl: Option[SslOptions],
    mqttQoS: Option[Int] = None,
    mqttVersion: Option[Int] = None): MqttPublisher = {

    new HivePublisher(
      mqttHost,
      mqttPort,
      mqttUser,
      mqttPass,
      mqttSsl,
      mqttQoS,
      mqttVersion)

  }

  /** ECLIPSE PAHO SUPPORT **/

  /**
   * This method builds a Mosquitto MQTT publisher
   * either for MQTT v3.1 or MQTT v3.1.1
   */
  def buildPaho: MqttPublisher = {

    val pahoCfg = mqttCfg.getConfig("paho")

    /* The url of the Mosquitto broker */
    val mqttUrl = pahoCfg.getString("mqttUrl")

    /* The client identifier */
    val clientId = {
      val v = pahoCfg.getString("clientId")
      if (v.isEmpty) None else Some(v)
    }

    /*
     * The name of a registered user of the Mosquitto broker.
     * This parameter is optional. Without providing a user
     * name, no authentication is executed.
     */
    val mqttUser = {
      val v = pahoCfg.getString("mqttUser")
      if (v.isEmpty) None else Some(v)
    }

    /* The password of the registered user */
    val mqttPass = {
      val v = pahoCfg.getString("mqttPass")
      if (v.isEmpty) None else Some(v)
    }

    /* The quality of service */
    val mqttQoS = {
      val v = pahoCfg.getInt("mqttQoS")
      Some(v)
    }

    /* The version of the MQTT protocol */
    val mqttVersion = {
      val v = pahoCfg.getInt("mqttVersion")
      Some(v)
    }

    /* The SSL configuration **/
    val mqttSsl = {
      val cfg = pahoCfg.getConfig("mqttSsl")
      if (!cfg.getBoolean("ssl")) None
      else
        Some(SslOptions.getSslOptions(cfg))
    }

    val cleanSession = Some(true)

    /* The keep alive interval timeout */
    val keepAlive = {
      val v = pahoCfg.getInt("keepAlive")
      Some(v)
    }

    /* The connection timeout */
    val timeout = {
      val v = pahoCfg.getInt("timeout")
      Some(v)
    }

    buildMqttPaho(
      mqttUrl,
      clientId,
      mqttUser,
      mqttPass,
      mqttSsl,
      cleanSession,
      mqttQoS,
      timeout,
      keepAlive,
      mqttVersion)

  }

  /**
   * A helper method to wrap the construction
   * of an Eclipse Paho MQTT publisher
   */
  private def buildMqttPaho(
    mqttUrl: String,
    clientId: Option[String],
    mqttUser: Option[String],
    mqttPass: Option[String],
    mqttSsl: Option[SslOptions] = None,
    cleanSession: Option[Boolean],
    qos: Option[Int],
    timeout: Option[Int],
    keepAlive: Option[Int],
    mqttVersion: Option[Int]): MqttPublisher = {

    new PahoPublisher(
      mqttUrl,
      clientId,
      mqttUser,
      mqttPass,
      mqttSsl,
      cleanSession,
      qos,
      timeout,
      keepAlive,
      mqttVersion)

  }

}

abstract class MqttPublisher() {

  def connect(): Unit

  def isConnected:Boolean

  def publish(topics:Array[String], message: String): Unit

  /**
   * This method evaluates the provided topics
   * and joins them into a single topic
   */
  protected def getMqttTopic(mqttTopics:Array[String]):String = {

    if (mqttTopics.isEmpty)
      throw new Exception("The topics must not be empty.")

    if (mqttTopics.length == 1)
      mqttTopics(0)

    else {

      /*
       * We expect a set of topics that differ at
       * the lowest level only
       */
      var levels = 0
      var head = ""

      mqttTopics.foreach(topic => {

        val tokens = topic.split("\\/").toList

        /* Validate levels */
        val length = tokens.length
        if (levels == 0) levels = length

        if (levels != length)
          throw new Exception("Supported MQTT topics must have the same levels.")

        /* Validate head */
        val init = tokens.init.mkString
        if (head.isEmpty) head = init

        if (head != init)
          throw new Exception("Supported MQTT topics must have the same head.")

      })
      /*
       * Merge MQTT topics with the same # of levels
       * into a single topic by replacing the lowest
       * level with a placeholder '#'
       */
      val topic = {

        val tokens = mqttTopics(0).split("\\/").init ++ Array("#")
        tokens.mkString("/")

      }

      topic

    }

  }

}
