package de.kp.works.beats.iotdb

/**
 * Copyright (c) 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import org.apache.iotdb.session.Session

object DBClient {

  private val session: Option[Session] = buildSession

  private def buildSession:Option[Session] = {

    try {

      var builder = new Session.Builder()
      builder = builder
        .host(DBOptions.host)
        .port(DBOptions.port)
        .enableCacheLeader(DBOptions.enableCacheLeader)
        .fetchSize(DBOptions.fetchSize)
        .thriftDefaultBufferSize(DBOptions.thriftDefaultBufferSize)
        .thriftMaxFrameSize(DBOptions.thriftMaxFrameSize)

      val userCreds = DBOptions.userCreds
      if (userCreds.nonEmpty) {

        val username = userCreds.get.username
        val userpass = userCreds.get.userpass

        if (username.nonEmpty && userpass.nonEmpty)
          builder = builder.username(username).password(userpass)
      }

      Some(builder.build)

    } catch {
      case _:Throwable => /* Do nothing */ None
    }

  }

  def getSession: Option[Session] = session

  def close:Boolean = {
    try {

      if (session.isEmpty) return false

      session.get.close()
      true

    } catch {
      case _:Throwable => /* Do nothing */ false
    }

  }

  def open:Boolean = {
    try {

      if (session.isEmpty) return false

      session.get.open(DBOptions.enableRPCCompression, DBOptions.connectionTimeoutInMs)
      true

    } catch {
      case _:Throwable => /* Do nothing */ false
    }
  }

}
