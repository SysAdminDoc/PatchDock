/*
 * PatchDock's user-driven APKMirror downloader.
 *
 * Copyright (C) 2026 PatchDock contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.morphe.manager.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.morphe.manager.R
import app.morphe.manager.domain.catalog.ApkValidationIssue
import app.morphe.manager.domain.catalog.ObservedApk
import app.morphe.manager.domain.catalog.ObservedPackageFormat
import app.morphe.manager.domain.catalog.PatchDockCatalog
import app.morphe.manager.domain.catalog.StockApkSpec
import app.morphe.manager.domain.catalog.sha256Hex
import app.morphe.manager.domain.catalog.validate
import app.morphe.manager.domain.installer.InstallerFileProvider
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Opens only the catalogued APKMirror page, lets the user press APKMirror's own download button,
 * then downloads and verifies the exact stock APK inside PatchDock.
 */
class ApkMirrorDownloadActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spec: StockApkSpec
    private var downloadJob: Job? = null
    private var activeMainFrameUrl: String? = null
    private var mainFrameLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        spec = packageName?.let {
            PatchDockCatalog.downloadSpec(
                displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME),
                packageName = it,
                versionName = intent.getStringExtra(EXTRA_VERSION_NAME),
                signingCertificateSha256 = intent
                    .getStringArrayListExtra(EXTRA_SIGNING_CERTIFICATES)
                    ?.toSet(),
                apkFileTypeName = intent.getStringExtra(EXTRA_APK_FILE_TYPE),
                resolvedDownloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_PAGE_URL),
            )
        } ?: run {
            finishWithError(getString(R.string.patchdock_download_unsupported))
            return
        }

        setContentView(buildContentView())
        configureWebView()
        activeMainFrameUrl = spec.downloadPageUrl
        webView.loadUrl(spec.downloadPageUrl)

        onBackPressedDispatcher.addCallback(this) {
            when {
                downloadJob?.isActive == true -> {
                    downloadJob?.cancel()
                    finish()
                }
                webView.canGoBack() -> webView.goBack()
                else -> finish()
            }
        }
    }

    private fun buildContentView(): View {
        val padding = 16.dp
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolveColor(android.R.attr.colorBackground, Color.BLACK))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, 8.dp, 8.dp, 8.dp)
        }
        toolbar.addView(TextView(this).apply {
            text = getString(R.string.patchdock_download_title, spec.displayName, spec.versionName)
            textSize = 18f
            setTextColor(resolveColor(android.R.attr.textColorPrimary, Color.WHITE))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        toolbar.addView(Button(this).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { finish() }
        })
        root.addView(toolbar)

        statusText = TextView(this).apply {
            text = getString(R.string.patchdock_download_instructions)
            setPadding(padding, 8.dp, padding, 12.dp)
            setTextColor(resolveColor(android.R.attr.textColorSecondary, Color.LTGRAY))
        }
        root.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                6.dp,
            ).apply {
                marginStart = padding
                marginEnd = padding
                bottomMargin = 8.dp
            }
        }
        root.addView(progressBar)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(webView)
        return root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return if (isAllowedApkMirrorUrl(request.url)) {
                    activeMainFrameUrl = request.url.toString()
                    mainFrameLoadFailed = false
                    false
                } else {
                    statusText.text = getString(R.string.patchdock_download_blocked_domain)
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank() && isAllowedApkMirrorUrl(url.toUri())) {
                    activeMainFrameUrl = url
                    mainFrameLoadFailed = false
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (downloadJob?.isActive != true && !mainFrameLoadFailed) {
                    statusText.text = getString(R.string.patchdock_download_instructions)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    mainFrameLoadFailed = true
                    statusText.text = getString(
                        if (error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE) {
                            R.string.patchdock_download_tls_failed
                        } else {
                            R.string.patchdock_download_page_failed
                        },
                    )
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError,
            ) {
                // Never bypass a bad certificate. WebView reports TLS failures for subresources
                // here as well as for the main document, so cancelling an ad/iframe must not
                // abort an otherwise secure APKMirror page.
                handler.cancel()
                val isMainFrameFailure = isMainFrameTlsFailure(error.url, activeMainFrameUrl)
                val failingHost = runCatching { error.url.toUri().host }.getOrNull() ?: "unknown"
                Log.w(
                    TAG,
                    "Blocked WebView TLS error code=${error.primaryError}, " +
                        "host=$failingHost, mainFrame=$isMainFrameFailure",
                )
                if (isMainFrameFailure) {
                    mainFrameLoadFailed = true
                    statusText.text = getString(R.string.patchdock_download_tls_failed)
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.isNullOrBlank() || !isAllowedApkMirrorUrl(url.toUri())) {
                finishWithError(getString(R.string.patchdock_download_blocked_domain))
                return@setDownloadListener
            }
            if (downloadJob?.isActive == true) return@setDownloadListener
            beginDownload(
                url = url,
                userAgent = userAgent.orEmpty().ifBlank { webView.settings.userAgentString },
                referer = webView.url ?: spec.downloadPageUrl,
                reportedLength = contentLength,
                suggestedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType),
                mimeType = mimeType.orEmpty(),
            )
        }
    }

    private fun beginDownload(
        url: String,
        userAgent: String,
        referer: String,
        reportedLength: Long,
        suggestedFileName: String,
        mimeType: String,
    ) {
        webView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.text = getString(R.string.patchdock_download_starting)

        downloadJob = lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    downloadAndVerify(
                        url,
                        userAgent,
                        referer,
                        reportedLength,
                        suggestedFileName,
                        mimeType,
                    )
                }
            }.onSuccess { file ->
                val uri = InstallerFileProvider.buildUri(this@ApkMirrorDownloadActivity, file.name)
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
                finish()
            }.onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) return@onFailure
                finishWithError(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.patchdock_download_failed),
                )
            }
        }
    }

    private suspend fun downloadAndVerify(
        url: String,
        userAgent: String,
        referer: String,
        reportedLength: Long,
        suggestedFileName: String,
        mimeType: String,
    ): File {
        validateDownloadLength(reportedLength)
        var partial: File? = null

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 5 * 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Encoding", "identity")
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Cookie", it)
            }
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException(getString(R.string.patchdock_download_http_failed, responseCode))
            }
            // HttpURLConnection follows redirects itself. Re-check the final URL so a valid
            // APKMirror link cannot bounce the authenticated download onto an untrusted host.
            if (!isAllowedApkMirrorUrl(connection.url.toString().toUri())) {
                throw SecurityException(getString(R.string.patchdock_download_blocked_domain))
            }
            val responseLength = connection.contentLengthLong
            validateDownloadLength(responseLength)

            val responseFileName = URLUtil.guessFileName(
                connection.url.toString(),
                connection.getHeaderField("Content-Disposition"),
                connection.contentType,
            )
            val extension = PatchDockCatalog.packageFileExtension(
                responseFileName,
                connection.contentType,
            ) ?: PatchDockCatalog.packageFileExtension(suggestedFileName, mimeType)
                ?: spec.preferredFileExtension
            val safeStem = "${spec.packageName}-${spec.versionName}"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(120)
            val shareDir = File(cacheDir, InstallerFileProvider.SHARE_DIR).also { it.mkdirs() }
            val partialFile = File(shareDir, "$safeStem.partial.$extension").also { it.delete() }
            val verified = File(shareDir, "$safeStem-stock.$extension")
            partial = partialFile

            var copied = 0L
            var lastUiUpdate = 0L
            val maximumLength = spec.fileSizeBytes ?: PatchDockCatalog.MAX_DOWNLOAD_BYTES
            val progressLength = spec.fileSizeBytes
                ?: responseLength.takeIf { it > 0 }
                ?: reportedLength.takeIf { it > 0 }
            connection.inputStream.buffered().use { input ->
                FileOutputStream(partialFile).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied > maximumLength) {
                            throw SecurityException(getString(R.string.patchdock_download_size_rejected))
                        }
                        val now = System.currentTimeMillis()
                        if (progressLength != null && now - lastUiUpdate >= 250L) {
                            lastUiUpdate = now
                            val progress = ((copied * 100L) / progressLength).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = progress
                                statusText.text = getString(R.string.patchdock_download_progress, progress)
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.isIndeterminate = true
                statusText.text = getString(R.string.patchdock_download_verifying)
            }

            val observed = inspectApk(partialFile)
            val issues = spec.validate(observed)
            if (issues.isNotEmpty()) {
                throw SecurityException(validationMessage(issues))
            }

            verified.delete()
            if (!partialFile.renameTo(verified)) {
                partialFile.copyTo(verified, overwrite = true)
                partialFile.delete()
            }
            return verified
        } catch (throwable: Throwable) {
            partial?.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun validateDownloadLength(length: Long) {
        if (length <= 0) return
        val expected = spec.fileSizeBytes
        if (length > (expected ?: PatchDockCatalog.MAX_DOWNLOAD_BYTES) ||
            (expected != null && length != expected)
        ) {
            throw SecurityException(getString(R.string.patchdock_download_size_rejected))
        }
    }

    private suspend fun inspectApk(file: File): ObservedApk {
        if (!SplitApkPreparer.isSplitArchive(file)) {
            val parsed = parseApk(file)
            return ObservedApk(
                packageName = parsed.packageName,
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                fileSizeBytes = file.length(),
                fileSha256 = file.sha256Hex(),
                signingCertificateSha256 = parsed.signatures,
                packageFormat = if (parsed.valid) {
                    ObservedPackageFormat.APK
                } else {
                    ObservedPackageFormat.UNKNOWN
                },
            )
        }

        val extracted = SplitApkInspector.extractRepresentativeApk(file, cacheDir)
            ?: return ObservedApk(
                packageName = null,
                versionName = null,
                versionCode = null,
                fileSizeBytes = file.length(),
                fileSha256 = file.sha256Hex(),
                signingCertificateSha256 = emptySet(),
                packageFormat = ObservedPackageFormat.UNKNOWN,
                archiveEntriesConsistent = false,
            )

        return try {
            val representative = parseApk(extracted.file)
            val archive = inspectArchiveModules(file, representative)
            ObservedApk(
                packageName = representative.packageName,
                versionName = representative.versionName,
                versionCode = representative.versionCode,
                fileSizeBytes = file.length(),
                fileSha256 = file.sha256Hex(),
                signingCertificateSha256 = archive.commonSignatures,
                packageFormat = if (representative.valid) {
                    ObservedPackageFormat.SPLIT_ARCHIVE
                } else {
                    ObservedPackageFormat.UNKNOWN
                },
                archiveEntriesConsistent = archive.entriesConsistent,
            )
        } finally {
            extracted.cleanup()
        }
    }

    /**
     * Verifies every root APK module that the split merger will consume. Intersecting signer sets
     * means a trusted signer must be present on every module, not just on the representative base.
     */
    private fun inspectArchiveModules(
        archiveFile: File,
        representative: ParsedApk,
    ): ArchiveModuleInspection {
        val workspace = File(cacheDir, "verify-splits-${UUID.randomUUID()}").also { it.mkdirs() }
        return try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .filter { !it.name.contains('/') && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                if (entries.isEmpty() || entries.size > MAX_SPLIT_APK_ENTRIES) {
                    return@use ArchiveModuleInspection(emptySet(), false)
                }

                var commonSignatures: Set<String>? = null
                var entriesConsistent = representative.valid
                var totalExpandedBytes = 0L

                entries.forEachIndexed { index, entry ->
                    if (entry.size > MAX_EXPANDED_SPLIT_BYTES) {
                        throw SecurityException(getString(R.string.patchdock_download_size_rejected))
                    }
                    val module = File(workspace, "module-$index.apk")
                    zip.getInputStream(entry).buffered().use { input ->
                        FileOutputStream(module).buffered().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                totalExpandedBytes += read
                                if (totalExpandedBytes > MAX_EXPANDED_SPLIT_BYTES) {
                                    throw SecurityException(
                                        getString(R.string.patchdock_download_size_rejected),
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    val parsed = parseApk(module)
                    entriesConsistent = entriesConsistent &&
                        parsed.valid &&
                        parsed.packageName == representative.packageName &&
                        parsed.versionName == representative.versionName &&
                        parsed.versionCode == representative.versionCode
                    commonSignatures = commonSignatures
                        ?.intersect(parsed.signatures)
                        ?: parsed.signatures
                }

                ArchiveModuleInspection(commonSignatures.orEmpty(), entriesConsistent)
            }
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    private fun parseApk(file: File): ParsedApk {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        val signatures = when {
            info == null -> emptyArray()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                info.signingInfo?.apkContentsSigners ?: emptyArray()
            else -> info.signatures ?: emptyArray()
        }
        val certificateDigests = signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        val hasManifest = runCatching {
            ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null }
        }.getOrDefault(false)

        return ParsedApk(
            packageName = info?.packageName,
            versionName = info?.versionName,
            versionCode = info?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
            },
            signatures = certificateDigests,
            valid = info != null && hasManifest,
        )
    }

    private data class ParsedApk(
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val signatures: Set<String>,
        val valid: Boolean,
    )

    private data class ArchiveModuleInspection(
        val commonSignatures: Set<String>,
        val entriesConsistent: Boolean,
    )

    private fun validationMessage(issues: Set<ApkValidationIssue>): String {
        val fields = issues.joinToString(", ") { it.name.lowercase(Locale.US).replace('_', ' ') }
        return getString(R.string.patchdock_download_integrity_rejected, fields)
    }

    private fun isAllowedApkMirrorUrl(uri: Uri): Boolean {
        return PatchDockCatalog.isTrustedApkMirrorUrl(uri.toString())
    }

    private fun finishWithError(message: String) {
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR, message),
        )
        finish()
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val value = android.util.TypedValue()
        return if (theme.resolveAttribute(attribute, value, true)) value.data else fallback
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        downloadJob?.cancel()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PatchDockDownloader"
        private const val MAX_SPLIT_APK_ENTRIES = 256
        private const val MAX_EXPANDED_SPLIT_BYTES = 2_000_000_000L
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_VERSION_NAME = "version_name"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_SIGNING_CERTIFICATES = "signing_certificates"
        private const val EXTRA_APK_FILE_TYPE = "apk_file_type"
        private const val EXTRA_DOWNLOAD_PAGE_URL = "download_page_url"
        const val EXTRA_ERROR = "download_error"

        fun createIntent(
            context: Context,
            displayName: String?,
            packageName: String,
            versionName: String?,
            signingCertificates: Set<String>?,
            apkFileTypeName: String?,
            resolvedDownloadUrl: String?,
        ): Intent =
            Intent(context, ApkMirrorDownloadActivity::class.java).apply {
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_VERSION_NAME, versionName)
                putStringArrayListExtra(
                    EXTRA_SIGNING_CERTIFICATES,
                    signingCertificates?.let { ArrayList(it) },
                )
                putExtra(EXTRA_APK_FILE_TYPE, apkFileTypeName)
                putExtra(EXTRA_DOWNLOAD_PAGE_URL, resolvedDownloadUrl)
            }
    }
}

internal fun isMainFrameTlsFailure(errorUrl: String?, activeMainFrameUrl: String?): Boolean {
    if (errorUrl.isNullOrBlank() || activeMainFrameUrl.isNullOrBlank()) return false
    return errorUrl.substringBefore('#') == activeMainFrameUrl.substringBefore('#')
}
