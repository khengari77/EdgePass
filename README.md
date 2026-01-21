# EdgePass - AI-Powered Passport Photo Generator

<div align="center">

![EdgePass Logo](https://img.shields.io/badge/EdgePass-Passport%20Photo%20Generator-blue?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?style=flat-square&logo=kotlin)
![Rust](https://img.shields.io/badge/Rust-1.75-orange?style=flat-square&logo=rust)
![Android](https://img.shields.io/badge/Android-24+-green?style=flat-square&logo=android)

**Generate ICAO-compliant passport photos with AI-powered background removal**

[Features](#features) • [Installation](#installation) • [Architecture](#architecture) • [Credits](#credits)

</div>

---

## 🌟 Overview

EdgePass is a production-grade Android application that generates ICAO-compliant passport photos using a hybrid architecture combining **Kotlin/Jetpack Compose** for the UI, **Rust** for high-performance image processing, and **ONNX Runtime** for AI inference.

The application features real-time face detection, automatic centering, and AI-powered background removal using state-of-the-art segmentation models.

---

## ✨ Features

### Core Functionality
- 📸 **Camera Integration**: Front/back camera support with live preview
- 👤 **Face Detection**: Android FaceDetector API with strict validation to reduce false positives
- 🎯 **Auto-Centering**: Automatically positions the crop based on detected face location
- 🖼️ **AI Background Removal**: BriaAI RMBG 1.4 model for professional-grade background removal
- 📐 **ICAO Compliance**: Supports multiple passport standards with correct dimensions
- 💾 **Gallery Export**: Save processed photos directly to device gallery

### Supported Passport Standards
| Standard | Dimensions | Notes |
|----------|------------|-------|
| Saudi eVisa | 50x50mm | White background |
| US Passport | 2x2 inches | White background |
| Schengen | 50x50mm | White background |
| General ID | 45x55mm | White background |
| UK Passport | 35x45mm | White background |
| India Passport | 35x50mm | White background |

---

## 🚀 Installation

### Prerequisites

1. **Android Studio** Hedgehog (2023.1.1) or later
2. **Rust Toolchain**: `rustup install stable`
3. **Android NDK**: Install via Android Studio SDK Manager
4. **cargo-ndk**: `cargo install cargo-ndk`

### Setup

```bash
# Clone the repository
git clone https://github.com/khengari77/EdgePass.git
cd EdgePass

# Download the ONNX model for background removal
# Note: Model file is excluded from git due to size limit (>100MB)
curl -L -o app/src/main/assets/models/background_remover.onnx \
  "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model.onnx"

# Install Rust targets for Android
rustup target add aarch64-linux-android armv7-linux-androideabi

# Build the project
./gradlew assembleDebug
```

> ⚠️ **Important**: The background removal model (~168MB) is not included in the repository due to GitHub's file size limit. Download it using the command above or from [HuggingFace](https://huggingface.co/briaai/RMBG-1.4).

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build (Optimized)
```bash
./gradlew assembleRelease
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                 Kotlin/Jetpack Compose               │
│         (UI, CameraX, State Management)             │
└─────────────────────┬───────────────────────────────┘
                      │ JNI
                      ▼
┌─────────────────────────────────────────────────────┐
│                    Rust Core                         │
│         (Image Processing, Cropping, ONNX)          │
│                  libedgepass_core.so                 │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│                ONNX Runtime Mobile                   │
│           (BriaAI RMBG 1.4 Background Removal)      │
└─────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI** | Kotlin + Jetpack Compose | Modern declarative UI |
| **Camera** | CameraX | Camera preview and capture |
| **Bridge** | JNI (Java Native Interface) | Kotlin ↔ Rust communication |
| **Core** | Rust 1.75 | Image processing, crop operations |
| **AI Inference** | ONNX Runtime 1.16.3 | Background removal model |
| **Face Detection** | Android FaceDetector | Face localization |

---

## 📁 Project Structure

```
EdgePass/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/edgepass/
│   │   │   ├── lib/
│   │   │   │   ├── PassportProcessor.kt    # JNI wrapper
│   │   │   │   ├── BackgroundRemover.kt    # ONNX inference
│   │   │   │   └── FaceDetector.kt         # Face detection
│   │   │   ├── ui/
│   │   │   │   ├── EdgePassApp.kt          # Main Compose app
│   │   │   │   ├── screens/
│   │   │   │   │   └── CameraScreen.kt     # Camera preview
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt
│   │   │   └── MainActivity.kt
│   │   ├── assets/models/
│   │   │   └── background_remover.onnx     # Download separately (see README)
│   │   ├── jniLibs/
│   │   │   └── arm64-v8a/
│   │   │       └── libedgepass_core.so     # Compiled Rust library
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── src/                                    # Rust crate
│   ├── lib.rs
│   ├── engine/mod.rs                       # Image processing
│   ├── standards.rs                        # Passport standards
│   └── jni/mod.rs                          # JNI bindings
├── Cargo.toml                              # Rust dependencies
├── build.gradle.kts                        # Root Gradle config
└── settings.gradle.kts
```

---

## 🔧 Technical Details

### Face Detection Validation

To reduce false positives, the application implements strict validation:

```kotlin
// Minimum eye distance: 50 pixels
if (eyeDistance < 50f) continue

// Confidence threshold: 0.5
if (confidence < 0.5f) continue

// Validate coordinates are within bounds
if (midX < 0 || midY < 0 || midX > width || midY > height) continue
```

### Background Removal Pipeline

1. Image resized to 1024x1024
2. Preprocessed to CHW float format (normalized to [0,1])
3. ONNX model inference
4. Alpha mask applied to composite with white background

---

## 📋 Known Issues

### High Priority
- **Face Bounding Box Display**: Bounding boxes in preview may not align perfectly with displayed image in all screen sizes (being refined)

### Medium Priority
- **ONNX Model Size**: The BriaAI RMBG model (168MB) significantly increases APK size - consider model quantization
- **Processing Time**: Background removal takes 2-5 seconds on mid-range devices

### Low Priority
- **Limited Error Handling**: Some edge cases in image processing may cause crashes
- **No Unit Tests**: Core processing logic lacks automated tests

---

## 🗺️ Roadmap

### Short Term (v0.2.0)
- [ ] Fix face bounding box alignment issues
- [ ] Add unit tests for Rust image processing
- [ ] Implement model quantization (INT8) to reduce APK size
- [ ] Add progress indicator for background removal

### Medium Term (v0.3.0)
- [ ] MediaPipe Face Detection integration for better accuracy
- [ ] Multi-face support in single photo
- [ ] Custom passport standard configuration UI
- [ ] Photo quality assessment (blur detection, lighting check)

### Long Term (v1.0.0)
- [ ] Cloud sync for processed photos
- [ ] Photo printing service integration
- [ ] Support for additional AI models (face restoration, beauty filter)
- [ ] Multi-language support (Arabic, French, Chinese)

---

## 🤝 Credits

<div align="center">

**This project was programmed entirely using AI**

---

### 🧠 Primary AI Assistant

**[OpenCode](https://github.com/anomalyco/opencode)** - An interactive CLI tool for software engineering tasks

OpenCode served as the primary AI assistant for this project, handling:
- Code implementation and refactoring
- Bug fixes and debugging
- Architecture design decisions
- Documentation writing

---

### 🖥️ AI Model

**[Minimax M2.1](https://huggingface.co/minimax-01)** - Large language model by Minimax

Minimax M2.1 provided advanced reasoning and code generation capabilities throughout the development process.

---

### 🔌 Mobile Automation

**[Mobile-MCP](https://github.com/mobile-next/mobile-mcp)** - Model Context Protocol server for mobile devices

Mobile-MCP enabled:
- Automated app testing and debugging
- UI element discovery and interaction
- Real-time log monitoring
- Screenshot capture for visual debugging

---

### 📦 Open Source Dependencies

- **ONNX Runtime Mobile** - AI inference
- **AndroidX CameraX** - Camera functionality
- **Jetpack Compose** - UI framework
- **Rust** - High-performance image processing
- **BriaAI RMBG 1.4** - Background removal model

</div>

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**EdgePass Development Team**

- GitHub: [@khengari77](https://github.com/khengari77)

---

<div align="center">

**Made with ❤️ using AI**

*OpenCode • Minimax M2.1 • Mobile-MCP*

</div>
