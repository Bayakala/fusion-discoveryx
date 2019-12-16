import Commons._
import Dependencies._
import Environment._

ThisBuild / buildEnv := {
  sys.props
    .get("build.env")
    .orElse(sys.env.get("BUILD_ENV"))
    .flatMap {
      case "prod"  => Some(BuildEnv.Production)
      case "stage" => Some(BuildEnv.Stage)
      case "test"  => Some(BuildEnv.Test)
      case "dev"   => Some(BuildEnv.Developement)
      case _       => None
    }
    .getOrElse(BuildEnv.Developement)
}

ThisBuild / scalaVersion := versionScala213

ThisBuild / crossScalaVersions := Seq(versionScala212, versionScala213)

ThisBuild / scalafmtOnCompile := true

ThisBuild / sonarUseExternalConfig := true

lazy val root = Project(id = "fusion-discoveryx", base = file("."))
  .aggregate(discoveryxFunctest, discoveryxServer, discoveryxClient, discoveryxCommon)
  .settings(Environment.settings: _*)
  .settings(skip in publish := true, aggregate in sonarScan := false)

lazy val discoveryxDocs = _project("discoveryx-docs")
  .enablePlugins(ParadoxMaterialThemePlugin)
  .dependsOn(discoveryxFunctest, discoveryxServer, discoveryxClient, discoveryxCommon)
  .settings(
    skip in publish := true,
    Compile / paradoxMaterialTheme ~= {
      _.withLanguage(java.util.Locale.SIMPLIFIED_CHINESE)
        .withColor("indigo", "red")
        .withRepository(uri("https://github.com/akka-fusion/fusion-discoveryx"))
        .withSocial(
          uri("http://akka-fusion.github.io/akka-fusion/"),
          uri("https://github.com/akka-fusion"),
          uri("https://weibo.com/yangbajing"))
    },
    paradoxProperties ++= Map(
        "github.base_url" -> s"https://github.com/akka-fusion/fusion-discoveryx/tree/${version.value}",
        "version" -> version.value,
        "scala.version" -> scalaVersion.value,
        "scala.binary_version" -> scalaBinaryVersion.value,
        "scaladoc.akka.base_url" -> s"http://doc.akka.io/api/$versionAkka",
        "akka.version" -> versionAkka))

lazy val discoveryxFunctest = _project("discoveryx-functest")
  .enablePlugins(MultiJvmPlugin)
  .dependsOn(discoveryxServer, discoveryxClient)
  .configs(MultiJvm)
  .settings(
    skip in publish := true,
    jvmOptions in MultiJvm := Seq("-Xmx512M"),
    libraryDependencies ++= Seq(_akkaMultiNodeTestkit % Test) ++ _akkaHttps)

lazy val discoveryxServer = _project("discoveryx-server")
  .enablePlugins(JavaAgent, AkkaGrpcPlugin, JavaAppPackaging)
  .dependsOn(discoveryxCommon)
  .settings(
    skip in publish := true,
    javaAgents += _alpnAgent % "runtime;test",
    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    libraryDependencies ++= Seq(
        _scalapbJson4s,
        _postgresql,
        _fusionProtobufV3,
        _fusionCore,
        _akkaPersistenceQuery,
        _akkaPersistenceJdbc,
        _akkaPersistenceTyped,
        _akkaPersistenceCassandraLauncher % Test) ++ _akkaHttps ++ _akkaClusters ++ _akkaPersistenceCassandras)

lazy val discoveryxClient =
  _project("discoveryx-client")
    .dependsOn(discoveryxCommon)
    .settings(Publishing.publishing: _*)
    .settings(libraryDependencies ++= Seq())

lazy val discoveryxCommon = _project("discoveryx-common")
  .enablePlugins(AkkaGrpcPlugin)
  .settings(Publishing.publishing: _*)
  .settings(
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        _fusionCommon))

def _project(name: String, _base: String = null) =
  Project(id = name, base = file(if (_base eq null) name else _base))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(basicSettings: _*)
    .settings(libraryDependencies ++= Seq(_fusionTestkit % Test))
