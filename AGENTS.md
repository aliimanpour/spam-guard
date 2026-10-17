# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

**Spam Guard** is a single-module Android app (Kotlin + Jetpack Compose, Material 3)
that filters spam SMS using user-defined rules. Each rule holds one or more
conditions (plain text / sender / regex) combined with AND or OR logic. Matching
messages are quarantined inside the app and can be restored, marked not-spam, or
deleted. It is also a full SMS client: conversations, compose, multi-select,
scheduled sends shown inline, and delivery receipts (two ticks).

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
  suite yet. Add unit tests for `RuleEngine`, model (de)serialization, and the
  pure helpers in `SmsThreads` (e.g. `SmsMessage.statusFor` delivery mapping).
- Keep `RuleEngine` pure and side-effect free so it stays trivially testable.

## Architecture

```
app/src/main/java/com/smsspam/detector/
├── MainActivity.kt            entry point, edge-to-edge Compose host
├── SpamGuardApp.kt            Application + notification channels + AppContainer (manual DI)
├── model/                     Rule, Condition, QuarantineMessage (+ org.json serialization)
├── engine/RuleEngine.kt       pure matching; allow-rules are checked before block-rules
├── data/                      repositories backed by JsonListStore, exposed as StateFlow
│                              (+ small prefs stores: UnreadStore badge stamp, SettingsStore
│                              auto-unsubscribe + delivery reports, SimStore per-conversation
│                              SIM, UnsubscribeLog)
├── sms/                       SmsReceiver, MmsReceiver, SmsStatusReceiver (delivery
│                              reports), quick-reply service, compose activity,
│                              SmsSender, inbox read/delete/restore, thread cache
├── notify/                    Notifier (new-message + scheduled-send notifications)
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
- **Conversations are cached and paged, never bulk-scanned.** The conversation
  list comes from the provider's precomputed threads table
  (`SmsThreads.readConversations`); a thread's messages load one
  `SmsThreads.PAGE_SIZE` window at a time (`SmsThreads.readThreadPage`, keyed
  by indexed `thread_id`), and the newest page of recently opened threads lives
  in the bounded LRU `ThreadCache`. Rows of the conversation list also
  prefetch their newest page into that same `ThreadCache` while the user
  scrolls (`AppViewModel.prefetchConversation`, one worker, most-recent-first),
  so opening a conversation paints instantly. `AppViewModel` also watches the
  provider with a debounced `ContentObserver` and serves resume refreshes from
  the cache within a freshness window. Do not reintroduce full-inbox scans or
  `address`-based queries (no index → full table scan) for these screens.
- **Scheduled messages live inside their conversation.** There is no separate
  "Scheduled" screen. `ConversationScreen` merges `viewModel.scheduled` — only
  `PENDING`/`FAILED` entries, matched to the open thread by
  `AppViewModel.scheduledFor` — into the message list as distinct
  `ChatItem.Scheduled` balloons with Cancel (pending) or Retry/Delete (failed).
  A sent entry disappears because the real provider row then represents it. New
  sends are still queued from the composer's clock button (`SmsAlarmScheduler`).
- **Delivery reports & ticks.** Outgoing sends request a delivery report (the
  Settings "Read receipts" toggle, on by default; `SmsSender` reads
  `SettingsStore.deliveryReports`). `SmsStatusReceiver` writes
  `STATUS_COMPLETE`/`STATUS_FAILED` back onto the outgoing provider row, and
  `SendStatus` renders clock → single ✓ → double ✓✓ (`DoneAll`) → error. Keep
  `SmsMessage.statusFor(type, providerStatus)` the single mapping used by
  `SmsThreads.readThreadPage`.
- **Conversation gestures & multi-select.** Tapping a bubble opens its action
  menu (copy/forward/share/retry/delete); **long-pressing** enters selection
  mode with that message chosen. In selection mode taps toggle and the top bar
  does Copy/Share/Delete through `AppViewModel.deleteMessages` (provider rows
  and pending stubs). After a send, the thread stays pinned to the newest
  balloon (`jumpToLatest` + `listState.scrollToItem`) until the in-flight stub
  is replaced by the real row.
- **Blocked messages are visible inside their conversation.** `ConversationScreen`
  also merges `viewModel.messages` (quarantine) for the open sender as
  `ChatItem.Quarantined` balloons (`QuarantinedBubble`, error-tinted, shows the
  matching rule). They were removed from the provider, so this is why opening a
  blocked message from the Blocked tab is no longer empty. A quarantine copy is
  skipped when an identical row is still in the provider (role not held) to
  avoid a duplicate.
- **Tapping the conversation header calls the contact.** The title row in
  `ConversationTopBar` is clickable and fires the same `ACTION_DIAL` as the
  overflow "Call" item (disabled for a brand-new compose). The composer shows
  the system **Enter** key (`ImeAction.Default`) so drafts can span lines; sends
  come from the send button, not the keyboard action.
- **Persistence = `JsonListStore`.** Rules and messages are stored as JSON arrays
  in `SharedPreferences`, not Room/SQLite. New persisted models implement
  `toJson()` / `fromJson()` with `org.json`, tolerate missing keys
  (`optString`/`optBoolean` with defaults), and generate a `UUID` id when absent.
  Single-value state (badge stamp, auto-unsubscribe and delivery-report toggles,
  per-conversation SIM) lives in tiny prefs-backed stores under `data/`.
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

- **Blocked messages are silent.** Never post a notification for quarantined
  SMS; the Blocked tab icon carries the unread badge instead
  (`AppViewModel.newBlockedCount`, reset when the user exits the app via
  `MainActivity.onStop` → `markBlockedSeen`, and when the Blocked tab opens).
- **Verification-code (OTP) messages are never blocked.** This lives in the
  `preset-verification` **allow rule** (`RuleAction.ALLOW`), not as a hardcoded
  engine override — users can see, edit or disable it in the rules UI.
  `RuleEngine.firstMatch` evaluates allow-rules before block-rules. When adding
  new presets, bump `RuleRepository.PRESET_VERSION` so stored lists pick them
  up on upgrade; keep the patterns conservative — a blocked OTP is worse than
  rare spam slipping through.
- **Auto unsubscribe sends SMS.** When the user enables it (Settings → Auto
  unsubscribe), a *blocked* message that carries a لغو hint gets the
  unsubscribe code ("11"/"12" or the code the ad itself mentions) replied via
  `AutoUnsubscriber`, at most once per sender (`UnsubscribeLog`). Never send
  for messages without a لغو hint; never fire from inbox scans.
- **SIM-aware sends.** Outgoing SMS go through `SmsSender.send(..., subId)`;
  an inactive/unknown subId falls back to the default SIM. The per-conversation
  choice is remembered in `SimStore`; scheduled messages persist their subId.
- **Delivery reports update the provider row, never a private store.** Every
  outgoing send requests a report (respect `SettingsStore.deliveryReports`,
  default on) and records `STATUS_COMPLETE`/`STATUS_FAILED` on the row it
  inserted, so ticks survive restarts and stay in sync with the system app. The
  row id must be known before `sendTextMessage`, so `SmsSender` inserts first.
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
- The Persian opt-out preset (`لغو11`) is intentionally enabled by default and now
  matches Persian/Arabic-Indic digits too
  (`لغو[\s\u200c]*[0-9۰-۹٠-٩]{1,2}`), alongside the enabled conservative
  `preset-fa-promo` scam/promo rule. Keep `RuleRepository.defaultRules()` and its
  tests consistent. Bump `PRESET_VERSION` when preset definitions change;
  `mergePresets` refreshes existing built-in presets to the latest definition
  while preserving each rule's enabled/disabled choice.
- MMS content is **not** downloaded; the WAP-push receiver exists only to satisfy
  the default-SMS-app role requirement.

## Workflow

- Commit style: short imperative summaries (e.g. `Add inbox scan result state`).
- Run `:app:testDebugUnitTest` before proposing changes that touch the engine,
  models or repositories.
- Do not commit build output, `.gradle/`, `.kotlin/`, `local.properties`, the
  release keystore (`keystore/`, `keystore.properties`, `*.jks`), or IDE files —
  they are covered by `.gitignore`.
- Release builds read signing credentials from `keystore.properties` (local) or
  `RELEASE_*` env vars (CI); see `scripts/generate-keystore.sh`. Signed release
  APK/AAB artifacts are what get published to Cafe Bazaar (see README).
