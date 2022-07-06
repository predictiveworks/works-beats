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
import de.kp.works.beats.{BeatsConf, BeatsLogging}
import de.kp.works.beats.opcua.security.SecurityUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.{OpcUaClientConfig, OpcUaClientConfigBuilder}
import org.eclipse.milo.opcua.sdk.client.api.identity.{AnonymousProvider, IdentityProvider, UsernameProvider}
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil

import java.security.Security
import java.util.{List => JList}
import java.util.Optional
import java.util.function.Function
import scala.collection.JavaConversions.asScalaBuffer

object OpcUaOptions extends BeatsLogging {

  private val cfg = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)
  /**
   * Retrieve the provided receiver configuration
   */
  private val receiverCfg:Config = cfg.getConfig("receiver")

  private val id = receiverCfg.getString("id")
  private val uri = "opc/" + id

  private val endpointUrl = receiverCfg.getString("endpointUrl")
  private val updateEndpointUrl = receiverCfg.getBoolean("updateEndpointUrl")

  private val connectTimeout = receiverCfg.getInt("connectTimeout")
  private val requestTimeout = receiverCfg.getInt("requestTimeout")

  private val keepAliveFailuresAllowed = receiverCfg.getInt("keepAliveFailuresAllowed")
  private val subscriptionSamplingInterval = receiverCfg.getDouble("subscriptionSamplingInterval")

  /**
   * Retrieve security policy that is expected to be
   * implemented at the endpoint (see endpoint filter)
   */
  private val securityPolicy = SecurityUtil.getSecurityPolicy
  if (securityPolicy.nonEmpty && securityPolicy.get == SecurityPolicy.Aes256_Sha256_RsaPss) {

    Security.addProvider(new BouncyCastleProvider)
    SecurityUtil.setSecurityPolicy(securityPolicy.get)

  }

  def buildClient: OpcUaClient = {

    val selectEndpoint = new Function[JList[EndpointDescription], Optional[EndpointDescription]] {

      override def apply(endpoints: JList[EndpointDescription]): Optional[EndpointDescription] = {
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
          .setIdentityProvider(getIdentityProvider)
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
  def endpointFilter(e:EndpointDescription):Boolean = {
    securityPolicy.isEmpty || securityPolicy.get.getUri.equals(e.getSecurityPolicyUri)
  }

  def getId:String = id

  def getIdentityProvider: IdentityProvider = {

      if (cfg.hasPath("userCredentials")) {

        val userCredentials = cfg.getConfig("userCredentials")
        val userName = userCredentials.getString("userName")
        val userPass = userCredentials.getString("userPass")

        new UsernameProvider(userName, userPass)

      }
      else new AnonymousProvider

  }

  def getSamplingInterval: Double =
    subscriptionSamplingInterval

  def getTopics:List[String] = {
    cfg
      .getStringList("subscribeOnStartup")
      .toList
  }

  def getUri: String = uri

  private def endpointUpdater(e:EndpointDescription ):EndpointDescription = {
    /*
     * Indicator to determine whether the endpoint
     * description is updated with the provided
     * endpoint URL
     */
    if (!updateEndpointUrl) return e
    /*
     * "opc.tcp://desktop-9o6hthf:4890"; // WinCC UA NUC
     * "opc.tcp://desktop-pc4fa6r:4890"; // WinCC UA Laptop
     * "opc.tcp://centos1.predictiveworks.local:4840"; // WinCC OA
     * "opc.tcp://ubuntu1:62541/discovery" // Ignition
     */
    val endpointParts = endpointUrl.split("://")
    if (endpointParts.length != 2) {
      warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
      return e
    }
    /*
     * Extract hostname and (optional) port
     * from the second part of the endpoint
     * url
     */
    val location = endpointParts(1).split(":")
    if (location.length == 1) {

      val hostname = location.head
      EndpointUtil.updateUrl(e, hostname)
    }
    else if (location.length == 2) {
      val hostname = location.head
      val port = location.last.toInt

      EndpointUtil.updateUrl(e, hostname, port)
    }
    else {
      warn("Provided endpoint url :" + endpointUrl + " cannot be split.")
      e
    }
  }

}
