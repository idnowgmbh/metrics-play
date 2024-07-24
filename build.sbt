organization:= "com.kenshoo"
name := "metrics-play"
version := s"${playVersion}_${metricsPlayVersion}"

scalaVersion := "2.13.8"

crossScalaVersions := Seq(scalaVersion.value, "3.3.0")

val playVersion = "3.0.4"
val metricsPlayVersion = "0.8.3"
val dropwizardVersion = "4.2.26"


scalacOptions := Seq("-unchecked", "-deprecation")

Test / testOptions += Tests.Argument("junitxml", "console")

Test / parallelExecution := false

resolvers += Resolver.jcenterRepo

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
resolvers += "Artifactory SBT Realm" at "https://docker.dev.idnow.de:443/artifactory/sbt"

libraryDependencies ++= Seq(
    "io.dropwizard.metrics" % "metrics-core" % dropwizardVersion,
    "io.dropwizard.metrics" % "metrics-json" % dropwizardVersion,
    "io.dropwizard.metrics" % "metrics-jvm" % dropwizardVersion,
    "io.dropwizard.metrics" % "metrics-logback" % dropwizardVersion,
    "org.playframework" %% "play" % playVersion % Provided,
    "org.joda" % "joda-convert" % "2.2.0",

    //Test
    "org.playframework" %% "play-test" % playVersion % Test,
    "org.playframework" %% "play-specs2" % playVersion % Test
)

publishMavenStyle := true

pomIncludeRepository := { _ => false }

Test / publishArtifact := false

pomExtra := (
  <url>https://github.com/kenshoo/metrics-play</url>
    <inceptionYear>2013</inceptionYear>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
        <comments>A business-friendly OSS license</comments>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:kenshoo/metrics-play.git</url>
      <connection>scm:git@github.com:kenshoo/metrics-play.git</connection>
    </scm>
    <developers>
      <developer>
        <name>Ran Nisim</name>
        <email>rannisim@gmail.com</email>
        <roles>
          <role>Author</role>
        </roles>
        <organization>Kenshoo</organization>
      </developer>
      <developer>
        <name>Lior Harel</name>
        <email>harel.lior@gmail.com</email>
        <roles>
          <role>Author</role>
        </roles>
        <organization>Kenshoo</organization>
      </developer>
    </developers>
  )
