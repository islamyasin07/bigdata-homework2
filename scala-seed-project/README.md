# TweetAnalyzer — Command-line Tweet Analysis (Scala + MongoDB)

This repository provides a compact, terminal-first toolkit to ingest JSON-formatted tweets into MongoDB, normalize timestamps and geolocation, and run accurate spatial + temporal word-frequency queries. It's designed for grading and reproducible runs without a web UI.

**Highlights**
- Ingest JSON tweets into a MongoDB collection named `tweets` with `timestamp` stored as BSON `Date` and `location` as GeoJSON `Point`.
- Create performant indexes: ascending `timestamp` and `2dsphere` `location`.
- Command-line utilities for inspection, migration, spatial counts and word-frequency analysis.
- Unit tests validate the core counting logic.

**Contents**
- `src/main/scala/InsertTweets.scala` — ingest JSON (Spark), normalize fields, write to MongoDB and create indexes.
- `src/main/scala/MigrateTweets.scala` — fill missing `timestamp` / `location` for existing documents.
- `src/main/scala/DbInspect.scala` — show collection count, a sample document and indexes.
- `src/main/scala/RegionCount.scala` — count documents inside a circular region + time interval.
- `src/main/scala/WordFreqCalculator.scala` — count word occurrences (text + hashtags) within region + time window.
- `src/test/scala/WordFreqCalculatorSpec.scala` — unit tests for counting logic.

**Requirements**
- Java 8+ and `sbt` installed.
- A running MongoDB instance (default `mongodb://localhost:27017`).

**Environment variables**
- `MONGO_URI` — MongoDB connection string (default: `mongodb://localhost:27017`).
- `MONGO_DB` — database name (default: `twitterDB`).
- `MONGO_COLLECTION` — collection name (default: `tweets`).
- `INCLUDE_HASHTAGS` — `true|false` (default: `true`). Controls whether hashtags are counted.
- `REGEX` — `true|false` (default: `false`). If `true`, interprets the word argument as a regular expression.
- `MATCH_TYPE` — `exact|substring` (default: `exact`). Determines token matching behavior.

**Build & run (quick)**
From the repository root:

```powershell
cd scala-seed-project
sbt test               # compile and run unit tests
```

To run any CLI utility use `sbt "runMain <MainClass> <args...>"`.

Examples

- Inspect the database (count, sample document, indexes):

```powershell
sbt "runMain DbInspect"
```

- Count documents in a circular region (lon lat radius_m start_epoch end_epoch):

```powershell
sbt "runMain RegionCount -118.10041174 34.14628356 100 1388462062 1388548462"
# Example output: Documents in region/time: 1
```

- Count occurrences of a word (word radius_m lon lat start_epoch end_epoch):

```powershell
sbt "runMain WordFreqCalculator drunk 100 -118.10041174 34.14628356 1388462062 1388548462"
# Example output: Total occurrences of 'drunk' = 2
```

- Broader example (used in unit tests):

```powershell
sbt "runMain WordFreqCalculator boulder 5000 -105.27 40.01 0 9999999999"
# Example output: Total occurrences of 'boulder' = 2202
```

Expected results for the example runs
- `DbInspect` prints the collection count (e.g., `18821`), a sample tweet and the index list containing `_id_`, `timestamp_1`, and `location_2dsphere`.
- `RegionCount` returns a small integer matching the number of documents within the specified circle and time window.
- `WordFreqCalculator` returns the total occurrences of the queried token across tweet text and hashtags (value depends on DB contents). Example runs above correspond to data in the included dataset.

Behavior & implementation notes
- `InsertTweets` converts `timestamp_ms` / `created_at` into a BSON Date field named `timestamp` and normalizes coordinates into `location: { type: "Point", coordinates: [lon, lat] }`.
- Spatial queries use MongoDB `$geoWithin` with `$centerSphere` (radius meters converted to radians using earth radius 6,378,100 m).
- Word counting is performed client-side after the geo+time filter; it normalizes text (NFKC) and applies exact, substring or regex matching and can include hashtags.

Testing

```powershell
cd scala-seed-project
sbt test
```

Troubleshooting
- If the repository appears large, build artifacts can be removed safely:

```powershell
Remove-Item -LiteralPath 'scala-seed-project\\target' -Recurse -Force
```

- If MongoDB is remote or uses different names, set `MONGO_URI`, `MONGO_DB` and `MONGO_COLLECTION` before running commands.

Packaging for submission
- The repository is ready for submission. For a compact package, exclude build caches (`target`, `.bloop`, `.metals`) when creating the zip.

Contact
- If you want additional runner scripts, a submission archive, or server-side aggregation for larger datasets, request the change and it will be added.

$env:MONGO_COLLECTION = "tweets"
```

Build

From the project root:

```powershell
sbt compile
```

Run examples (PowerShell)

- Insert tweets from JSON

```powershell
# Insert a JSON file into MongoDB (path may be a glob)
sbt "runMain InsertTweets C:\path\to\tweets.json mongodb://localhost:27017 twitterDB tweets"
```

- Count occurrences of a word in a region and time interval

```powershell
# Usage: sbt "runMain WordFreqCalculator <word> <radius_m> <lon> <lat> <start_epoch_sec> <end_epoch_sec>"
# Example: count 'flood' within 1000m of (-105.27,40.01) between 2015-01-01 and 2015-01-02 (epoch secs shown below)
sbt "runMain WordFreqCalculator flood 1000 -105.27 40.01 1420070400 1420156800"
```

- Count documents in a region/time (no text matching)

```powershell
# Usage: sbt "runMain RegionCount <lon> <lat> <radius_m> <start_epoch_sec> <end_epoch_sec>"
sbt "runMain RegionCount -118.10041174 34.14628356 1000 1388448000 1388534400"
```

Useful environment flags for CLI runs

- `INCLUDE_HASHTAGS=true|false` — whether hashtags are counted (default: true)
- `REGEX=true|false` — enable regex matching (default: false)
- `MATCH_TYPE=exact|substring` — match behavior for non-regex mode (default: exact)

Testing

Run unit/integration tests:

```powershell
sbt test
```

Submission notes

- The project is configured to be terminal-first. The grader can run `sbt test` and the example CLI commands above. The included `DbInspect` confirms the dataset and indexes.
- Files included for submission: `src/main/scala`, `src/test/scala`, `build.sbt`, `project/`, `README.md`, `.gitignore`.

Troubleshooting

- If CLI tools can't find MongoDB, verify `MONGO_URI` and that the MongoDB service is running and accessible.
- If counts are unexpectedly zero, run `DbInspect` to confirm the collection and sample documents:

```powershell
sbt "runMain DbInspect"
```

Contact / Notes

If you want, I can:

- create a small PowerShell helper script to run common queries,
- create a single `Compress-Archive` zip for submission, or
- push this repository to the remote (I can do that now).

---
Short changelog

- Removed web UI and server code to keep the project terminal-first.
- Added CLI utilities: `InsertTweets`, `MigrateTweets`, `WordFreqCalculator`, `RegionCount`, `DbInspect`.
- Added tests and updated `.gitignore`.

---
Good luck with the submission — tell me if you want me to push the README commit now.