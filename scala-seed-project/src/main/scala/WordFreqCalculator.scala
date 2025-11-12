import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document

import scala.concurrent.Await
import scala.concurrent.duration._
import org.mongodb.scala.bson.{BsonArray, BsonDouble}

object WordFreqCalculator {
  case class CountOptions(includeHashtags: Boolean = true, regex: Boolean = false, preview: Int = 0, matchType: String = "exact")

  // b1: helpers
  private def bsonToStringValue(bv: org.mongodb.scala.bson.BsonValue): String = try bv.asString().getValue catch { case _: Throwable => bv.toString }
  private def bsonToStringOpt(opt: Option[org.mongodb.scala.bson.BsonValue]): String = opt.map(bsonToStringValue).getOrElse("")
  private def bsonToStringFromAny(v: Any): String = v match { case null => ""; case bv: org.mongodb.scala.bson.BsonValue => bsonToStringValue(bv); case other => other.toString }

  def countOccurrences(mongoUri: String, dbName: String, collName: String,
                       word: String, radiusMeters: Double, lon: Double, lat: Double,
                       startEpochSec: Long, endEpochSec: Long, opts: CountOptions): Long = {
    val client: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = client.getDatabase(dbName)
    val collection: MongoCollection[Document] = database.getCollection(collName)

    val earth = 6378100.0
    val rad = radiusMeters / earth
    val centerSphere = BsonArray(BsonArray(BsonDouble(lon), BsonDouble(lat)), BsonDouble(rad))
    val filter = Document("$and" -> Seq(
      Document("location" -> Document("$geoWithin" -> Document("$centerSphere" -> centerSphere))),
      Document("timestamp" -> Document("$gte" -> new java.util.Date(startEpochSec * 1000L), "$lte" -> new java.util.Date(endEpochSec * 1000L)))
    ))

  //prepare filter and project text/hashtags

    try {
      val docsF = collection.find(filter).projection(Document("text" -> 1, "entities.hashtags.text" -> 1)).toFuture()
      val docs = Await.result(docsF, 120.seconds)

        val normalizedWord = java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFKC).toLowerCase
        val total = docs.foldLeft(0L) { (acc, doc) =>
          var localCount = 0
          if (doc.contains("text")) {
            val raw = bsonToStringOpt(doc.get("text"))
            val text = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).toLowerCase
            if (opts.regex) {
              try {
                val pattern = normalizedWord.r
                localCount += pattern.findAllIn(text).length
              } catch { case _: Throwable => }
            } else {
              // tokenize
              val tokens = text.replaceAll("[^\\p{L}\\p{N}]+", " ").split(" ").filter(_.nonEmpty)
              opts.matchType match {
                case "exact" => localCount += tokens.count(_ == normalizedWord)
                case "substring" => localCount += tokens.count(_.contains(normalizedWord))
                case _ => localCount += tokens.count(_ == normalizedWord)
              }
            }
          }
        if (opts.includeHashtags && doc.contains("entities")) {
            try {
              val ent = doc.get("entities").get.asDocument()
            if (ent.contains("hashtags")) {
              val arr = ent.getArray("hashtags")
              (0 until arr.size()).foreach { i =>
                val h = try { arr.get(i).asDocument().getString("text").getValue } catch { case _: Throwable => bsonToStringFromAny(arr.get(i).asDocument().get("text")) }
                val hn = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFKC).toLowerCase
                if (opts.regex) {
                  try { if (normalizedWord.r.findAllIn(hn).nonEmpty) localCount += 1 } catch { case _: Throwable => }
                } else {
                  opts.matchType match {
                    case "exact" => if (hn == normalizedWord) localCount += 1
                    case "substring" => if (hn.contains(normalizedWord)) localCount += 1
                    case _ => if (hn == normalizedWord) localCount += 1
                  }
                }
              }
            }
          } catch { case _: Throwable => }
        }
        acc + localCount
      }
      total
    } finally {
      client.close()
    }
  }

  def timeSeriesByDay(mongoUri: String, dbName: String, collName: String,
                      word: String, radiusMeters: Double, lon: Double, lat: Double,
                      startEpochSec: Long, endEpochSec: Long, opts: CountOptions): Seq[(String, Long)] = {
    val client: MongoClient = MongoClient(mongoUri)
    val database: MongoDatabase = client.getDatabase(dbName)
    val collection: MongoCollection[Document] = database.getCollection(collName)

    val earth = 6378100.0
    val rad = radiusMeters / earth
    val centerSphere = BsonArray(BsonArray(BsonDouble(lon), BsonDouble(lat)), BsonDouble(rad))
    val matchDoc = Document("$and" -> Seq(
      Document("location" -> Document("$geoWithin" -> Document("$centerSphere" -> centerSphere))),
      Document("timestamp" -> Document("$gte" -> new java.util.Date(startEpochSec * 1000L), "$lte" -> new java.util.Date(endEpochSec * 1000L)))
    ))

  //aggregate by day for timeseries
    val projectDay = Document("$project" -> Document("day" -> Document("$dateToString" -> Document("format" -> "%Y-%m-%d", "date" -> "$timestamp")), "text" -> 1, "entities.hashtags.text" -> 1))
    val group = Document("$group" -> Document("_id" -> "$day", "docs" -> Document("$push" -> Document("text" -> "$text", "hashtags" -> "$entities.hashtags"))))
    val pipeline = Seq(Document("$match" -> matchDoc), projectDay, group)

    try {
      val agg = collection.aggregate(pipeline).toFuture()
      val groups = Await.result(agg, 120.seconds)
      val results = groups.map { g =>
        val day = g.getString("_id")
        val docsArray = g.get("docs").map(_.asArray()).getOrElse(org.mongodb.scala.bson.BsonArray())
  // count occurrences in this day's docs
  var dayCount = 0L
        (0 until docsArray.size()).foreach { i =>
          val d = docsArray.get(i).asDocument()
          // text
          if (d.contains("text")) {
            val raw = bsonToStringFromAny(d.get("text"))
            val text = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).toLowerCase
            if (opts.regex) {
              try { dayCount += normalizedRegexCount(opts, text) } catch { case _: Throwable => }
            } else {
              val tokens = text.replaceAll("[^\\p{L}\\p{N}]+", " ").split(" ").filter(_.nonEmpty)
              opts.matchType match {
                case "exact" => dayCount += tokens.count(_ == word.toLowerCase)
                case "substring" => dayCount += tokens.count(_.contains(word.toLowerCase))
                case _ => dayCount += tokens.count(_ == word.toLowerCase)
              }
            }
          }
          // hashtags
          if (opts.includeHashtags && d.contains("hashtags")) {
            try {
              val arr = d.getArray("hashtags")
              (0 until arr.size()).foreach { j =>
                val h = try { arr.get(j).asDocument().getString("text").getValue } catch { case _: Throwable => bsonToStringFromAny(arr.get(j).asDocument().get("text")) }
                val hn = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFKC).toLowerCase
                if (opts.regex) { try { if (word.toLowerCase.r.findAllIn(hn).nonEmpty) dayCount += 1 } catch { case _: Throwable => } }
                else opts.matchType match { case "exact" => if (hn == word.toLowerCase) dayCount += 1; case "substring" => if (hn.contains(word.toLowerCase)) dayCount += 1; case _ => if (hn == word.toLowerCase) dayCount += 1 }
              }
            } catch { case _: Throwable => }
          }
        }
        (day, dayCount)
      }
      // sort by day
      client.close()
      results.sortBy(_._1)
    } finally {
      client.close()
    }
  }

  // b1: placeholder
  private def normalizedRegexCount(opts: CountOptions, text: String): Int = 0

  def main(args: Array[String]): Unit = {
    if (args.length != 6) {
      System.err.println("Usage: WordFreqCalculator.scala w r lon lat start end")
      System.exit(1)
    }
    val word = args(0)
    val radiusMeters = args(1).toDouble
    val lon = args(2).toDouble
    val lat = args(3).toDouble
    val startEpochSec = args(4).toLong
    val endEpochSec = args(5).toLong

    val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = sys.env.getOrElse("MONGO_DB", "twitterDB")
    val collName = sys.env.getOrElse("MONGO_COLLECTION", "tweets")

  val includeHashtags = sys.env.getOrElse("INCLUDE_HASHTAGS", "true").toLowerCase == "true"
  val regex = sys.env.getOrElse("REGEX", "false").toLowerCase == "true"
  val preview = sys.env.getOrElse("PREVIEW", "0").toInt
  val matchType = sys.env.getOrElse("MATCH_TYPE", "exact")

  val opts = CountOptions(includeHashtags = includeHashtags, regex = regex, preview = preview, matchType = matchType)
    val total = countOccurrences(mongoUri, dbName, collName, word, radiusMeters, lon, lat, startEpochSec, endEpochSec, opts)
    println(s"Total occurrences of '$word' = $total")
  }
}
