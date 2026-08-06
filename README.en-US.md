

# HideScreen

[English](#english) | [Chinese](#chinese)

---

<a id="chinese"></a>
## Chinese Documentation

### Introduction

**HideScreen** is a lightweight Xposed / LSPosed module for Android.  
It hides the UI of scoped applications from screenshots, screen recordings, and screen casting outputs, while the app's local display and interactions remain completely unaffected.

The module relies entirely on Android's native secure surface mechanism — by setting `SurfaceControl.setSkipScreenshot(true)`, it instructs the system compositor to automatically skip specific windows during screen composition. The module itself **does not modify app content, inject views, or run continuously**; it only flags windows once during creation, offering excellent performance and negligible power consumption.

<div align="center">
  <img src="/app/1.jpg" width="45%" alt="Screenshot Effect - 1" />
  <img src="/app/2.jpg" width="45%" alt="Screenshot Effect - 2" />
</div>

---

### Features

- ✅ Completely hides the target app's UI from screenshots, recordings, and casting outputs  
- ✅ Local display and interactions remain unchanged and completely unaffected  
- ✅ Does not affect physical display output (USB / HDMI casting works normally)  
- ✅ Zero configuration, no background services, no UI  
- ✅ Minimalistic design: one-time configuration, permanent effect  
- ✅ Compatible with Android 6.0 to 15+, covering most custom OEM ROMs  
- ✅ Compatible with all windows added via `WindowManager` (including IME, dialogs, Toast, etc.)

---

### Technical Principles & Optimizations

The design philosophy of this module is: **do one thing exceptionally well**.

- **Core Mechanism**  
  At critical points in the `ViewRootImpl` window lifecycle, it uses reflection to call the hidden system API `SurfaceControl.Transaction.setSkipScreenshot(true)`, writing the anti-screenshot flag to SurfaceFlinger. The system screenshot and recording services automatically respect this flag, requiring no additional interception or system modification.

- **Key Optimizations**  
  - **Reflection Warming & Caching**: All reflection members are loaded only once upon first use and reused thereafter, avoiding performance fluctuations from repeated lookups.  
  - **Intelligent Surface Field Caching**: Automatically detects and memorizes the actual field on the device storing `SurfaceControl`, allowing subsequent windows to hit the cache directly and eliminating invalid reflection traversals.  
  - **Transaction Deduplication**: Within each window's lifecycle, the anti-screenshot flag is set only once. Even with per-frame callbacks, Binder transactions are not resent, reducing overhead to the theoretical minimum.  
  - **Multi-version API Fallback Chain**: Attempts different signatures of `setSkipScreenshot` and `setSecure` methods in priority order to ensure broad compatibility from Android 6.0 to the latest versions.  
  - **Safe Resource Release**: Calls `close()` proactively after transaction completion to promptly release underlying Binder references, preventing potential system resource accumulation.  
  - **Weak Reference Containers**: Window markers are stored entirely in weak-reference collections. Related records are automatically cleaned up when windows are destroyed by the system, requiring no manual maintenance and eliminating memory leak risks.  
  - **Thread Safety & Reentrancy Guard**: Employs appropriate synchronization wrappers and protection mechanisms to ensure stable operation even under multi-threaded callback scenarios.

- **Performance & Power Consumption**  
  The module performs only extremely brief transaction submissions when a window is created, rotated, or rebuilt, remaining completely silent otherwise. In daily use, its impact on CPU and battery is minimal and entirely negligible. Even on low-end devices, it causes no perceptible lag or heating.

---

### Requirements

- Android 6.0 to 15+ (actual behavior depends on system implementation)  
- Active Xposed framework (Original, EdXposed, or LSPosed)  
- Module only affects third-party apps selected in its scope

---

### Usage

1. Install the module APK  
2. Enable the module in the LSPosed manager  
3. **Only select the apps** whose UI you wish to hide  
4. Force stop and relaunch the target app

Once enabled, the selected app's UI will not appear in system screenshots, screen recordings, or casting outputs.

---

### ⚠️ Important Warning

**DO NOT apply this module to the following targets:**

- System UI  
- Android framework (`android`) or core services (`system_server`)  
- Any system-level apps (e.g., Settings, Phone, Launcher, etc.)

Incorrectly applying the module to system components may cause:

- Complete failure of system screenshot and recording functions  
- Black screens, unresponsive UI, or boot loops stuck on the startup animation  
- System instability or failure to boot

**This module is strictly designed for ordinary third-party applications. Please be very careful when configuring the scope in LSPosed.**

---

### Notes & Disclaimer

- This project is primarily intended for technical research, privacy protection, and compliance testing.  
- Due to varying OEM support for `setSkipScreenshot`, the module's effectiveness may be limited on some heavily customized systems (known issues are extremely rare).  
- Users must comply with local laws and regulations. Do not use this module for illegal purposes.  
- Users assume full responsibility for any consequences arising from the use of this module.

---

### Acknowledgments

The core concept of this module was inspired by [Transparent Screenshot](https://github.com/Dszsu/Transparent_screenshot). We extend our gratitude to the original author for their open-source spirit.  
The current version has been thoroughly refactored in terms of performance, compatibility, and resource management. See the [Technical Principles & Optimizations](#technical-principles--optimizations) section for detailed differences.

---

### License

This project is released under the [MIT License](LICENSE).

---

<a id="english"></a>
## English

### Overview

**HideScreen** is a lightweight Xposed / LSPosed module for Android.  
It hides the target application's UI from screenshots, screen recordings, and screen casting results, while the app remains fully visible and interactive on the physical device.

The module leverages Android's native secure surface flag — `SurfaceControl.setSkipScreenshot(true)` — to instruct SurfaceFlinger to exclude specific windows from the final composed output. No content modification, view injection, or background service is involved. Performance is excellent, and power consumption is negligible.

<div align="center">
  <img src="/app/1.jpg" width="45%" alt="Screenshot 1" />
  <img src="/app/2.jpg" width="45%" alt="Screenshot 2" />
</div>

---

### Features

- ✅ Hides app UI from screenshots, recordings, and casting results  
- ✅ No impact on local display or touch interaction  
- ✅ Physical display output (USB / HDMI) remains unaffected  
- ✅ Zero configuration, no UI, no background services  
- ✅ Minimalistic design: one‑time flag, permanent effect  
- ✅ Supports Android 6.0 through 15+, works on most custom OEM ROMs  
- ✅ Covers all windows added via `WindowManager` (including IME, dialogs, toasts)

---

### Technical Highlights & Optimizations

The module follows a single‑responsibility principle: **do one thing exceptionally well**.

- **Core Approach**  
  Hooks into `ViewRootImpl` lifecycle methods (`setView`, `relayoutWindow`, `performTraversals`) and applies the screenshot‑skip flag via reflective calls to `SurfaceControl.Transaction`. The system screenshot / screen recording pipeline respects this flag automatically.

- **Key Optimizations**  
  - **Reflection pre‑warming & caching** – All reflection members are resolved once and reused, eliminating repeated lookup overhead.  
  - **Intelligent Surface field caching** – The actual `SurfaceControl` field used by the device is detected and memorized, avoiding unnecessary reflection traversal.  
  - **Transaction deduplication** – Each window is marked only once per lifecycle, preventing redundant Binder IPC calls and reducing overhead to the absolute minimum.  
  - **Multi‑version API fallback chain** – Attempts different signatures of `setSkipScreenshot` and `setSecure` in optimal order, ensuring broad compatibility from Android 6.0 to the latest versions.  
  - **Resource cleanup** – Actively calls `close()` on the transaction object to release underlying Binder references promptly.  
  - **Weak reference containers** – Window markers are stored in weak‑reference sets, allowing automatic cleanup when windows are destroyed with zero memory leaks.  
  - **Thread safety & reentrancy guard** – Proper synchronization and protective mechanisms ensure stability even under concurrent callbacks.

- **Performance & Power**  
  The module performs a brief transaction only when a window is created, rotated, or rebuilt. The rest of the time it stays completely idle. Its impact on CPU and battery is so minimal that it is entirely unnoticeable in daily use, even on low‑end devices.

---

### Requirements

- Android 6.0 – 15+ (actual behavior depends on system implementation)  
- An active Xposed framework (original, EdXposed, or LSPosed)  
- The module scope should be limited to third‑party applications only

---

### Usage

1. Install the module APK  
2. Enable the module in LSPosed Manager  
3. **Select only the third‑party apps** you wish to hide from screenshots / recordings  
4. Force‑stop and relaunch the target apps

Once enabled, selected apps will no longer appear in system screenshots, screen recordings, or casting outputs.

---

### ⚠️ Important Warning

**Do NOT apply this module to the following:**

- System UI  
- Android framework (`android`) or `system_server`  
- Any system‑level packages (Settings, Phone, Launcher, etc.)

Applying the module to system components may cause:

- Complete breakage of screenshot / recording functionality  
- Black screens, unresponsive UI, or boot loops  
- System instability or failure to start

This module is **strictly designed for ordinary third‑party applications**. Choose scopes carefully within LSPosed.

---

### Disclaimer

- This project is intended for technical research, privacy protection, and compliance testing.  
- Effectiveness may vary on heavily customized OEM ROMs due to differing support for `setSkipScreenshot`.  
- Users are responsible for complying with local laws and regulations.  
- The author assumes no liability for any consequences resulting from the use of this module.

---

### Acknowledgments

The core idea of this module was inspired by [Transparent Screenshot](https://github.com/Dszsu/Transparent_screenshot). We appreciate the original author's open-source spirit.  
The current version has been completely refactored in terms of performance, compatibility, and resource management. See [Technical Highlights](#technical-highlights--optimizations) for detailed differences.

---

### License

This project is licensed under the [MIT License](LICENSE).
