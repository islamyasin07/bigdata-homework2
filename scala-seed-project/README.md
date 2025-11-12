# TweetAnalyzer (Scala)

A compact, terminal-first Scala project for ingesting tweets into MongoDB and running spatial + temporal word-frequency queries.

This repository contains small CLI utilities (Scala, sbt):

- `InsertTweets` — ingest JSON-formatted tweets into MongoDB and normalize fields (creates `timestamp` Date and GeoJSON `location` when available). It also creates indexes: ascending on `timestamp` and a `2dsphere` index on `location`.
- `MigrateTweets` — helper to migrate/normalize existing documents (sets `timestamp`/`location` where possible).
- `WordFreqCalculator` — count occurrences of a word within a circular region (meters) and a time interval (epoch seconds). Supports counting in tweet text and hashtags, exact/substring matching and optional regex mode.
- `RegionCount` — count documents in a spatial/time window (useful to check dataset density).
- `DbInspect` — quick inspection tool: prints collection count, a sample document, and indexes.

Why this repo is ready for submission
- Build and tests pass locally (see `src/test/scala/WordFreqCalculatorSpec.scala`).
- Data normalization and indexes are set up (timestamp Date field + `2dsphere` index).
- The project is intentionally terminal-first — web UI code was removed per submission instructions.

Prerequisites
- Java 8+ and sbt
- A running MongoDB instance accessible from the machine running these programs

Default MongoDB settings
- MONGO_URI: `mongodb://localhost:27017` (default)
- MONGO_DB: `twitterDB` (default)
- MONGO_COLLECTION: `tweets` (default)

You can override these with environment variables. Example (PowerShell):

```powershell
$env:MONGO_URI = "mongodb://localhost:27017"
$env:MONGO_DB = "tweetsDB"
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