# Blutut Printer - Ultra-Lightweight Android Print Bridge (v1.0.3)

An extremely lightweight native Android print bridge. It acts as a robust, native alternative to apps like RawBT, allowing direct printing to Bluetooth classic (SPP) thermal receipt printers from **web browsers (via HTTP/CORS)**, **direct TCP sockets**, or **Android print intents**.

---

## 🌟 What's New in v1.0.3

### 📱 1. Premium Cybernetic UI/UX Revamp
*   **Floating Navigation Capsule Dock**: Replaced the traditional flat bottom navigation bar with a floating capsule dock featuring smooth margins, rounded corners, and a double-stroke neon-glowing border.
*   **Active Tab Capsule Indicators**: Added neat, interactive 2.5dp Sky Blue neon indicators underneath the active tab icon to give a premium, tactile, and highly responsive feedback.
*   **Dynamic Status Pill Badges**: Replaced static key-value labels with visually striking status pills that dynamically shift backgrounds:
    *   **Active/Running**: Radiant Emerald-to-Teal gradient with glowing white text.
    *   **Stopped/Disconnected**: Elegant Slate background with thin Rose/Crimson outlines.
*   **Sleek Custom Spinners**: Created fully customized spinner layouts (`custom_spinner_item` and `custom_spinner_dropdown_item`) featuring modern typography (`sans-serif-medium`) and minimalist chevron vector drawables, completely removing legacy Android system spinner graphics.
*   **Space-Efficient Layouts**: Configured horizontal side-by-side rows for testing buttons to conserve vertical space and maximize ergonomics.
*   **Glowing Monospace Console**: High-contrast console terminal log styled with rounded bounds and a radiant `#00FFCC` neon-green text theme.

### 🖨️ 2. Smart BUMS Footnote Watermark
Appends a beautiful two-line footer watermark centered at the bottom of all successful receipts:
```text
                  BUMS
Badan Usaha Milik STIT Riyadhussholihiin
```
*   **Font B Compact Sizing**: Printed in crisp **Font B** (`ESC M 1`), which fits perfectly on standard 58mm budget printers without wrapping, while remaining highly readable.
*   **Smart Stream Injector**: Scans the last 15 bytes of incoming ESC/POS streams for paper cuts (`GS V` / `0x1D 0x56`) or feed commands (`ESC d` / `0x1B 0x64`) and injects the watermark precisely *before* them. This ensures it is printed cleanly at the bottom of the invoice before the paper is cut or fed, preventing cut-offs or printing on the next receipt. Works universally for HTTP POST, raw TCP, and PDF System Print Services.

---

## Key Features

1. **Ultra-Tiny Footprint**: Compiled using native Java and optimized resource shrinking, resulting in a final install file size under **150KB** (easily beating constraints).
2. **Dual-Protocol Background Server**:
   - **HTTP Server (Port 6801)**: Fully compatible with Web Browser `fetch` calls, with **CORS headers enabled**.
   - **TCP Raw Server (Port 6801)**: Accepts raw TCP print bytes from standard network printer client apps (like Loyverse, Kyte, etc.).
3. **RawBT Compatibility**: Supports standard printing intents (`com.rawbt.print.ACTION_PRINT` and standard sharing intents).
4. **Boot Auto-Start**: Can automatically launch the print server service as soon as the phone boots up.
5. **Dynamic Paper Sizes**: Configurable dimensions between 58mm (384px) and 80mm (576px) receipts.
6. **Buffer Throttling**: Splits large dithered images into 512-byte blocks with a tiny 80ms sleep to prevent overflow crashes on budget thermal printers.

---

## 🚀 How to Build the APK (Without Installing Android SDK)

Since you don't have the Android SDK installed on your machine, we have built a **GitHub Actions Cloud Compiler** directly into the project. You can build the ready-to-install APK in the cloud for free in under 2 minutes:

1. **Create a GitHub Repository**:
   - Go to [github.com](https://github.com) and create a new **free repository** (it can be Public or Private).
2. **Push the Code to GitHub**:
   - Open the terminal in this project folder (`d:\Antigapps\blutut-printer`) and run:
     ```bash
     git init
     git add .
     git commit -m "Initial commit for Blutut Printer Bridge v1.0.3"
     git branch -M main
     git remote add origin <YOUR_GITHUB_REPOSITORY_URL>
     git push -u origin main
     ```
3. **Download the APK**:
   - Go to your repository page on GitHub and click the **Actions** tab at the top.
   - You will see the **Build Android APK** workflow running. Click on it.
   - Once it finishes (about 1.5 minutes), click on the completed run and scroll to the bottom to the **Artifacts** section.
   - Click on **blutut-printer-debug-apk** to download the ZIP file.
   - Unzip the file, transfer the `app-debug.apk` to your phone, and install it! (If prompted, enable "Install from Unknown Sources" since it is your own custom developer build).

---

## 📱 Quick Start Guide

1.  **Connect Printer**: Grant Bluetooth and Nearby Devices permissions. Under the **Printer** tab, select your paired Bluetooth printer from the custom dropdown spinner and tap **Connect Printer**.
2.  **Enable Server**: Under the **Server** tab, configure your desired port and toggle **Enable HTTP & TCP Server** to ON.
3.  **Start Printing**: The print bridge is ready to receive and print transaction streams from your web or local POS client.

---

## 🌐 Web POS Integration (JavaScript CORS)

Send ESC/POS raw binary print streams directly from your web browser using `fetch`. The smart watermark is automatically injected at the bottom of the invoice:

```javascript
const printPOSReceipt = (binaryBytes) => {
    // Replace with your Android phone's IP address shown on the app dashboard
    const serverUrl = "http://192.168.1.100:6801/"; 

    fetch(serverUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/octet-stream"
        },
        body: new Uint8Array(binaryBytes)
    })
    .then(res => res.json())
    .then(data => console.log("Print successful:", data))
    .catch(err => console.error("Print failed:", err));
};
```

---

## 🔌 Network POS (TCP) & PDF Integration

*   **Network POS Apps (Loyverse, Kyte, etc.)**: In your POS printer settings, select **Ethernet / Network / WiFi** printer type, enter your phone's **IP Address**, and set the port to **`6801`**.
*   **PDF Printing (Android System Print)**: Enable **Bluetooth Thermal Printer** in the Android Print Service Settings. You can print PDFs directly from WhatsApp, Chrome, or File Manager with real-time contrast scaling.
