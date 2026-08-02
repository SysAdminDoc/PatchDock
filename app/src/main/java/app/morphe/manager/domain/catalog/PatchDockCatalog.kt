/*
 * PatchDock catalog.
 *
 * Copyright (C) 2026 PatchDock contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.morphe.manager.domain.catalog

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/** How a patch bundle expects the unmodified app to be packaged. */
enum class StockPackageFormatPolicy {
    APK_ONLY,
    SPLIT_ARCHIVE_ONLY,
    EITHER,
}

/** The package shape observed after downloading and parsing the file. */
enum class ObservedPackageFormat {
    APK,
    SPLIT_ARCHIVE,
    UNKNOWN,
}

/**
 * A stock package that PatchDock is prepared to download and patch.
 *
 * Exact catalog entries pin every nullable artifact field. Bundle-derived entries deliberately
 * leave those fields null, but still require package, version, package shape, and a publisher
 * certificate declared by an enabled patch bundle.
 */
data class StockApkSpec(
    val displayName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long?,
    val minSdk: Int?,
    val downloadPageUrl: String,
    val fileSizeBytes: Long?,
    val fileSha256: String?,
    val signingCertificateSha256: Set<String>,
    val formatPolicy: StockPackageFormatPolicy,
) {
    val isArtifactPinned: Boolean
        get() = versionCode != null && fileSizeBytes != null && fileSha256 != null

    val preferredFileExtension: String
        get() = if (formatPolicy == StockPackageFormatPolicy.SPLIT_ARCHIVE_ONLY) "apkm" else "apk"
}

/** A pinned patch source and the release digests accepted from it. */
data class PatchSourceSpec(
    val displayName: String,
    val repositoryUrl: String,
    val manifestUrl: String,
    val releaseSha256ByVersion: Map<String, String>,
)

/** Facts observed after Android has parsed a downloaded APK or split archive. */
data class ObservedApk(
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val fileSizeBytes: Long,
    val fileSha256: String,
    val signingCertificateSha256: Set<String>,
    val packageFormat: ObservedPackageFormat,
    val archiveEntriesConsistent: Boolean = true,
)

enum class ApkValidationIssue {
    PACKAGE_FORMAT,
    ARCHIVE_CONTENTS,
    PACKAGE_NAME,
    VERSION_NAME,
    VERSION_CODE,
    FILE_SIZE,
    FILE_SHA256,
    SIGNING_CERTIFICATE,
}

/**
 * Versioned trust roots shipped with PatchDock.
 *
 * TikTok remains an exact byte-for-byte artifact pin. Other apps are discovered from enabled
 * patch bundles and become eligible for the in-app downloader only when the bundle supplies a
 * valid SHA-256 publisher-certificate fingerprint. Built-in patch bundles are also release-pinned.
 */
object PatchDockCatalog {
    const val MANAGER_REPOSITORY_URL = "https://github.com/SysAdminDoc/patchdock-manager"
    const val BUNDLED_SOURCE_CATALOG_VERSION = 1
    const val MAX_DOWNLOAD_BYTES = 1_500_000_000L

    private val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    private val ANDROID_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val PACKAGE_EXTENSIONS = setOf("apk", "apkm", "apks", "xapk")

    val tiktokPatches = PatchSourceSpec(
        displayName = "TikTok Patches",
        repositoryUrl = "https://github.com/icysymmetra/tiktok-patches-for-morphe",
        manifestUrl = "https://raw.githubusercontent.com/icysymmetra/tiktok-patches-for-morphe/main/patches-bundle.json",
        releaseSha256ByVersion = mapOf(
            "0.4.1" to "58510605b618b6b5fe7bf9ae2d284857a07aa7c359cea189dcfb09efc7bb147c",
        ),
    )

    val officialMorphePatches = PatchSourceSpec(
        displayName = "Morphe Patches",
        repositoryUrl = "https://github.com/MorpheApp/morphe-patches",
        manifestUrl = "https://raw.githubusercontent.com/MorpheApp/morphe-patches/main/patches-bundle.json",
        releaseSha256ByVersion = mapOf(
            "1.38.0" to "31d088b81414c65b9294e33feb3d5d7f14de3c694d84d74388ca3b22afc331f0",
        ),
    )

    val builtInPatchSources: List<PatchSourceSpec> = listOf(tiktokPatches, officialMorphePatches)

    val tiktok4383 = StockApkSpec(
        displayName = "TikTok",
        packageName = "com.zhiliaoapp.musically",
        versionName = "43.8.3",
        versionCode = 2_024_308_030L,
        minSdk = 23,
        downloadPageUrl = "https://www.apkmirror.com/apk/tiktok-pte-ltd/tik-tok-including-musical-ly/tiktok-43-8-3-release/tiktok-43-8-3-2-android-apk-download/",
        fileSizeBytes = 477_739_554L,
        fileSha256 = "ff30d4d43eb2e5764a6ea1cd022168811553052c3a37b5b65caba079ba026bb9",
        signingCertificateSha256 = setOf(
            "9041803e91bcb814b4b4399fb5c85a91640b755e5e8ba76813814bf4cf2ab5ba",
        ),
        formatPolicy = StockPackageFormatPolicy.APK_ONLY,
    )

    val stockApks: List<StockApkSpec> = listOf(tiktok4383)

    fun stockApk(packageName: String, versionName: String? = null): StockApkSpec? =
        stockApks.firstOrNull {
            it.packageName == packageName && (versionName == null || it.versionName == versionName)
        }

