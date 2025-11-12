import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.LongType
import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.Await
import scala.concurrent.duration._

object InsertTweets {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println("Usage: InsertTweets <tweets-json-file> [mongoUri] [db] [collection]")
      System.exit(1)
    }

    val jsonFile = args(0)
    val mongoUri = if (args.length >= 2) args(1) else sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = if (args.length >= 3) args(2) else "tweetsDB"
    val collName = if (args.length >= 4) args(3) else "tweets"

    val spark = SparkSession.builder()
      .appName("InsertTweets")
      .master("local[*]")
      .config("spark.mongodb.output.uri", s"$mongoUri/$dbName.$collName")
      .getOrCreate()

    import spark.implicits._

    //  read JSON and normalize minimal fields
    val raw = spark.read.option("multiLine", value = false).json(jsonFile)

    var df = raw
    if (raw.columns.contains("timestamp_ms")) {
      df = df.withColumn("timestamp",
        to_timestamp(from_unixtime((col("timestamp_ms").cast(LongType) / 1000).cast(LongType))))
    } else if (raw.columns.contains("timestamp")) {
      df = df.withColumn("timestamp",
        when(col("timestamp").cast(LongType).isNotNull,
          to_timestamp(from_unixtime(col("timestamp").cast(LongType))))
          .otherwise(col("timestamp")))
    }

    // normalize coordinates to `location` when available
    if (raw.columns.contains("coordinates")) {
      df = df.withColumn("location",
        when(col("coordinates").isNotNull && col("coordinates.coordinates").isNotNull,
          struct(lit("Point").as("type"), col("coordinates.coordinates").as("coordinates")))
          .otherwise(col("location")))
    }

    if (raw.columns.contains("geo") && df.columns.contains("geo")) {
      df = df.withColumn("location",
        when(col("geo.coordinates").isNotNull,
          struct(lit("Point").as("type"), array(
            col("geo.coordinates").getItem(0),
            col("geo.coordinates").getItem(1)
          ).as("coordinates")))
          .otherwise(col("location")))
    }

    // write to MongoDB
    df.write
      .format("mongodb")
      .option("uri", s"$mongoUri/$dbName.$collName")
      .mode("append")
      .save()

    spark.stop()

    // Create indexes using MongoDB Scala driver
    val client: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = client.getDatabase(dbName)
    val collection: MongoCollection[Document] = database.getCollection(collName)

    try {
      val f1 = collection.createIndex(Document("timestamp" -> 1)).toFuture()
      Await.result(f1, 30.seconds)
    } catch {
      case _: Throwable => // ignore
    }

    try {
      val f2 = collection.createIndex(Document("location" -> "2dsphere")).toFuture()
      Await.result(f2, 30.seconds)
    } catch {
      case _: Throwable => // ignore
    }

    client.close()
    println(s"Finished writing tweets from $jsonFile to $mongoUri/$dbName.$collName and ensured indexes")
  }
}
