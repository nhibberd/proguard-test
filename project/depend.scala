import sbt._
import Keys._

object depend {
  val aws = Seq(
      "com.amazonaws"       %  "aws-java-sdk" % "1.9.0" exclude("joda-time", "joda-time") // This is declared with a wildcard
    , "com.owtelse.codec"   %  "base64"       % "1.0.6"
    , "javax.mail"          %  "mail"         % "1.4.7")

  val resolvers = Seq(
      Resolver.sonatypeRepo("releases")
    , Resolver.typesafeRepo("releases")
    , "cloudera"              at "https://repository.cloudera.com/content/repositories/releases"
    , Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
    , "Scalaz Bintray Repo"   at "http://dl.bintray.com/scalaz/releases")

}
