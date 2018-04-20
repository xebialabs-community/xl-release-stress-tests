package com.xebialabs.xlrelease.stress

import java.util.concurrent._

import akka.http.scaladsl.model.Uri
import com.xebialabs.xlrelease.stress.handlers.xlr.akkaClient.AkkaHttpXlrClient

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Main extends Runner {
  val usage: String =
    """
      |    sbt "run xlReleaseUrl numUsers"
      |
      |example:
      |    sbt "run http://xl-release.xebialabs.com:5516 100"
    """.stripMargin


  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println(usage)
      System.exit(-1)
    }

    val hostname = Uri(args(0))
    val numUsers = args(1).toInt


    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(2 * numUsers)
    )

    implicit val client: AkkaHttpXlrClient = new AkkaHttpXlrClient(hostname)

    println("MAIN: running scenario...")
    scenarios.CompleteReleases(numUsers).run
    println("MAIN: done")

    System.exit(0)
  }


}

