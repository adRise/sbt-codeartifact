package codeartifact

import sbt._
import scala.concurrent.duration._

object CodeArtifact {

  def mkCredentials(token: String)(repo: CodeArtifactRepo): Credentials = Credentials(
    userName = "aws",
    realm = repo.realm,
    host = repo.host,
    passwd = token
  )

  object Defaults {
    val READ_TIMEOUT: Int = 1.minutes.toMillis.toInt
    val CONNECT_TIMEOUT: Int = 5.seconds.toMillis.toInt
  }
}
