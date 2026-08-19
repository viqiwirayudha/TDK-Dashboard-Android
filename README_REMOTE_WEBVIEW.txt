PT TIGADAUN KAPUAS - TDK DASHBOARD ANDROID V18.4
=================================================

TUJUAN V18.4
------------
Memperbaiki download Android yang sebelumnya berhenti pada 96%.
APK V18.4 mempunyai DownloadManager native dan mengirim progress nyata kembali ke HTML.

URL DASHBOARD
-------------
https://dashboard-operasional.tigadaunkapuasplasma.workers.dev/

FITUR DOWNLOAD V18.4
--------------------
1. HTML memanggil AndroidTDK.downloadFile(url, fileName).
2. Android DownloadManager mengunduh langsung ke folder Download.
3. APK membaca byte yang sudah diunduh dan total byte.
4. APK mengirim progress 0-99% ke HTML melalui:
   window.TDKAndroidDownloadProgress(...)
5. Saat DownloadManager benar-benar SUCCESSFUL, APK mengirim:
   window.TDKAndroidDownloadComplete(...)
6. HTML berubah menjadi 100% DOWNLOAD SELESAI.
7. Android menampilkan dialog:
   Buka File / Tutup.
8. Tombol Buka File pada HTML juga dapat memanggil AndroidTDK.openLastDownloadedFile().
9. Status download disimpan sementara sehingga saat aplikasi diminimalkan lalu dibuka lagi,
   APK dapat melanjutkan pengecekan download yang masih aktif.
10. DownloadManager tetap menampilkan notifikasi Android.

LOKASI FILE
-----------
Penyimpanan Internal / Download

Nama file diberi timestamp agar file lama tidak tertimpa, contoh:
DataDashboard_Public_ValueOnly_20260819_225500.xlsx

UPLOAD EXCEL OPERATOR
---------------------
Pemilih file Android tetap tersedia untuk menu Operator.

OFFLINE
-------
Jika dashboard online gagal dibuka, APK memuat cadangan HTML dari app/src/main/assets/index.html.
File cadangan pada paket ini memakai Dashboard V18.3.

CATATAN KEAMANAN
----------------
Native download hanya menerima URL HTTPS dari:
- dashboard-operasional.tigadaunkapuasplasma.workers.dev
- nyqjecnlotuvwacbukiy.supabase.co

Tidak ada Supabase service-role key/secret key di APK.
