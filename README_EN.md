# PocketTavern-Lite

<p align="center">
  <a href="README.md">简体中文</a> | <b>English</b>
</p>

PocketTavern-Lite is a lightweight, high-performance Android roleplay (RP) chat client. Built on top of [RikkaHub](https://github.com/rikkahub/rikkahub) 3.0.0, it deeply aligns with the [SillyTavern](https://github.com/SillyTavern/SillyTavern) ecosystem, assets, and user workflows, providing a pure, responsive, and immersive mobile conversational experience.

License: [AGPL-3.0](LICENSE). Upstream copyright notices and licenses are fully preserved.

---

## Key Features

- 🚀 **Lightweight & Native Architecture**: Crafted with modern Kotlin + Jetpack Compose. Focuses on the core roleplay experience with rapid startup and minimal memory footprint. Can be installed side-by-side with official RikkaHub.
- 🎭 **Comprehensive SillyTavern Compatibility**:
  - **Character Cards**: Full support for V1, V2, and V3 specifications (PNG embedded metadata and JSON), including multiple alternate greetings and character-specific settings.
  - **Presets & Prompt Ordering**: Precise arrangement of `prompts` and `prompt_order`, with granular depth and frequency controls for Author's Notes.
  - **Lorebooks**: Keyword triggers, recursive scanning, insertion depth, token budget control, and dynamic entry insertion.
  - **Regex Scripts**: Display-layer and prompt-layer regex transformations. Features an **in-app Regex Script Editor** for real-time testing, debugging, and live replacement previews against raw model outputs.
- 🎨 **In-Place HTML & Front-End Card Rendering**:
  - **In-Place Story Rendering**: Raw HTML appearing on its own line after story text (such as status bars, affinity gauges, or branch choices) renders directly inside the message thread. Adjacent `<style>` and markup blocks merge seamlessly for continuous reading.
  - **Full-Page Codeblock Rendering**: In line with the SillyTavern Assistant (酒馆助手) standard, closed code blocks containing full document markup (`<body>...</body>` or `<html>...</html>`) automatically render as interactive cards, supporting languages like `text`, `xml`, or `html`.
  - **Sandboxed Security & Network Whitelist**: Each message runs in an isolated WebView sandbox with DOM storage disabled and API keys hidden. Includes a default whitelist for common script CDNs (jsdelivr, unpkg, cdnjs, jQuery, Tailwind), web fonts (Google Fonts and mirrors), and image hosts (catbox, imgur), with user-customizable domain lists.
  - **JS Assistant API Support**: Basic compatibility for helper methods like `getChatMessages`, `getVariables`, `setVariables`, `triggerSlash`, and `Mvu` (`mvu.stat_data`) for dynamic state management.
- ⚡ **Prompt Cache-Optimized Architecture**:
  - Automatically structures context into a **stable prefix** (system prompt, character card, memory lorebook, visible history) and a **dynamic tail** (triggered lore, variables, Author's Note, timestamps, and current speaker).
  - Freezes dynamic macros and variable values per user turn to prevent tool iterations from breaking the prefix. Maximizes prompt cache hit rates across Anthropic (Claude cache breakpoints), OpenAI (`prompt_cache_key`), and DeepSeek, significantly decreasing time-to-first-token and API costs.
- 🧠 **Progressive Long-Conversation Memory Summarization**:
  - Automatically summarizes earlier conversation floors (default: summarizes at 40 floors, keeping the 10 most recent floors active).
  - Appends structured summaries with `---` floor-range dividers directly to the conversation's dedicated memory lorebook, preserving historical narrative context. When memory tokens exceed budgets, earlier recaps are consolidated into versioned summaries.
  - Supports manual one-tap summarization and pre-save review. Summarized messages can be archived (hidden without consuming tokens, restorable at any time) or purged.
- 🌐 **Extensive Model Provider Support & Connection Diagnostics**:
  - 20+ built-in provider presets plus custom endpoints, supporting OpenAI Chat Completions / Responses, Anthropic Messages, Google Gemini `generateContent` / `Interactions`, and Google Vertex AI (Express mode and Service Account JSON).
  - Integrated provider connectivity tests and real-time message exchange diagnostic tools.
- 📲 **Seamless In-App Updates & Installation**:
  - Independent download engine powered by OkHttpClient, respecting global app proxy settings with automatic fallback to acceleration mirrors (`gh-proxy.com`).
  - Dedicated notification channel displaying real-time download progress and speed; triggers system installer automatically via FileProvider upon completion.
  - Device ABI detection (`arm64-v8a` / `x86_64`) with prioritized package recommendations.
- 👥 **Group Chats & Quick Replies**:
  - **Group Chats**: Flexible speaker rotation (natural mention-based matching, sequential list, pooled random, or manual selection). Group members share a memory lorebook, and member cards remain pinned in the stable prefix to preserve prompt caches.
  - **Quick Replies**: Import SillyTavern quick reply collections, displayed as accessible action bars above the chat input, supporting macros like `/send`, `/trigger`, `/setvar`, `/getvar`, `/gen`, and `/input`.

---

## Quick Start & Building

### Requirements
- **Build Environment**: JDK 21, Android SDK (Platform API 34+)
- **Device Requirements**: Android 8.0 (API Level 26) or higher, 64-bit architecture recommended (`arm64-v8a` or `x86_64`)

### Build from Source
```bash
# Clone the repository and initialize submodules
git clone https://github.com/delightedMaster/PocketTavern-Lite.git
cd PocketTavern-Lite
git submodule update --init

# Build Debug APK
./gradlew :app:assembleDebug
```
The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Release Signing Configuration

To build a signed release APK, configure the signing properties in `local.properties` (located in the project root, relative to the app module) or supply the corresponding environment variables:

| `local.properties` Key | Environment Variable | Description |
| --- | --- | --- |
| `storeFile` | `ANDROID_KEYSTORE_FILE` | Relative path to keystore file (e.g., `release.keystore`) |
| `storePassword` | `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `keyAlias` | `ANDROID_KEY_ALIAS` | Key alias |
| `keyPassword` | `ANDROID_KEY_PASSWORD` | Key password |

> When all four properties are provided, `./gradlew :app:assembleRelease` outputs a signed Release APK; otherwise, an unsigned APK is produced. Pushing a `v*` tag triggers the GitHub Actions workflow to build and attach signed release assets automatically.

---

## Development Roadmap

- **0.1.0**: Baseline established on RikkaHub 3.0.0; deep integration of SillyTavern assets (character cards, lorebooks, presets, regex, and Author's Notes).
- **0.1.1**: Trimmed non-essential modules, streamlined roleplay client identity, and enhanced GitHub Releases update checking.
- **0.1.2**: Implemented import compatibility diagnostic reports; revamped model provider management and connection tests.
- **0.1.3**: In-chat HTML codeblock rendering, context and max token budget controls, and joint preset/regex importing.
- **0.1.4**: In-place post-story HTML rendering, full-page codeblock detection, and built-in front-end CDN/font/image whitelists.
- **0.1.5** (Current):
  - In-app update engine overhaul (proxy support, mirror acceleration, notification progress, and auto-install trigger).
  - Interactive in-app Regex Script Editor with live model output replacement preview.
  - CI/CD build artifact naming standardization (version tag + ABI suffix) with SHA-256 checksum generation.
  - Bilingual documentation rewrite and modernization.

---

## Acknowledgements & Upstream Projects

- [RikkaHub](https://github.com/rikkahub/rikkahub): Excellent native Android LLM client baseline. This project maintains RikkaHub's Git history and upstream copyright notices.
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android): Provided valuable design inspiration and implementation patterns for bringing SillyTavern logic to Android.
- [SillyTavern](https://github.com/SillyTavern/SillyTavern): The definitive ecosystem standard for LLM roleplay and rich UI card interactions.
- [Summaryception](https://github.com/Lodactio/Extension-Summaryception) (AGPL-3.0): Built-in narrative summarization prompt templates are adapted and optimized from its narrative preset.

When distributing modified versions of this project, please abide by the [AGPL-3.0](LICENSE) license and retain all copyright and acknowledgement notices.
