package com.xebialabs.xlrelease.stress.handlers.xlr.http

import akka.http.scaladsl.model.Uri
import cats.implicits._
import com.github.nscala_time.time.Imports.DateTime
import com.xebialabs.xlrelease.stress.config.{AdminPassword, XlrServer}
import com.xebialabs.xlrelease.stress.domain._
import com.xebialabs.xlrelease.stress.dsl.http.{Client, Http, HttpLib}
import com.xebialabs.xlrelease.stress.dsl.xlr
import com.xebialabs.xlrelease.stress.dsl.xlr.protocol.CreateReleaseArgs
import com.xebialabs.xlrelease.stress.handlers.xlr.XlrRest
import com.xebialabs.xlrelease.stress.utils.DateFormat
import com.xebialabs.xlrelease.stress.utils.JsUtils._
import freestyle.free._
import freestyle.free.implicits._
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps


class ReleasesHandler[F[_]]()
                           (implicit val
                            server: XlrServer,
                            adminPassword: AdminPassword,
                            client: Client[F],
                            http: Http[F]) extends XlrRest {

  val httpLib = new HttpLib[F]()

  type Target[A] = FreeS[F, A]

  implicit def releasesHandler: xlr.Releases.Handler[Target] = new xlr.Releases.Handler[Target] with DefaultJsonProtocol with DateFormat {

    protected def importTemplate(template: Template)
                                (implicit session: User.Session): Target[Template.ID] =
      for {
        _ <- debug(s"importTemplate(${template.name})")
        resp <- httpLib.client.postZip(api(_ / "templates" / "import"), template.xlrTemplate)
        content <- http.client.parseJson(resp)
        templateId <- http.json.read(readFirstId)(content)
      } yield templateId

    protected def getTemplateTeams(templateId: Template.ID)
                                  (implicit session: User.Session): Target[Seq[Team]] =
      for {
        _ <- debug(s"getTemplateTeams($templateId)")
        resp <- http.client.get(api(_ / "templates" / "Applications" / templateId / "teams"))
        content <- http.client.parseJson(resp)
        teams <- http.json.read(readTeams)(content)
      } yield teams

    protected def setTemplateTeams(templateId: Template.ID, teams: Seq[Team])
                                  (implicit session: User.Session): Target[Map[String, String]] =
      for {
        _ <- debug(s"setTemplateTeams($templateId, ${teams.map(_.teamName).mkString("[", ", ", "]")})")
        resp <- httpLib.client.postJSON(api(_ / "templates" / "Applications" / templateId / "teams"), teams.map(_.toJson).toJson)
        content <- http.client.parseJson(resp)
        teamIds <- http.json.read(readTeamIds)(content)
      } yield teamIds

    protected def setTemplateScriptUser(templateId: Template.ID, scriptUser: Option[User])
                                       (implicit session: User.Session): Target[Unit] = {
      val user = scriptUser.getOrElse(session.user)
      for {
        _ <- debug(s"setTemplateScriptUser($templateId, $scriptUser)")
        resp <- httpLib.client.putJSON(api(_ / "templates" / "Applications" / templateId),
          JsObject(
            "id" -> JsNull,
            "scheduledStartDate" -> DateTime.now.toJson,
            "type" -> "xlrelease.Release".toJson,
            "scriptUsername" -> user.username.toJson,
            "scriptUserPassword" -> user.password.toJson
          )
        )
        _ <- http.client.discard(resp)
      } yield ()
    }

    protected def createFromTemplate(templateId: Template.ID, createReleaseArgs: CreateReleaseArgs)
                                    (implicit session: User.Session): Target[Release.ID] =
      for {
        _ <- debug(s"createFromTeamplte($templateId, ${createReleaseArgs.show})")
        resp <- httpLib.client.postJSON(api(_ / "templates" / "Applications" / templateId / "create"), createReleaseArgs.toJson)
        content <- http.client.parseJson(resp)
        releaseId <- http.json.read(readIdString)(content)
      } yield releaseId

    protected def createRelease(title: String, scriptUser: Option[User])
                               (implicit session: User.Session): Target[Phase.ID] = {
      val user: User = scriptUser.getOrElse(session.user)
      for {
        _ <- debug(s"createRelease($title, ${scriptUser.map(_.show)})")
        resp <- httpLib.client.postJSON(root(_ / "releases"),
          JsObject(
            "templateId" -> JsNull,
            "title" -> title.toJson,
            "scheduledStartDate" -> DateTime.now.toJson,
            "dueDate" -> (DateTime.now plusHours 5).toJson,
            "type" -> "xlrelease.Release".toJson,
            "owner" -> JsObject(
              "username" -> session.user.username.toJson
            ),
            "scriptUsername" -> JsObject(
              "username" -> user.username.toJson
            ),
            "scriptUserPassword" -> user.password.toJson
          ))
        content <- http.client.parseJson(resp)
        phaseId <- http.json.read(readFirstPhaseId(sep = "-"))(content)
      } yield phaseId
    }

    protected def start(releaseId: Release.ID)
                       (implicit session: User.Session): Target[Release.ID] = {
      for {
        _ <- debug(s"start($releaseId)")
        resp <- httpLib.client.postJSON(api(_ / "releases" / "Applications" / releaseId / "start"), JsNull)
        _ <- http.client.discard(resp)
      } yield releaseId
    }


    protected def getTasksByTitle(releaseId: Release.ID, taskTitle: String, phaseTitle: Option[String])
                                 (implicit session: User.Session): Target[Seq[Task.ID]] = {


      val baseQuery = Uri.Query(
        "releaseId" -> s"Applications/$releaseId",
        "taskTitle" -> taskTitle
      )
      val query = phaseTitle.fold(baseQuery) { pt =>
        ("phaseTitle" -> pt) +: baseQuery
      }
      for {
        _ <- debug(s"getTasksByTitle($releaseId, $taskTitle, $phaseTitle)")
        resp <- http.client.get(api(_ / "tasks" / "byTitle").withQuery(query))
        content <- http.client.parseJson(resp)
        taskIds <- doReadTaskIds(content)
      } yield taskIds
    }

    protected def waitFor(releaseId: Release.ID, status: ReleaseStatus, interval: FiniteDuration, retries: Option[Int])
                         (implicit session: User.Session): Target[Unit] = {
      def getReleaseStatus: Target[ReleaseStatus] =
        for {
          _ <- debug(s"waitFor: getReleaseStatus($releaseId, $status, $interval, $retries)")
          resp <- http.client.get(api(_ / "releases" / "Applications" / releaseId))
          content <- http.client.parseJson(resp)
          releaseStatus <- http.json.read(readReleaseStatus)(content)
        } yield releaseStatus

      httpLib.until[ReleaseStatus](_ == status, interval, retries)(getReleaseStatus)
        .map(_ => ())
    }
  }

  private def debug(msg: String)(implicit session: User.Session): Target[Unit] =
    http.log.debug(s"${session.user.username}: $msg")

  private def doReadTaskIds(content: JsValue): Target[List[Task.ID]] =
    http.json.read(readTaskIds(sep = "/"))(content)

}
