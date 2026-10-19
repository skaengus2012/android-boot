# android-boot

> **Just run.** Opinionated starter template for rapid modern Android development.

`android-boot` is a production-ready starter template designed to eliminate boilerplate fatigue. Jump straight into writing domain logic and building UI without spending 30 minutes configuring Gradle, Hilt, Retrofit, and Coroutines.

---

## ✨ Features

- ⚡ **Zero-Setup Velocity**: Pre-configured build scripts and dependencies. Clone and run instantly.
- 🎨 **Modern Declarative UI**: Jetpack Compose with Material 3 design system.
- 💉 **Dependency Injection**: Pre-configured Dagger Hilt with KSP.
- 🌐 **Robust Networking**: Retrofit + OkHttp + `kotlinx.serialization` (JSON).
- 🖼️ **Asynchronous Image Loading**: Integrated Coil Compose.
- 📦 **Modern Dependency Management**: Gradle Version Catalog (`libs.versions.toml`).

---

## 🛠 Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose / Material 3 |
| **Dependency Injection** | Dagger Hilt (KSP) |
| **Asynchronous** | Coroutines & Flow |
| **Networking** | Retrofit 2 + OkHttp 3 + kotlinx.serialization |
| **Image Loading** | Coil Compose |
| **Build System** | Gradle 9+ with Version Catalog |

---

## 🚀 Getting Started

### 1. Use as Template / Clone
```bash
git clone https://github.com/skaengus2012/android-boot.git my-app
cd my-app
```

### 2. Build & Run
```bash
./gradlew assembleDebug
```
