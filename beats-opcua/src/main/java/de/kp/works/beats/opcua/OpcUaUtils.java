package de.kp.works.beats.opcua;
/*
 * Copyright (c) 2019 - 2021 Dr. Krusche & Partner PartG. All rights reserved.
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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.Sets.newHashSet;

public class OpcUaUtils {

    public static Logger LOGGER = Logger.getLogger(OpcUaUtils.class.getName());

    public static final String APPLICATION_NAME = "OPC-UA Beat@" + getHostname();
    public static final String APPLICATION_URI = String.format("urn:%s:works:opcua", OpcUaUtils.getHostname());

    public static String getRootNodeIdOfName(String item) {
        switch(item) {
            case "Root": {
                return "i=84";
            }
            case "Objects": {
                return "i=85";
            }
            case "Types": {
                return "i=86";
            }
            case "Views": {
                return "i=87";
            }
            default:
                return item;
        }
    }

    /** HOSTNAME UTILS **/

    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    public static Set<String> getHostnames(String address) {
        return getHostnames(address, true);
    }

    public static Set<String> getHostnames(String address, boolean includeLoopback) {
        Set<String> hostnames = newHashSet();
        try {

            InetAddress inetAddress = InetAddress.getByName(address);
            if (inetAddress.isAnyLocalAddress()) {

                try {

                    List<NetworkInterface> netInterfaces =
                            Collections.list(NetworkInterface.getNetworkInterfaces());

                    for (NetworkInterface netInterface : netInterfaces) {

                        List<InetAddress> inetAddresses = Collections.list(netInterface.getInetAddresses());
                        inetAddresses.forEach(ia -> {
                            if (ia instanceof Inet4Address) {
                                if (includeLoopback || !ia.isLoopbackAddress()) {
                                    hostnames.add(ia.getHostName());
                                    hostnames.add(ia.getHostAddress());
                                    hostnames.add(ia.getCanonicalHostName());
                                }
                            }
                        });
                    }
                } catch (SocketException e) {
                    String message = "Retrieval of host name failed for address: " + address;
                    LOGGER.log(Level.WARNING, message, e);
                }

            } else {
                if (includeLoopback || !inetAddress.isLoopbackAddress()) {
                    hostnames.add(inetAddress.getHostName());
                    hostnames.add(inetAddress.getHostAddress());
                    hostnames.add(inetAddress.getCanonicalHostName());
                }
            }
        } catch (UnknownHostException e) {
            String message = "Failed to get InetAddress for address: " + address;
            LOGGER.log(Level.WARNING, message, e);
        }

        return hostnames;
    }

}
