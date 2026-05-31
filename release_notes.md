# 🚀 Catatan Rilis - v1.1.0 (Terbaru)

Selamat datang di rilis utama **v1.1.0** dari **Blutut Printer (Web & TCP Print Bridge)**!

Rilis ini menghadirkan fitur personalisasi struk belanja secara mandiri dengan dukungan kustomisasi **Header & Footer** kustom (teks atau gambar logo) langsung dari pengaturan aplikasi, serta penyelarasan tata letak watermark agar tidak terlalu jauh ke bawah.

---

## 🌟 Apa yang Baru di Rilis v1.1.0

### 🎨 1. Kustomisasi Header & Footer Struk (Teks / Gambar)
Kini Anda memiliki kendali penuh atas identitas brand pada struk belanja yang dicetak:
- **Header Kustom**: Menyisipkan teks tebal rata tengah atau logo gambar di bagian paling atas struk (tepat setelah inisialisasi printer).
- **Footer Kustom**: Mengganti atau menyesuaikan watermark default dengan teks khusus (misal: pesan terima kasih) atau gambar footer yang dither secara real-time.
- **Dukungan Gambar & Dithering**: Gambar logo yang dipilih akan diskalakan secara otomatis dan diproses menggunakan algoritma *Floyd-Steinberg dithering* agar tercetak dengan tingkat kontras tinggi di printer thermal.

### 💾 2. Sistem Penyimpanan & Caching Gambar yang Kokoh
- Memilih gambar logo dari galeri kini menggunakan integrasi Android Activity Result Contract yang modern.
- Gambar yang dipilih akan disalin dan disimpan secara permanen di direktori internal aplikasi (`files/custom_header.png` & `files/custom_footer.png`). Ini mencegah hilangnya izin akses URI Android saat ponsel dinyalakan ulang (*reboot*).

### 📏 3. Penyelarasan Posisi Watermark (Tinggi Ideal)
- Mengatasi masalah tulisan watermark/footer lama yang tercetak terlalu ke bawah dan terlalu dekat dengan garis potong.
- Algoritma pencetakan diperbarui untuk memindai status stream ESC/POS dan menyuntikkan footer tepat **sebelum** perintah pemotongan kertas (`GS V`) atau penggulungan kertas (`ESC d`). Hasil cetak watermark kini memiliki jarak ideal dari garis potong kertas.

### ⚙️ 4. Tampilan Menu Baru
- Panel pengaturan **Header & Footer Receipt** terintegrasi secara elegan di tab **Advanced Settings** dengan desain gelap (*cybernetic dark-themed dashboard*) yang konsisten.
- Uji coba cetak test print kini secara langsung menerapkan konfigurasi header/footer yang Anda atur.
