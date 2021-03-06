package de.kp.works.beats.mitre
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

import de.kp.works.beats.BeatsConf

object MitreOptions {

  private val DEFAULT_CONFIDENCE = 75

  private val cfg = BeatsConf.getBeatCfg(BeatsConf.MITRE_CONF)
  private val receiverCfg = cfg.getConfig("receiver")
  /*
   * The confidence is a number between [0, 100],
   * where 0 = unknown, and 100 = fully trusted
   */
  def getConfidenceLevel:Int = {
    if (receiverCfg.hasPath("confidence"))
      receiverCfg.getInt("confidence")

    else DEFAULT_CONFIDENCE

  }
}
