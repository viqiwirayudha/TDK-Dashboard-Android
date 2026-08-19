package com.tigadaun.tdkdashboard

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val DASHBOARD_URL = "https://dashboard-operasional.tigadaunkapuasplasma.workers.dev/"
        private const val DASHBOARD_HOST = "dashboard-operasional.tigadaunkapuasplasma.workers.dev"
        private const val SUPABASE_HOST = "nyqjecnlotuvwacbukiy.supabase.co"
        private const val OFFLINE_URL = "file:///android_asset/index.html"
        private const val FILE_CHOOSER_REQUEST = 9001
        private const val EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val PREFS = "tdk_download_state"
        private const val PREF_ACTIVE_ID = "active_id"
        private const val PREF_ACTIVE_NAME = "active_name"
        private const val PREF_LAST_ID = "last_id"
        private const val PREF_LAST_NAME = "last_name"
    }

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var showingOfflineFallback = false
    private val handler = Handler(Looper.getMainLooper())
    private var activeDownloadId: Long = -1L
    private var activeDownloadName: String = ""
    private var lastDownloadId: Long = -1L
    private var lastDownloadName: String = ""
    private var completionDialogShownForId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreDownloadState()
        webView = WebView(this)
        setContentView(webView)
        configureWebView()
        openDashboard()
        resumeActiveDownloadIfAny()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (isOnline()) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        // Dashboard is a fixed trusted origin. Native methods below also validate download hosts.
        webView.addJavascriptInterface(AndroidBridge(), "AndroidTDK")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = EXCEL_MIME
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(EXCEL_MIME, "application/vnd.ms-excel"))
                        }
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    Toast.makeText(this@MainActivity, "Pemilih file tidak tersedia", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
                val host = uri.host.orEmpty().lowercase(Locale.US)

                if (scheme == "file") return false
                if ((scheme == "https" || scheme == "http") && host == DASHBOARD_HOST) return false

                // Fallback for HTML versions that navigate directly to the public XLSX URL.
                if (scheme == "https" && host == SUPABASE_HOST && looksLikeDashboardExcel(uri)) {
                    startNativeDownload(uri.toString(), guessDownloadName(uri.toString(), null, EXCEL_MIME))
                    return true
                }

                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                showingOfflineFallback = url.startsWith("file:///android_asset/")
                notifyNativeReady()
                pushCurrentDownloadStateToHtml()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && !showingOfflineFallback) {
                    showingOfflineFallback = true
                    view.loadUrl(OFFLINE_URL)
                    Toast.makeText(this@MainActivity, "Offline: membuka dashboard cadangan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Safety net: if WebView itself recognizes a downloadable response, route it to DownloadManager.
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val safeName = guessDownloadName(url, contentDisposition, mimeType)
            startNativeDownload(url, safeName)
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun downloadFile(url: String, fileName: String) {
            runOnUiThread { startNativeDownload(url, fileName) }
        }

        @JavascriptInterface
        fun openLastDownloadedFile() {
            runOnUiThread { openLastDownloadedFileNative() }
        }

        @JavascriptInterface
        fun openDownloadedFile(fileName: String) {
            runOnUiThread { openLastDownloadedFileNative(fileName) }
        }

        @JavascriptInterface
        fun getDownloadState(): String {
            return when {
                activeDownloadId > 0 -> "downloading"
                lastDownloadId > 0 -> "complete"
                else -> "idle"
            }
        }
    }

    private fun startNativeDownload(rawUrl: String, requestedFileName: String) {
        try {
            val uri = Uri.parse(rawUrl)
            if (!isAllowedDownloadUri(uri)) {
                sendDownloadFailed("Alamat download tidak diizinkan.")
                return
            }
            if (activeDownloadId > 0) {
                pushCurrentDownloadStateToHtml()
                Toast.makeText(this, "Download masih berjalan", Toast.LENGTH_SHORT).show()
                return
            }

            val stamped = uniqueDownloadName(requestedFileName.ifBlank { "DataDashboard_Public_ValueOnly.xlsx" })
            val request = DownloadManager.Request(uri)
                .setTitle(stamped)
                .setDescription("Mengunduh data TDK Dashboard")
                .setMimeType(EXCEL_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, stamped)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            activeDownloadId = dm.enqueue(request)
            activeDownloadName = stamped
            persistDownloadState()
            sendProgress(0, "Download dimulai • menyimpan ke folder Download…")
            Toast.makeText(this, "Download dimulai", Toast.LENGTH_SHORT).show()
            pollDownload(dm, activeDownloadId)
        } catch (e: Exception) {
            clearActiveDownload()
            sendDownloadFailed(e.message ?: "Gagal memulai download")
        }
    }

    private fun pollDownload(dm: DownloadManager, id: Long) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (id != activeDownloadId) return
                val terminal = queryAndPublishDownloadState(dm, id)
                if (!terminal && id == activeDownloadId) handler.postDelayed(this, 500)
            }
        }, 250)
    }

    private fun queryAndPublishDownloadState(dm: DownloadManager, id: Long): Boolean {
        var c: Cursor? = null
        try {
            c = dm.query(DownloadManager.Query().setFilterById(id))
            if (c == null || !c.moveToFirst()) {
                sendProgress(0, "Menunggu Download Manager Android…")
                return false
            }

            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val pct = if (total > 0) ((done * 100 / total).coerceIn(0, 99)).toInt() else 0

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    lastDownloadId = id
                    lastDownloadName = activeDownloadName
                    clearActiveDownload(keepLast = true)
                    persistDownloadState()
                    sendJs("window.TDKAndroidDownloadComplete&&window.TDKAndroidDownloadComplete(${jsQuote(lastDownloadName)})")
                    val uri = dm.getUriForDownloadedFile(id)
                    if (completionDialogShownForId != id) {
                        completionDialogShownForId = id
                        showDownloadComplete(uri, lastDownloadName)
                    }
                    return true
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    clearActiveDownload()
                    sendDownloadFailed("Download Android gagal (kode $reason)")
                    return true
                }
                DownloadManager.STATUS_PAUSED -> sendProgress(pct, "Download dijeda Android…")
                DownloadManager.STATUS_PENDING -> sendProgress(pct, "Menunggu Download Manager Android…")
                DownloadManager.STATUS_RUNNING -> {
                    val detail = if (total > 0) {
                        "Mengunduh ${formatBytes(done)} / ${formatBytes(total)}…"
                    } else {
                        "Mengunduh ${formatBytes(done)}…"
                    }
                    sendProgress(pct, detail)
                }
            }
        } catch (e: Exception) {
            clearActiveDownload()
            sendDownloadFailed(e.message ?: "Gagal membaca progress download")
            return true
        } finally {
            c?.close()
        }
        return false
    }

    private fun resumeActiveDownloadIfAny() {
        if (activeDownloadId <= 0) return
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val terminal = queryAndPublishDownloadState(dm, activeDownloadId)
        if (!terminal && activeDownloadId > 0) pollDownload(dm, activeDownloadId)
    }

    private fun pushCurrentDownloadStateToHtml() {
        if (!::webView.isInitialized) return
        if (activeDownloadId > 0) {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            queryAndPublishDownloadState(dm, activeDownloadId)
        } else if (lastDownloadId > 0 && lastDownloadName.isNotBlank()) {
            // Do not force the completion panel on every page load, only advertise native readiness.
            notifyNativeReady()
        }
    }

    private fun sendProgress(pct: Int, status: String) {
        sendJs("window.TDKAndroidDownloadProgress&&window.TDKAndroidDownloadProgress(${pct.coerceIn(0, 99)},${jsQuote(status)})")
    }

    private fun sendDownloadFailed(message: String) {
        sendJs("window.TDKAndroidDownloadFailed&&window.TDKAndroidDownloadFailed(${jsQuote(message)})")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun notifyNativeReady() {
        sendJs("window.TDKAndroidNativeReady=true")
    }

    private fun showDownloadComplete(uri: Uri?, name: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("✅ 100% DOWNLOAD SELESAI")
            .setMessage("$name\n\nTersimpan di Penyimpanan Internal / Download.")
            .setPositiveButton("Buka File") { _, _ ->
                if (uri != null) openDownloadedFile(uri)
                else openLastDownloadedFileNative()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun openLastDownloadedFileNative(preferredName: String? = null) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = lastDownloadId
        if (id > 0) {
            val uri = dm.getUriForDownloadedFile(id)
            if (uri != null) {
                openDownloadedFile(uri)
                return
            }
        }
        val name = preferredName?.takeIf { it.isNotBlank() } ?: lastDownloadName
        Toast.makeText(
            this,
            if (name.isNotBlank()) "Buka File Manager → Download → $name" else "Belum ada file download yang selesai",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openDownloadedFile(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, EXCEL_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi Excel yang dapat membuka file ini", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAllowedDownloadUri(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == SUPABASE_HOST || host == DASHBOARD_HOST
    }

    private fun looksLikeDashboardExcel(uri: Uri): Boolean {
        val p = uri.path.orEmpty().lowercase(Locale.US)
        return p.endsWith(".xlsx") || p.contains("dashboard-public") || p.contains("datadashboard_public_valueonly")
    }

    private fun guessDownloadName(url: String, contentDisposition: String?, mimeType: String?): String {
        val guessed = try { URLUtil.guessFileName(url, contentDisposition, mimeType) } catch (_: Exception) { "" }
        return if (guessed.endsWith(".xlsx", true)) guessed else "DataDashboard_Public_ValueOnly.xlsx"
    }

    private fun uniqueDownloadName(name: String): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ".xlsx"
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${base}_${stamp}${ext}"
    }

    private fun formatBytes(v: Long): String = when {
        v >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", v / 1048576.0)
        v >= 1024L -> String.format(Locale.US, "%.0f KB", v / 1024.0)
        else -> "$v B"
    }

    private fun jsQuote(s: String): String = "'" + s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ") + "'"

    private fun sendJs(code: String) {
        runOnUiThread {
            if (::webView.isInitialized) webView.evaluateJavascript(code, null)
        }
    }

    private fun restoreDownloadState() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        activeDownloadId = p.getLong(PREF_ACTIVE_ID, -1L)
        activeDownloadName = p.getString(PREF_ACTIVE_NAME, "") ?: ""
        lastDownloadId = p.getLong(PREF_LAST_ID, -1L)
        lastDownloadName = p.getString(PREF_LAST_NAME, "") ?: ""
    }

    private fun persistDownloadState() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PREF_ACTIVE_ID, activeDownloadId)
            .putString(PREF_ACTIVE_NAME, activeDownloadName)
            .putLong(PREF_LAST_ID, lastDownloadId)
            .putString(PREF_LAST_NAME, lastDownloadName)
            .apply()
    }

    private fun clearActiveDownload(keepLast: Boolean = false) {
        activeDownloadId = -1L
        activeDownloadName = ""
        if (!keepLast) {
            // Keep previous successful file available for the Buka File button.
        }
        persistDownloadState()
    }

    private fun openDashboard() {
        showingOfflineFallback = false
        webView.settings.cacheMode = if (isOnline()) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.loadUrl(DASHBOARD_URL)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && showingOfflineFallback && isOnline()) openDashboard()
        if (activeDownloadId > 0) resumeActiveDownloadIfAny()
    }

    @Deprecated("Deprecated in Android API, retained for Android 7+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val result = if (resultCode == RESULT_OK) {
                data?.data?.let { arrayOf(it) }
                    ?: data?.clipData?.let { clip -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri } }
            } else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
