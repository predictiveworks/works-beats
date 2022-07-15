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

case class UserCreds(username:String, userpass:String)

object DBOptions {

  def enableCacheLeader:Boolean = ???

  def fetchSize:Int = ???
  /*
   * The current implementation supports a single node
   */
  def host:String = ???

  def port:Int = ???

  def thriftDefaultBufferSize:Int = ???

  def thriftMaxFrameSize:Int = ???

  def userCreds:Option[UserCreds] = ???

}
