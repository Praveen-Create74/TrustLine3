

```markdown
# 🛡️ TrustLine Neural Defense (v3.0.0)

TrustLine is an on-demand, multi-pass forensic evaluation and live-call telemetry defense platform designed to detect synthetic voice clones (ElevenLabs, RVC, Wav2Lip), facial manipulation, and real-time caller spoofing.

---

## 📑 Table of Contents
- [Architecture Overview](#-architecture-overview)
- [Key Features](#-key-features)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Backend Setup](#backend-setup)
  - [Frontend & Mobile Setup](#frontend--mobile-setup)
- [Environment Configuration](#-environment-configuration)
- [API Reference](#-api-reference)
- [License](#-license)

---

## 🏛️ Architecture Overview

The system operates across three synchronized execution tiers:

1. **Client & UX Layer (React 19 / Capacitor):** Material 3 adaptable UI featuring draggable call HUDs, file upload dropzones, gesture navigation, and interactive forensic report cards.
2. **Native Android Engine (Kotlin):** Background coroutine offloading, low-latency AudioRecord buffer capture (16kHz PCM mono), and network streaming via `OkHttp` multipart requests.
3. **Backend Forensic Pipeline (Node.js/Express):** 
   - **Primary:** Gemini 3.7 Flash Cloud AI (Google File API integration).
   - **Secondary Fallback:** Reality Defender API (5000ms circuit-breaker timeout).
   - **Tertiary Failsafe:** On-Device Neural Scanner (Acoustic and spectral heuristics).

---

## ✨ Key Features

- **Quick Scan Forensic Studio:** On-demand analysis of voice notes, audio tracks, video clips, and images (up to 50 MB).
- **Streamed File Processing:** Chunked multipart/form-data streaming via native Kotlin threads to eliminate memory spikes and UI thread freezes.
- **Circuit Breaker Cloud Ensemble:** Dynamic failover between Gemini and Reality Defender with automatic local acoustic fallback upon network drops.
- **Intent Integration:** Native Android `ACTION_SEND` intent listener to capture voice notes shared directly from WhatsApp or Telegram.
- **Live Call Telemetry Pipeline:** Sub-200ms incoming call metadata scoring and carrier attestation validation.

---

## 🛠️ Tech Stack

- **Frontend:** React 19, TypeScript, Tailwind CSS, Lucide Icons, Capacitor 6
- **Native Mobile:** Android SDK, Kotlin, Coroutines (`Dispatchers.IO`), OkHttp3
- **Backend API:** Node.js, Express, Multer, TypeScript
- **AI & Forensics:** Google Gemini 3.7 Flash SDK, Reality Defender API
- **Storage & State:** IndexedDB (Local Vault), Cloud Firestore

---

## 📂 Project Structure

```text
├── android/                   # Native Android Studio Project
│   └── app/src/main/java/     # Kotlin Services & Capacitor Plugins
│       ├── TrustLineMediaPlugin.kt
│       ├── CallCaptureService.kt
│       ├── CallDetectionAccessibilityService.kt
│       └── CallNotificationListenerService.kt
├── backend/                   # Node.js / Express Server
│   ├── src/
│   │   ├── routes/            # Forensics & Telemetry API endpoints
│   │   ├── services/          # Gemini & Reality Defender clients
│   │   └── server.ts          # Server entrypoint & middleware setup
│   └── package.json
└── src/                       # React 19 Frontend
    ├── components/            # QuickScanStudio, CallHUD, ReportCards
    ├── context/               # Global state & bridge listeners
    ├── App.tsx
    └── index.tsx

```

---

## 🚀 Getting Started

### Prerequisites

* Node.js (v20+ recommended)
* Android Studio Ladybug / Jellyfish or newer
* JDK 17+
* Android SDK (API Level 34+)

### Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
npm install

```


2. Create a `.env` file in the `backend/` directory:
```env
PORT=3000
GEMINI_API_KEY=your_gemini_api_key_here
REALITY_DEFENDER_API_KEY=your_reality_defender_key_here

```


3. Start the development server (bound to `0.0.0.0` for emulator/device access):
```bash
npm run dev

```



### Frontend & Mobile Setup

1. Install frontend dependencies:
```bash
npm install

```


2. Build the web distribution:
```bash
npm run build

```


3. Sync web assets to the native Android layer:
```bash
npx cap sync android

```


4. Open the native Android project in Android Studio:
```bash
npx cap open android

```


5. Deploy to an emulator (`10.0.2.2` mapping) or physical device over USB debugging.

---

## 📡 API Reference

### Quick Scan Forensic Ingestion

* **Route:** `POST /api/forensics/quick-scan`
* **Content-Type:** `multipart/form-data`
* **Fields:**
* `media`: Binary file payload (.opus, .mp3, .wav, .mp4, .png, etc.)
* `type`: `"audio"` | `"video"` | `"image"`
* `fileName`: Target filename


* **Response:**
```json
{
  "success": true,
  "source": "gemini-3.7-flash Cloud AI",
  "fileResult": {
    "fileName": "sample_note.opus",
    "mimeType": "audio/opus",
    "fileSize": "2.40 MB",
    "tier": "Fake",
    "syntheticProbability": 94.0,
    "anomaliesDetected": [
      "Detected vocoder phase dispersion in 3.2kHz - 5.8kHz band"
    ],
    "verdict": "CRITICAL: Synthetic pitch contour flatness and vocoder artifacts matched."
  }
}

```



---

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

```

If you need adjustments for specific commands, extra scripts, or deployment steps (such as Docker or cloud hosting), let me know.

```
