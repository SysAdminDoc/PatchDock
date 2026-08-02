package app.morphe.manager.domain.catalog

import java.nio.file.Files
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchDockCatalogTest {
    private val youtubeSigner =
        "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a"
    private val redditSigner =
        "970ba52f5e501c3bda4ac0a31d31f56c2a278bc9b20e42e3d4c3453bb8a8736f"

    @Test
    fun `catalog resolves only the exact pinned TikTok build`() {
        assertEquals(
            PatchDockCatalog.tiktok4383,
            PatchDockCatalog.stockApk("com.zhiliaoapp.musically", "43.8.3"),
        )
        assertNull(PatchDockCatalog.stockApk("com.zhiliaoapp.musically", "43.8.4"))
        assertNull(PatchDockCatalog.stockApk("com.example.other", "43.8.3"))
    }

    @Test
    fun `matching pinned observation passes every trust check`() {
        val spec = PatchDockCatalog.tiktok4383
        val observed = ObservedApk(
            packageName = spec.packageName,
            versionName = spec.versionName,
            versionCode = spec.versionCode,
            fileSizeBytes = requireNotNull(spec.fileSizeBytes),
            fileSha256 = requireNotNull(spec.fileSha256).uppercase(),
            signingCertificateSha256 = spec.signingCertificateSha256.mapTo(mutableSetOf()) {
                it.uppercase()
            },
            packageFormat = ObservedPackageFormat.APK,
        )

        assertTrue(spec.validate(observed).isEmpty())
        assertTrue(spec.isArtifactPinned)
    }

    @Test
    fun `mismatched pinned observation reports every failed field`() {
        val issues = PatchDockCatalog.tiktok4383.validate(
            ObservedApk(
                packageName = "bad.package",
                versionName = "0",
                versionCode = 0,
                fileSizeBytes = 0,
                fileSha256 = "00",
                signingCertificateSha256 = emptySet(),
                packageFormat = ObservedPackageFormat.SPLIT_ARCHIVE,
                archiveEntriesConsistent = false,
            ),
        )

        assertEquals(ApkValidationIssue.entries.toSet(), issues)
    }

    @Test
    fun `bundle declared YouTube target becomes a certificate pinned APK policy`() {
        val spec = PatchDockCatalog.downloadSpec(
            displayName = "YouTube",
            packageName = "com.google.android.youtube",
            versionName = "20.21.37",
            signingCertificateSha256 = setOf(youtubeSigner.uppercase()),
            apkFileTypeName = "APK_REQUIRED",
            resolvedDownloadUrl = "https://www.apkmirror.com/apk/google-inc/youtube/",
        )

        assertNotNull(spec)
        assertEquals(StockPackageFormatPolicy.APK_ONLY, spec.formatPolicy)
        assertEquals(setOf(youtubeSigner), spec.signingCertificateSha256)
        assertEquals("https://www.apkmirror.com/apk/google-inc/youtube/", spec.downloadPageUrl)
        assertFalse(spec.isArtifactPinned)
        assertTrue(
            spec.validate(
                ObservedApk(
                    packageName = spec.packageName,
                    versionName = spec.versionName,
                    versionCode = 123,
                    fileSizeBytes = 456,
                    fileSha256 = "unpublished-artifact-hash",
                    signingCertificateSha256 = setOf(youtubeSigner),
                    packageFormat = ObservedPackageFormat.APK,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `required split bundle rejects a plain APK`() {
        val spec = PatchDockCatalog.downloadSpec(
            displayName = "Reddit",
            packageName = "com.reddit.frontpage",
            versionName = "2026.14.0",
            signingCertificateSha256 = setOf(redditSigner),
            apkFileTypeName = "APKM_REQUIRED",
        )

        assertNotNull(spec)
        assertEquals(StockPackageFormatPolicy.SPLIT_ARCHIVE_ONLY, spec.formatPolicy)
        val issues = spec.validate(
            ObservedApk(
                packageName = spec.packageName,
                versionName = spec.versionName,
                versionCode = 1,
                fileSizeBytes = 1,
                fileSha256 = "anything",
                signingCertificateSha256 = setOf(redditSigner),
                packageFormat = ObservedPackageFormat.APK,
            ),
        )
        assertEquals(setOf(ApkValidationIssue.PACKAGE_FORMAT), issues)
    }

    @Test
    fun `untrusted or incomplete bundle metadata cannot enable the downloader`() {
        assertNull(
            PatchDockCatalog.downloadSpec(
                displayName = "Unknown",
                packageName = "com.example.unknown",
                versionName = "1.0",
                signingCertificateSha256 = emptySet(),
                apkFileTypeName = "APK",
            ),
        )
        assertNull(
            PatchDockCatalog.downloadSpec(
                displayName = "Unknown",
                packageName = "com.example.unknown",
                versionName = "1.0",
                signingCertificateSha256 = setOf("not-a-sha256"),
                apkFileTypeName = "APK",
            ),
        )
        assertNull(
            PatchDockCatalog.downloadSpec(
                displayName = "Unknown",
                packageName = "not a package",
                versionName = "1.0",
                signingCertificateSha256 = setOf(youtubeSigner),
                apkFileTypeName = "APK",
            ),
        )
        assertNull(
            PatchDockCatalog.downloadSpec(
                displayName = "Unknown",
                packageName = "com.example.unknown",
                versionName = null,
                signingCertificateSha256 = setOf(youtubeSigner),
                apkFileTypeName = "APK",
            ),
        )
    }

    @Test
    fun `untrusted resolved URL falls back to an APKMirror search`() {
        val spec = PatchDockCatalog.downloadSpec(
            displayName = "YouTube Music",
            packageName = "com.google.android.apps.youtube.music",
            versionName = "9.15.51",
            signingCertificateSha256 = setOf(youtubeSigner),
            apkFileTypeName = "APK_REQUIRED",
            resolvedDownloadUrl = "https://example.com/untrusted.apk",
        )

        assertNotNull(spec)
        assertEquals(
            "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=YouTube+Music+9.15.51",
            spec.downloadPageUrl,
        )
    }

    @Test
    fun `built in patch sources pin both release bundles`() {
        assertEquals(
            "58510605b618b6b5fe7bf9ae2d284857a07aa7c359cea189dcfb09efc7bb147c",
            PatchDockCatalog.expectedPatchBundleSha256(
                PatchDockCatalog.tiktokPatches.manifestUrl,
                "0.4.1",
            ),
        )
        assertEquals(
            "31d088b81414c65b9294e33feb3d5d7f14de3c694d84d74388ca3b22afc331f0",
            PatchDockCatalog.expectedPatchBundleSha256(
                PatchDockCatalog.officialMorphePatches.manifestUrl,
                "1.38.0",
            ),
        )
        assertNull(
            PatchDockCatalog.expectedPatchBundleSha256(
                PatchDockCatalog.officialMorphePatches.manifestUrl,
                "1.38.1",
            ),
        )
    }

    @Test
    fun `package extension detection accepts only supported APK family types`() {
        assertEquals("apk", PatchDockCatalog.packageFileExtension("stock.APK", null))
        assertEquals("apkm", PatchDockCatalog.packageFileExtension("stock.apkm?key=x", null))
        assertEquals("apks", PatchDockCatalog.packageFileExtension(null, "application/vnd.android.apks"))
        assertEquals("xapk", PatchDockCatalog.packageFileExtension("download.bin", "application/x-xapk"))
        assertNull(PatchDockCatalog.packageFileExtension("page.html", "text/html"))
    }

    @Test
    fun `sha256 helper hashes the exact file bytes`() {
        val file = Files.createTempFile("patchdock-sha", ".bin").toFile()
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                file.sha256Hex(),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `APKMirror origin policy accepts only HTTPS APKMirror hosts`() {
        assertTrue(PatchDockCatalog.isTrustedApkMirrorUrl("https://www.apkmirror.com/apk/example"))
        assertTrue(PatchDockCatalog.isTrustedApkMirrorUrl("https://download.apkmirror.com/file.apk"))

        listOf(
            "http://www.apkmirror.com/file.apk",
            "https://apkmirror.com.evil.example/file.apk",
            "https://evil-apkmirror.com/file.apk",
            "https://apkmirror.com@evil.example/file.apk",
            "javascript:alert(1)",
            "not a url",
        ).forEach { rejected ->
            assertFalse(PatchDockCatalog.isTrustedApkMirrorUrl(rejected), rejected)
        }
    }
}
