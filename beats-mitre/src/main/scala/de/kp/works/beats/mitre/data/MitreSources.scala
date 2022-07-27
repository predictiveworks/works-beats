package de.kp.works.beats.mitre.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.gson.{JsonObject, JsonParser}
import de.kp.works.beats.{BeatsConf, BeatsLogging}
import de.kp.works.beats.BeatsConf.MITRE_CONF
import de.kp.works.beats.mitre.{MitreClient, MitreDomains}

import java.io.File
import java.net.URI
import java.nio.file.{Files, Paths}

object MitreSources extends BeatsLogging {

  private val cfg = BeatsConf.getBeatCfg(MITRE_CONF)
  private val receiverCfg = cfg.getConfig("receiver")

  private val base = receiverCfg.getString("folder")

  def main(args:Array[String]):Unit = {
    getExtendedDS.foreach(println)
  }

  def getExtendedDS:Seq[JsonObject] = {

    val path = s"$base/attack-datasources/contribution"
    val file = new File(path)

    file.listFiles()
      .map(file => {
        val fname = file.getAbsolutePath
        getYaml(fname)
    })

  }

  /**
   * This method is an internal method to retrieve
   * the configuration files for a certain base path.
   */
  def getYaml(path:String):JsonObject = {

    try {
      val uri = new File(path).toURI
      YamlReader.fromUri(uri)

    } catch {
      case _:Throwable => new JsonObject
    }

  }
}

object YamlReader {

  def fromUri(uri:URI):JsonObject = {
    /*
     * Read YAML file from URI and convert
     * convent into a Java Object
     */
    val content = new String(Files.readAllBytes(Paths.get(uri)))

    val reader = new ObjectMapper(new YAMLFactory())
    val obj = reader.readValue(content, classOf[Object])
    /*
     * Leverage Java Object and convert into
     * a String
     */
    val writer = new ObjectMapper()
    val json = writer.writeValueAsString(obj)
    /*
     * Use GSON to convert JSON String into JsonObject
     */
    JsonParser.parseString(json).getAsJsonObject

  }
}
