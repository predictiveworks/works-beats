package de.kp.works.beats

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

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.rolling.{RollingFileAppender, SizeAndTimeBasedRollingPolicy}
import ch.qos.logback.core.util.FileSize
import org.slf4j.LoggerFactory

object BeatsLogger {

  private var instance:Option[BeatsLogger] = None
  private var logger:Option[Logger] = None

  def getInstance(name:String,path:String):BeatsLogger = {
    if (instance.isEmpty) {
      val beatLogger = new BeatsLogger(name, path)
      logger = Some(beatLogger.buildLogger)

      instance = Some(beatLogger)
    }

    instance.get
  }

  def getLogger:Logger = logger.get

}

class BeatsLogger(name:String, path:String) {

  /**
   * This method build the Logback logger including
   * a rolling file appender programmatically
   */
  def buildLogger:Logger = {

    val logCtx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    /*
     * Build encoder and use default pattern
     */
    val logEncoder = new PatternLayoutEncoder()
    logEncoder.setContext(logCtx)

    logEncoder.setPattern(" %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n")
    logEncoder.start()
    /*
     * Build rolling file appender
     */
    val logFileAppender = new RollingFileAppender[ILoggingEvent]()
    logFileAppender.setContext(logCtx)

    logFileAppender.setName("logFileAppender")
    logFileAppender.setEncoder(logEncoder.asInstanceOf[Encoder[ILoggingEvent]])

    logFileAppender.setAppend(true)
    logFileAppender.setFile(s"$path/beat.log")
    /*
     * Set time- and size-based rolling policy
     */
    val logFilePolicy = new SizeAndTimeBasedRollingPolicy[ILoggingEvent]()
    logFilePolicy.setContext(logCtx)

    logFilePolicy.setParent(logFileAppender)
    logFilePolicy.setFileNamePattern(s"$path/beat.%d{yyyy-MM-dd}.%i.log")

    logFilePolicy.setMaxFileSize(FileSize.valueOf("100mb"))
    logFilePolicy.setTotalSizeCap(FileSize.valueOf("3GB"))

    logFilePolicy.setMaxHistory(30)
    logFilePolicy.start()

    logFileAppender.setRollingPolicy(logFilePolicy)
    logFileAppender.start()
    /*
     * Finally builder logger
     */
    val logger = logCtx.getLogger(name)
    logger.setAdditive(false)

    logger.setLevel(Level.INFO)
    logger.addAppender(logFileAppender)

    logFileAppender.start()
    logger

  }
}
