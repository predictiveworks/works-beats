package de.kp.works.beats

import ch.qos.logback.classic.Logger

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

trait BeatsLogging {
  /**
   * Initialize the overall `WorksBeat` configuration
   */
  if (!BeatsConf.isInit) BeatsConf.init()

  private val loggerName = "BeatsLogger"
  private val loggerPath = BeatsConf.getLogFolder
  /*
   * Initialize the [BeatLogger]; this is the first
   * step to retrieve the [Logger]
   */
  BeatsLogger.getInstance(loggerName, loggerPath)
  val logger: Logger = BeatsLogger.getLogger

  def info(message: String): Unit = {
    logger.info(s"$message")
  }

  def warn(message: String): Unit = {
    logger.warn(s"$message")

  }

  def error(message: String): Unit = {
    logger.error(s"$message")
  }

}
