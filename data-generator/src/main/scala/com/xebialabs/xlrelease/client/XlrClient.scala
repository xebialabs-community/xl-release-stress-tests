package com.xebialabs.xlrelease.client

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.xebialabs.xlrelease.client.XlrClient._
import com.xebialabs.xlrelease.domain._
import com.xebialabs.xlrelease.json.XlrJsonProtocol
import spray.client.pipelining._
import spray.http.{BasicHttpCredentials, _}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object XlrClient {

  /**
    * A wrapper for error messages extracted from non-successful responses.
    */
  class XlrClientException(m: String) extends RuntimeException(m)

  /**
    * Returns a failed [[Future]] for all the non-successful responses.
    */
  private[client] def failNonSuccessfulResponses(responseFuture: Future[HttpResponse]) = responseFuture.flatMap {
    case response if response.status.isSuccess =>
      responseFuture
    case response if response.status == StatusCodes.Conflict =>
      responseFuture
    case response if response.status == StatusCodes.BadRequest && response.entity.data.asString.contains("already exists") =>
      responseFuture
    case response =>
      Future.failed(new XlrClientException(response.entity.data.asString))
  }
}

class XlrClient(apiUrl: String, username: String = "admin", password: String = "admin") extends XlrJsonProtocol with AdditionalFormats with LazyLogging {

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(30 days)
  val requestCounter = new AtomicInteger(0)

  private val strictPipeline = (req: HttpRequest) => {
    val requestNum = requestCounter.getAndIncrement()
    val loggingReq = (i: HttpRequest) => {
      logger.debug(i.toString)
    }
    val loggingResp = (i: HttpResponse) => {
      logger.info(s"Request $requestNum execution done with ${i.status}")
      logger.debug(i.toString)
    }

    val pipeline = logRequest(loggingReq) ~>
      addCredentials(BasicHttpCredentials(username, password)) ~>
      sendReceive ~>
      logResponse(loggingResp)

    failNonSuccessfulResponses(pipeline(req))
  }

  def createUser(u: User): Future[HttpResponse] = strictPipeline(Post(s"$apiUrl/users", u))

  def setRoles(roles: Seq[Principal]): Future[HttpResponse] = strictPipeline(Put(s"$apiUrl/roles/principals", roles))

  def getPermissions(roleName: String): Future[Permission] = strictPipeline(Get(s"$apiUrl/roles/permissions/global"))
    .map(obj => {
      obj.entity.as[JsObject] match {
        case Right(r) =>
          val roles = r.fields("rolePermissions").asInstanceOf[JsArray]
          roles.elements.find(r => r.asJsObject.fields("role").asJsObject.fields("name").asInstanceOf[JsString].value == roleName).get.convertTo[Permission]
        case Left(_) => null
      }
    })

  def setPermissions(permissions: Seq[Permission]): Future[HttpResponse] = strictPipeline(Put(s"$apiUrl/roles/permissions/global", permissions))

  def removeCi(id: String): Future[HttpResponse] =
    strictPipeline(Delete(s"$apiUrl/fixtures/$id"))

  def removeRelease(id: String): Future[HttpResponse] =
    removeCi(id)

  def createCis(cis: Seq[Ci]): Future[HttpResponse] =
    strictPipeline(Post(s"$apiUrl/fixtures/", cis))

  def createRelease(release: Release): Future[HttpResponse] =
    strictPipeline(Post(s"$apiUrl/fixtures/release", release))

  def createActivityLogs(releaseId: String, logs: Seq[Ci]): Future[HttpResponse] =
    strictPipeline(Post(s"$apiUrl/fixtures/activityLogs/$releaseId", logs))

  def createReleaseAndRelatedCis(releaseData: ReleaseAndRelatedCis): Future[HttpResponse] =
    createRelease(releaseData.release)
      .flatMap(_ => createActivityLogs(releaseData.release.id, releaseData.activityLogs))

  def createOrUpdateCis(cis: Seq[Ci]): Future[HttpResponse] =
    strictPipeline(Put(s"$apiUrl/fixtures/", cis))

  def createFolders(cis: Seq[Ci]): Future[HttpResponse] =
    strictPipeline(Post(s"$apiUrl/fixtures/folders", cis))

  def createTeams(cis: Seq[Ci]): Future[HttpResponse] =
    strictPipeline(Post(s"$apiUrl/fixtures/teams", cis))

  def importTemplate(file: String): Future[HttpResponse] = {
    val is = getClass.getResourceAsStream(file)
    val bytes = Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
    val formFile = FormFile(file, HttpEntity(HttpData(bytes)).asInstanceOf[HttpEntity.NonEmpty])
    val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))

    strictPipeline(Post(s"$apiUrl/api/v1/templates/import", mfd))
  }

}
