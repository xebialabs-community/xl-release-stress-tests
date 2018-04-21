package com.xebialabs.xlrelease.stress.dsl.xlr.protocol

import cats.Show
import com.github.nscala_time.time.Imports._
import com.xebialabs.xlrelease.stress.utils.DateFormat
import spray.json._


case class CreateReleaseArgs(title: String,
                             variables: Map[String, String],
                             passwordVariables: Map[String, String] = Map.empty,
                             scheduledStartDate: DateTime = DateTime.now(),
                             autoStart: Boolean = false)

object CreateReleaseArgs extends DefaultJsonProtocol with DateFormat {

  implicit val createReleaseArgsWriter: RootJsonWriter[CreateReleaseArgs] = cra =>
    JsObject(
      "releaseTitle" -> cra.title.toJson,
      "releaseVariables" -> cra.variables.toJson,
      "releasePasswordVariables" -> cra.passwordVariables.toJson,
      "scheduledStartDate" -> cra.scheduledStartDate.toJson
    )

  implicit val showCreateReleaseArgs: Show[CreateReleaseArgs] = {
    case CreateReleaseArgs(title, variables, passwordVariables, scheduledStartDate, autoStart) => {
      val vars: String = (variables ++ passwordVariables).map({ case (k,v) => s"$k:$v"}).mkString("{", " ", "}")
      s"$title $vars @${scheduledStartDate.toString} [auto: $autoStart]"
    }
  }
}