package de.kp.works.beats.opcua
/*
 * Copyright (c) 2020 Dr. Krusche & Partner PartG. All rights reserved.
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

import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem

import scala.collection.mutable
import scala.collection.JavaConversions._

case class MonitoredItem(item:UaMonitoredItem)

object OpcUaRegistry {

  private val topics = mutable.HashSet.empty[OpcUaTopic]

  private val topicClients = mutable.HashMap.empty[String, mutable.HashSet[String]]
  private val topicMonitoredItems = mutable.HashMap.empty[String, mutable.ArrayBuffer[MonitoredItem]]

  def addClient(clientId: String, topic: OpcUaTopic): (Int, Boolean) = {

    val clients = topicClients.getOrElse(topic.topicName, mutable.HashSet.empty[String])
    val added = clients.add(clientId)

    topicClients += topic.topicName -> clients
    (clients.size, added)
  }

  def delClient(clientId: String, topic: OpcUaTopic): Int = {

    val clients = topicClients.getOrElse(topic.topicName, mutable.HashSet.empty[String])
    if (clients.isEmpty) return 0

    clients.remove(clientId)
    if (clients.isEmpty) {
      topicClients.remove(topic.topicName)
      return 0
    }

    topicClients += topic.topicName -> clients
    clients.size
  }

  def addMonitoredItem(item: MonitoredItem, topic: OpcUaTopic):Unit = {
    topics.add(topic)

    val items = topicMonitoredItems.getOrElse(topic.topicName, mutable.ArrayBuffer.empty[MonitoredItem])
    items += item

    topicMonitoredItems += topic.topicName -> items
  }

  def delMonitoredItems(topic: OpcUaTopic):Unit = {
    topicMonitoredItems.remove(topic.topicName)
  }

  def getTopics: List[OpcUaTopic] = {
    topics.toList
  }

  def delTopic(topic: OpcUaTopic) : Unit = {
    topics.remove(topic)
    topicMonitoredItems.remove(topic.topicName)
  }

}
