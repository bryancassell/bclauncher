package io.github.bryancassell.bclauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import kotlin.math.roundToInt

private const val TAG = "HomeScreen"

/** The launcher's home screen: the installed apps in a grid, where selecting one opens it. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    // Back does nothing (PRD.md, Design decisions).
    BackHandler {}
    HomeScreen(
        uiState = uiState,
        // The screen starts the app itself, with the activity's context (ARCHITECTURE.md, Layers and packages).
        onAppClick = { app ->
            if (!context.startActivitySafely(app.launchIntent)) {
                val message = resources.getString(R.string.app_did_not_open, app.label)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.onLaunchFailed()
            }
        },
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * Whether [intent]'s activity started. A tile can outlive its activity: an update can remove it
 * (ActivityNotFoundException), or stop exporting it or add a permission to it (SecurityException), before the reload
 * drops the tile. Android's own launcher, Launcher3, catches and logs both in its startActivitySafely.
 */
internal fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.w(TAG, "Couldn't start $intent", e)
    false
} catch (e: SecurityException) {
    Log.w(TAG, "Couldn't start $intent", e)
    false
}

/** Shows nothing while the apps load, since a load takes a fraction of a second and an indicator would only flash. */
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAppClick: (InstalledApp) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            HomeUiState.Loading -> Unit
            is HomeUiState.Content -> AppGrid(uiState.apps, onAppClick)
            HomeUiState.Empty -> Message(stringResource(R.string.home_empty))
            HomeUiState.Error -> LoadError(onRetry)
        }
    }
}

/**
 * Six 124 dp tiles to a row (PRD.md, Design decisions), inside 58 dp margins at the sides and 28 dp at the top and
 * bottom, with 20 dp between tiles. These are the margins in Android's TV layout guide.
 *
 * Focus starts on the first tile. Starting another app leaves focus where it was, since the window losing focus
 * doesn't clear it. The focused tile is also saved each time focus moves, so focus returns to it after the activity
 * is recreated, such as for a configuration change or when its process was stopped to free memory.
 *
 * Saving once before an app starts isn't enough: saveFocusedChild() saves the tile focused when it's called, and the
 * restore after a recreation doesn't save it again (Compose UI 1.12.1's FocusRestorer.kt). So a tile reached with the
 * D-pad since then, or anything after a second recreation, went back to the first tile on the Google TV emulator.
 */
@Composable
private fun AppGrid(apps: List<InstalledApp>, onAppClick: (InstalledApp) -> Unit) {
    val grid = remember { FocusRequester() }
    val focus = remember { GridFocus() }
    // When the focused tile's app leaves the list, as after onLaunchFailed() reloads it, focus goes to the tile that
    // takes its place. Compose clears focus when the focused node is removed and moves it nowhere else, so Select would
    // do nothing.
    val replacement = remember(apps) { focus.replacementIn(apps) }
    SideEffect { focus.shownApps = apps }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(grid)
            .focusRestorer(),
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Keyed by component, so focus stays on the same app when the list is replaced.
        items(apps, key = { app -> app.component }) { app ->
            val tile = remember { FocusRequester() }
            AppTile(
                app = app,
                onClick = { onAppClick(app) },
                modifier = Modifier
                    .focusRequester(tile)
                    .onFocusChanged {
                        if (it.isFocused) {
                            focus.app = app.component
                            grid.saveFocusedChild()
                        }
                    },
            )
            if (app.component == replacement) {
                LaunchedEffect(apps) { tile.requestFocus() }
            }
        }
    }
    // restoreFocusedChild() first, because requestFocus() on the grid focuses its first tile even when focusRestorer
    // restores another one on the way in: the restore runs inside the request, which then carries on (Compose UI
    // 1.12.1's FocusTargetNode.requestFocus).
    LaunchedEffect(Unit) {
        if (!grid.restoreFocusedChild()) grid.requestFocus()
    }
}

/** The app whose tile last had focus, and the list the grid last showed. */
private class GridFocus {
    var app: ComponentName? = null
    var shownApps: List<InstalledApp> = emptyList()

    /**
     * When the focused app has left [apps], the one whose tile takes its place: the next app after it in the shown
     * list that's still in [apps], or else the last one. Taking the next app rather than the same position keeps focus
     * beside where it was when tiles before it leave in the same reload.
     */
    fun replacementIn(apps: List<InstalledApp>): ComponentName? {
        val focused = app ?: return null
        if (apps.any { it.component == focused }) return null
        val remaining = apps.mapTo(HashSet()) { it.component }
        val next = shownApps.dropWhile { it.component != focused }.drop(1).firstOrNull {
            it.component in
                remaining
        }
        return (next ?: apps.lastOrNull())?.component
    }
}

/**
 * The app's banner, or its icon when it has none (PRD.md, Design decisions). The icon comes with the app's name, which
 * a banner already shows.
 */
@Composable
private fun AppTile(app: InstalledApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(16f / 9f)) {
        val banner = app.banner
        if (banner != null) {
            Image(
                painter = remember(banner) { DrawablePainter(banner) },
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = remember(app.icon) { DrawablePainter(app.icon) },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Says the apps didn't load, with a Try again button that takes focus, so Select presses it. */
@Composable
private fun LoadError(onRetry: () -> Unit) {
    val retry = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_error),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retry)) {
            Text(text = stringResource(R.string.home_retry))
        }
    }
    LaunchedEffect(Unit) { retry.requestFocus() }
}

/**
 * Draws a banner or icon from PackageManager at the size it's given. They may be bitmaps, vectors or adaptive icons,
 * so drawing the Drawable keeps vectors sharp, where converting it to a bitmap first would fix its resolution.
 */
internal class DrawablePainter(private val drawable: Drawable) : Painter() {
    // A Drawable with no size of its own, such as a ColorDrawable, reports -1.
    override val intrinsicSize: Size =
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        } else {
            Size.Unspecified
        }

    // So a drawable marked autoMirrored, such as an icon with an arrow, is mirrored in a right-to-left language.
    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean =
        drawable.setLayoutDirection(
            when (layoutDirection) {
                LayoutDirection.Ltr -> View.LAYOUT_DIRECTION_LTR
                LayoutDirection.Rtl -> View.LAYOUT_DIRECTION_RTL
            },
        )

    override fun DrawScope.onDraw() {
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
