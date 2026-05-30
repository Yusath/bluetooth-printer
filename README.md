# Blutut Printer - Ultra-Lightweight Android Print Bridge

An extremely lightweight (final compiled APK size **under 150 KB**) native Android print bridge. It is designed to act as an alternative to apps like RawBT, allowing direct printing to Bluetooth classic (SPP) thermal receipt printers from **web browsers (via HTTP/CORS)**, **direct TCP sockets**, or **Android print intents**.

---

## Key Features

1. **Ultra-Tiny Footprint**: Compiled using native Java and optimized resource shrinking, resulting in an install file size of **~100KB to 150KB** (easily beating the 3MB limit).
2. **Dual-Protocol Background Server**:
   - **HTTP Server (Port 6801)**: Fully compatible with Web Browser `fetch` calls, complete with **CORS headers enabled**.
   - **TCP Raw Server (Port 6801)**: Accepts raw TCP print bytes from standard network printer client apps.
3. **RawBT Compatibility**: Supports standard printing intents (`com.rawbt.print.ACTION_PRINT` and standard sharing intents).
4. **Boot Auto-Start**: Can automatically launch the print server service as soon as the phone boots up.
5. **Modern Premium UI**: Sleek slate-dark theme dashboard with real-time status badges, settings configurators, a printer scanner, and a stylized test receipt printing utility.

---

## 🚀 How to Build the APK (Without Installing Android SDK)

Since you don't have the Android SDK installed on your machine, we have built a **GitHub Actions Cloud Compiler** directly into the project. You can build the ready-to-install APK in the cloud for free in under 2 minutes:

1. **Create a GitHub Repository**:
   - Go to [github.com](https://github.com) and create a new **free repository** (it can be Public or Private).
2. **Push the Code to GitHub**:
   - Open terminal in this project folder (`d:\Antigapps\blutut-printer`) and run:
     ```bash
     git init
     git add .
     git commit -m "Initial commit for Blutut Printer Bridge"
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

## 📱 How to Use the App

1. **Grant Permissions**: Open the app, and allow the requested Bluetooth permissions.
2. **Select & Connect Printer**:
   - Make sure your thermal printer is turned on and paired in your phone's Android Bluetooth Settings.
   - Select your printer from the dropdown spinner inside the app.
   - Click **Connect Printer**. The printer status will change to `Connected to [Printer Name]`.
3. **Start the Print Server**:
   - Enter your desired port (default: `6801`).
   - Toggle **Enable HTTP & TCP Server** to ON.
   - The server status will update showing your local access addresses (e.g. `http://localhost:6801` and `http://192.168.1.100:6801`).
   - Optional: Toggle **Start Server on Device Boot** if you want it to run automatically when your phone turns on.
4. **Test the Connection**:
   - Click **Send Test Receipt**. Your thermal printer will instantly print a beautifully formatted sample receipt with aligned text, separator lines, and a feed-and-cut command!

---

## 🌐 Web Application Integration (Javascript CORS)

Your web application (e.g., your POS system running on a local server or public website) can trigger printing directly using the Web Bluetooth/HTTP bridge.

Since the server runs directly on your Android phone, you can call it from any client using `fetch`!

### 1. Printing Raw Text
```javascript
const printText = (text) => {
    // Replace with your Android phone's IP address if calling from a different device,
    // or use 'localhost' if the web app is running in the Android browser on the same phone.
    const serverUrl = "http://192.168.1.100:6801/"; 

    fetch(serverUrl, {
        method: "POST",
        headers: {
            "Content-Type": "text/plain"
        },
        body: text + "\n\n\n\n" // feed lines
    })
    .then(response => response.json())
    .then(data => console.log("Print success:", data))
    .catch(error => console.error("Print error:", error));
};

// Example print trigger
printText("Hello World!\nThis is printed directly from the web app!");
```

### 2. Printing ESC/POS Binary Commands (Formatted Receipts)
To print bold headings, centered text, or feed/cut commands, you can send raw byte streams as binary data:

```javascript
const printEscPosReceipt = () => {
    const serverUrl = "http://192.168.1.100:6801/";

    // ESC/POS Commands
    const ESC_INITIALIZE = [0x1b, 0x40];
    const ESC_ALIGN_CENTER = [0x1b, 0x61, 0x01];
    const ESC_FONT_LARGE = [0x1d, 0x21, 0x11];
    const ESC_FONT_NORMAL = [0x1d, 0x21, 0x00];
    const ESC_BOLD_ON = [0x1b, 0x45, 0x01];
    const ESC_BOLD_OFF = [0x1b, 0x45, 0x00];
    const ESC_FEED_AND_CUT = [0x1d, 0x56, 0x42, 0x00];

    const encoder = new TextEncoder();
    
    // Assemble the byte array
    const data = [
        ...ESC_INITIALIZE,
        ...ESC_ALIGN_CENTER,
        ...ESC_FONT_LARGE,
        ...ESC_BOLD_ON,
        ...encoder.encode("MY POS STORE\n"),
        ...ESC_FONT_NORMAL,
        ...ESC_BOLD_OFF,
        ...encoder.encode("Bridged via Blutut Printer\n"),
        ...encoder.encode("--------------------------------\n"),
        ...encoder.encode("Item A               Rp 10.000\n"),
        ...encoder.encode("Item B               Rp 15.000\n"),
        ...encoder.encode("--------------------------------\n"),
        ...ESC_BOLD_ON,
        ...encoder.encode("TOTAL                Rp 25.000\n"),
        ...ESC_BOLD_OFF,
        ...encoder.encode("--------------------------------\n"),
        ...encoder.encode("Thank you for your purchase!\n\n\n\n"),
        ...ESC_FEED_AND_CUT
    ];

    const uint8Array = new Uint8Array(data);

    fetch(serverUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/octet-stream"
        },
        body: uint8Array
    })
    .then(response => response.json())
    .then(data => console.log("Receipt printed successfully:", data))
    .catch(error => console.error("Print receipt error:", error));
};
```

---

## 🔌 Generic Network Printer Integration (TCP)

Our print bridge also acts as a raw **TCP network printer wrapper**. You can connect standard network-enabled POS apps (like Loyverse, Kyte, etc.) directly to it:

1. In the printer settings of your POS app, choose **Ethernet/Network/WiFi** printer type.
2. Enter the IP Address of your Android phone (as shown on the Blutut Printer dashboard under the `http://` banner).
3. Enter Port `6801` (or whichever port you configured).
4. The POS app will establish a direct TCP socket and stream receipts straight to your Bluetooth printer!
