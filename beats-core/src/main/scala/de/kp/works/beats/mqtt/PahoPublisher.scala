package de.kp.works.beats.mqtt
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

import de.kp.works.beats.ssl.SslOptions
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory

import java.nio.charset.Charset

/**
 * MQTT v3.1 & MQTT v3.1.1
 * 
 * 
 * This input stream uses the Eclipse Paho MqttClient (http://www.eclipse.org/paho/).
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
 * @param mqttUrl           Url of remote mqtt broker
 * @param clientId           ClientId to use for the mqtt connection
 * @param mqttUser					 Name of the mqtt user
 * @param mqttPass           Password of the mqtt user
 * @param mqttSsl            SSL authentication
 * @param cleanSession       Sets the mqtt cleanSession parameter
 * @param qos                Quality of service to use for the topic subscriptions
 * @param timeout            Connection timeout for the mqtt connection
 * @param keepAlive          Keep alive interval for the mqtt connection
 * @param mqttVersion        Version to use for the mqtt connection
 */
class PahoPublisher(
   mqttUrl: String,
   clientId: Option[String],
   mqttUser: Option[String],
   mqttPass: Option[String],
   mqttSsl: Option[SslOptions] = None,
   cleanSession: Option[Boolean],
   qos: Option[Int],
   timeout: Option[Int],
   keepAlive: Option[Int],
   mqttVersion: Option[Int]) extends MqttPublisher {

  private final val LOG = LoggerFactory.getLogger(classOf[PahoPublisher])
  private val UTF8 = Charset.forName("UTF-8")

  private var connected:Boolean = false
  private var mqtt3Client: Option[MqttClient] = None

  private def connectToMqtt3(): Unit = {

    /* 						MESSAGE PERSISTENCE
     *
     * Since we don’t want to persist the state of pending
     * QoS messages and the persistent session, we are just
     * using a in-memory persistence. A file-based persistence
     * is used by default.
     */
    val persistence = new MemoryPersistence()
    /*
     * Initializing Mqtt Client specifying brokerUrl, clientID
     * and [MqttClientPersistence]
     */
    val client = new MqttClient(
      mqttUrl,
      clientId.getOrElse(MqttClient.generateClientId()),
      persistence)

    /* Connect to MqttBroker */
    client.connect(getMqttOptions)
    connected = client.isConnected

    if (connected) mqtt3Client = Some(client)

  }
  /**
   * This method prepares the MQTT connection
   * parameters
   */
  private def getMqttOptions: MqttConnectOptions = {
    /*
     * The MQTT connection is configured to
     * enable automatic rec-connection
     */
    val options = new MqttConnectOptions()
    options.setAutomaticReconnect(true)

    options.setCleanSession(cleanSession.getOrElse(true))
   options.setConnectionTimeout(timeout.getOrElse(10))

    if (keepAlive.isDefined) {
      options.setKeepAliveInterval(keepAlive.get)
    }

    /* User authentication */

    if (mqttUser.isDefined && mqttPass.isDefined)
      options.setUserName(mqttUser.get)
      options.setPassword(mqttPass.get.toCharArray)
    
    if (mqttSsl.isDefined)
      options.setSocketFactory(mqttSsl.get.getSslSocketFactory)

    /*
     * Connect with MQTT 3.1 or MQTT 3.1.1
     * 
     * Depending which MQTT broker you are using, you may want to explicitely 
     * connect with a specific MQTT version.
     * 
     * By default, Paho tries to connect with MQTT 3.1.1 and falls back to 
     * MQTT 3.1 if it’s not possible to connect with 3.1.1.
     * 
     * We therefore do not specify a certain MQTT version.
     */
    
    if (mqttVersion.isDefined) {
      options.setMqttVersion(mqttVersion.get)
    }
    
    options
    
  }

  override def connect(): Unit = connectToMqtt3()

  override def isConnected:Boolean = connected

  override def publish(topics:Array[String], message: String): Unit = {

    if (!connected || mqtt3Client.isEmpty) {
      throw new Exception("No connection to MQTT broker established")
    }
    /*
     * The Paho client supports a single topic; the publish
     * interface, however, accept a list of topics to increase
     * use flexibility
     */
    val mqttTopic = getMqttTopic(topics)
    val mqttMessage = new MqttMessage()

    mqttMessage.setQos(qos.getOrElse(1))
    mqttMessage.setPayload(message.getBytes(UTF8))

    mqtt3Client.get.publish(mqttTopic, mqttMessage)

  }

}
