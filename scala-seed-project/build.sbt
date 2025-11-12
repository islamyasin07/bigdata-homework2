name := "TweetAnalyzer"
version := "1.0"
scalaVersion := "2.12.18" 

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.0",
  "org.apache.spark" %% "spark-sql" % "3.5.0",
  "org.mongodb.spark" %% "mongo-spark-connector" % "10.2.1"
)

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "4.9.0"
// Testing
libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
