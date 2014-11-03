import sbt._
import Keys._
import com.typesafe.sbt.SbtProguard._

import scala.collection.JavaConverters._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val standardSettings = Defaults.coreDefaultSettings ++
                   projectSettings          ++
                   compilationSettings      ++
                   Seq(resolvers ++= depend.resolvers)

  lazy val projectSettings: Seq[Settings] = Seq(
      name := "proguard-test"
    , version in ThisBuild := "1.2.1"
    , organization := "com.ambiata"
    , scalaVersion := "2.11.2"
    , crossScalaVersions := Seq(scalaVersion.value)
  )

  val ProguardPre = config("proguard-pre") extend(Compile)

  def dependencies: Seq[Setting[_]] = Seq(
    ivyConfigurations += ProguardPre,
    libraryDependencies <+= (ProguardKeys.proguardVersion in ProguardPre) { version =>
      "net.sf.proguard" % "proguard-base" % version % ProguardPre.name
    }
  )

  lazy val s3 = Project(
    id = "s3"
  , base = file(".")
  , settings = standardSettings ++ proguardSettings
      ++ Seq[Settings](libraryDependencies ++= depend.aws)
      ++ inConfig(ProguardPre)(dependencies ++ ProguardSettings.default)
      ++ Seq[Settings](
      ProguardKeys.options in ProguardPre <<= (update, packageBin in Compile).map({ case (u, b) => Seq(s"""
             -injars $b
             -printmappings mappings.map
             -keep class com.ambiata.** { *; }
             -keep class com.amazonaws.** { *; }
             -keepclassmembers class com.ambiata.** { *; }
             -keepclassmembers class com.amazonaws.** { *; }
             -libraryjars <java.home>/lib/rt.jar
             -libraryjars $b
             ${u.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-injars $f(!META-INF/MANIFEST.MF)").mkString("\n")}
             -outjars empty.jar
            -dontoptimize
            -dontshrink
            -dontpreverify
            -dontwarn META-INF**
            -dontwarn scala.**
            -dontwarn com.ambiata.**
            -dontwarn org.apache.hadoop.**
            -dontwarn org.apache.avro.**
            -dontwarn org.apache.avalon.**
            -dontwarn org.apache.commons.lang.**
            -dontwarn org.specs2.**
            -dontwarn org.scalacheck.**
            -dontwarn org.springframework.**
            -dontwarn com.owtelse.**
            -dontwarn org.jdom.**
            -dontwarn org.junit.**
            -dontwarn org.aspectj.**
            -dontwarn org.slf4j.**
            -dontwarn scodec.**
            -dontwarn org.fusesource.**
            -dontwarn org.apache.log4j.**
            -dontwarn org.apache.log.**
            -dontwarn com.bea.xml.**
            -dontwarn com.nicta.scoobi.**
            -dontwarn com.amazonaws.**
            -dontwarn org.xmlpull.**
            -dontwarn net.sf.cglib.**
            -dontwarn nu.xom.**
            -dontwarn com.ctc.wstx.**
            -dontwarn org.kxml2.**
            -dontwarn org.dom4j.**
            -dontwarn org.codehaus.jettison.**
            -dontwarn javassist.**
            -dontwarn javax.**
            -dontwarn org.joda.time.**
            -dontwarn scala.reflect.**
           """)})
         )
      ++ Seq[Settings](
    name := "test-s3"
  , ProguardKeys.options in Proguard <<= (ProguardKeys.proguard in ProguardPre, name, version in ThisBuild, baseDirectory, update, packageBin in Compile).map({ case (_, n, v, dir, u, b) => {
        val r = IO.readLines(b.getParentFile / "mappings.map")
        val s = r.filter(!_.startsWith(" "))
        val t = s.map(_.replace("-> com.amazonaws", "-> com.ambiata.com.amazonaws"))
        IO.writeLines(b.getParentFile / "aws.map", t)
        Seq(s"""
      -injars $b
      -keep class com.ambiata.** { *; }
      -keepclassmembers class com.ambiata.** { *; }
      -applymapping aws.map
      -libraryjars <java.home>/lib/rt.jar
      -libraryjars $b
      ${u.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-injars $f(!META-INF/MANIFEST.MF)").mkString("\n")}
      -outjars ${n}-hackzzz_${v}.jar
      -dontoptimize
      -dontshrink
      -dontpreverify
      -dontwarn META-INF**
      -dontwarn scala.**
      -dontwarn com.ambiata.**
      -dontwarn org.apache.hadoop.**
      -dontwarn org.apache.avro.**
      -dontwarn org.apache.avalon.**
      -dontwarn org.apache.commons.lang.**
      -dontwarn org.specs2.**
      -dontwarn org.scalacheck.**
      -dontwarn org.springframework.**
      -dontwarn com.owtelse.**
      -dontwarn org.jdom.**
      -dontwarn org.junit.**
      -dontwarn org.aspectj.**
      -dontwarn org.slf4j.**
      -dontwarn scodec.**
      -dontwarn org.fusesource.**
      -dontwarn org.apache.log4j.**
      -dontwarn org.apache.log.**
      -dontwarn com.bea.xml.**
      -dontwarn com.nicta.scoobi.**
      -dontwarn com.amazonaws.**
      -dontwarn org.xmlpull.**
      -dontwarn net.sf.cglib.**
      -dontwarn nu.xom.**
      -dontwarn com.ctc.wstx.**
      -dontwarn org.kxml2.**
      -dontwarn org.dom4j.**
      -dontwarn org.codehaus.jettison.**
      -dontwarn javassist.**
      -dontwarn javax.**
      -dontwarn org.joda.time.**
      -dontwarn scala.reflect.**
    """) } })
    , javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G"))
    ++ addArtifact(name.apply(n => Artifact(s"$n-inlined", "shade", "jar")), (ProguardKeys.proguard in Proguard).map(_.head))
    //++ addArtifact(Artifact("test-s3_hackzzz", "dist", "jar"), (packagedArtifact in Proguard).map(_._2) )
  )

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 20,
    scalacOptions ++= Seq("-feature", "-language:_"),
    scalacOptions in Compile ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )
}
