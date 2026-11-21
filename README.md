# Nimrod

A voice-activated AI assistant for Android. Say the wake word, ask a question out
loud, and Nimrod answers out loud while remembering what you have talked about and
which subject each conversation belonged to.

## What it does

1. **Wake word** — Picovoice Porcupine listens in the background for the trigger word.
2. **Speech to text** — Android's `SpeechRecognizer` captures the utterance.
3. **Topic routing** — `TopicManager` scores the utterance against keyword sets and
   files it under `legal`, `finance`, `development`, or `general`.
4. **Memory** — `MemoryManager` merges the recent conversation with the history of
   the matching topic and injects it into the system prompt.
5. **Answer** — `OpenAIClient` calls `gpt-4o-mini` and returns a spoken-style reply.
6. **Text to speech** — the reply is spoken, and both turns are persisted in Room.

Everything is stored locally in SQLite. Only the prompt and the memory block are sent
to OpenAI.

## Project layout

```
Nimrod/
├── settings.gradle
├── build.gradle                     (project)
├── local.properties.example         keys template - copy to local.properties
├── app/
│   ├── build.gradle                 (module: AGP 8.1.4, Kotlin 1.9.0, SDK 34)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  mic + internet permissions
│       │   ├── java/com/example/nimrod/
│       │   │   ├── MainActivity.kt      the voice loop
│       │   │   ├── OpenAIClient.kt      chat completions over OkHttp
│       │   │   ├── audio/
│       │   │   │   └── WakeWordManager.kt   Porcupine wake word
│       │   │   ├── data/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── MessageEntity.kt
│       │   │   │   ├── MessageDao.kt
│       │   │   │   ├── TopicEntity.kt
│       │   │   │   └── TopicDao.kt
│       │   │   ├── memory/
│       │   │   │   └── MemoryManager.kt     topic-aware context building
│       │   │   └── topic/
│       │   │       └── TopicManager.kt      keyword classifier
│       │   └── res/
│       │       ├── layout/activity_main.xml
│       │       ├── values/strings.xml
│       │       ├── values/themes.xml
│       │       └── raw/                     put nimrod.ppn here
│       └── test/java/com/example/nimrod/topic/TopicManagerTest.kt
└── gradle/
```

## Setup

1. Copy the keys template:

   ```bash
   cp local.properties.example local.properties
   ```

2. Fill in `local.properties` (it is gitignored, never committed):

   | Key | Where to get it |
   | --- | --- |
   | `sdk.dir` | your Android SDK path |
   | `OPENAI_API_KEY` | platform.openai.com → API keys |
   | `PICOVOICE_ACCESS_KEY` | console.picovoice.ai → AccessKey |

   Both keys are surfaced through `BuildConfig`, so no secret ever lives in source.

3. (Optional) Train a custom **Nimrod** wake word in the Picovoice console, download
   the Android `.ppn`, and drop it in `app/src/main/res/raw/nimrod.ppn`. Without it
   the app falls back to Porcupine's built-in `JARVIS` keyword so it still runs.

4. Build:

   ```bash
   ./gradlew assembleDebug
   # or
   ./gradlew installDebug
   ```

5. Run the classifier tests:

   ```bash
   ./gradlew test
   ```

## Data model

`messages` stores every turn (`content`, `role`, `timestamp`, `topicId`).
`topics` stores each topic name with a `lastActive` stamp, and messages hold a
foreign key to it that nulls out on delete instead of destroying history.

`MemoryManager.buildContext()` takes the last 12 turns plus up to 12 turns from the
current topic, de-duplicates them, and orders them oldest to newest before handing
them to the model.

## Notes on this commit

The Gradle files, `MainActivity`, `OpenAIClient.sendMessage`, `MessageEntity` and the
Room database wiring come from the code you supplied. `MessageDao`, `TopicDao`,
`TopicEntity`, `MemoryManager`, `TopicManager`, `WakeWordManager`, the manifest, the
layout and the tests are written out here to make the project compile and run end to
end. If your local copy has moved on since then, push it over this commit.
