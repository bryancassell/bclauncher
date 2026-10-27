package io.github.bryancassell.bclauncher.ui.home

import android.content.ActivityNotFoundException
import android.graphics.drawable.Drawable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import kotlin.math.roundToInt

/** The launcher's home screen: the installed apps in a grid, where selecting one opens it. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    // A home screen has nothing behind it to go back to, so Back does nothing rather than closing it.
    BackHandler {}
    HomeScreen(
        uiState = uiState,
        // The screen starts the app itself, with the activity's context (ARCHITECTURE.md, Layers and packages).
        onAppClick = { app ->
            try {
                context.startActivity(app.launchIntent)
            } catch (_: ActivityNotFoundException) {
                val message = resources.getString(R.string.app_did_not_open, app.label)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.onLaunchFailed()
            }
        },
        modifier = modifier,
    )
}

/** Shows nothing while the apps load, since a load takes a fraction of a second and an indicator would only flash. */
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            HomeUiState.Loading -> Unit
            is HomeUiState.Content -> AppGrid(uiState.apps, onAppClick)
            HomeUiState.Empty -> Message(stringResource(R.string.home_empty))
            HomeUiState.Error -> Message(stringResource(R.string.home_error))
        }
    }
}

/**
 * Six 124 dp tiles to a row, inside 58 dp margins at the sides and 28 dp at the top and bottom, with 20 dp between
 * tiles. These are the card width and margins in Android's TV layout guide (PRD.md, Design decisions).
 *
 * Focus starts on the first tile. Starting another app leaves focus where it was, since the window losing focus
 * doesn't clear it. The focused tile is also saved before an app starts, so focus returns to it after the activity is
 * recreated, such as when its process was stopped to free memory.
 */
@Composable
private fun AppGrid(apps: List<InstalledApp>, onAppClick: (InstalledApp) -> Unit) {
    val grid = remember { FocusRequester() }
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
            AppTile(
                app = app,
                onClick = {
                    grid.saveFocusedChild()
                    onAppClick(app)
                },
            )
        }
    }
    // restoreFocusedChild() first, because requestFocus() on the grid focuses its first tile even when focusRestorer
    // restores another one on the way in: the restore runs inside the request, which then carries on (Compose UI
    // 1.12.1's FocusTargetNode.requestFocus).
    LaunchedEffect(Unit) {
        if (!grid.restoreFocusedChild()) grid.requestFocus()
    }
}

/** The app's banner, or its icon and name when it has none (PRD.md, Design decisions). */
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

/**
 * Draws a banner or icon from PackageManager at the size it's given. They may be bitmaps, vectors or adaptive icons,
 * so drawing the Drawable keeps vectors sharp, where converting it to a bitmap first would fix its resolution.
 */
private class DrawablePainter(private val drawable: Drawable) : Painter() {
    // A Drawable with no size of its own, such as a ColorDrawable, reports -1.
    override val intrinsicSize: Size =
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        } else {
            Size.Unspecified
        }

    override fun DrawScope.onDraw() {
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
