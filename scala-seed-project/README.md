# TweetAnalyzer (Scala)

This small project provides two utilities:

- `InsertTweets.scala` — reads JSON-formatted tweets and inserts them into a MongoDB collection using the MongoSpark connector. It also creates an index on the `timestamp` field and a `2dsphere` index on `location`.
- `WordFreqCalculator.scala` — counts occurrences of a word inside a circular geographic region (radius in meters) and inside a time interval.

Prerequisites
- Java + sbt
- A running MongoDB instance. By default the code uses `mongodb://localhost:27017` and database `tweetsDB`, collection `tweets`. You can override those using environment variables `MONGO_URI`, `MONGO_DB`, and `MONGO_COLLECTION`.

Build

Run with sbt. From the project root:

```powershell
sbt compile
```

Insert tweets from JSON into MongoDB

```powershell
# single file (or glob), optional mongoUri, db, collection
sbt "runMain InsertTweets path\to\tweets.json mongodb://localhost:27017 tweetsDB tweets"
```

Word frequency query

```powershell
# Usage:
# sbt "runMain WordFreqCalculator w r lon lat start end"
# example: count occurrences of word 'flood' within 1000m of (-105.27,40.01)
sbt "runMain WordFreqCalculator flood 1000 -105.27 40.01 1420070400 1420156800"
```

Notes & assumptions
- The inserter attempts to normalize common timestamp fields such as `timestamp_ms` and `timestamp` and creates a `timestamp` Date field in MongoDB.
- The inserter tries to normalize coordinate fields (`coordinates.coordinates`, `geo.coordinates`) into a GeoJSON `location` field. The inserted collection has a `2dsphere` index on `location` created automatically.
- The query uses a MongoDB aggregation pipeline with `$geoNear` + aggregation expressions to clean text, split tokens, and count exact matches (case-insensitive). The pipeline uses operators available on MongoDB 4.4+ (e.g. `$regexReplace`).

Improvements added
- Word/token counting now supports counting occurrences in `text` and in `entities.hashtags`.
- You can enable regex matching and configure whether hashtags are counted through environment variables when running the CLI:
	- `INCLUDE_HASHTAGS=true|false` (default true)
	- `REGEX=true|false` (default false)
	- `PREVIEW=N` (not used heavily yet)

Automated test
- A munit integration test `WordFreqCalculatorSpec` was added in `src/test/scala` that inserts sample documents into a temporary test DB and validates the counting logic. Run tests with:

```powershell
sbt test
```

Terminal usage
- The project ships command-line utilities and tests. Use the included CLI programs directly from sbt or package them. Example usage:

```powershell
# Insert tweets from JSON into MongoDB (example)
sbt "runMain InsertTweets path\to\tweets.json mongodb://localhost:27017 tweetsDB tweets"

# Word frequency query (CLI):
# sbt "runMain WordFreqCalculator w r lon lat start end"
# example: count occurrences of word 'flood' within 1000m of (-105.27,40.01)
sbt "runMain WordFreqCalculator flood 1000 -105.27 40.01 1420070400 1420156800"
```

The project is terminal-first: a previous web UI was removed. Use the CLI tools below from the terminal.
Final notes
- The migration program `MigrateTweets` normalized the existing collection and created `timestamp` and `location` indexes.
- If you want more visual polish, we can replace the static UI with a React/Vite app and add charts (e.g., Chart.js) and downloadable PDF reports. Tell me which style you prefer and I will scaffold it.