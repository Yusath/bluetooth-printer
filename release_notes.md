# 🚀 Catatan Rilis - v1.0.4 (Terbaru)

Selamat datang di rilis milestone **v1.0.4** dari **Blutut Printer (Web & TCP Print Bridge)**!

Rilis ini berfokus pada integrasi sistem pembaruan yang cerdas, modern, aman dari kehilangan data, serta penambahan indikator visual versi aplikasi dinamis pada dasbor utama.

---

## 🌟 Apa yang Baru di Rilis v1.0.4

### 🔄 1. Popup Dialog Pembaruan Modern & Custom Progress Bar (Material 3)
*   **Material 3 Dialogs**: Mengganti dialog sistem klasik yang kaku dengan **`MaterialAlertDialogBuilder`** yang memiliki sudut melengkung estetik dan tipografi modern Google Material 3 yang menyatu dengan tema gelap dasbor.
*   **LinearProgressIndicator Kustom**: Menghapus `ProgressDialog` jadul (deprecated) dan menggantinya dengan dialog kustom yang memuat `dialog_download_progress.xml`. Saat proses download berlangsung, progress bar horizontal berwarna Sky Blue (`accent_blue`) akan meluncur mulus secara real-time, lengkap dengan teks persentase dinamis (misal: "85%") dan statistik ukuran unduhan (misal: "Downloading: 104 KB / 134 KB").
*   **Auto-Redirect Instan**: Tepat ketika unduhan selesai, dialog kustom langsung tertutup otomatis dan langsung mengarahkan Anda ke layar instalasi bawaan Android tanpa ada klik jeda di dalam aplikasi.

### 🛡️ 2. Pengaturan Perlindungan Data dari Reset / Uninstal (Safety Net)
*   **Fragile User Data Protection**: Menyematkan parameter `android:hasFragileUserData="true"` di dalam berkas manifest.
*   **Tahan Banting**: Mulai Android 10+, jika Anda terpaksa menghapus instalan manual aplikasi lama (misalnya saat beralih dari versi debug kompilasi lokal ke versi release cloud), Android secara cerdas akan menawarkan kotak centang **"Keep app data? (Simpan data aplikasi?)"**. Cukup centang kotak tersebut, maka seluruh pengaturan Bluetooth printer dan port server Anda akan tetap aman dan langsung pulih otomatis saat APK versi baru dipasang!

### 📱 3. Label Versi Dinamis Dasbor Utama
*   **Dynamic Version Header**: Menambahkan label penunjuk versi aplikasi (`tvAppVersion`) tepat di bawah sub-keterangan dasbor utama. Informasi versi diambil secara dinamis dari `versionName` di Gradle sistem, sehingga akan terperbarui otomatis di rilis mendatang tanpa edit XML manual.

---

# 🚀 Catatan Rilis - v1.0.3 (Milestone UI & Watermark)

Selamat datang di rilis milestone **v1.0.3** dari **Blutut Printer (Web & TCP Print Bridge)**!

Rilis ini menghadirkan **Perombakan UI/UX Premium Modern** yang mengubah total antarmuka aplikasi dari gaya lama menjadi dasbor modern bertema gelap (*cybernetic dark-themed dashboard*). Selain itu, versi ini menyertakan fitur cetak **Watermark Cerdas BUMS STIT Riyadhussholihiin** rata tengah 2 baris.

---

## 🌟 Apa yang Baru di Rilis v1.0.3

### 📱 1. Perombakan UI/UX Premium & Modern (Menghilangkan Kesan Aplikasi Tua)
*   **Floating Navigation Dock (Bilah Menu Melayang)**: Navigasi bawah kini didesain melayang berupa kapsul rounded modern (`bg_nav_dock`) berjarak dari tepi layar dengan sudut melengkung sempurna (`32dp`) dan border neon sky-blue tipis.
*   **Active Tab Indicators**: Menambahkan garis kapsul aktif (2.5dp Sky Blue) di bawah ikon tab yang sedang aktif untuk memberikan umpan balik taktil dan responsif saat berpindah menu.
*   **Warna Transisi Modern**: Transisi aktif/non-aktif pada navigasi kini menggunakan warna Sky Blue (`accent_blue`) dan Slate Grey (`text_secondary`) alih-alih warna holo biru kuno bawaan Android lama.
*   **Dynamic Status Pill Badges**: Mengubah teks status statis yang kaku menjadi pill badge rounded dinamis yang secara otomatis berubah latar belakang:
    *   *Aktif/Berjalan (Running/Connected)*: Latar belakang gradasi Emerald-Teal dengan teks putih kontras tinggi yang bersinar (*glowing*).
    *   *Mati/Terputus (Stopped/Disconnected)*: Latar belakang Slate gelap dengan outline warna Rose/Muted Red yang elegan.
*   **Custom Dropdown Spinner**: Spinner Printer, Ukuran Kertas, dan Jeda Baris dirombak menggunakan layout kustom (`custom_spinner_item` & `custom_spinner_dropdown_item`) dengan font modern `sans-serif-medium` dan ikon penunjuk chevron minimalis, menghilangkan total drop-down sistem bawaan Android yang terlihat tua.
*   **Tata Letak Ergonomis**: Tombol pengujian printer "Test Text" (Test Text) dan "Test Image" (Test Gambar) kini diposisikan bersebelahan menyamping (*horizontal row*) secara presisi untuk menghemat ruang layar dan mempermudah akses.
*   **Monospace Console Log**: Panel log didesain ulang dengan sudut melengkung dan warna teks hijau neon `#00FFCC` yang bercahaya tajam.

### 🖨️ 2. Watermark Cerdas 2 Baris BUMS
Secara otomatis menyuntikkan catatan kaki watermark 2 baris rata tengah pada setiap struk belanja/faktur POS Anda:
```text
                  BUMS
Badan Usaha Milik STIT Riyadhussholihiin
```
*   **Font B Compact Sizing**: Watermark dicetak dalam **Font B** (`ESC M 1`), yaitu ukuran font rapat 9x17 dot yang sangat pas untuk menampung teks panjang 39 karakter pada kertas 58mm tanpa terpotong atau terlipat barisnya (*no auto-wrapping*), namun tetap terbaca dengan sangat tajam dan jelas.
*   **Smart Stream Injector**: Algoritma cerdas yang memindai 15 byte terakhir stream cetakan untuk mendeteksi perintah pemotongan kertas otomatis (`GS V`) atau penggulungan kertas (`ESC d`). Watermark akan **disuntikkan tepat sebelum perintah potong/gulung** agar tercetak sempurna di posisi paling bawah struk sebelum kertas robek/terpotong, baik dicetak via HTTP POST, TCP raw, maupun PDF System Print Service.

---

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

1.  Download the **`app-release.apk`** attached to this release.
2.  Install it manually on your Android device (this establishes the new base build with updater support).
3.  Future updates will be detected, downloaded, and installed automatically!
