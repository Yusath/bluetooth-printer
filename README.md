# 🖨️ Blutut Printer - Web & TCP Print Bridge
### *Badan Usaha Milik STIT Riyadhussholihiin (BUMS)*

A native Android print bridge application that seamlessly connects Bluetooth classic (SPP) thermal receipt printers with web browsers (via HTTP/CORS), raw TCP sockets, and the Android System Print Service (PDF). 

Designed with an ultra-lightweight footprint (**&lt; 150 KB**), a premium cybernetic dark theme, and robust data protection safety nets.

---

## 🌟 Key Features

*   **Premium Floating UI**: Features a sleek floating navigation capsule with dynamic active tab indicators and gradient status badges.
*   **Smart BUMS Watermark**: Automatically injects a two-line centered footnote watermark (`BUMS` and `Badan Usaha Milik STIT Riyadhussholihiin`) in small, crisp **Font B** (`ESC M 1`) precisely before any paper cuts or feed commands at the end of receipts to ensure it is never cut off.
*   **Dual-Protocol Bridge**: Operates a background HTTP server (with full CORS support) and raw TCP sockets on a customs port to process  printing transactions directly from web browsers or local POS clients.
*   **Data Retention Safeguard**: Utilizes `hasFragileUserData` uninstall protection so your default Bluetooth printer address and server configurations are preserved when updating the app.
*   **Buffer Throttling**: Implements active data stream throttling to prevent buffer overflows and garbled printouts on budget thermal printer models.

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
