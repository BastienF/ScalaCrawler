
name := "ScalaCrawler"

version := "1.0"

scalaVersion := "2.11.4"

mainClass in Compile := Some("com.octo.crawler.ActorMain")

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
  "org.scalaj" %% "scalaj-http" % "1.1.0"
)


