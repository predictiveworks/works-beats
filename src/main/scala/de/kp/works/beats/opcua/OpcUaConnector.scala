package de.kp.works.beats.opcua
/*
 * Copyright (c) 20129 - 2021 Dr. Krusche & Partner PartG. All rights reserved.
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
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.handler.OutputHandler
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.{OpcUaClientConfig, OpcUaClientConfigBuilder}
import org.eclipse.milo.opcua.sdk.client.api.identity.{AnonymousProvider, UsernameProvider}
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager
import org.eclipse.milo.opcua.sdk.client.api.{ServiceFaultListener, UaClient}
import org.eclipse.milo.opcua.stack.core.UaException
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.{DateTime, LocalizedText, StatusCode}
import org.eclipse.milo.opcua.stack.core.types.structured.{EndpointDescription, ServiceFault}
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil
import org.slf4j.LoggerFactory

import java.security.Security
import java.util.Optional
import java.util.concurrent.{CompletableFuture, Future}
import java.util.function.{BiConsumer, Consumer, Function}
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable

class OpcUaConnector() {

  private val LOGGER = LoggerFactory.getLogger(classOf[OpcUaConnector])

  import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription

  private var opcUaClient:OpcUaClient = _
  private var subscription:UaSubscription = _

  private var outputHandler:OutputHandler = _

  private val opcUaCfg: Config = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)

  private val identityProvider =
    if (opcUaCfg.hasPath("userCredentials")) {

      val userCredentials = opcUaCfg.getConfig("userCredentials")
      val userName = userCredentials.getString("userName")
      val userPass = userCredentials.getString("userPass")

      new UsernameProvider(userName, userPass)

    }
    else new AnonymousProvider

  private val subscribeOnStartup = {

    val topics = mutable.ArrayBuffer.empty[String]

    val values = opcUaCfg.getList("subscribeOnStartup")
    val size = values.size
    for (i <- 0 until size) {

      val cval = values.get(i)

      val topic = cval.asInstanceOf[String]
      topics += topic

    }

    topics.toList

  }

  private val receiverCfg:Config = opcUaCfg.getConfig("receiver")

  private val id = receiverCfg.getString("id")
  private val uri = "opc/" + id

  private val endpointUrl = receiverCfg.getString("endpointUrl")
  private val updateEndpointUrl = receiverCfg.getBoolean("updateEndpointUrl")

  private val connectTimeout = receiverCfg.getInt("connectTimeout")
  private val requestTimeout = receiverCfg.getInt("requestTimeout")

  private val keepAliveFailuresAllowed = receiverCfg.getInt("keepAliveFailuresAllowed")
  private val subscriptionSamplingInterval = receiverCfg.getDouble("subscriptionSamplingInterval")

  private val retryWait = 5000
  /*
   * Retrieve security policy that is expected to be
   * implemented at the endpoint (see endpoint filter)
   */
  private val securityPolicy = OpcUaSecurity.getSecurityPolicy
  if (securityPolicy != null && securityPolicy == SecurityPolicy.Aes256_Sha256_RsaPss)
    Security.addProvider(new BouncyCastleProvider)

  private val opcUaSecurity = new OpcUaSecurity(securityPolicy)
  /*
   * Initialize subscription listener to monitor
   * subscription status
   */
  private val subscriptionListener = new UaSubscriptionManager.SubscriptionListener() {

    override def onKeepAlive(subscription: UaSubscription, publishTime: DateTime): Unit = {
      /* Do nothing */
    }

    override def onStatusChanged(subscription: UaSubscription, status: StatusCode): Unit = {
      LOGGER.info("Status changed: " + status.toString)
    }

    override def onPublishFailure(exception: UaException): Unit = {
      LOGGER.warn("Publish failure: " + exception.getMessage)
    }

    override def onNotificationDataLost(subscription: UaSubscription): Unit = {
      LOGGER.warn("Notification data lost: " + subscription.getSubscriptionId)
    }

    override def onSubscriptionTransferFailed(subscription: UaSubscription, statusCode: StatusCode): Unit = {
      LOGGER.warn("Subscription transfer failed: " + statusCode.toString)
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
        override def accept(s: UaSubscription, e: Throwable): Unit = {
          if (e == null) {
            subscription = s
            try resubscribe()
            catch {
              case ex: Exception =>
                LOGGER.error("Unable to re-subscribe. Fail with: " + ex.getLocalizedMessage)
            }
          }
          else {
            LOGGER.error("Unable to create a subscription. Fail with: " + e.getLocalizedMessage)
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
  def start():Future[Boolean] = {

    val future = new CompletableFuture[Boolean]()
    try {

      val res = connect().get()
      if (res) {
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
              future.complete(subscriber.subscribeTopic(id, topic).get())

            } catch {
              case t:Throwable =>
                LOGGER.error("Starting OPC-UA connection failed with: " + t.getLocalizedMessage)
                future.complete(false);
              }
          }
        })
      }
      else
        future.complete(false)

    } catch {
      case t:Throwable =>
        LOGGER.error("Starting OPC-UA connection failed with: " + t.getLocalizedMessage)
        future.complete(false)
    }

    future
  }

  def connect():Future[Boolean] = {

    val future = new CompletableFuture[Boolean]()
    try {
      /*
       * STEP #1: Create OPC-UA client
       */
      val result = createClientAsync().get()
      if (!result) {
        future.complete(false)
      }
      else {
        /*
         * STEP #2: Connect to OPC-UA server
         */
        val result = connectClientAsync().get()
        if (!result) {
          future.complete(false)
        }
        else {
          /*
           * STEP #3: Add fault & subscription listener
           */
          opcUaClient.addFaultListener(new ServiceFaultListener() {
            override def onServiceFault(serviceFault: ServiceFault): Unit = {
              LOGGER.warn("[OPC-UA FAULT] " + serviceFault.toString)
            }
          })

          opcUaClient.getSubscriptionManager
            .addSubscriptionListener(subscriptionListener)

          /*
           * STEP #4: Create subscription
           */
          createSubscription()
          future.complete(true)

        }
      }

    } catch {
      case _:Throwable =>
        future.complete(false)
    }

    future
  }

  def disconnect():Future[Boolean] = {

    val future = new CompletableFuture[Boolean]()
    opcUaClient
      .disconnect()
      .thenAccept(new Consumer[OpcUaClient] {
        override def accept(c: OpcUaClient): Unit = {
          future.complete(true)
        }
      })

    future

  }

  def shutdown():Future[Boolean] = {
    disconnect()
  }

  private def createClientAsync():Future[Boolean] = {
    val future = new CompletableFuture[Boolean]()
    createClientWithRetry(future)
    future
  }

  /**
   * This method creates an OPC-UA client with a retry mechanism
   * in case an error occurred; the current implementation retries
   * after 5000 ms with a maximum of 10 retries
   */
  @tailrec
  private def createClientWithRetry(future:CompletableFuture[Boolean]):Unit = {

    try {
      opcUaClient = createClient()
      future.complete(true)

    } catch {
      case _:UaException =>
        Thread.sleep(retryWait)
        createClientWithRetry(future)

      case _:Exception =>
      future.complete(false)

    }
  }

  private def createClient():OpcUaClient = {

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
          .setKeyPair(opcUaSecurity.getClientKeyPair)
          .setCertificate(opcUaSecurity.getClientCertificate)
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
    securityPolicy == null || securityPolicy.getUri.equals(e.getSecurityPolicyUri)
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
      LOGGER.warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
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
      LOGGER.warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
      e
    }
  }

  private def connectClientAsync():Future[Boolean] = {
    val future = new CompletableFuture[Boolean]()
    connectClientWithRetry(future)
    future
  }

  private def connectClientWithRetry(future:CompletableFuture[Boolean]):Unit = {

    if (opcUaClient == null) {
      future.complete(false)

    } else {
      opcUaClient
        .connect()
        .whenComplete(new BiConsumer[UaClient, Throwable] {
          override def accept(c: UaClient, t: Throwable): Unit = {
            if (t == null) {
              future.complete(true)
            }
            else {
              try {
                Thread.sleep(retryWait)
                connectClientWithRetry(future)

              } catch {
                case _:Throwable =>
                  future.complete(false)
              }

            }
          }
        })
    }

  }

}
