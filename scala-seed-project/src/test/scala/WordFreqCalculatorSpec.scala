import munit.FunSuite
import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonArray, BsonDouble, BsonDateTime, BsonDocument}
import scala.concurrent.Await
import scala.concurrent.duration._
import org.mongodb.scala.bson._

class WordFreqCalculatorSpec extends FunSuite {
  test("countOccurrences counts text and hashtags") {
    val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val client = MongoClient(mongoUri)
    val db = client.getDatabase("test_db_for_counts")
    val coll = db.getCollection("tweets_test")

    // cleanup
    Await.result(coll.drop().toFuture(), 5.seconds)

    val docs = Seq(
      BsonDocument("text" -> "Flood in Boulder!", "timestamp" -> BsonDateTime(0), "location" -> BsonDocument("type" -> "Point", "coordinates" -> BsonArray(BsonDouble(-105.27), BsonDouble(40.01))),
        "entities" -> BsonDocument("hashtags" -> BsonArray(BsonDocument("text" -> "boulder")))),
      BsonDocument("text" -> "No issue here", "timestamp" -> BsonDateTime(0), "location" -> BsonDocument("type" -> "Point", "coordinates" -> BsonArray(BsonDouble(-105.27), BsonDouble(40.01))))
    )

  // convert BsonDocument to org.mongodb.scala.Document
  val docsConv = docs.map(d => org.mongodb.scala.Document(d.toJson()))
  Await.result(coll.insertMany(docsConv).toFuture(), 5.seconds)

    // Call the method
    val count = WordFreqCalculator.countOccurrences(mongoUri, "test_db_for_counts", "tweets_test",
      "boulder", 5000, -105.27, 40.01, 0L, 9999999999L, WordFreqCalculator.CountOptions(includeHashtags = true, regex = false, preview = 0))

  // one occurrence in text and one in hashtags => 2
  assertEquals(count, 2L)

    Await.result(coll.drop().toFuture(), 5.seconds)
    client.close()
  }
}
