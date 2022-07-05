package de.kp.works.beats.opcua

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.typesafe.config.Config
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.opcua.security.SecurityUtil
import de.kp.works.beats.{BeatsConf, BeatsLogging}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.{OpcUaClientConfig, OpcUaClientConfigBuilder}
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.{UaSubscription, UaSubscriptionManager}
import org.eclipse.milo.opcua.sdk.client.api.{ServiceFaultListener, UaClient}
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.{DateTime, LocalizedText, StatusCode}
import org.eclipse.milo.opcua.stack.core.types.structured.{EndpointDescription, ServiceFault}
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil

import java.security.Security
import java.util.Optional
import java.util.function.{BiConsumer, Consumer, Function}
import scala.collection.JavaConversions._

class OpcUaConnector() extends BeatsLogging {

  private var opcUaClient:OpcUaClient = _
  private var subscription:UaSubscription = _

  private var outputHandler:OutputHandler = _

  private val opcUaCfg: Config = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)

  private val identityProvider = OpcUaOptions.getIdentityProvider
  private val subscribeOnStartup = OpcUaOptions.getTopics

  private val receiverCfg:Config = opcUaCfg.getConfig("receiver")

  private val id = receiverCfg.getString("id")
  private val uri = "opc/" + id

  private val endpointUrl = receiverCfg.getString("endpointUrl")
  private val updateEndpointUrl = receiverCfg.getBoolean("updateEndpointUrl")

  private val connectTimeout = receiverCfg.getInt("connectTimeout")
  private val requestTimeout = receiverCfg.getInt("requestTimeout")

  private val keepAliveFailuresAllowed = receiverCfg.getInt("keepAliveFailuresAllowed")
  private val subscriptionSamplingInterval = receiverCfg.getDouble("subscriptionSamplingInterval")

  private val retryWait  = 5000
  private val maxRetries = 10
  /*
   * Retrieve security policy that is expected to be
   * implemented at the endpoint (see endpoint filter)
   */
  private val securityPolicy = SecurityUtil.getSecurityPolicy
  if (securityPolicy.nonEmpty && securityPolicy.get == SecurityPolicy.Aes256_Sha256_RsaPss) {

    Security.addProvider(new BouncyCastleProvider)
    SecurityUtil.setSecurityPolicy(securityPolicy.get)

  }
  /*
   * Initialize subscription listener to determine
   * whether a subscription transfer failed, and
   * in this case, re-create the subscription
   */
  private val subscriptionListener = new UaSubscriptionManager.SubscriptionListener() {

    override def onKeepAlive(subscription: UaSubscription, publishTime: DateTime): Unit = {
      /* Do nothing */
    }

    override def onStatusChanged(subscription: UaSubscription, status: StatusCode): Unit = {
      info("Status changed: " + status.toString)
    }

    override def onPublishFailure(exception: UaException): Unit = {
      warn("Publish failure: " + exception.getMessage)
    }

    override def onNotificationDataLost(subscription: UaSubscription): Unit = {
      warn("Notification data lost: " + subscription.getSubscriptionId)
    }

    override def onSubscriptionTransferFailed(subscription: UaSubscription, statusCode: StatusCode): Unit = {
      warn("Subscription transfer failed: " + statusCode.toString)
      /*
       * Re-create subscription
       */
      createSubscription()
    }
  }

  private def createSubscription(): Unit = {

    opcUaClient
      .getSubscriptionManager
      .createSubscription(subscriptionSamplingInterval)
      .whenCompleteAsync(new BiConsumer[UaSubscription, Throwable] {
        override def accept(s: UaSubscription, t: Throwable): Unit = {
          if (t == null) {
            subscription = s
            try resubscribe()
            catch {
              case ex: Throwable =>
                error(s"Re-subscription failed: ${ex.getLocalizedMessage}")
            }
          }
          else {
            error(s"Create a subscription failed: ${t.getLocalizedMessage}")
          }
        }
      })

  }

  private def resubscribe():Unit = {

    val topics = OpcUaRegistry.getTopics
    if (topics.nonEmpty) {
      /*
       * Delete topics from registry
       */
      topics.foreach(topic => OpcUaRegistry.delTopic(topic))

      val subscriber = new OpcUaSubscriber(opcUaClient, subscription, outputHandler)
      subscriber.subscribeTopics(topics)

    }
  }
  /**
   * This method is used to retrieve data from the
   * UA server and send to output channels
   */
  def setOutputHandler(handler:OutputHandler):OpcUaConnector = {
    outputHandler = handler
    this
  }
  /**
   * This is the main method to connect to the OPC-UA
   * server and subscribe.
   */
  def start():Boolean = {

    var success = false
    try {

      if (connect()) {
        subscribeOnStartup.foreach(pattern => {
          /*
           * node/ns=2;s=ExampleDP_Float.ExampleDP_Arg1
           * node/ns=2;s=ExampleDP_Text.ExampleDP_Text1
           * path/Objects/Test/+/+
           */
          val topic = OpcUaTransform.parse(uri + "/" + pattern)
          if (topic.isValid) {

            val subscriber = new OpcUaSubscriber(opcUaClient, subscription, outputHandler)
            try {
              /*
               * This subscriber method registers the provided
               * topic in the `OpcUaRegistry`
               */
              success = subscriber.subscribeTopic(id, topic)

            } catch {
              case t:Throwable =>
                error(s"Starting OPC-UA connection failed: ${t.getLocalizedMessage}")
              }
          }
        })
      }

    } catch {
      case t:Throwable =>
        error(s"Starting OPC-UA connection failed: ${t.getLocalizedMessage}")
    }

    success

  }
  /**
   * This method create an OPC-UA client and
   * connects the client to the OPC-UA server.
   *
   * Fault & subscription listener are assigned
   * to the client and finally the configured
   * subscriptions are set
   */
  def connect():Boolean = {

    try {
      /*
       * STEP #1: Create OPC-UA client
       */
      if (!createClientWithRetry) return false
      /*
       * STEP #2: Connect to OPC-UA server
       */
      if (!connectClientWithRetry) return false
      /*
       * STEP #3: Add fault & subscription listener
       */
      opcUaClient.addFaultListener(new ServiceFaultListener() {
        override def onServiceFault(serviceFault: ServiceFault): Unit = {
          warn(s"Fault listener returned: ${serviceFault.toString}")
        }
      })

      opcUaClient.getSubscriptionManager
        .addSubscriptionListener(subscriptionListener)

      /*
       * STEP #4: Create subscription
       */
      createSubscription()
      true

    } catch {
      case t:Throwable =>
        val message = s"Connecting to OPC-UA server failed: ${t.getLocalizedMessage}"
        error(message)

        false
    }

  }

  def disconnect():Boolean = {

    var success = false
    opcUaClient
      .disconnect()
      .thenAccept(new Consumer[OpcUaClient] {
        override def accept(c: OpcUaClient): Unit = {
          success = true
        }
      }).get

    success

  }

  def shutdown():Boolean = disconnect()
  /**
   * This method creates an OPC-UA client with a retry mechanism
   * in case an error occurred; the current implementation retries
   * after 5000 ms with a maximum of 10 retries
   */
  private def createClientWithRetry:Boolean = {

    var success = false

    var retry = true
    var numRetry = 0

    while (retry) {

      try {
        opcUaClient = createClient()

        retry   = false
        success = true

      } catch {
        case _:Throwable =>
          numRetry += 1
          if (numRetry < maxRetries)
            Thread.sleep(retryWait)

          else {
            retry   = false
            success = false
          }

      }

    }

    success

  }

  private def createClient(): OpcUaClient = {

    val selectEndpoint = new Function[java.util.List[EndpointDescription], Optional[EndpointDescription]] {
      override def apply(endpoints: java.util.List[EndpointDescription]): Optional[EndpointDescription] = {
        Optional.of[EndpointDescription](
          endpoints
            .filter(e => endpointFilter(e))
            .map(e => endpointUpdater(e))
            .head)
      }
    }

    val buildConfig = new Function[OpcUaClientConfigBuilder, OpcUaClientConfig] {
      override def apply(configBuilder: OpcUaClientConfigBuilder): OpcUaClientConfig = {

        configBuilder
          .setApplicationName(LocalizedText.english(OpcUaUtils.APPLICATION_NAME))
          .setApplicationUri(OpcUaUtils.APPLICATION_URI)
          .setIdentityProvider(identityProvider)
          .setKeyPair(SecurityUtil.getClientKeyPair.get)
          .setCertificate(SecurityUtil.getClientCertificate.get)
          .setConnectTimeout(UInteger.valueOf(connectTimeout))
          .setRequestTimeout(UInteger.valueOf(requestTimeout))
          .setKeepAliveFailuresAllowed(UInteger.valueOf(keepAliveFailuresAllowed))
          .build()

      }
    }

    OpcUaClient.create(endpointUrl, selectEndpoint, buildConfig)

  }
  /**
   * Method restricts endpoints to those that either
   * have no security policy implemented or a policy
   * the refers to configured policy.
   */
  private def endpointFilter(e:EndpointDescription):Boolean = {
    securityPolicy.isEmpty || securityPolicy.get.getUri.equals(e.getSecurityPolicyUri)
  }

  private def endpointUpdater(e:EndpointDescription ):EndpointDescription = {

    if (!updateEndpointUrl) return e
    /*
     * "opc.tcp://desktop-9o6hthf:4890"; // WinCC UA NUC
     * "opc.tcp://desktop-pc4fa6r:4890"; // WinCC UA Laptop
     * "opc.tcp://centos1.predictiveworks.local:4840"; // WinCC OA
     * "opc.tcp://ubuntu1:62541/discovery" // Ignition
     */
    val parts1 = endpointUrl.split("://")
    if (parts1.length != 2) {
      warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
      return e
    }
    val parts2 = parts1(1).split(":")
    if (parts2.length == 1) {
      EndpointUtil.updateUrl(e, parts2(0))
    }
    else if (parts2.length == 2) {
      EndpointUtil.updateUrl(e, parts2(0), Integer.parseInt(parts2(1)))
    }
    else {
      warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
      e
    }
  }

  private def connectClientWithRetry:Boolean = {

    if (opcUaClient == null) return false

    var success = false

    var retry = true
    var numRetry = 0

    while (retry) {

      try {
        opcUaClient
          .connect
          .whenComplete(new BiConsumer[UaClient, Throwable] {
            override def accept(c: UaClient, t: Throwable): Unit = {
              if (t != null) throw t
            }
          }).get

        retry   = false
        success = true

      } catch {
        case _:Throwable =>
          numRetry += 1
          if (numRetry < maxRetries)
            Thread.sleep(retryWait)

          else {
            retry   = false
            success = false
          }
      }

    }

    success

  }
}
