package de.kp.works.beats.zeek
/*
 * Copyright (c) 2021 Dr. Krusche & Partner PartG. All rights reserved.
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
import akka.actor.{ActorRef, Props}
import de.kp.works.beats.zeek.actor.ZeekActor
import de.kp.works.beats.{BeatsConf, FileMonitor}

import java.nio.file.Path

class ZeekMonitor(folder: String, eventHandler: ZeekHandler) extends FileMonitor(BeatsConf.ZEEK_CONF, folder) {
  /**
   * A helper method to build a file listener actor
   */
  override protected def buildFileActor(path:Path):ActorRef = {
    val actorName = "File-Actor-" + java.util.UUID.randomUUID.toString
    system.actorOf(Props(new ZeekActor(path, eventHandler)), actorName)
  }
}