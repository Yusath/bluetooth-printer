# Blutut Printer

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/Yusath/bluetooth-printer)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)

An ultra-lightweight, high-performance native Android print bridge. It serves as a robust alternative to third-party tools like RawBT, allowing direct printing to Bluetooth classic (SPP) thermal receipt printers from **web browsers (via HTTP/CORS)**, **direct TCP sockets**, or **Android print intents**.

---

## 🌟 Key Features

*   **Dual-Protocol Background Server**:
    *   **HTTP Server (Port 6801)**: Allows web applications to trigger print jobs directly via standard CORS-enabled JavaScript `fetch` calls.
    *   **TCP Raw Server (Port 6801)**: Emulates a network printer, accepting raw print streams from POS applications (like Loyverse, Kyte, etc.).
*   **Custom Receipt Branding**:
    *   **Receipt Header**: Customize the top of the receipt with centered bold text or a custom logo/image.
    *   **Receipt Footer**: Customize the bottom watermark with a small Font B text or a custom image.
    *   Supports dynamic Floyd-Steinberg dithering and scaling for custom images.
*   **Smart Layout Injection**: Scans incoming ESC/POS streams to insert the footer precisely *before* feed (`ESC d`) and cut (`GS V`) commands to avoid empty space and cut-offs.
*   **Offline Job Queue**: Temporarily buffers and saves print jobs when the printer is offline, automatically printing them once the printer connects.
*   **Hardware Compatibility**:
    *   Dynamic paper width selection (58mm vs 80mm).
    *   Anti-overflow buffer throttling for budget printers.
    *   Floyd-Steinberg contrast slider calibration.

---

## 📱 Quick Start Guide

1.  **Connect Printer**: Turn on Bluetooth, pair your thermal printer, and select it from the dropdown in the **Printer** tab. Tap **Connect**.
2.  **Start Server**: Go to the **Server** tab, configure the port (default: `6801`), and enable the server.
3.  **Receipt Branding**: Go to the **Settings** tab, configure custom header/footer text or select logo images.
4.  **Print**: Send print jobs through web HTTP POST, raw TCP, or the Android System Print Service.

---

## 🌐 Integration Guide

### Web POS (JavaScript / CORS)
Send ESC/POS raw bytes directly from any web browser:
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

### Network POS Apps (Loyverse, Kyte, etc.)
Configure a **Network / Ethernet** printer in your POS application:
- **IP Address**: The IP address displayed on the server tab.
- **Port**: `6801`.

### PDF Printing (Android System Print)
Go to your Android System settings -> Connection & Sharing -> Printing -> Enable the **Bluetooth Thermal Printer** service. You can now print PDFs from Chrome, Chrome Viewer, Chrome Docs, or WhatsApp.
