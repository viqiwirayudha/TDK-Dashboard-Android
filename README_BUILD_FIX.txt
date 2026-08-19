TDK Dashboard Android V18.4 - BUILD FIX

Penyebab APK lama:
Workflow lama mengekstrak TDK-Dashboard-Source.zip lalu membuild folder TDK-Dashboard-Android, sehingga source V18.4 yang ada di root repository tidak ikut dibuild.

Perbaikan:
Workflow baru build langsung source root repository:
  gradle assembleDebug --stacktrace

Hasil artifact:
  TDK-Dashboard-V18.4.zip
  berisi TDK-Dashboard-V18.4.apk

Langkah:
1. Upload seluruh isi paket ini ke root repository viqiwirayudha/TDK-Dashboard-Android dan overwrite file lama.
2. Commit changes.
3. Actions -> Build APK V18.4 -> Run workflow.
4. Tunggu hijau.
5. Download artifact TDK-Dashboard-V18.4.
6. Extract, install TDK-Dashboard-V18.4.apk.
