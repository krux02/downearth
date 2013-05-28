name := "downearth"

organization := "fdad"

version := "0.2.0"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  //"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  //"Slick" at "http://slick.cokeandcode.com/mavenrepo"
)



libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2-M3",
  "org.scala-lang" % "scala-reflect" % "2.10.1",
  "org.simplex3d" %% "simplex3d-math-double" % "2.4.7",
  "org.simplex3d" %% "simplex3d-data-double" % "2.4.7",
  //"slick" % "slick" % "274",
  "java3d" % "vecmath" % "1.3.1"
)

//net.virtualvoid.sbt.graph.Plugin.graphSettings

//filterScalaLibrary := false

seq( LWJGLPlugin.lwjglSettings: _*)

lwjgl.version := "2.9.0"

scalacOptions ++= Seq(
  "-unchecked", 
  "-deprecation", 
  "-feature", 
  "-Yinline-warnings", 
  "-language:_"
)

javaOptions in run ++= Seq(
  "-XX:+ExplicitGCInvokesConcurrent",
  "-Xmx512m"
)

//initialCommands := """
//import simplex3d.math._
//import simplex3d.math.float._
//import simplex3d.math.float.functions._
//import ._
//"""
