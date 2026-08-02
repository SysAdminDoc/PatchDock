/*
 * Copyright (C) 2026 PatchDock contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.morphe.manager.ui.download

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkMirrorTlsPolicyTest {
    private val page = "https://www.apkmirror.com/apk/example/download/"

    @Test
    fun `main document TLS failure is surfaced`() {
        assertTrue(isMainFrameTlsFailure(page, page))
        assertTrue(isMainFrameTlsFailure("$page#download", page))
    }

    @Test
    fun `third party TLS failure does not abort the main page`() {
        assertFalse(
            isMainFrameTlsFailure(
                "https://ads.example.invalid/resource.js",
                page,
            ),
        )
    }

    @Test
    fun `same host subresource TLS failure does not abort the main page`() {
        assertFalse(
            isMainFrameTlsFailure(
                "https://www.apkmirror.com/wp-content/example.js",
                page,
            ),
        )
    }

    @Test
    fun `missing callback URL is not misclassified as the main page`() {
        assertFalse(isMainFrameTlsFailure(null, page))
        assertFalse(isMainFrameTlsFailure(page, null))
    }
}
