name := "Iot"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-http" % "10.0.5","commons-io" % "commons-io" % "2.3",
  "com.typesafe.akka" %% "akka-cluster" % "2.4.17",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.17",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.17",
  "io.scalac" %% "reactive-rabbit" % "1.1.4")
    