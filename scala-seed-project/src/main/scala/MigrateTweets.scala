import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonDocument, BsonArray, BsonDouble, BsonDateTime}
import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._

import scala.concurrent.Await
import scala.concurrent.duration._
import java.text.SimpleDateFormat
import java.util.Locale

object MigrateTweets {
  def main(args: Array[String]): Unit = {
    val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = if (args.length >= 1) args(0) else sys.env.getOrElse("MONGO_DB", "twitterDB")
    val collName = if (args.length >= 2) args(1) else sys.env.getOrElse("MONGO_COLLECTION", "tweets")

    val client: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = client.getDatabase(dbName)
    val collection: MongoCollection[Document] = database.getCollection(collName)

    // migrate missing timestamp/location
    val filter = Document("$or" -> Seq(
      Document("timestamp" -> Document("$exists" -> false)),
      Document("location" -> Document("$exists" -> false))
    ))

    println(s"Finding documents missing timestamp or location in $dbName.$collName...")
    val docsF = collection.find(filter).toFuture()
    val docs = Await.result(docsF, 60.seconds)
    println(s"Found ${docs.size} documents to inspect")

  val sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

    var updated = 0
    docs.foreach { doc =>
      val updates = scala.collection.mutable.ListBuffer.empty[Bson]

  //  fill timestamp when possible
      if (!doc.contains("timestamp")) {
        if (doc.contains("timestamp_ms")) {
          try {
            val tsMs = doc.get("timestamp_ms").get.asString().getValue.toLong
            updates += set("timestamp", new BsonDateTime(tsMs.toLong))
          } catch {
            case _: Throwable => // ignore parse issues
          }
        } else if (doc.contains("created_at")) {
          try {
            val s = doc.get("created_at").get.asString().getValue
            val d = sdf.parse(s)
            updates += set("timestamp", new BsonDateTime(d.getTime))
          } catch {
            case _: Throwable => // ignore parse issues
          }
        }
      }

  // b1: normalize location when possible
      if (!doc.contains("location")) {
        // If coordinates field exists and appears to be a GeoJSON point
        if (doc.contains("coordinates")) {
          try {
            val coords = doc.get("coordinates").get.asDocument()
            if (coords.contains("coordinates")) {
              // use coordinates as-is
              updates += set("location", coords)
            }
          } catch {
            case _: Throwable => // ignore
          }
        } else if (doc.contains("geo")) {
          try {
            val geo = doc.get("geo").get.asDocument()
            if (geo.contains("coordinates")) {
              val arr = geo.getArray("coordinates")
              if (arr.size() >= 2) {
                // geo.coordinates likely [lat, lon] -> convert to [lon, lat]
                val lat = arr.get(0).asDouble().getValue
                val lon = arr.get(1).asDouble().getValue
                val point = BsonDocument("type" -> "Point", "coordinates" -> BsonArray(BsonDouble(lon), BsonDouble(lat)))
                updates += set("location", point)
              }
            }
          } catch {
            case _: Throwable => // ignore
          }
        }
      }

  if (updates.nonEmpty) {
        try {
          val updateCombined = combine(updates.toSeq: _*)
          Await.result(collection.updateOne(Document("_id" -> doc.get("_id").get), updateCombined).toFuture(), 30.seconds)
        } catch {
          case _: Throwable =>
        }
        updated += 1
      }
    }

    println(s"Updated $updated documents (set timestamp/location where available)")

    // ensure indexes
    try {
      Await.result(collection.createIndex(Document("timestamp" -> 1)).toFuture(), 30.seconds)
    } catch { case _: Throwable => }
    try {
      Await.result(collection.createIndex(Document("location" -> "2dsphere")).toFuture(), 30.seconds)
    } catch { case _: Throwable => }

    println("Ensured indexes on timestamp and location (2dsphere)")
    client.close()
  }
}