    /**
     * Builds a downloader policy for an app target exposed by an enabled patch bundle.
     *
     * Unknown targets fail closed unless they have an exact version and every declared signing
     * fingerprint is a syntactically valid SHA-256 value. A resolved APKMirror URL is used when
     * available; otherwise the restricted WebView opens an APKMirror-only search.
     */
    fun downloadSpec(
        displayName: String?,
        packageName: String,
        versionName: String?,
        signingCertificateSha256: Set<String>?,
        apkFileTypeName: String?,
        resolvedDownloadUrl: String? = null,
    ): StockApkSpec? {
        stockApk(packageName, versionName)?.let { return it }

        val normalizedPackage = packageName.trim()
        val normalizedVersion = versionName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!ANDROID_PACKAGE.matches(normalizedPackage)) return null

        val rawSignatures = signingCertificateSha256.orEmpty()
        if (rawSignatures.isEmpty() || rawSignatures.any { !SHA_256.matches(it) }) return null
        val signatures = rawSignatures.mapTo(linkedSetOf()) { it.lowercase(Locale.US) }

        val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedPackage
        val pageUrl = resolvedDownloadUrl
            ?.takeIf(::isTrustedApkMirrorUrl)
            ?: apkMirrorSearchUrl(name, normalizedVersion)

        return StockApkSpec(
            displayName = name,
            packageName = normalizedPackage,
            versionName = normalizedVersion,
            versionCode = null,
            minSdk = null,
            downloadPageUrl = pageUrl,
            fileSizeBytes = null,
            fileSha256 = null,
            signingCertificateSha256 = signatures,
            formatPolicy = formatPolicy(apkFileTypeName),
        )
    }

    fun patchSource(endpoint: String): PatchSourceSpec? = builtInPatchSources.firstOrNull {
        endpoint.equals(it.manifestUrl, ignoreCase = true)
    }

    fun expectedPatchBundleSha256(endpoint: String, version: String): String? =
        patchSource(endpoint)?.releaseSha256ByVersion?.get(version)

    /** Exact origin policy used by both WebView navigation and redirected downloads. */
    fun isTrustedApkMirrorUrl(rawUrl: String): Boolean = runCatching {
        val uri = URI(rawUrl)
        val host = uri.host?.lowercase(Locale.US) ?: return@runCatching false
        uri.scheme.equals("https", ignoreCase = true) &&
            (host == "apkmirror.com" || host.endsWith(".apkmirror.com"))
    }.getOrDefault(false)

    fun apkMirrorSearchUrl(displayName: String, versionName: String): String {
        val query = URLEncoder.encode("$displayName $versionName", Charsets.UTF_8.name())
        return "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=$query"
    }

    /** Returns a supported APK-family extension inferred from a name or MIME type. */
    fun packageFileExtension(fileName: String?, mimeType: String?): String? {
        val extension = fileName
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
        if (extension in PACKAGE_EXTENSIONS) return extension

        return when (mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)) {
            "application/vnd.android.package-archive" -> "apk"
            "application/vnd.android.apkm", "application/x-apkm" -> "apkm"
            "application/vnd.android.apks", "application/x-apks" -> "apks"
            "application/vnd.android.xapk", "application/x-xapk" -> "xapk"
            else -> null
        }
    }

    private fun formatPolicy(apkFileTypeName: String?): StockPackageFormatPolicy = when {
        apkFileTypeName == "APK_REQUIRED" -> StockPackageFormatPolicy.APK_ONLY
        apkFileTypeName?.endsWith("_REQUIRED") == true -> StockPackageFormatPolicy.SPLIT_ARCHIVE_ONLY
        else -> StockPackageFormatPolicy.EITHER
    }
}

fun StockApkSpec.validate(observed: ObservedApk): Set<ApkValidationIssue> = buildSet {
    val formatMatches = when (formatPolicy) {
        StockPackageFormatPolicy.APK_ONLY -> observed.packageFormat == ObservedPackageFormat.APK
        StockPackageFormatPolicy.SPLIT_ARCHIVE_ONLY ->
            observed.packageFormat == ObservedPackageFormat.SPLIT_ARCHIVE
        StockPackageFormatPolicy.EITHER -> observed.packageFormat != ObservedPackageFormat.UNKNOWN
    }
    if (!formatMatches) add(ApkValidationIssue.PACKAGE_FORMAT)
    if (!observed.archiveEntriesConsistent) add(ApkValidationIssue.ARCHIVE_CONTENTS)
    if (observed.packageName != packageName) add(ApkValidationIssue.PACKAGE_NAME)
    if (observed.versionName != versionName) add(ApkValidationIssue.VERSION_NAME)
    if (versionCode != null && observed.versionCode != versionCode) add(ApkValidationIssue.VERSION_CODE)
    if (fileSizeBytes != null && observed.fileSizeBytes != fileSizeBytes) add(ApkValidationIssue.FILE_SIZE)
    if (fileSha256 != null && !observed.fileSha256.equals(fileSha256, ignoreCase = true)) {
        add(ApkValidationIssue.FILE_SHA256)
    }
    if (observed.signingCertificateSha256.none { observedSignature ->
            signingCertificateSha256.any { expectedSignature ->
                observedSignature.equals(expectedSignature, ignoreCase = true)
            }
        }
    ) {
        add(ApkValidationIssue.SIGNING_CERTIFICATE)
    }
}

fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
