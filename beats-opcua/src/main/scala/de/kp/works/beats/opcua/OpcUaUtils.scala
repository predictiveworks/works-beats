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

import java.net.{Inet4Address, InetAddress, NetworkInterface}
import java.util
import java.util.Collections
import scala.collection.JavaConversions.asScalaBuffer

object OpcUaUtils {

  def APPLICATION_NAME:String = "OPC-UA Beat@" + getHostname()
  def APPLICATION_URI:String = String.format("urn:%s:works:opcua", getHostname())

  def getRootNodeIdOfName(item:String):String = item match {
    case "Root" =>
      "i=84"

    case "Objects" =>
      "i=85"

    case "Types" =>
      "i=86"

    case "Views" =>
      "i=87"

    case _ =>
      item
  }

  //
  /** HOSTNAME UTILS **/

  def getHostname:String = {
    try {
      InetAddress.getLocalHost.getHostName

    } catch {
      case _:Throwable => "localhost"
    }
  }

  def getHostnames(address:String):util.HashSet[String] = {
    getHostnames(address, includeLoopback = true)
  }

  def getHostnames(address:String, includeLoopback:Boolean):util.HashSet[String] = {

    val hostnames = new util.HashSet[String]()
    try {

      val inetAddress = InetAddress.getByName(address)
      if (inetAddress.isAnyLocalAddress) {

        try {

          val netInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces)
          netInterfaces.foreach(netInterface => {

            val inetAddresses = Collections.list(netInterface.getInetAddresses)
            inetAddresses.foreach(ia => {

              if (ia.isInstanceOf[Inet4Address]) {

                if (includeLoopback || !ia.isLoopbackAddress) {
                  hostnames.add(ia.getHostName)
                  hostnames.add(ia.getHostAddress)
                  hostnames.add(ia.getCanonicalHostName)
                }

              }
            })
          })

        } catch {
          case _:Throwable => /* Do nothing */
        }

      } else {

        if (includeLoopback || !inetAddress.isLoopbackAddress) {
          hostnames.add(inetAddress.getHostName)
          hostnames.add(inetAddress.getHostAddress)
          hostnames.add(inetAddress.getCanonicalHostName)
        }
      }
    } catch {
      case _:Throwable => /* Do nothing */

    }

    hostnames

  }

}
