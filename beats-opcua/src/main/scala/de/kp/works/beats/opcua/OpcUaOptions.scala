package de.kp.works.beats.opcua

import com.typesafe.config.Config
import de.kp.works.beats.BeatsConf
import org.eclipse.milo.opcua.sdk.client.api.identity.{AnonymousProvider, IdentityProvider, UsernameProvider}

import scala.collection.JavaConversions.asScalaBuffer

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

object OpcUaOptions {

  private val cfg = BeatsConf.getBeatCfg(BeatsConf.OPCUA_CONF)

  def getIdentityProvider: IdentityProvider = {

      if (cfg.hasPath("userCredentials")) {

        val userCredentials = cfg.getConfig("userCredentials")
        val userName = userCredentials.getString("userName")
        val userPass = userCredentials.getString("userPass")

        new UsernameProvider(userName, userPass)

      }
      else new AnonymousProvider

  }

  def getTopics:List[String] = {
    cfg
      .getStringList("subscribeOnStartup")
      .toList
  }
}
