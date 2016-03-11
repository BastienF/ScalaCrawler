
name := "ScalaCrawler"

version := "1.0"

scalaVersion := "2.11.4"

mainClass in Compile := Some("com.octo.crawler.ActorMain")

lazy val commonSettings = Seq(
  version := "0.1-SNAPSHOT",
  organization := "com.example",
  scalaVersion := "2.10.1"
)

resolvers ++= Seq(
  "Maven Central Server" at "http://repo1.maven.org/maven2"
)

val buildSettings = Defaults.defaultSettings ++ Seq(
  javaOptions += "-Xmx4G",
  javaOptions += "-Xms4G"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.8",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.8",
  "com.typesafe.akka" %% "akka-remote" % "2.3.8",
  "com.typesafe.akka" %% "akka-agent" % "2.3.8",
  "org.scalaj" %% "scalaj-http" % "1.1.0",
  "io.reactivex" %% "rxscala" % "0.25.1",
  "com.ning" % "async-http-client" % "1.9.31",
  "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "2.0.1"
)


