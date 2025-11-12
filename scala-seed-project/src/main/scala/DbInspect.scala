import org.mongodb.scala._
import org.mongodb.scala.bson._
import scala.concurrent.Await
import scala.concurrent.duration._

object DbInspect {
  def main(args: Array[String]): Unit = {
    //  simple DB inspector
    val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = if (args.length >= 1) args(0) else sys.env.getOrElse("MONGO_DB", "twitterDB")
    val collName = if (args.length >= 2) args(1) else sys.env.getOrElse("MONGO_COLLECTION", "tweets")

    val client: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = client.getDatabase(dbName)
    val collection: MongoCollection[Document] = database.getCollection(collName)

    try {
      val countF = collection.countDocuments().toFuture()
      val count = Await.result(countF, 10.seconds)
      println(s"Collection $dbName.$collName - count = $count")

      val sampleF = collection.find().first().toFuture()
      val sampleOpt = try Some(Await.result(sampleF, 10.seconds)) catch { case _: Throwable => None }
      sampleOpt match {
        case Some(doc) =>
          println("\nSample document (pretty JSON):")
          println(doc.toJson())

          // show presence of timestamp/location
          if (doc.contains("timestamp")) println(s"Found 'timestamp'") else println("No 'timestamp' in sample")
          if (doc.contains("location")) println(s"Found 'location'") else println("No 'location' in sample")
        case None => println("No sample document returned (collection may be empty)")
      }

      val idxF = collection.listIndexes().toFuture()
      val idxs = Await.result(idxF, 10.seconds)
      println("\nIndexes:")
      idxs.foreach(d => println(d.toJson()))

    } finally {
      client.close()
    }
  }
}
