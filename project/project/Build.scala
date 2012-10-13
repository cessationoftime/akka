import sbt._
object PluginDef extends Build {
  override def projects = Seq(root)
  lazy val root = Project("plugins", file(".")) dependsOn(multiJvmPlugin)
 
 //  lazy val multiJvmPlugin = uri("git://github.com/cessationoftime/sbt-multi-jvm.git")
 lazy val multiJvmPlugin = uri("file:///C:/Users/cvanvranken/gits/sbt-multi-jvm")
}
