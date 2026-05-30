# 🖨️ Blutut Printer - Web & TCP Print Bridge
### *Badan Usaha Milik STIT Riyadhussholihiin (BUMS)*

Aplikasi penghubung (*print bridge*) native Android untuk menjembatani printer thermal Bluetooth classic (SPP) dengan sistem kasir web melalui **HTTP/CORS**, data **TCP Socket mentah**, serta **Layanan Cetak Sistem Android (PDF)**. 

Didesain dengan ukuran super ringkas (**&lt; 150 KB**), antarmuka gelap (*cybernetic dark theme*) yang premium, serta sistem otomatisasi yang aman dan tahan banting.

---

## 🌟 Fitur Unggulan

*   **Antarmuka Premium Melayang**: Navigasi kapsul melayang (*floating navigation capsule*) dengan indikator aktif neon-blue dan badge status bergradasi warna dinamis.
*   **Watermark Cerdas BUMS**: Secara otomatis menyuntikkan catatan kaki rata tengah 2 baris (`BUMS` dan `Badan Usaha Milik STIT Riyadhussholihiin`) berukuran kecil rapat (**Font B**) tepat sebelum perintah pemotongan/penggulungan di akhir struk agar tidak terpotong.
*   **Jembatan Protokol Ganda**: Server latar belakang HTTP (dengan dukungan CORS penuh) dan TCP pada port kustom untuk menerima transaksi cetak langsung dari browser web atau aplikasi kasir lokal.
*   **Perlindungan Data Pengaturan**: Dilengkapi pengaman data uninstal (`hasFragileUserData`) agar alamat printer Bluetooth dan port server kasir Anda tidak terhapus jika aplikasi diperbarui.
*   **Optimasi Aliran Data**: Penanganan *Buffer Throttling* untuk mencegah *overflow* (cetakan acak/macet) pada model printer thermal ekonomis.

---

## 📱 Panduan Singkat Penggunaan

1.  **Sambungkan Printer**: Izinkan hak akses Bluetooth. Pada tab **Printer**, pilih printer thermal Bluetooth Anda dari dropdown kustom lalu klik **Connect Printer**.
2.  **Aktifkan Server**: Pada tab **Server**, masukkan nomor Port kasir Anda lalu aktifkan **Enable HTTP & TCP Server**.
3.  **Mulai Cetak**: Jembatan cetak kasir siap menerima aliran cetakan dari aplikasi web atau POS kasir Anda.

---

## 🌐 Contoh Integrasi Aplikasi Web (JavaScript CORS)

Kirimkan biner cetak kasir standar ESC/POS langsung dari browser web POS Anda menggunakan fungsi `fetch`. Watermark cerdas BUMS akan secara otomatis disisipkan di bagian paling dasar struk sebelum pemotongan kertas:

```javascript
const cetakFakturPOS = (byteBiner) => {
    // Alamat IP ponsel Android Anda yang tertera di dasbor aplikasi
    const serverUrl = "http://192.168.1.100:6801/"; 

    fetch(serverUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/octet-stream"
        },
        body: new Uint8Array(byteBiner)
    })
    .then(res => res.json())
    .then(data => console.log("Cetak berhasil:", data))
    .catch(err => console.error("Gagal mencetak:", err));
};
```

---

## 🔌 Integrasi Kasir Jaringan (TCP) & PDF

*   **Aplikasi Kasir Jaringan (Loyverse, Kyte, dll.)**: Pada pengaturan printer aplikasi kasir Anda, pilih jenis printer **Ethernet / Network**, masukkan **Alamat IP** ponsel Anda, dan masukkan Port **`6801`**.
*   **Pencetakan Berkas PDF**: Aktifkan **Bluetooth Thermal Printer** pada menu Pengaturan Layanan Cetak Android. Anda dapat langsung mencetak berkas PDF faktur dari WhatsApp, Chrome, atau File Manager dengan opsi kontras density yang dinamis.
