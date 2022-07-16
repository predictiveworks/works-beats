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
import org.apache.iotdb.session.template.{MeasurementNode, Template}
import org.apache.iotdb.tsfile.file.metadata.enums.{CompressionType, TSDataType, TSEncoding}

import scala.collection.JavaConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}

case class AlignedTimeseries(
  dataType:TSDataType,
  encoding:TSEncoding,
  compressor:CompressionType,
  measurement:String,
  measurementAlias:String)

case class Timeseries(
  path:String,
  dataType:TSDataType,
  encoding:TSEncoding,
  compressor:CompressionType,
  props:Map[String,String] = Map.empty[String,String],
  /*
   * `tags` can be used to describe metadata
   * like "unit" -> "kg"
   */
  tags:Map[String,String] = Map.empty[String,String],
  /*
   * `attributes` can be used to describe metadata
   * like "minValue" -> 1, "maxValue" -> 10
   */
  attributes:Map[String,String] = Map.empty[String,String],
  /*
   * The `measurementAlias` specifies the real world
   * semantic measurement name like `weight`
   */
  measurementAlias:String)

object DDLApi {

  /**
   * Data path: root.<storage-group>.<device>.<measurement>
   *
   * Sample: root.sg1.d1.s1 (for a single measurement)
   *
   * Device `d1` with multiple measurements s1 to s5:
   * root.sg1.d1.s1
   * root.sg1.d1.s2
   * root.sg1.d1.s3
   * root.sg1.d1.s4
   * root.sg1.d1.s5
   */

  /** STORAGE GROUP **/

  def setStorageGroup(session:Session, storageGroup:String):Unit = {
    session.setStorageGroup(storageGroup)
  }

  def deleteStorageGroups(session:Session, storageGroups:List[String]):Unit = {
    session.deleteStorageGroups(storageGroups.asJava)
  }

  /** TEMPLATE
   *
   * Defining template is useful, if a large number of
   * devices has the same schema, as this improves memory
   * performance.
   */
  def buildSchemaTemplate(session:Session, path:String, schema:StructSchema):Unit = {

    val templateName = schema.name
    val isShareTime = schema.isShareTime

    val measurements = schema.fields.map(field => {
      /*
              new MeasurementNode("s1", TSDataType.INT64, TSEncoding.RLE, CompressionType.SNAPPY);

       */
    })
  }
  def buildSchemaTemplate(session:Session, templateName:String, isShareTime:Boolean,
                          path:String, measurements:List[MeasurementNode]):Unit = {

    val template = new Template(templateName, isShareTime)
    measurements.foreach(template.addToTemplate)

    createSchemaTemplate(session, template)
    setSchemaTemplate(session, templateName, path)

  }

  def createSchemaTemplate(session:Session, template:Template):Unit = {
    session.createSchemaTemplate(template)
  }

  def setSchemaTemplate(session:Session, templateName:String, path:String):Unit = {
    session.setSchemaTemplate(templateName, path)
  }
  /** TIMESERIES **/

  def checkTimeseriesExists(session:Session, path:String):Boolean = {
    session.checkTimeseriesExists(path)
  }

  def createAlignedTimeseries(session:Session, deviceId:String, ts:List[AlignedTimeseries]):Unit = {

    val dataTypes = ts.map(v => v.dataType).asJava
    val encodings = ts.map(v => v.encoding).asJava

    val compressors = ts.map(v => v.compressor).asJava

    val measurements = ts.map(v => v.measurement).asJava
    val measurementAliasList = ts.map(v => v.measurementAlias).asJava

    session.createAlignedTimeseries(deviceId, measurements, dataTypes,
      encodings, compressors, measurementAliasList)
  }
  /**
   * Create a timeseries for a certain `path` (fully qualified attribute path)
   * with a specific data type, encoding and data compression (e.g., SNAPPY).
   *
   * The measurement alias refers to the real world name of the attribute
   * like `temperature`.
   */
  def createTimeseries(session:Session, ts:Timeseries):Unit = {
    session.createTimeseries(ts.path, ts.dataType, ts.encoding, ts.compressor,
      ts.props.asJava, ts.tags.asJava, ts.attributes.asJava, ts.measurementAlias)

  }

  def createMultipleTimeseries(session:Session, ts:List[Timeseries]):Unit = {

    val paths = ts.map(v => v.path).asJava
    val dataTypes = ts.map(v => v.dataType).asJava

    val encodings = ts.map(v => v.encoding).asJava
    val compressors = ts.map(v => v.compressor).asJava

    val propsList = ts.map(v => v.props.asJava).asJava
    val tagsList = ts.map(v => v.tags.asJava).asJava

    val attributesList = ts.map(v => v.attributes.asJava).asJava
    val measurementAliasList = ts.map(v => v.measurementAlias).asJava

    session.createMultiTimeseries(paths, dataTypes, encodings, compressors,
      propsList, tagsList, attributesList, measurementAliasList)
  }

  def deleteTimeseries(session:Session, paths:List[String]):Unit = {
    session.deleteTimeseries(paths.asJava)
  }

}
