package de.kp.works.beats.mitre.osquery
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

import com.google.gson.JsonObject
import de.kp.works.beats.mitre.MitreDomains.MitreDomain
import de.kp.works.beats.mitre.osquery.NodeTypes.NodeType

import scala.collection.JavaConversions.asScalaSet

trait Edge {

  var src:Option[String] = None
  var dst:Option[String] = None

  var name:Option[String] = None

}

trait Node {

  var `type`:String
  var id:Option[String] = None

}

object NodeTypes extends Enumeration {
  type NodeType = Value

  val FULL:NodeType  = Value(0, "FULL")
  val LIGHT:NodeType = Value(1, "LIGHT")

}

class GEdge extends Edge {

  def setDst(dst:Option[String]):Unit = this.dst = dst

  def setName(name:Option[String]):Unit = this.name = name

  def setSrc(src:Option[String]): Unit = this.src = src

}

class GGraph(val nodes:Seq[Node], val edges:Seq[Edge])

class GAsset extends Node {

  override var `type` = "asset"

  var hostname:Option[String] = None

  def setId(hostname:String):Unit = {
    this.id = Some("asset--" + java.util.UUID.fromString(hostname).toString)
  }

  def setHostname(hostname:String):Unit = {
    this.hostname = Some(hostname)
  }
}
/**
 * The class [GFile] represents a certain change event
 * of the Osquery table `file_events`
 */
class GFile extends Node {

  override var `type` = "file"
  /*
   * The file node type specifies whether
   * this node is FULL file node, or, a
   * LIGHT wrapper, that must be merged with
   * the respective FULL nodes.
   */
  var nodeType:Option[NodeType] = None

  /*********************
   *
   * ATTRIBUTES
   *
   */
  var assetId:Option[String] = None
  var filePath:Option[String] = None

  var created:Option[Long]  = None
  var deleted:Option[Long]  = None
  var lastSeen:Option[Long] = None
  /**
   * The columns below specify the file_events.
   * Currently, no other events contribute with
   * columns.
   *
   * "columns": {
   *   "atime": "1599180507",
   *   "category": "temp",
   *   "ctime": "1603395939",
   *   "gid": "0",
   *   "hashed": "0",
   *   "inode": "101",
   *   "md5": "",
   *   "mode": "1777",
   *   "mtime": "1603395939",
   *   "sha1": "",
   *   "sha256": "",
   *   "size": "4096",
   *   "transaction_id": "0",
   *   "uid": "0"
   * }
   */
  var columns:Option[Set[(String, Any)]] = None

  /*********************
   *
   * MITRE ATT&CK
   *
   * Reference to the MITRE data component
   */
  var mitreDCId:Option[String]  = None
  var mitreDCName:Option[String] = None

  var mitreDomain:Option[MitreDomain] = None

  def setAssetId(assetId:String):Unit = {
    this.assetId = Some(assetId)
  }

  def setColumns(columns:JsonObject):Unit = {

    val bigint = Seq(
      "atime",
      "ctime",
      "gid",
      "inode",
      "mtime",
      "size",
      "transaction_id",
      "uid")

    val integer = Seq("hashed")
    val dataset = columns.keySet.map( key => {

      val value = if (bigint.contains(key)) {
        if (columns.has(key)) {

          val v = columns.get(key)
          if (v.isJsonPrimitive) {

            val primitive = v.getAsJsonPrimitive

            if (primitive.isNumber) primitive.getAsLong
            else primitive.getAsString.toLong

          } else Long.MinValue

        } else Long.MinValue

      }
      else if (integer.contains(key)) {
        if (columns.has(key)) {

          val v = columns.get(key)
          if (v.isJsonPrimitive) {

            val primitive = v.getAsJsonPrimitive

            if (primitive.isNumber) primitive.getAsInt
            else primitive.getAsString.toInt

          } else Int.MinValue

        } else Int.MinValue
      }
      else
        if (columns.has(key)) columns.get(key).getAsString else ""

      (key, value)

    })

    this.columns = Some(dataset.toSet)
  }
  def setCreated(time:Long):Unit = {
    this.created = Some(time)
  }

  def setDeleted(time:Long):Unit = {
    this.deleted = Some(time)
  }

  def setFilePath(targetPath:String):Unit = {
    this.filePath = Some(targetPath)
  }

  def setId(hostname:String):Unit = {
    this.id = Some("file--" + java.util.UUID.fromString(hostname).toString)
  }

  def setLastSeen(time:Long):Unit = {
    this.lastSeen = Some(time)
  }

  def setMitreDomain(domain:MitreDomain):Unit = {
    this.mitreDomain = Some(domain)
  }
  /**
   * Set the unique identifier of the MITRE
   * data component that refers to `File`.
   */
  def setMitreId(id:String):Unit = {
    this.mitreDCId = Some(id)
  }
  def setMitreName(name:String):Unit = {
    this.mitreDCName = Some(name)
  }

  def setNodeType(nodeType:NodeType):Unit = {
    this.nodeType = Some(nodeType)
  }
}

class GProcess extends Node {

  override var `type` = "process"
  /*
   * The process node type specifies whether
   * this node is FULL process node, or, a
   * LIGHT wrapper, that must be merged with
   * the respective FULL nodes.
   */
  var nodeType:Option[NodeType] = None

  /*********************
   *
   * ATTRIBUTES
   *
   */
  var assetId:Option[String] = None
  var lastSeen:Option[Long] = None

  def setAssetId(assetId:String):Unit = {
    this.assetId = Some(assetId)
  }

  def setId(pid:Long):Unit = {
    this.id = Some(s"process--$pid")
  }

  def setLastSeen(time:Long):Unit = {
    this.lastSeen = Some(time)
  }

  def setNodeType(nodeType:NodeType):Unit = {
    this.nodeType = Some(nodeType)
  }

}