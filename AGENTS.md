# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

**Spam Guard** is a single-module Android app (Kotlin + Jetpack Compose, Material 3)
that filters spam SMS using user-defined rules. Each rule holds one or more
conditions (plain text / sender / regex) combined with AND or OR logic. Matching
messages are quarantined inside the app and can be restored, marked not-spam, or
deleted.

- Module: `:app` (single Gradle module)
- Package / `applicationId`: `com.smsspam.detector`
- minSdk 26 · target/compileSdk 37 · JDK 17
- No Hilt, no Room, no XML layouts — see Conventions.

## Build & test

Requires JDK 17+ and an Android SDK. Point Gradle at the SDK once:

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
```

Common commands (always run from the repo root):

```bash
./gradlew :app:assembleDebug        # build the debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests (the rule engine lives here)
./gradlew :app:compileDebugKotlin   # fast compile check
```

- `local.properties` is git-ignored and must never be committed.
- Tests are plain JUnit4 in `app/src/test/`. There is no instrumented (androidTest)
  suite yet. Add unit tests for `RuleEngine` and model (de)serialization logic.
- Keep `RuleEngine` pure and side-effect free so it stays trivially testable.

## Architecture

```
app/src/main/java/com/smsspam/detector/
├── MainActivity.kt            entry point, edge-to-edge Compose host
├── SpamGuardApp.kt            Application + notification channels + AppContainer (manual DI)
├── model/                     Rule, Condition, QuarantineMessage (+ org.json serialization)
├── engine/RuleEngine.kt       pure matching + AND/OR evaluation
├── data/                      repositories backed by JsonListStore, exposed as StateFlow
├── sms/                       SmsReceiver, MmsReceiver, quick-reply service, compose
│                              activity, inbox read/delete/restore
├── notify/                    Notifier (spam + new-message notifications)
└── ui/                        AppRoot nav host, AppViewModel, screens, components, theme
```

Data flow: `SmsReceiver` evaluates `RuleEngine.firstMatch` against
`ruleRepository.rules.value`; matches go to `MessageRepository`; both repositories
expose `StateFlow` so the Compose UI updates live even while the receiver acts in
the background.

## Conventions

- **Compose only.** UI is 100% Jetpack Compose / Material 3. `res/` holds only
  values, drawables, mipmaps and XML rules — do not add layout XML.
- **Manual DI.** Everything hangs off `AppContainer` (created in `SpamGuardApp`).
  Do not introduce Hilt/Koin; pass `AppViewModel` down from `AppRoot`.
- **Persistence = `JsonListStore`.** Rules and messages are stored as JSON arrays
  in `SharedPreferences`, not Room/SQLite. New persisted models implement
  `toJson()` / `fromJson()` with `org.json`, tolerate missing keys
  (`optString`/`optBoolean` with defaults), and generate a `UUID` id when absent.
- **Repositories** wrap a `JsonListStore`, hold a `MutableStateFlow`, and expose an
  `asStateFlow()`. Mutating methods are `@Synchronized` and write through to disk.
  Keep the StateFlow as the single source of truth.
- **One-off UI messages** use the `UiEvent` sealed interface surfaced via
  `AppViewModel.events` and shown as snackbars (see `AppRoot`). Call
  `consumeEvent()` after displaying.
- **Style:** Kotlin official code style (`.editorconfig`-free; 4-space indent),
  trailing commas, KDoc on non-obvious public types/functions. Labels for enums
  used in the UI live on the enum itself (e.g. `MatchType.label`).

## Domain rules to respect

- **Default-SMS-app role matters.** Since Android 4.4 only the default SMS app may
  write to / delete from the system SMS provider. `SmsReceiver` handles both
  `SMS_DELIVER` (authoritative when we hold the role) and `SMS_RECEIVED`
  (best-effort otherwise). Never process the same message twice: when we are the
  default app and action is `SMS_RECEIVED`, return early.
- **Never lose a message.** When holding the default role, non-spam messages must
  be persisted to the system inbox (`SmsInbox.insertIncoming`), and spam must be
  kept in the app quarantine. Guard inserts with `SmsInbox.exists` to avoid dupes.
- **Multipart SMS** arrive as several PDUs from the same sender — group parts by
  sender and concatenate bodies before matching.
- **Dedupe** quarantined messages via `QuarantineMessage.dedupeKey` in
  `MessageRepository.add` / `addAll`.
- The Persian opt-out preset (`لغو11`) is intentionally enabled by default; keep
  `RuleRepository.defaultRules()` and its tests consistent.
- MMS content is **not** downloaded; the WAP-push receiver exists only to satisfy
  the default-SMS-app role requirement.

## Workflow

- Commit style: short imperative summaries (e.g. `Add inbox scan result state`).
- Run `:app:testDebugUnitTest` before proposing changes that touch the engine,
  models or repositories.
- Do not commit build output, `.gradle/`, `.kotlin/`, `local.properties`, or IDE
  files — they are covered by `.gitignore`.
