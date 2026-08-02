/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * Section 5: persistent home navigation.
 *
 * Home used to hide labels as soon as search or sort appeared, which left compact-screen
 * users with a row of unrelated icons and no indication of their current destination. This
 * follows the platform navigation-bar pattern instead: every item stays labelled and Home is
 * always selected as the orientation anchor.
 */
@Composable
fun HomeBottomActionBar(
    modifier: Modifier = Modifier,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isExpertModeEnabled: Boolean = false,
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        NavigationBar(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            NavigationBarItem(
                selected = false,
                onClick = onBundlesClick,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.sources)) },
                modifier = Modifier
                    .then(if (onSourcesPositioned != null) Modifier.onGloballyPositioned { coords ->
                        onSourcesPositioned(coords.boundsInWindow())
                    } else Modifier)
            )

            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.home)) }
            )

            NavigationBarItem(
                selected = false,
                onClick = onSettingsClick,
                icon = {
                    Icon(
                        imageVector = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.settings)) },
                modifier = Modifier
                    .then(if (onSettingsPositioned != null) Modifier.onGloballyPositioned { coords ->
                        onSettingsPositioned(coords.boundsInWindow())
                    } else Modifier)
            )
        }
    }
}

/**
 * Individual bottom action button.
 * Rectangular shape with rounded corners.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    showLabel: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    isExpertMode: Boolean = false,
    stateDescription: String? = null
) {
    val shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius)

    // Use expert mode colors if enabled
    val finalContainerColor = containerColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val finalContentColor = contentColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val expertModeLabel = stringResource(R.string.settings_advanced_expert_mode)
    val loadingLabel = stringResource(R.string.loading)

    // Build content description for accessibility
    val contentDesc = remember(text, isExpertMode, showProgress) {
        buildString {
            text?.let { append(it) }
            if (isExpertMode) {
                append(", ")
                append(expertModeLabel)
            }
            if (showProgress) {
                append(", ")
                append(loadingLabel)
            }
        }
    }

    // Press-scale feedback matches the home pill and category header pattern so every
    // interactive surface on this screen shares the same tactile response
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        enabled = enabled,
        label = "bottom_action_press_scale"
    )
    // Surface(enabled = enabled) already gates the click, so we can pass onClick straight
    val handleClick = rememberHapticClick(onClick)

    val button: @Composable (Modifier) -> Unit = { outerModifier ->
        Surface(
            onClick = handleClick,
            modifier = outerModifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDesc
                    if (stateDescription != null) {
                        this.stateDescription = stateDescription
                    }
                    if (showProgress) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
            shape = shape,
            color = finalContainerColor.copy(alpha = if (enabled) 1f else 0.5f),
            interactionSource = interactionSource,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        finalContentColor.copy(alpha = if (enabled) 0.2f else 0.1f),
                        finalContentColor.copy(alpha = if (enabled) 0.1f else 0.05f)
                    )
                )
            ),
            enabled = enabled
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MorpheDefaults.ItemSpacing),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MorpheDefaults.IconSizeSmall),
                        color = finalContentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    MorpheIcon(
                        icon = icon,
                        tint = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f)
                    )
                    if (showLabel && text != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // The weight/positioning modifier must land on this Box so Row still sees it as the direct
    // child; TooltipBox applies its own modifier to an inner wrapper, which Row can't see
    Box(modifier = modifier) {
        // Only surface a tooltip when the label itself is hidden; otherwise the two would repeat
        if (!showLabel && text != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(text) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth()
            ) {
                button(Modifier)
            }
        } else {
            button(Modifier.fillMaxWidth())
        }
    }
}
