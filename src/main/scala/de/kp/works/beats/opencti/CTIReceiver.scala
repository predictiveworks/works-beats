package de.kp.works.beats.opencti

import akka.stream.scaladsl.SourceQueueWithComplete
import de.kp.works.beats.ssl.SslOptions

import java.util.concurrent.Executors

/**
 * The [CTIReceiver] leverages an SSE client to
 * listen to the OpenCTI event stream.
 */
class CTIReceiver(
   /*
    * The endpoint of the OpenCTI server
    */
   endpoint:String,
   /*
    * The (optional) authorization token
    * to access the OpenCTI server
    */
   authToken:Option[String] = None,
   /*
    * The optional SSL configuration to
    * access a secure OpenCTI server
    */
   sslOptions:Option[SslOptions] = None,
   /*
    * The SSE output queue to publish the
    * incoming OpenCTI events
    */
   queue:Option[SourceQueueWithComplete[String]] = None,
   /* The number of threads to use for processing */
   numThreads:Int = 1) {

  private val executorService = Executors.newFixedThreadPool(numThreads)

  def start():Unit = {
    /*
     * Wrap connector and output handler in a runnable
     */
    val worker = new Runnable {
      /*
       * Initialize the output handler
       */
      private val outputHandler = new CTIHandler(queue)
      /*
       * Initialize the connector to the
       * OpenCTI server
       */
      private val connector = new CTIConnector(endpoint, outputHandler, authToken, sslOptions)

      override def run(): Unit = {

        val now = new java.util.Date().toString
        println(s"[CTIReceiver] $now - Receiver worker started.")

        connector.start()

      }
    }

    try {

      /* Initiate stream execution */
      executorService.execute(worker)

    } catch {
      case e:Exception => executorService.shutdown()
    }

  }

  def stop():Unit = {

    /* Stop listening to the OpenCTI events stream  */
    executorService.shutdown()
    executorService.shutdownNow()

  }

}
