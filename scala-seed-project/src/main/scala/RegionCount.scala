import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonArray, BsonDouble}
import scala.concurrent.Await
import scala.concurrent.duration._

object RegionCount {
  def main(args: Array[String]): Unit = {
    //  terminal region counter
    if (args.length < 5) {
      System.err.println("Usage: RegionCount lon lat radius_meters start_epoch_sec end_epoch_sec")
      System.exit(1)
    }
    val lon = args(0).toDouble
    val lat = args(1).toDouble
    val radius = args(2).toDouble
    val start = args(3).toLong
    val end = args(4).toLong

    val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = sys.env.getOrElse("MONGO_DB", "twitterDB")
    val collName = sys.env.getOrElse("MONGO_COLLECTION", "tweets")

    val client = MongoClient(mongoUri)
    val db = client.getDatabase(dbName)
    val coll = db.getCollection(collName)

  //  compute spherical radius
  val earth = 6378100.0
    val rad = radius / earth

    val centerSphere = BsonArray(BsonArray(BsonDouble(lon), BsonDouble(lat)), BsonDouble(rad))
    val filter = Document("$and" -> Seq(
      Document("location" -> Document("$geoWithin" -> Document("$centerSphere" -> centerSphere))),
      Document("timestamp" -> Document("$gte" -> new java.util.Date(start * 1000L), "$lte" -> new java.util.Date(end * 1000L)))
    ))

  //  perform count
  val countF = coll.countDocuments(filter).toFuture()
    val count = Await.result(countF, 20.seconds)
    println(s"Documents in region/time: $count")
    client.close()
  }
}
