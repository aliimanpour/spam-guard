# GEMINI.md

This repository's agent instructions live in [AGENTS.md](./AGENTS.md). Read and
follow that file for everything: project overview, build/test commands,
architecture, conventions, and domain rules.

Quick reference:

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
```

Single module `:app`, package `com.smsspam.detector`. Compose-only UI, manual DI
via `AppContainer`, persistence via `JsonListStore` + `StateFlow` repositories.
