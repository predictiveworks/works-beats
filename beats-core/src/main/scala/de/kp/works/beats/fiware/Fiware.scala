package de.kp.works.beats.fiware

/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import akka.http.scaladsl.HttpsConnectionContext
import com.typesafe.config.Config
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.http.HttpConnect
import de.kp.works.beats.ssl.SslOptions

abstract class Fiware extends HttpConnect {

  protected val fiwareCfg: Config = BeatsConf.getBeatCfg(BeatsConf.FIWARE_CONF)

  protected val brokerCfg: Config = fiwareCfg.getConfig("broker")
  protected val securityCfg: Config = brokerCfg.getConfig("security")
  /**
   * Make sure that the HTTP connection is secured,
   * if the respective configuration exists
   */
  private val httpsContext = getHttpsContext
  if (httpsContext.nonEmpty)
    setHttpsContext(httpsContext.get)

  private def getHttpsContext: Option[HttpsConnectionContext] = {

    val ssl = securityCfg.getBoolean("ssl")
    if (!ssl) return None
    /*
     * The request protocol in the broker url must be
     * specified as 'https://'. In this case, an SSL
     * security context must be specified
     */
    val context = SslOptions.buildClientConnectionContext(securityCfg)
    Some(context)

  }

  def getBrokerUrl:String = {
    brokerCfg.getString("brokerUrl")
  }

}
