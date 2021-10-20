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

import com.hivemq.client.internal.mqtt.message.auth.MqttSimpleAuthBuilder
import com.hivemq.client.internal.mqtt.message.auth.mqtt3.Mqtt3SimpleAuthViewBuilder
import com.hivemq.client.internal.mqtt.mqtt3.Mqtt3RxClientViewBuilder
import com.hivemq.client.internal.mqtt.{MqttClientSslConfigImplBuilder, MqttRxClientBuilder}
import com.hivemq.client.mqtt._
import com.hivemq.client.mqtt.datatypes._
import com.hivemq.client.mqtt.mqtt3._
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt5._
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import de.kp.works.beats.ssl.SslOptions
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import java.nio.charset.Charset
import java.security.Security
import java.util.UUID
import scala.collection.JavaConversions._

class HivePublisher(
    mqttHost: String,
    mqttPort: Int,
    mqttUser: String,
    mqttPass: String,
    mqttSsl: Option[SslOptions],
    mqttQoS: Option[Int] = None,
    mqttVersion: Option[Int] = None) extends MqttPublisher {

  private final val LOG = LoggerFactory.getLogger(classOf[HivePublisher])
  private val UTF8 = Charset.forName("UTF-8")

  private var connected:Boolean = false

  private var mqtt3Client: Option[Mqtt3AsyncClient] = None
  private var mqtt5Client: Option[Mqtt5AsyncClient] = None

  /***** MQTT 3 *****/

  private val onMqtt3Connect = new java.util.function.BiConsumer[Mqtt3ConnAck, Throwable] {

    def accept(connAck: Mqtt3ConnAck, throwable: Throwable):Unit = {

      /* Error handling */
      if (throwable != null) {
        /*
         * In case of an error, the respective message is log,
         * but streaming is continued
         */
        LOG.error(throwable.getLocalizedMessage)

      } else {

        LOG.info("Connecting to HiveMQ Broker successfull")
        connected = true

      }
    }

  }

  private def connectToMqtt3(): Unit = {

    /***** BUILD CLIENT *****/

    val identifier = UUID.randomUUID().toString

    val builder = new Mqtt3RxClientViewBuilder()
      .identifier(identifier)
      .serverHost(mqttHost)
      .serverPort(mqttPort)

    /* Transport layer security */

    val sslConfig = getMqttSslConfig
    if (sslConfig != null) builder.sslConfig(sslConfig)

    /* Application layer security */

    val simpleAuth = new Mqtt3SimpleAuthViewBuilder.Default()
      .username(mqttUser)
      .password(mqttPass.getBytes(UTF8))
      .build()

    builder.simpleAuth(simpleAuth)

    /***** CONNECT *****/

    mqtt3Client = Some(builder.buildAsync())
    mqtt3Client.get
      .connectWith()
      .send()
      .whenComplete(onMqtt3Connect)

  }

  private def publishToMqtt3(topics:Array[String], payload: Array[Byte]):Unit = {

    if (!connected || mqtt3Client.isEmpty) {
      throw new Exception("No connection to HiveMQ broker established")
    }
    /*
     * The HiveMQ client supports a single topic; the publish
     * interface, however, accept a list of topics to increase
     * use flexibility
     */
    val mqttTopic = getMqttTopic(topics)

    /*
     * Define the connection callback, and, in case of a
     * successful connection, continue to subscribe to
     * an MQTT topic
     */
    val onPublish = new java.util.function.BiConsumer[Mqtt3Publish, Throwable] {

      def accept(mqtt3Publish: Mqtt3Publish, throwable: Throwable):Unit = {

        /* Error handling */
        if (throwable != null) {
          /*
           * In case of an error, the respective message is log,
           * but streaming is continued
           */
          LOG.error(throwable.getLocalizedMessage)

        } else {

          LOG.info("Publishing to HiveMQ Broker successfull")

        }
      }

    }

    mqtt3Client.get
      .publishWith()
      .topic(mqttTopic)
      .payload(payload)
      .qos(getMqttQoS)
      .send()
      .whenComplete(onPublish)

  }

  /***** MQTT 5 *****/

  private val onMqtt5Connect = new java.util.function.BiConsumer[Mqtt5ConnAck, Throwable] {

    def accept(connAck: Mqtt5ConnAck, throwable: Throwable):Unit = {

      /* Error handling */
      if (throwable != null) {
        /*
         * In case of an error, the respective message is log,
         * but streaming is continued
         */
        LOG.error(throwable.getLocalizedMessage)

      } else {

        LOG.info("Connecting to HiveMQ Broker successfull")
        connected = true

      }
    }

  }

  private def connectToMqtt5(): Unit = {

    /***** BUILD CLIENT *****/

    val identifier = UUID.randomUUID().toString

    val builder = new MqttRxClientBuilder()
      .identifier(identifier)
      .serverHost(mqttHost)
      .serverPort(mqttPort)

    /* Transport layer security */

    val sslConfig = getMqttSslConfig
    if (sslConfig != null) builder.sslConfig(sslConfig)

    /* Application layer security */

    val simpleAuth = new MqttSimpleAuthBuilder.Default()
      .username(mqttUser)
      .password(mqttPass.getBytes(UTF8))
      .build()

    builder.simpleAuth(simpleAuth)

    /***** CONNECT *****/

    mqtt5Client = Some(builder.buildAsync())
    mqtt5Client.get
      .connectWith()
      .send()
      .whenComplete(onMqtt5Connect)

  }

  private def publishToMqtt5(topics:Array[String], payload: Array[Byte]):Unit = {

    if (!connected || mqtt5Client.isEmpty) {
      throw new Exception("No connection to HiveMQ server established")
    }
    /*
     * The HiveMQ client supports a single topic; the publish
     * interface, however, accept a list of topics to increase
     * use flexibility
     */
    val mqttTopic = getMqttTopic(topics)

    val onPublish = new java.util.function.BiConsumer[Mqtt5PublishResult, Throwable] {

      def accept(mqtt5Publish: Mqtt5PublishResult, throwable: Throwable):Unit = {

        /* Error handling */
        if (throwable != null) {
          /*
           * In case of an error, the respective message is log,
           * but streaming is continued
           */
          LOG.error(throwable.getLocalizedMessage)

        } else {

          LOG.info("Publishing to HiveMQ Broker successful")

        }
      }

    }

    mqtt5Client.get
      .publishWith()
      .topic(mqttTopic)
      .payload(payload)
      .qos(getMqttQoS)
      .send()
      .whenComplete(onPublish)

  }

  private def getMqttQoS: MqttQos = {

    val qos = mqttQoS.getOrElse(1)
    qos match {
      case 0 =>
        /*
         * QoS for at most once delivery according to the
         * capabilities of the underlying network.
         */
        MqttQos.AT_MOST_ONCE
      case 1 =>
        /*
         * QoS for ensuring at least once delivery.
         */
        MqttQos.AT_LEAST_ONCE
      case 2 =>
        /*
         * QoS for ensuring exactly once delivery.
         */
        MqttQos.EXACTLY_ONCE
      case _ => throw new RuntimeException(s"Quality of Service '$qos' is not supported.")
    }

  }

  /** Transport layer security */

  private def getMqttSslConfig: MqttClientSslConfig = {

    if (mqttSsl.isDefined) {

      Security.addProvider(new BouncyCastleProvider())

      val sslOptions = mqttSsl.get
      val builder = new MqttClientSslConfigImplBuilder.Default()

      /* CipherSuites */
      val cipherSuites = sslOptions.getCipherSuites
      if (cipherSuites != null)
          builder.cipherSuites(cipherSuites)

      /* Hostname verifier */
      builder.hostnameVerifier(sslOptions.getHostnameVerifier)

      /* Key manager factory */
      val keyManagerFactory = sslOptions.getKeyManagerFactory
      if (keyManagerFactory != null)
          builder.keyManagerFactory(keyManagerFactory)

      /* Trust manager factory */
      val trustManagerFactory = sslOptions.getTrustManagerFactory
      if (trustManagerFactory != null)
          builder.trustManagerFactory(trustManagerFactory)


      builder.build()

    } else null

  }

  override def connect(): Unit = {
    /*
     * HiveMQ supports MQTT version 5 as well as version 3;
     * default is version 3
     */
    val version = mqttVersion.getOrElse(3)
    if (version == 3)
      connectToMqtt3()

    else
      connectToMqtt5()

  }

  override def isConnected:Boolean = connected

  override def publish(topics:Array[String], message: String): Unit = {

    val payload = message.getBytes(UTF8)

    /*
     * HiveMQ supports MQTT version 5 as well as version 3;
     * default is version 3
     */
    val version = mqttVersion.getOrElse(3)
    if (version == 3)
      publishToMqtt3(topics, payload)

    else
      publishToMqtt5(topics, payload)

  }

}