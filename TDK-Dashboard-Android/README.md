# TDK Dashboard Android

Aplikasi Android native ringan yang membuka dashboard operasional PT. Tigadaun Kapuas.

## Fitur

- Dashboard layar penuh
- Upload Excel melalui pemilih file Android
- Unduh PDF/Excel ke folder Downloads
- JavaScript, penyimpanan lokal, cookie, dan Supabase didukung
- Tombol Back mengikuti riwayat halaman
- Progress bar saat dashboard dimuat
- Tombol muat ulang dan bagikan dashboard
- Halaman offline dengan tombol coba lagi
- Hanya koneksi HTTPS yang diizinkan
- Ikon dan splash memakai logo TDK

## Membuat APK

1. Instal Android Studio versi terbaru.
2. Pilih **Open**, lalu buka folder `TDK-Dashboard-Android`.
3. Tunggu Gradle Sync selesai.
4. Pilih **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.

Jika Android Studio meminta versi Gradle, pilih versi yang direkomendasikan oleh Android Studio dan lanjutkan **Sync Project**.

Untuk APK rilis resmi, gunakan **Build > Generate Signed App Bundle / APK**, pilih APK, lalu buat/simpan keystore perusahaan. Jangan membagikan file keystore atau password-nya.

## Membuat APK tanpa Android Studio

1. Buat repository kosong di GitHub.
2. Unggah **isi folder** `TDK-Dashboard-Android` ke repository tersebut.
3. Buka tab **Actions** dan pilih workflow **Build APK**.
4. Tekan **Run workflow** lalu **Run workflow** sekali lagi.
5. Setelah proses berwarna hijau, buka hasil build dan unduh artifact **TDK-Dashboard-APK**.
6. Ekstrak ZIP artifact; di dalamnya terdapat `app-debug.apk` yang dapat dipasang di HP Android.

Workflow juga berjalan otomatis setiap kali source pada cabang `main` diperbarui. APK debug cocok untuk pemasangan internal. Distribusi publik melalui Play Store memerlukan APK/AAB rilis yang ditandatangani dengan keystore perusahaan.

## Pengaturan

- Nama aplikasi: `TDK Dashboard`
- Package ID: `com.tigadaunkapuas.dashboard`
- URL: `MainActivity.java`, konstanta `HOME_URL`
- Minimum Android: Android 7.0 (API 24)
