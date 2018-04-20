package com.xebialabs.xlrelease.stress.utils

import cats.effect.IO
import cats.implicits._
import com.xebialabs.xlrelease.stress.domain._
import spray.json._

object JsUtils {
  type JsParsed[A] = Either[DeserializationException, A]

  def jsObject: JsValue => JsParsed[JsObject] = {
    case obj: JsObject => obj.asRight
    case other =>
      wrongType("jsObject", "JsObject", other)
  }

  def jsArray: JsValue => JsParsed[JsArray] = {
    case arr: JsArray => arr.asRight
    case other =>
      wrongType("jsArray", "JsArray", other)
  }

  def jsString: JsValue => JsParsed[JsString] = {
    case str: JsString => str.asRight
    case other =>
      wrongType("jsString", "JsString", other)
  }

  def getElements: JsValue => JsParsed[Seq[JsValue]] =
    json =>
      jsArray(json) map (_.elements)

  def getString: JsValue => JsParsed[String] =
    json =>
      jsString(json) map (_.value)

  def getFields: JsValue => JsParsed[Map[String, JsValue]] =
    json =>
      jsObject(json) map (_.fields)

  def getField(key: String): JsValue => JsParsed[JsValue] =
    json =>
      for {
        obj <- jsObject(json)
        res <- (obj.fields.get(key) map (_.asRight))
          .getOrElse(notFound("getField", key, obj))
      } yield res

  def getObjectField(key: String): JsValue => JsParsed[JsObject] =
    json =>
      for {
        field <- getField(key)(json)
        obj <- jsObject(field)
      } yield obj

  def getStringField(key: String): JsValue => JsParsed[JsString] =
    json =>
      for {
        field <- getField(key)(json)
        str <- jsString(field)
      } yield str

  def getArrayField(key: String): JsValue => JsParsed[JsArray] =
    json =>
      getField(key)(json) >>=
        jsArray

  def getElements(key: String): JsValue => JsParsed[Seq[JsValue]] =
    json =>
      getField(key)(json) >>=
        getElements

  def getFirst: JsValue => JsParsed[JsValue] =
    json =>
      jsArray(json) >>= { array =>
        array.elements.headOption.map(_.asRight).getOrElse(
          err("getFirst: empty array.", array)
        )
      }

  def getIdString: JsValue => JsParsed[String] =
    json =>
      getStringField("id")(json) map (_.value)

  def getFirstId: JsValue => JsParsed[String] =
    json =>
      getFirst(json) >>=
        getIdString

  def readReleaseId: JsValue => JsParsed[Release.ID] =
    json =>
      getIdString(json) map (_.replaceFirst("Applications/", ""))

  def readTaskId(sep: String): JsValue => JsParsed[Task.ID] =
    json =>
      getIdString(json) >>=
        parseTaskId(sep)

  def readTaskIds(sep: String): JsValue => JsParsed[List[Task.ID]] =
    json =>
      getElements(json) >>= readTaskIdsInner(sep)

  def readTaskIdsInner(sep: String): Seq[JsValue] => JsParsed[List[Task.ID]] =
    elements =>
      elements.toList
        .map(e => getIdString(e) >>= parseTaskId(sep))
        .sequence[JsParsed, Task.ID]

  def getStatus: JsValue => JsParsed[JsValue] =
    getField("status")

  def readTaskStatus: JsValue => JsParsed[TaskStatus] =
    json =>
      getStatus(json) map toTaskStatus

  def readFirstTaskStatus: JsValue => JsParsed[TaskStatus] =
    json =>
      getFirst(json) map toTaskStatus

  def matchesTaskStatus(expectedStatus: TaskStatus): JsValue => Boolean =
    json =>
      readFirstTaskStatus(json)
        .contains(expectedStatus)

  def readReleaseStatus: JsValue => JsParsed[ReleaseStatus] =
    json =>
      getStatus(json) map toReleaseStatus

  def readFirstPhaseId(sep: String): JsValue => JsParsed[Phase.ID] =
    json =>
      getField("phases")(json) >>=
        getFirstId >>=
        parsePhaseId(sep)

  def getTeamIdEntry: JsValue => JsParsed[(String, String)] =
    json =>
      jsObject(json) >>= { obj =>
        for {
          teamName <- getStringField("teamNam")(obj)
          id <- getStringField("id")(obj)
        } yield teamName.value -> id.value
      }

  def readTeamIds: JsValue => JsParsed[Map[String, String]] =
    json =>
      (getElements(json) >>= readTeamIdsInner) map (_.toMap)

  def readTeamIdsInner: Seq[JsValue] => JsParsed[List[(String, String)]] =
    elements =>
      elements.toList
        .map(getTeamIdEntry)
        .sequence[JsParsed, (String, String)]

  def readUsername: JsValue => JsParsed[User.ID] =
    json =>
      getStringField("username")(json) map (_.value)


  def toTaskStatus: JsValue => TaskStatus =
    _.convertTo[TaskStatus]

  def toReleaseStatus: JsValue => ReleaseStatus =
    _.convertTo[ReleaseStatus]

  def parsePhaseId(sep: String = "/"): String => JsParsed[Phase.ID] =
    fullId =>
      fullId.split(sep).toList match {
        case releaseId :: phaseId :: Nil if isReleaseId(releaseId) && isPhaseId(phaseId) =>
          Phase.ID(releaseId, phaseId).asRight
        case _ =>
          parsePhaseIdError(fullId)
      }

  def parseTaskId(sep: String = "/"): String => JsParsed[Task.ID] =
    fullId =>
      fullId.split(sep).toList match {
        case _ :: _ :: Nil =>
          parseTaskIdError(fullId)
        case releaseId :: phaseId :: taskId if isReleaseId(releaseId) && isPhaseId(phaseId) && (taskId forall isTaskId) =>
          Task.ID(Phase.ID(releaseId, phaseId), taskId.mkString("/")).asRight
        case _ =>
          parseTaskIdError(fullId)
      }

  def isReleaseId: String => Boolean = _.startsWith("Release")
  def isPhaseId: String => Boolean = _.startsWith("Phase")
  def isTaskId: String => Boolean = _.startsWith("Task")

  def parsePhaseIdError[A](fullId: String): JsParsed[A] =
    error(s"parsePhaseId: not a Phase ID: $fullId")

  def parseTaskIdError[A](fullId: String): JsParsed[A] =
    error(s"parseTaskId: not a Task ID: $fullId")

  def notFound[A](msg: String, key: String, actual: JsValue): JsParsed[A] =
    err(s"$msg: Not found, key: $key.", actual)

  def wrongType[A](msg: String, expected: String, actual: JsValue): JsParsed[A] =
    err(s"$msg: Wrong type: $jsType(actual), expected: $expected.", actual)

  def error[A](msg: String, original: Option[JsValue] = None, fieldNames: List[String] = Nil): JsParsed[A] =
    DeserializationException(
      msg,
      (original map debug(msg)).orNull,
      fieldNames
    ).asLeft

  def err[A](msg: String, original: JsValue, fieldNames: List[String] = Nil): JsParsed[A] =
    error(msg, Some(original), fieldNames)

  def debug(msg: String)(jsValue: JsValue): SerializationException =
    new SerializationException(msg + "\n" + jsValue.prettyPrint)

  def jsType[A <: JsValue]: JsValue => String = {
    case _: JsObject => "JsObject"
    case _: JsArray => "JsArray"
    case _: JsString => "JsString"
    case _: JsNumber => "JsNumber"
    case _: JsBoolean => "JsBoolean"
    case JsNull => "JsNull"
  }
}