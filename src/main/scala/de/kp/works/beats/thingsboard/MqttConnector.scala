package de.kp.works.beats.thingsboard
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
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.ssl.SslOptions
import org.eclipse.paho.client.mqttv3
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import java.nio.charset.Charset

/**
 * MQTT v3.1 & MQTT v3.1.1
 *
 *
 * This class uses the Eclipse Paho MqttClient (http://www.eclipse.org/paho/).
 *
 * Eclipse Paho is an umbrella project for several MQTT and MQTT-SN client implementations.
 * This project was one of the first open source MQTT client implementations available and
 * is actively maintained by a huge community.
 *
 * Paho features a Java client which is suited for embedded use, Android applications and
 * Java applications in general. The initial code base was donated to Eclipse by IBM in 2012.
 *
 * The Eclipse Paho Java client is rock-solid and is used by a broad range of companies from
 * different industries around the world to connect to MQTT brokers.
 *
 * The synchronous/blocking API of Paho makes it easy to implement the applications MQTT logic
 * in a clean and concise way while the asynchronous API gives the application developer full
 * control for high-performance MQTT clients that need to handle high throughput.
 *
 * The Paho API is highly callback based and allows to hook in custom business logic to different
 * events, e.g. when a message is received or when the connection to the broker was lost.
 *
 * Paho supports all MQTT features and a secure communication with the MQTT Broker is possible
 * via TLS.
 *
 */
class MqttConnector(
  brokerUrl:String,
  outputHandler:OutputHandler,
  mqttCreds: MqttCreds,
  sslOptions: Option[SslOptions] = None) {

  private val thingsboardCfg = BeatsConf.getBeatCfg(BeatsConf.THINGS_CONF)
  private val mqttCfg = thingsboardCfg.getConfig("mqtt")
  /**
   * This topic is used to subscribe to shared device attribute changes.
   */
  private val THINGS_BOARD_TOPIC:String = "v1/gateway/attributes"
  private val QOS:Int = 1

  def stop(): Unit = {
    /* Do nothing */
  }

  def start(): Unit = {

    val UTF8 = Charset.forName("UTF-8")

    /* 						MESSAGE PERSISTENCE
     *
     * Since we don’t want to persist the state of pending
     * QoS messages and the persistent session, we are just
     * using a in-memory persistence. A file-based persistence
     * is used by default.
     */
    val persistence = new MemoryPersistence()
    /*
     * Initializing Mqtt Client specifying brokerUrl, clientId
     * and MqttClientPersistence
     */
    val clientId: String =
      if (mqttCreds.clientId.nonEmpty) mqttCreds.clientId
      else mqttv3.MqttClient.generateClientId()

    val client = new mqttv3.MqttClient(brokerUrl, clientId, persistence)

    /* Initialize mqtt parameters */
    val options = getOptions

    /*
     * Callback automatically triggers as and when new message
     * arrives on specified topic
     */
    val callback: mqttv3.MqttCallback = new mqttv3.MqttCallback() {

      override def messageArrived(topic: String, message: mqttv3.MqttMessage) {

        /* Timestamp when the message arrives */
        val timestamp = new java.util.Date().getTime
        val seconds = timestamp / 1000

        val payload = message.getPayload
        if (payload == null) {

          /* Do nothing */

        } else {

          val qos = message.getQos

          val duplicate = message.isDuplicate
          val retained = message.isRetained

          /* Serialize plain byte message */
          val json = new String(payload, UTF8)

          val mqttEvent = new MqttEvent(timestamp, seconds, topic, qos, duplicate, retained, json)
          outputHandler.sendThingsEvent(mqttEvent)

        }

      }

      override def deliveryComplete(token: mqttv3.IMqttDeliveryToken) {}

      override def connectionLost(cause: Throwable) {
        restart(cause)
      }

    }

    /*
     * Set up callback for MqttClient. This needs to happen before
     * connecting or subscribing, otherwise messages may be lost
     */
    client.setCallback(callback)

    /* Connect to MqttBroker */
    client.connect(options)

    /* Subscribe to ThingsBoard topic */
    client.subscribe(THINGS_BOARD_TOPIC, QOS)

  }

  private def getOptions: mqttv3.MqttConnectOptions = {

    /* Initialize mqtt parameters */
    val options = new mqttv3.MqttConnectOptions()

    /* User authentication
     *
     * ThingsBoard uses access token credentials. The [MqttClient] needs to
     * send MQTT CONNECT message with username that contains the access token.
     *
     * The alternative option is to use Basic MQTT Credentials - combination of
     * client id, username and password
     */
    if (mqttCreds.authToken.isDefined)
      options.setUserName(mqttCreds.authToken.get)

    else {
      if (mqttCreds.userName.isEmpty || mqttCreds.userPass.isEmpty)
        throw new Exception("No user credentials provided.")

      options.setUserName(mqttCreds.userName.get)
      options.setPassword(mqttCreds.userPass.get.toCharArray)
    }



    if (sslOptions.isDefined)
      options.setSocketFactory(sslOptions.get.getSslSocketFactory)
    /*
     * We always start with a clean session
     */
    options.setCleanSession(true)

    val timeout = mqttCfg.getInt("timeout")
    options.setConnectionTimeout(timeout)

    val keepAlive = mqttCfg.getInt("keepAlive")
    options.setKeepAliveInterval(keepAlive)
    /*
     * Connect with MQTT 3.1 or MQTT 3.1.1
     *
     * By default, Paho tries to connect with MQTT 3.1.1
     * and falls back to MQTT 3.1 if it’s not possible to
     * connect with 3.1.1.
     *
     * We therefore do not specify a certain MQTT version.
     */
    options

  }

  def restart(t:Throwable): Unit = {

    val now = new java.util.Date().toString
    println(s"[MqttConnector] $now - Restart due to: ${t.getLocalizedMessage}")

    start()

  }

}
