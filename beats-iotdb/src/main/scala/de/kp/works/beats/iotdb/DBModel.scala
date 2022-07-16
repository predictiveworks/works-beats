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

import de.kp.works.beats.iotdb.Compressions._
import de.kp.works.beats.iotdb.Encodings._
import org.apache.iotdb.tsfile.file.metadata.enums.{CompressionType, TSDataType, TSEncoding}

object Compressions extends Enumeration {
  type Compression = Value
  val UNCOMPRESSED:Compression = Value(0, "UNCOMPRESSED")
  val SNAPPY:Compression       = Value(1, "SNAPPY")
  val GZIP:Compression         = Value(2, "GZIP")
  val LZO:Compression          = Value(3, "LZO")
  val SDT:Compression          = Value(4, "SDT")
  val PAA:Compression          = Value(5, "PAA")
  val PLA:Compression          = Value(6, "PLA")
  val LZ4:Compression          = Value(7, "LZ4")

}

object Encodings extends Enumeration {
  type Encoding = Value

  val PLAIN:Encoding      = Value(0, "PLAIN")
  val DICTIONARY:Encoding = Value(1, "DICTIONARY")
  val RLE:Encoding        = Value(2, "RLE")
  val DIFF:Encoding       = Value(3, "DIFF")
  val TS_2DIFF:Encoding   = Value(4, "TS_2DIFF")
  val BITMAP:Encoding     = Value(5, "BITMAP")
  val GORILLA_V1:Encoding = Value(6, "GORILLA_V1")
  val REGULAR:Encoding    = Value(7, "REGULAR")
  val GORILLA:Encoding    = Value(8, "GORILLA")

}

case class StructField(
  fieldName:String,
  fieldType:String,
  encoding:Encoding,
  compression:Compression,
  nullable:Boolean)

case class StructSchema(
  name:String,
  isShareTime:Boolean,
  fields:Seq[StructField])

object DBUtil extends DBConstants {

  def convertCompressor(compressor:String):CompressionType = {
    compressor match {
      case UNCOMPRESSED => CompressionType.UNCOMPRESSED
      case SNAPPY       => CompressionType.SNAPPY
      case GZIP         => CompressionType.GZIP
      case LZO          => CompressionType.LZO
      case SDT          => CompressionType.SDT
      case PAA          => CompressionType.PAA
      case PLA          => CompressionType.PLA
      case LZ4          => CompressionType.LZ4
    }
  }
  /*
   * Date & time related data types must be
   * matched `TEXT` to be compliant with the
   * supported data types
   */
  def convertDataType(dataType:String):TSDataType = {
    dataType match {
      case BIG_DECIMAL    => TSDataType.DOUBLE
      case BIG_INTEGER    => TSDataType.INT64
      case BOOLEAN        => TSDataType.BOOLEAN
      case BYTE           => TSDataType.INT32
      case DOUBLE         => TSDataType.DOUBLE
      case FLOAT          => TSDataType.FLOAT
      case INTEGER        => TSDataType.INT32
      case LOCAL_DATE     => TSDataType.TEXT
      case LOCAL_DATETIME => TSDataType.TEXT
      case LOCAL_TIME     => TSDataType.TEXT
      case LONG           => TSDataType.INT64
      case SHORT          => TSDataType.INT32
      case STRING         => TSDataType.TEXT
      case _ =>
        throw new Exception(s"Data type `$dataType` is not supported.")
      }
  }

  def convertEncoding(encoding:Encoding):TSEncoding = {
    encoding match {
      case PLAIN      => TSEncoding.PLAIN
      case DICTIONARY => TSEncoding.DICTIONARY
      case RLE        => TSEncoding.RLE
      case DIFF       => TSEncoding.DIFF
      case TS_2DIFF   => TSEncoding.TS_2DIFF
      case BITMAP     => TSEncoding.BITMAP
      case GORILLA_V1 => TSEncoding.GORILLA_V1
      case REGULAR    => TSEncoding.REGULAR
      case GORILLA    => TSEncoding.GORILLA
    }
  }

}