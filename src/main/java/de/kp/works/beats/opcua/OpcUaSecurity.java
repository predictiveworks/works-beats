package de.kp.works.beats.opcua;
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

import com.typesafe.config.Config;
import de.kp.works.beats.BeatsConf;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

public class OpcUaSecurity {
    /**
     * The security policy determines how to create the connection
     * with the UA server
     */
    private final SecurityPolicy securityPolicy;

    private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private X509Certificate clientCertificate;
    private KeyPair clientKeyPair;

    private final String password;

    private static Config opcUaCfg = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF());

    public OpcUaSecurity(SecurityPolicy securityPolicy) {

        this.securityPolicy = securityPolicy;
        this.password = opcUaCfg
            .getConfig("userCredentials")
            .getString("userPass");

        try {
            init();

        } catch (Exception e) {
            /* Do nothing */
        }
    }

    public static SecurityPolicy getSecurityPolicy() {

        if (opcUaCfg == null)
            opcUaCfg = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF());

        String securityPolicy = opcUaCfg.getString("securityPolicy");
        if (securityPolicy.isEmpty()) return null;

        switch (securityPolicy) {
            case "None": {
                return SecurityPolicy.None;
            }
            case "Basic128Rsa15": {
                return SecurityPolicy.Basic128Rsa15;
            }
            case "Basic256": {
                return SecurityPolicy.Basic256;
            }
            case "Basic256Sha256": {
                return SecurityPolicy.Basic256Sha256;
            }
            case "Aes128_Sha256_RsaOaep": {
                return SecurityPolicy.Aes128_Sha256_RsaOaep;
            }
            case "Aes256_Sha256_RsaPss": {
                return SecurityPolicy.Aes256_Sha256_RsaPss;
            }
            default:
                return null;
        }
    }

    private void init() throws Exception {

        String securityDir = opcUaCfg.getString("securityDir");
        if (securityDir.isEmpty())
            throw new Exception("Configuration does not contain the path to security related information.");

        Path securityPath = Paths.get(securityDir);
        Files.createDirectories(securityPath);

        if (!Files.exists(securityPath)) {
            throw new Exception("Unable to create security directory: " + securityDir);
        }

        load(securityPath);

    }
    /**
     * Extract client certificate and key pair from the
     * configured keystore; if the keystore does not exist,
     * it is created, a self-signed certificate and the key
     * pair.
     */
    private void load(Path baseDir) throws Exception {

        Config keyStoreCfg = opcUaCfg.getConfig("keyStore");

        KeyStore keyStore;
        Path serverKeyStore = baseDir.resolve(keyStoreCfg.getString("fileName"));

        String CERT_ALIAS        = keyStoreCfg.getString("certAlias");
        String PRIVATE_KEY_ALIAS = keyStoreCfg.getString("privateKeyAlias");

        String KEYSTORE_TYPE = keyStoreCfg.getString("keyStoreType");

        if (!Files.exists(serverKeyStore)) {
            /*
             * Create a new key pair with a key size that matches
             * the provided security policy
             */
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(getKeySize());
            /*
             * Create a new certificate
             */
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            X509Certificate certificate = buildCertificate(keyPair);
            /*
             * Create keystore and assign key pair and certificate
             */
            keyStore = createKeystore(KEYSTORE_TYPE, password);
            /*
             * Add client certificate to key store, the client certificate alias is
             * 'certificate' (see IBM Watson IoT platform)
             */
            keyStore.setCertificateEntry(CERT_ALIAS, certificate);
            /*
             * Add private key to keystore and distinguish between use case with and without
             * password
             */
            keyStore.setKeyEntry(PRIVATE_KEY_ALIAS, keyPair.getPrivate(), password.toCharArray(), new X509Certificate[]{certificate});
            try (OutputStream out = Files.newOutputStream(serverKeyStore)) {
                keyStore.store(out, password.toCharArray());
            }

        }
        else {
            keyStore = loadKeystore(serverKeyStore, KEYSTORE_TYPE, password);

        }

        Key privateKey = keyStore.getKey(PRIVATE_KEY_ALIAS, password.toCharArray());
        if (privateKey instanceof PrivateKey) {

            clientCertificate = (X509Certificate) keyStore.getCertificate(CERT_ALIAS);

            PublicKey serverPublicKey = clientCertificate.getPublicKey();
            clientKeyPair = new KeyPair(serverPublicKey, (PrivateKey) privateKey);

        }

    }

    private int getKeySize() {
        if (securityPolicy.getUri().equals(SecurityPolicy.Basic128Rsa15.getUri())) {
            return 1024;
        }
        else
            return 2048;
    }

    private String getSignatureAlgorithm() {
        /*
         * Define the algorithm to use for certificate signatures.
         *
         * The OPC UA specification defines that the algorithm should be (at least)
         * "SHA1WithRSA" for application instance certificates used for security
         * policies Basic128Rsa15 and Basic256. For Basic256Sha256 it should be
         * "SHA256WithRSA".
         *
         */
        String uri = securityPolicy.getUri();
        if (uri.equals(SecurityPolicy.None.getUri())) {
            return "SHA1WithRSA";
        }
        else if (uri.equals(SecurityPolicy.Basic128Rsa15.getUri())) {
            return "SHA1WithRSA";
        }
        else if (uri.equals(SecurityPolicy.Basic256.getUri())) {
            return "SHA1WithRSA";
        }
        else {
            return "SHA256WithRSA";
        }
    }
    /**
     * A helper method to build a self-signed certificate
     * from the provided certificate meta information.
     */
    private X509Certificate buildCertificate(KeyPair keyPair) throws Exception {

       String signatureAlgorithm = getSignatureAlgorithm();

       Config certInfo = opcUaCfg.getConfig("certificateInfo");
       SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
           .setSignatureAlgorithm(signatureAlgorithm)
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
           .addIpAddress(certInfo.getString("ipAddress"));

        /*
         * Retrieve  as many hostnames and IP addresses
         * to be listed in the certificate.
         */
        for (String hostname : OpcUaUtils.getHostnames("0.0.0.0")) {
            if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                builder.addIpAddress(hostname);

            } else {
                builder.addDnsName(hostname);
            }
        }

        return builder.build();

    }
    private KeyStore loadKeystore(Path keystoreFile, String keystoreType, String keystorePassword)
            throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {

        KeyStore keystore = null;
        if (keystoreFile != null) {
            keystore = KeyStore.getInstance(keystoreType);
            char[] passwordArr = (keystorePassword == null) ? null : keystorePassword.toCharArray();
            try (InputStream is = Files.newInputStream(keystoreFile)) {
                keystore.load(is, passwordArr);
            }
        }
        return keystore;
    }

    public static KeyStore createKeystore(String keystoreType, String keystorePassword)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

        KeyStore keystore = KeyStore.getInstance(keystoreType);
        char[] passwordArr = (keystorePassword == null) ? null : keystorePassword.toCharArray();

        keystore.load(null, passwordArr);
        return keystore;

    }

    X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    KeyPair getClientKeyPair() {
        return clientKeyPair;
    }
}