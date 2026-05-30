# 🚀 Release Notes - v1.0.2

Welcome to the **v1.0.2** milestone release of **Blutut Printer (Web & TCP Print Bridge)**! 

This release introduces a premium **Bottom Navigation Bar UI/UX**, a bulletproof **GitHub Auto-Updater**, robust **TCP Stream Throttling** to prevent budget printer crashes, and fully optimized integrations for your **POS-Project** local network printing.

---

## 🌟 What's New in This Release

### 1. 📱 Premium Bottom Navigation UI (No More Long Scrolling!)
We have redesigned the dashboard into **4 instantaneous Tab Menus** locked to the bottom of the screen:
*   **Printer Tab**: Handles Bluetooth warning alerts, pairing selection, connection state, and dual calibration test prints.
*   **Server Tab**: Configures local HTTP & TCP sockets, port settings, and boot-autostart features.
*   **Settings Tab**: Advanced printing options including paper sizes, print contrast, extra feed lines, buffer throttling, and System Print Service settings.
*   **Logs Tab**: A dedicated console window to view local network transaction events.

### 🔄 2. GitHub Actions Auto-Updater (Fully Handled!)
*   **Settings Switch**: Added a toggle switch in Settings to activate/deactivate automatic update checks on launch.
*   **Manual Checker**: Added a manual check button to request immediate updates.
*   **Silent 404/Fallback Handlers**: Gracefully handles new repositories with zero published releases by reporting "No releases found" instead of displaying connection failures.
*   **Live Progress Bar**: Shows a sleek horizontal progress bar during updates.

### 🛡️ 3. Budget Printer Buffer Throttling (Anti-Overflow)
*   Budget thermal printers have tiny buffer capacities (e.g. 1KB).
*   Added a **Buffer Throttling Switch** that splits large dithered images into 512-byte blocks with a tiny 80ms sleep, preventing overflow crashes and random gibberish printouts.

### 📏 4. Dynamic Paper Sizes (58mm vs 80mm)
*   **Paper Size Selector**: Toggle between 58mm (384px) and 80mm (576px) receipt dimensions.
*   **Adaptive Scaling**: Dynamically adjusts raster matrices for both Virtual PDF prints and raw HTTP streams to ensure text is never cropped.

### 🎛️ 5. Contrast & Tear-off Calibration
*   **Contrast Density Slider**: Tune Floyd-Steinberg dithering thresholds (80-180) to match thermal paper heat sensitivity.
*   **Post-Print Feed Lines**: Feed 1 to 5 extra blank lines after printing to bring text past the tear-off blade.
*   **Calibration Image Print**: Outputs a test grid, grayscale contrast blocks, and active configuration stats to immediately calibrate paper width and contrast.

### 📡 6. High-Fidelity POS Integrations & State Checks
*   **TCP Stream Timeout**: Added a robust 300ms socket read timeout for raw TCP prints, ensuring multi-packet receipts from POS-Projects compile into one complete print job.
*   **Bluetooth Alert Banner**: Monitors system settings in real-time and shows a sleek warning banner if Bluetooth is turned off.

---

## 🛠️ Installation Instructions

1.  Download the **`app-debug.apk`** attached to this release.
2.  Install it manually on your Android device (this establishes the new base build with updater support).
3.  Future updates will be detected, downloaded, and installed automatically!
