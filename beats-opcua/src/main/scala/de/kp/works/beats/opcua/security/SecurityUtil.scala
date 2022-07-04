package de.kp.works.beats.opcua.security

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

import de.kp.works.beats.BeatsConf
import de.kp.works.beats.opcua.OpcUaUtils
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder

import java.nio.file.{Files, Path, Paths}
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, KeyStore, PrivateKey}
import java.util.regex.Pattern
import scala.collection.JavaConversions._

object SecurityUtil {

  private val IP_ADDR_PATTERN:Pattern = Pattern.compile(
    "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")

  private val opcUaCfg = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)
  private val password = opcUaCfg
    .getConfig("userCredentials")
    .getString("userPass")

  /**
   * The security policy determines how to create the connection
   * with the UA server
   */
  private var securityPolicy:Option[SecurityPolicy] = None

  private var clientCertificate:Option[X509Certificate] = None
  private var clientKeyPair:Option[KeyPair] = None

  def initialize():Unit = {

    val securityDir = opcUaCfg.getString("securityDir")
    if (securityDir.isEmpty)
      throw new Exception("Configuration does not contain the path to security related information.")

    val securityPath = Paths.get(securityDir)
    Files.createDirectories(securityPath)

    if (!Files.exists(securityPath))
      throw new Exception("Unable to create security directory: " + securityDir)

    load(securityPath)

  }

  def getClientCertificate:Option[X509Certificate] = {
    clientCertificate
  }

  def getClientKeyPair:Option[KeyPair] = {
    clientKeyPair
  }

  def getSecurityPolicy:Option[SecurityPolicy] = {

    val securityPolicy = opcUaCfg.getString("securityPolicy")
    if (securityPolicy.isEmpty) return null

    securityPolicy match {
      case "None" =>
        Some(SecurityPolicy.None)

      case "Basic128Rsa15" =>
        Some(SecurityPolicy.Basic128Rsa15)

      case "Basic256" =>
        Some(SecurityPolicy.Basic256)

      case "Basic256Sha256" =>
        Some(SecurityPolicy.Basic256Sha256)

      case "Aes128_Sha256_RsaOaep" =>
        Some(SecurityPolicy.Aes128_Sha256_RsaOaep)

      case "Aes256_Sha256_RsaPss" =>
        Some(SecurityPolicy.Aes256_Sha256_RsaPss)

      case _ =>
        None
    }
  }

  def setSecurityPolicy(securityPolicy:SecurityPolicy):Unit = {
    this.securityPolicy = Some(securityPolicy)
  }

  private def getKeySize:Int = {

    if (securityPolicy.isEmpty) 2048
    else {
      if (securityPolicy.get.getUri == SecurityPolicy.Basic128Rsa15.getUri) 1024
      else 2048

    }
  }

  /**
   * Extract client certificate and key pair from the
   * configured keystore; if the keystore does not exist,
   * it is created, a self-signed certificate and the key
   * pair.
   */
  def load(baseDir:Path):Unit = {

    val keyStoreCfg = opcUaCfg.getConfig("keyStore")

    val serverKeyStore = baseDir.resolve(keyStoreCfg.getString("fileName"))

    val CERT_ALIAS        = keyStoreCfg.getString("certAlias")
    val PRIVATE_KEY_ALIAS = keyStoreCfg.getString("privateKeyAlias")

    val KEYSTORE_TYPE = keyStoreCfg.getString("keyStoreType")

    val keystore = if (!Files.exists(serverKeyStore)) {
      /*
       * Create a new key pair with a key size that matches
       * the provided security policy
       */
      val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
      keyPairGenerator.initialize(getKeySize)
      /*
       * Create a new certificate
       */
      val keyPair = keyPairGenerator.generateKeyPair()
      val certificate = buildCertificate(keyPair)
      /*
       * Create keystore and assign key pair and certificate
       */
      val ks = createKeystore(KEYSTORE_TYPE, password)
      if (ks.nonEmpty && certificate.nonEmpty) {
        /*
         * Add client certificate to key store, the client certificate alias is
         * 'certificate' (see IBM Watson IoT platform)
         */
        ks.get.setCertificateEntry(CERT_ALIAS, certificate.get)
        /*
         * Add private key to keystore and distinguish between use case with and without
         * password
         */
        ks.get.setKeyEntry(
          PRIVATE_KEY_ALIAS, keyPair.getPrivate, password.toCharArray, List(certificate.get).toArray)

        val out = Files.newOutputStream(serverKeyStore)
        ks.get.store(out, password.toCharArray)
      }

      ks

    }
    else {
      loadKeystore(serverKeyStore, KEYSTORE_TYPE, password)

    }

    if (keystore.nonEmpty) {

      val privateKey = keystore.get.getKey(PRIVATE_KEY_ALIAS, password.toCharArray)
      privateKey match {
        case key: PrivateKey =>

          clientCertificate = Some(keystore.get.getCertificate(CERT_ALIAS).asInstanceOf[X509Certificate])

          val serverPublicKey = clientCertificate.get.getPublicKey
          clientKeyPair = Some(new KeyPair(serverPublicKey, key))

        case _ =>
      }

    }

  }

  /**
   * A helper method to build a self-signed certificate
   * from the provided certificate meta information.
   */
  private def buildCertificate(keyPair: KeyPair):Option[X509Certificate] = {

    val certInfo = opcUaCfg.getConfig("certificateInfo")

    val signatureAlgorithm = getSignatureAlgorithm
    if (signatureAlgorithm.nonEmpty) {

      val builder = new SelfSignedCertificateBuilder(keyPair)
        .setSignatureAlgorithm(signatureAlgorithm.get)
        /*
         * Set certificate information from provided
         * configuration and application specification
         */
        .setCommonName(OpcUaUtils.APPLICATION_NAME)
        .setApplicationUri(OpcUaUtils.APPLICATION_URI)
        /*
         * Assign certificate info to certificate builder
         */
        .setOrganization(certInfo.getString("organization"))
        .setOrganizationalUnit(certInfo.getString("organizationalUnit"))
        .setLocalityName(certInfo.getString("localityName"))
        .setCountryCode(certInfo.getString("countryCode"))
        /*
         * Append DNS name and IP address
         */
        .addDnsName(certInfo.getString("dnsName"))
        .addIpAddress(certInfo.getString("ipAddress"))

      /*
       * Retrieve  as many hostnames and IP addresses
       * to be listed in the certificate.
       */
      OpcUaUtils.getHostnames("0.0.0.0").foreach(hostname => {
        if (IP_ADDR_PATTERN.matcher(hostname).matches)
          builder.addIpAddress(hostname)

        else
          builder.addDnsName(hostname)

      })

      Some(builder.build)

    } else None

  }

  private def getSignatureAlgorithm:Option[String] = {
    /*
     * Define the algorithm to use for certificate signatures.
     *
     * The OPC UA specification defines that the algorithm should be (at least)
     * "SHA1WithRSA" for application instance certificates used for security
     * policies Basic128Rsa15 and Basic256. For Basic256Sha256 it should be
     * "SHA256WithRSA".
     *
     */
    if (securityPolicy.isEmpty) None

    val uri = securityPolicy.get.getUri
    val algorithm =
      if (uri == SecurityPolicy.None.getUri)
        "SHA1WithRSA"
      else if (uri == SecurityPolicy.Basic128Rsa15.getUri)
        "SHA1WithRSA"
      else if (uri == SecurityPolicy.Basic256.getUri)
        "SHA1WithRSA"
      else
        "SHA256WithRSA"

    Some(algorithm)

  }

  def createKeystore(keystoreType:String,keystorePassword:String):Option[KeyStore] = {

    val keystore = KeyStore.getInstance(keystoreType)

    val passwordArr =
      if (keystorePassword == null) null
      else
        keystorePassword.toCharArray

    keystore.load(null, passwordArr)

    Some(keystore)

  }

  def loadKeystore(keystoreFile:Path, keystoreType: String,keystorePassword: String):Option[KeyStore] = {

    if (keystoreFile != null) {

      val keystore = KeyStore.getInstance(keystoreType)
      val passwordArr =
        if (keystorePassword == null) null
        else
          keystorePassword.toCharArray

      try {

        val is = Files.newInputStream(keystoreFile)
        keystore.load(is, passwordArr)

        is.close()
        Some(keystore)

      } catch {
        case _:Throwable => None
      }

    } else None

  }

}
