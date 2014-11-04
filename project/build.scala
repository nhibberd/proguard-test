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

  val ProguardPre = config("proguard-pre")

  def dependenciesPre: Seq[Setting[_]] = Seq(
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

      ++ inConfig(ProguardPre)(ProguardSettings.default ++ dependenciesPre ++ Seq(managedClasspath <<= (managedClasspath, managedClasspath in Compile).map({ case (y, x) => y ++ x })) )
      ++ dependenciesPre
      ++ Seq[Settings](
      ProguardKeys.options in ProguardPre <<= (update, packageBin in Compile).map({ case (u, b) => Seq("-ignorewarnings", s"""
             -injars $b
             -printmapping mappings.map
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
           """)})
         )
      ++ Seq[Settings](
    name := "test-s3"
  , ProguardKeys.options in Proguard <<= (ProguardKeys.proguard in ProguardPre, name, version in ThisBuild, baseDirectory, update, packageBin in Compile).map({ case (_, n, v, dir, u, b) => {
        val r = IO.readLines(b.getParentFile / "proguard" / "mappings.map")
        val s = r.filter(!_.startsWith(" "))
        val t = s.map(_.replace("-> com.amazonaws", "-> com.ambiata.com.amazonaws"))
        IO.writeLines(b.getParentFile / "proguard" / "aws.map", t)
        Seq("-ignorewarnings", s"""
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
