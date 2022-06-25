package de.kp.works.beats.http

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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.{FileIO, RestartSource, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult, KillSwitches}
import akka.util.{ByteString, Timeout}
import com.google.gson._

import java.nio.file.Paths
import scala.collection.JavaConversions.asScalaSet
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait HttpConnect {

  private val uuid = java.util.UUID.randomUUID.toString
  implicit val httpSystem: ActorSystem = ActorSystem(s"http-connect-$uuid")

  implicit lazy val httpContext: ExecutionContextExecutor = httpSystem.dispatcher
  implicit val httpMaterializer: ActorMaterializer = ActorMaterializer()

  /*
   * Common timeout for all Akka connection
   */
  val duration: FiniteDuration = 30.seconds
  implicit val timeout: Timeout = Timeout(duration)
  /**
   * The HTTPS connection context
   */
  private var httpsContext:Option[HttpsConnectionContext] = None
  /**
   * Leveraging `superPool`, you get back a Flow which will make sure
   * that it will not take more requests than the pool is able to handle,
   * because of the backpressure.
   */
  private var pool = Http(httpSystem).superPool[NotUsed]()
  /**
   * A shared kill switch used to terminate the *entire stream* if needed
   */
  private val killSwitch = KillSwitches.shared("killSwitch")

  def setHttpsContext(context:HttpsConnectionContext):Unit = {
    httpsContext = Some(context)
    pool = Http(httpSystem).superPool[NotUsed](context)
  }

  def extractTextBody(source: Source[ByteString, Any]): String = {

    /* Extract body as String from request entity */
    val future = source.runFold(ByteString(""))(_ ++ _)
    /*
     * We do not expect to retrieve large messages
     * and accept a blocking wait
     */
    val bytes = Await.result(future, timeout.duration)
    bytes.decodeString("UTF-8")

  }

  def extractHtmlBody(source: Source[ByteString, Any]): String = {

    /* Extract body as String from request entity */
    val future = source.runFold(ByteString(""))(_ ++ _)
    /*
     * We do not expect to retrieve large messages
     * and accept a blocking wait
     */
    val bytes = Await.result(future, timeout.duration)

    val body = bytes.decodeString("UTF-8")
    body

  }

  def extractJsonBody(source: Source[ByteString, Any]): JsonElement = {

    /* Extract body as String from request entity */
    val future = source.runFold(ByteString(""))(_ ++ _)
    /*
     * We do not expect to retrieve large messages
     * and accept a blocking wait
     */
    val bytes = Await.result(future, timeout.duration)

    val body = bytes.decodeString("UTF-8")
    JsonParser.parseString(body)

  }

  /*
   * Extract the comma-separated lines from the HTTP response
   * body and transform chunks into Seq[Seq]
   *
   * @param header    The comma-separated response contain a
   * 								 column specification as header, that
   * 								 differs from endpoint to endpoint
   */
  def extractCsvBody(body: Source[ByteString, Any], charset: String = "UTF-8"): List[String] = {

    val future = body.map(byteString => {
      /*
       * The incoming [ByteString] can be a chunk of multiple
       * lines; therefore, all chunks must be split first
       */
      byteString.decodeString(charset).trim

    }).runWith(Sink.seq)
    /*
     * Await result
     */
    val result = Await.result(future, timeout.duration).asInstanceOf[Seq[String]]

    val lines = result.mkString.split("\\n")
    lines.toList

  }

  def delete(endpoint: String, headers: Map[String, String] = Map.empty[String, String]): Source[ByteString, Any] = {

    try {

      val request = {
        if (headers.isEmpty)
          HttpRequest(HttpMethods.DELETE, endpoint)

        else
          HttpRequest(HttpMethods.DELETE, endpoint, headers = headers.map { case (k, v) => RawHeader(k, v) }.toList)

      }

      val future: Future[HttpResponse] = Http(httpSystem).singleRequest(request)
      val response = Await.result(future, duration)

      val status = response.status
      if (status == StatusCodes.OK)
        return response.entity.dataBytes

      if (status == StatusCodes.NoContent)
        return response.entity.dataBytes

      throw new Exception(s"Request to Http endpoint returns with: ${status.value}.")

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }
  /**
   * This method supports a simple download request
   * without any HTTP headers and informs the requestor
   * about the result via the download handler
   */
  def download(endpoint:String, file:String):IOResult = {

    val headers = Map.empty[String,String]
    val pooled = false

    download(endpoint, headers, pooled, file)

  }

  def download(endpoint:String,headers: Map[String, String],pooled: Boolean, file:String):IOResult = {

    try {

      if (file.isEmpty)
        throw new Exception("The provided file name is empty.")

      val request = {
        if (headers.isEmpty)
          HttpRequest(HttpMethods.GET, endpoint)

        else
          HttpRequest(HttpMethods.GET, endpoint, headers = headers.map { case (k, v) => RawHeader(k, v) }.toList)
      }

      val response =
        if (pooled) {
          pooledGet(request)

        } else singleGet(request)

      val future = Await
        .result(response, duration)
        .runWith(FileIO.toPath(Paths.get(file)))

     Await.result(future, duration)

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }
  /**
   * A public GET method that either leverages the Akka
   * `singleRequest` or the `pooled` API
   */
  def get(endpoint: String, headers: Map[String, String] = Map.empty[String,String], pooled: Boolean = false): Source[ByteString, Any] = {

    try {

      val request = {
        if (headers.isEmpty)
          HttpRequest(HttpMethods.GET, endpoint)

        else
          HttpRequest(HttpMethods.GET, endpoint, headers = headers.map { case (k, v) => RawHeader(k, v) }.toList)
      }

      val response = Await.result({
        if (pooled) {
          pooledGet(request)

        } else singleGet(request)

      }, duration)

      response

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }

  /**
   * The `backoff` policy for pooled GET requests
   */
  def backoff: BackoffPolicy = throw new Exception("Not implemented.")

  /**
   * The `parallelism` of pooled GET requests
   */
  def parallelism: Int = 1

  private def singleGet(request: HttpRequest): Future[Source[ByteString, Any]] = {

    if (httpsContext.isEmpty) {
      Http(httpSystem)
        .singleRequest(request)
        .map(handleSingleResponse)

    } else {
      Http(httpSystem)
        .singleRequest(request = request, connectionContext = httpsContext.get)
        .map(handleSingleResponse)

    }

  }

  private def handleSingleResponse: PartialFunction[HttpResponse, Source[ByteString, Any]] = {
    /*
     * Handling regular Http responses
     */
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entity.withoutSizeLimit().dataBytes

    case HttpResponse(StatusCodes.UnprocessableEntity, _, entity, _) =>
      entity.dataBytes
    /*
     * Handling (429) Too many requests, and apply
     * exponential backoff
     */
    case HttpResponse(StatusCodes.TooManyRequests, _, entity, _) =>
      entity.discardBytes()
      throw TooManyRequests

    case HttpResponse(statusCode, _, entity, _) =>
      entity.discardBytes()
      killSwitch.shutdown()

      throw new Exception(s"Request to Http endpoint returns with: ${statusCode.value}.")

  }

  /**
   * This method supports GET requests with `backoff` support,
   * and also enables proper handling of `Too many requests`.
   *
   * The [Backoff] must be adjusted to the selected REST API
   * like Shopify, Sage, (Open)Weather etc.
   */
  private def pooledGet(request: HttpRequest): Future[Source[ByteString, Any]] = {
    /*
     * Perform connection pool based Http GET request; it uses
     * the `backoff` feature to supervise a stream and restart
     * it with exponential backoff.
     */
    RestartSource.withBackoff(
      minBackoff = backoff.minBackoff, maxBackoff = backoff.maxBackoff, randomFactor = backoff.randomFactor, maxRestarts = backoff.maxRestarts) { () =>
      Source.single((request, NotUsed)).via(pool)
        .mapAsync(parallelism = parallelism)(handlePooledResponse)

    }.runWith(Sink.head).recover {
      case _ => throw FailedAfterMaxRetries
    }

  }

  private def handlePooledResponse: PartialFunction[(Try[HttpResponse], NotUsed), Future[Source[ByteString, Any]]] = {
    case (Success(answer), _) => answer match {
      /*
       * Handling regular Http responses
       */
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Future {
          entity.withoutSizeLimit().dataBytes
        }

      case HttpResponse(StatusCodes.UnprocessableEntity, _, entity, _) =>
        Future {
          entity.dataBytes
        }
      /*
       * Handling (429) Too many requests, and apply
       * exponential backoff
       */
      case HttpResponse(StatusCodes.TooManyRequests, _, entity, _) =>
        entity.discardBytes()
        throw TooManyRequests

      case HttpResponse(statusCode, _, entity, _) =>
        entity.discardBytes()
        killSwitch.shutdown()

        throw new Exception(s"Request to Http endpoint returns with: ${statusCode.value}.")
    }
    case (Failure(failure), _) => throw failure

  }

  def patch(endpoint: String, headers: Map[String, String], body: JsonObject, contentType: String = "application/json"): Source[ByteString, Any] = {

    try {

      val reqEntity = buildRequestEntity(body, contentType)
      val reqHeaders = headers.map { case (k, v) => RawHeader(k, v) }.toList

      val request = HttpRequest(HttpMethods.POST, endpoint, entity = reqEntity, headers = reqHeaders)
      val future: Future[HttpResponse] = Http(httpSystem).singleRequest(request)

      val response = Await.result(future, duration)

      val statuses = Seq(StatusCodes.OK, StatusCodes.Created, StatusCodes.NoContent)
      val status = response.status

      if (!statuses.contains(status))
        throw new Exception(s"Request to Http endpoint returns with: ${status.value}.")

      response.entity.dataBytes

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }

  def post(endpoint: String, headers: Map[String, String],
           body: JsonObject, contentType: String = "application/json"): Source[ByteString, Any] = {

    try {

      val reqEntity = buildRequestEntity(body, contentType)
      val reqHeaders = headers.map { case (k, v) => RawHeader(k, v) }.toList

      val request = HttpRequest(HttpMethods.POST, endpoint, entity = reqEntity, headers = reqHeaders)
      val future: Future[HttpResponse] = Http(httpSystem).singleRequest(request)

      val response = Await.result(future, duration)

      val statuses = Seq(StatusCodes.OK, StatusCodes.Created, StatusCodes.NoContent)
      val status = response.status

      if (!statuses.contains(status))
        throw new Exception(s"Request to Http endpoint returns with: ${status.value}.")

      response.entity.dataBytes

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }

  def postHttp(endpoint: String, headers: Map[String, String],
               body: JsonObject, contentType: String = "application/json"): HttpResponse = {

    try {

      val reqEntity = buildRequestEntity(body, contentType)
      val reqHeaders = headers.map { case (k, v) => RawHeader(k, v) }.toList

      val request = HttpRequest(HttpMethods.POST, endpoint, entity = reqEntity, headers = reqHeaders)
      val future: Future[HttpResponse] = Http(httpSystem).singleRequest(request)

      val response = Await.result(future, duration)

      val statuses = Seq(StatusCodes.OK, StatusCodes.Created, StatusCodes.NoContent)
      val status = response.status

      if (!statuses.contains(status))
        throw new Exception(s"Request to Http endpoint returns with: ${status.value}.")

      response

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }

  /**
   * A helper method to build request entity for
   * `patch`, `post` and `put` requests
   */
  private def buildRequestEntity(
    body: JsonObject, contentType: String = "application/json"):RequestEntity = {

    contentType match {
      case "application/json" =>
        HttpEntity(`application/json`, ByteString(body.toString))

      case "application/x-www-form-urlencoded" =>
        /*
         * Transform JSON body to form
         */
        val fields = body.keySet()
        val form = fields.map(field => {

          val value = body.get(field).getAsString
          (field, value)

        }).toMap
        /*
         * Leverage `FormData` as respective HttpEntity
         */
        FormData(form).toEntity

      case _ => throw new Exception(s"Content-type `$contentType` is not supported.")
    }

  }

  def put(endpoint: String, headers: Map[String, String], body: String, cookies: Map[String, String] = Map.empty[String, String]): Source[ByteString, Any] = {

    try {

      val reqEntity = HttpEntity(`application/json`, ByteString(body))
      val baseHeaders = headers.map { case (k, v) => RawHeader(k, v) }.toList

      val reqHeaders =
        if (cookies.isEmpty) baseHeaders
        else {
          val cookieHeaders = cookies.map { case (k, v) => Cookie(k, v) }.toList
          baseHeaders ++ cookieHeaders
        }

      val request = HttpRequest(HttpMethods.PUT, endpoint, entity = reqEntity, headers = reqHeaders)
      val future: Future[HttpResponse] = Http(httpSystem).singleRequest(request)

      val response = Await.result(future, duration)

      val statuses = Seq(StatusCodes.OK)
      val status = response.status

      if (!statuses.contains(status))
        throw new Exception(s"Request to Http endpoint returns with: ${status.value}.")

      response.entity.dataBytes

    } catch {
      case t: Throwable =>
        throw new Exception(t.getLocalizedMessage)
    }

  }

}
