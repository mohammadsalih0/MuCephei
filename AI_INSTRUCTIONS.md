# Project Rules: Remote CI/CD, Token Efficiency & GitHub First

- **Build Execution & Zero Local Overhead:**
  - Heavy builds, NDK/C++ compilations, and release packaging happen exclusively on GitHub Actions, never locally.
  - Do NOT invoke local Gradle build tools (`gradle_build` or `./gradlew assemble*`). Offload all builds to GitHub Actions from the start.

- **Debugging & CLI Tools:**
  - Use `/home/linuxbrew/.linuxbrew/bin/gh run list` and `/home/linuxbrew/.linuxbrew/bin/gh run view --log-failed <run-id>` to inspect remote CI/CD logs when troubleshooting.

- **Token & Context Efficiency:**
  - **Avoid Massive File Reads:** Do not read or scan large 3rd-party vendor source trees (e.g., GGML, Llama.cpp, or multi-thousand-line C++ files) into the chat context. Rely on targeted searches and header definitions.
  - **Fresh Chat Sessions:** After finishing a major multi-file feature or native C++ integration, start a fresh AI chat session to reset context size and prevent token inflation.

- **CI/CD Cache Maintenance:**
  - Ensure GitHub Actions workflow (`.github/workflows/build.yml`) maintains active caching for:
    1. Gradle Wrapper Distributions (`~/.gradle/wrapper/dists`)
    2. Android SDK CMake Installation (`/usr/local/lib/android/sdk/cmake`)
    3. NDK C/C++ CCache (`hendrikmuhs/ccache-action` with `-DCMAKE_C_COMPILER_LAUNCHER=ccache`)

- **Version Management:**
  - When preparing a release, manually bump `versionCode` (integer) and `versionName` (string) in both `app/build.gradle.kts` and `version.json`.
  - Keep local `versionCode` aligned with `version.json` during active development on `main` to prevent false update prompts.

- **Changelog Single Source of Truth:**
  - Maintain release changelogs in `version.json` (`changelog` field). The GitHub Actions CI/CD pipeline automatically extracts this changelog when publishing GitHub Releases.

- **Release Signing & Tags:**
  - Releases are triggered by pushing a tag (e.g., `git tag v2.1.0 && git push origin v2.1.0`). Ensure GitHub repository secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`) are configured.

- **Update Testing Workflow:**
  - To test the in-app update dialog instantly without pushing tags, temporarily lower local `versionCode` in `app/build.gradle.kts` below `version.json`.
