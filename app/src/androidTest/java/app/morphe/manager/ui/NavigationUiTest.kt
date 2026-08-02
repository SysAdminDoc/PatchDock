/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.morphe.manager.MainActivity
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.SettingsTab
import app.morphe.manager.ui.screen.SettingsTabRow
import app.morphe.manager.ui.screen.SettingsTopBar
import app.morphe.manager.ui.screen.home.HomeBottomActionBar
import app.morphe.manager.ui.screen.home.HomeUtilityBar
import app.morphe.manager.ui.screen.home.SourceManagementHeader
import app.morphe.manager.ui.screen.patcher.PatcherBottomActionBar
import app.morphe.manager.ui.screen.shared.MorpheDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeSeparatesListToolsFromPrimaryDestinations() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Column {
                        HomeUtilityBar(
                            showSearch = true,
                            searchActive = false,
                            showSort = true,
                            sortMode = HomeAppSortMode.MANUAL,
                            onSearchClick = {},
                            onSortClick = {}
                        )
                        HomeBottomActionBar(
                            onBundlesClick = {},
                            onSettingsClick = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Search").assertIsDisplayed()
        composeRule.onNodeWithText("Sort").assertIsDisplayed()
        composeRule.onNodeWithText("Sources").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsShowsParentBackAndEverySectionLabel() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Column {
                        SettingsTopBar(
                            currentTab = SettingsTab.ADVANCED,
                            onBackClick = {}
                        )
                        SettingsTabRow(
                            currentTab = SettingsTab.ADVANCED,
                            onTabSelected = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        listOf("Appearance", "Advanced", "System").forEach { label ->
            composeRule.onAllNodesWithText(label)[0].assertIsDisplayed()
        }
    }

    @Test
    fun sourceManagementOffersVisibleExitAndAddActions() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    SourceManagementHeader(
                        sourceCount = 2,
                        showSort = true,
                        sortModeLabel = "Manual order",
                        onSortClick = {},
                        onAddSource = {},
                        onDismissRequest = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Patch sources").assertIsDisplayed()
        composeRule.onNodeWithText("Add").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sort").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun titledFullScreenDialogsHaveAnExplicitCloseAction() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    MorpheDialog(
                        onDismissRequest = {},
                        title = "Details"
                    ) {
                        Text("Dialog body")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun patcherActionsAreLabelledAndHomeRemainsAvailable() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    PatcherBottomActionBar(
                        showCancelButton = false,
                        showHomeButton = true,
                        showCopyLogsButton = true,
                        showInstallButton = true,
                        onCancelClick = {},
                        onHomeClick = {},
                        onSaveClick = {},
                        onErrorClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Install").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
    }
}
