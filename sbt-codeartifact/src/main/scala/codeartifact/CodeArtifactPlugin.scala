package codeartifact

import sbt._
import sbt.Keys._
import sbt.internal.util.ManagedLogger

object CodeArtifactPlugin extends AutoPlugin {
  import CodeArtifactKeys._
  import InternalCodeArtifactKeys._

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] = buildPublishSettings
  override def projectSettings: Seq[Setting[_]] = codeArtifactSettings

  object autoImport extends CodeArtifactKeys

  def buildPublishSettings: Seq[Setting[_]] = Seq(
    ThisBuild / codeArtifactUrl := "",
    ThisBuild / codeArtifactResolvers := Nil
  )

  def codeArtifactSettings: Seq[Setting[_]] = Seq(
    codeArtifactPublish := dynamicallyPublish.value,
    codeArtifactRepo := CodeArtifactRepo.fromUrl(codeArtifactUrl.value),
    codeArtifactToken := sys.env
      .get("CODEARTIFACT_AUTH_TOKEN")
      .orElse(
        Credentials.loadCredentials(Path.userHome / ".sbt" / "credentials").toOption.map(_.passwd)
      )
      .orElse(CodeArtifact.getAuthToken(codeArtifactRepo.value)),
    codeArtifactConnectTimeout := CodeArtifact.Defaults.CONNECT_TIMEOUT,
    codeArtifactReadTimeout := CodeArtifact.Defaults.READ_TIMEOUT,
    codeArtifactPackage := CodeArtifactPackage(
      organization = organization.value,
      name = name.value,
      version = version.value,
      scalaVersion = scalaVersion.value,
      sbtBinaryVersion = if (sbtPlugin.value) Some(sbtBinaryVersion.value) else None,
      // See: https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Scala-version+specific+source+directory
      isScalaProject = crossPaths.value
    ),
    credentials ++= {
      val token = codeArtifactToken.value.getOrElse {
        streams.value.log.warn(
          """
            |    ___        ______     ____          _         _         _   _  __            _
            |   / \ \      / / ___|   / ___|___   __| | ___   / \   _ __| |_(_)/ _| __ _  ___| |_
            |  / _ \ \ /\ / /\___ \  | |   / _ \ / _` |/ _ \ / _ \ | '__| __| | |_ / _` |/ __| __|
            | / ___ \ V  V /  ___) | | |__| (_) | (_| |  __// ___ \| |  | |_| |  _| (_| | (__| |_
            |/_/   \_\_/\_/  |____/   \____\___/ \__,_|\___/_/   \_\_|   \__|_|_|  \__,_|\___|\__|
            |
            |
            |The AWS CodeArtifact sbt plugin was not able to get a valid auth token. If you recieve and error due to
            |the a dependency not being found then it could be caused by this.""".stripMargin
        )
        val instructions = codeArtifactGetTokenInstructions.value.map("\n" + _ + "\n").getOrElse("")
        streams.value.log.warn(instructions)
        ""
      }
      val repos = codeArtifactRepo.value +: codeArtifactResolvers.value
        .map(CodeArtifactRepo.fromUrl)

      repos.map(CodeArtifact.mkCredentials(token))
    },
    publishTo := Some(codeArtifactRepo.value.resolver),
    publishMavenStyle := true,
    // Useful for consuming artifacts.
    resolvers ++= (codeArtifactUrl.value +: codeArtifactResolvers.value)
      .map(CodeArtifactRepo.fromUrl)
      .map(_.resolver)
  )

  // Uses taskDyn because it can return one of two potential tasks
  // as its result, each with their own dependencies.
  // See: https://www.scala-sbt.org/1.x/docs/Howto-Dynamic-Task.html
  private def dynamicallyPublish: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val shouldSkip = (publish / skip).value
    val publishEnabled = (publish / publishArtifact).value
    val logger = streams.value.log
    val ref = thisProjectRef.value

    if (shouldSkip || !publishEnabled) Def.task {
      logger.debug(s"Skipping publish for ${ref.project}")
    }
    else publish0
  }

  private def publish0: Def.Initialize[Task[Unit]] = Def.task {
    val logger = streams.value.log
    val token = codeArtifactToken.value.getOrElse {
      throw new RuntimeException(
        "Failed to publish to AWS Codeartifact because the auth token was not set."
      )
    }
    val api = new CodeArtifactApi(
      token = token,
      readTimeout = codeArtifactReadTimeout.value,
      connectTimeout = codeArtifactConnectTimeout.value
    )
    val url = codeArtifactUrl.value.stripSuffix("/")
    val pkg = codeArtifactPackage.value
    val basePublishPath = pkg.basePublishPath
    val versionPublishPath = pkg.versionPublishPath

    val files = packagedArtifacts.value.toList
      // Drop Artifact.
      .map { case (_, file) => file }
      // Convert to os.Path.
      .map(file => os.Path(file))
      // Create CodeArtifact file name.
      .map(file => s"$versionPublishPath/${file.last}" -> file)

    val metadataFile = {
      val td = os.temp.dir()
      os.write(td / "maven-metadata.xml", codeArtifactPackage.value.mavenMetadata)
      val file = td / "maven-metadata.xml"
      s"$basePublishPath/${file.last}" -> file
    }

    val responses = (files :+ metadataFile)
      .map { case (fileName, file) =>
        logger.info(s"Uploading $fileName")
        api.upload(s"$url/$fileName", os.read.bytes(file))
      }

    reportPublishResults(responses, logger)
  }

  private def reportPublishResults(
    publishResults: Seq[requests.Response],
    logger: ManagedLogger
  ) = {
    if (publishResults.forall(_.is2xx)) {
      logger.info(s"Successfully published to AWS Codeartifact")
    } else {
      val errors = publishResults
        .filterNot(_.is2xx)
        .map { response =>
          s"Code: ${response.statusCode}, message: ${response.text()}"
        }
        .mkString("\n")

      throw new RuntimeException(s"Failed to publish to AWS Codeartifact. Errors: \n$errors")
    }
  }
}
