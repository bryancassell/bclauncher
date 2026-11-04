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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
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

// The side and the top and bottom margins in Android's TV layout guide, which the buttons at the top, the message and
// the grid share.
private val HORIZONTAL_MARGIN = 58.dp
private val VERTICAL_MARGIN = 28.dp

/**
 * Between the buttons at the top and what's below them. It keeps five rows of tiles, the 30 apps that fit on screen
 * (PRD.md, Design decisions), above the bottom margin of a 540 dp screen: 28 + 40 + 12 + 5 × 70 + 4 × 20 = 510 dp of
 * the 512 dp inside it. A 69.75 dp tile is drawn 70 dp tall at the 2x density of a 1080p TV, and 15 dp put the last
 * row 1 dp into the margin. That's at the default text size: larger text makes the buttons taller, and the last row
 * then reaches into the margin, though it stays on screen.
 */
private val GAP_BELOW = 12.dp

/**
 * The launcher's home screen: the installed apps in a grid, where selecting one opens it and holding Select on one
 * opens its menu.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showsHomeButtonMessage by viewModel.showsHomeButtonMessage.collectAsStateWithLifecycle()
    val googleTvHomeIntent by viewModel.googleTvHomeIntent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Back does nothing (PRD.md, Design decisions).
    BackHandler {}

    // The screen starts other apps' activities itself, with the activity's context (ARCHITECTURE.md, Layers and
    // packages), and says so when one doesn't open. The message is made only then.
    fun start(intent: Intent, failure: () -> String): Boolean =
        context.startActivitySafely(intent).also { started ->
            if (!started) Toast.makeText(context, failure(), Toast.LENGTH_SHORT).show()
        }
    HomeScreen(
        uiState = uiState,
        showsHomeButtonMessage = showsHomeButtonMessage,
        onAppClick = { app ->
            val started =
                start(app.launchIntent) {
                    resources.getString(R.string.app_did_not_open, app.label)
                }
            if (!started) viewModel.onLaunchFailed()
        },
        onAppSettingsClick = { app ->
            start(app.appSettingsIntent) {
                resources.getString(R.string.app_settings_did_not_open, app.label)
            }
        },
        onUninstallClick = { app ->
            start(app.uninstallIntent) {
                resources.getString(R.string.uninstall_did_not_open, app.label)
            }
        },
        onRetry = viewModel::retry,
        onUseBcLauncher = {
            start(viewModel.homeButtonSettingsIntent) {
                resources.getString(R.string.home_button_settings_did_not_open)
            }
        },
        showsGoogleTv = googleTvHomeIntent != null,
        onGoogleTv = {
            googleTvHomeIntent?.let { intent ->
                viewModel.onOpeningGoogleTvHome {
                    // The broadcast can come back after the user has opened an app, or after an earlier press has
                    // opened Google TV's home, and neither should be covered.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        start(intent) { resources.getString(R.string.google_tv_did_not_open) }
                    }
                }
            }
        },
        onSettings = {
            start(viewModel.settingsIntent) { resources.getString(R.string.settings_did_not_open) }
        },
        modifier = modifier,
    )
}

/**
 * Whether [intent]'s activity started. Android's own launcher, Launcher3, catches and logs the same two exceptions in
 * its startActivitySafely:
 * - ActivityNotFoundException, when nothing handles the intent. A tile can outlive its activity, since an update can
 *   remove it before the reload drops the tile, and a device may have no screen for a Settings action.
 * - SecurityException, when an update stopped exporting a tile's activity or added a permission to it.
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

/**
 * Shows only the Google TV and Settings buttons while the apps load, since a load takes a fraction of a second and an
 * indicator would only flash. The buttons go at the top right, where Up from the first row reaches them and they don't
 * move as apps come and go. The message offering to make Home open BC Launcher goes between them and the apps (PRD.md,
 * Design decisions).
 */
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    showsHomeButtonMessage: Boolean,
    onAppClick: (InstalledApp) -> Unit,
    onAppSettingsClick: (InstalledApp) -> Unit,
    onUninstallClick: (InstalledApp) -> Unit,
    onRetry: () -> Unit,
    onUseBcLauncher: () -> Unit,
    showsGoogleTv: Boolean,
    onGoogleTv: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            SystemScreenButtons(
                showsGoogleTv,
                onGoogleTv,
                onSettings,
                takesFocus = uiState == HomeUiState.Empty && !showsHomeButtonMessage,
            )
            if (showsHomeButtonMessage) {
                HomeButtonMessage(onUseBcLauncher, takesFocus = uiState == HomeUiState.Empty)
            }
            Box(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    HomeUiState.Loading -> Unit

                    is HomeUiState.Content -> AppGrid(
                        uiState.apps,
                        onAppClick,
                        onAppSettingsClick,
                        onUninstallClick,
                        showsHomeButtonMessage,
                    )

                    HomeUiState.Empty -> Message(stringResource(R.string.home_empty))

                    HomeUiState.Error -> LoadError(onRetry, showsHomeButtonMessage)
                }
            }
        }
    }
}

/**
 * The buttons that open Google TV's home and device settings, at the top right inside the grid's margins. Google TV's
 * is left out when [showsGoogleTv] is false. Focus starts on the apps, and Up from the first row, or from the message,
 * reaches them.
 *
 * With no apps and no message, the first button [takesFocus], since nothing else can, so Select presses it. It takes
 * focus again when the message leaves, but not from the other button the user is on, as when they come back from
 * Settings having removed the last app. When Google TV's button arrives while the buttons take focus, as when the apps
 * load before Google TV's home is found, it takes focus. When it leaves with focus on it, as when Google TV's launcher
 * is turned off, Settings does. A focused button that leaves, like the message's, leaves focus nowhere, for the reason
 * AppGrid gives.
 *
 * Each button has its own FocusRequester: requestFocus() on the row would enter it as if moving Right from its top
 * left, whatever the layout direction, so in a right-to-left language it would focus Settings (Compose UI 1.12.1's
 * TwoDimensionalFocusSearch.kt, b/244528858).
 */
@Composable
private fun SystemScreenButtons(
    showsGoogleTv: Boolean,
    onGoogleTv: () -> Unit,
    onSettings: () -> Unit,
    takesFocus: Boolean,
) {
    val googleTv = remember { FocusRequester() }
    val settings = remember { FocusRequester() }
    var googleTvFocused by remember { mutableStateOf(false) }
    var settingsFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HORIZONTAL_MARGIN, top = VERTICAL_MARGIN, end = HORIZONTAL_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        if (showsGoogleTv) {
            Button(
                onClick = onGoogleTv,
                modifier = Modifier
                    .focusRequester(googleTv)
                    .onFocusChanged { googleTvFocused = it.isFocused },
            ) {
                Text(text = stringResource(R.string.google_tv))
            }
        }
        Button(
            onClick = onSettings,
            modifier = Modifier
                .focusRequester(settings)
                .onFocusChanged { settingsFocused = it.isFocused },
        ) {
            Text(text = stringResource(R.string.settings))
        }
    }
    val first = if (showsGoogleTv) googleTv else settings
    LaunchedEffect(takesFocus) {
        if (takesFocus && !googleTvFocused && !settingsFocused) first.requestFocus()
    }
    // Read while composing, before the button leaves: clearing its focus as it leaves reports it unfocused.
    val googleTvLeftWithFocus = !showsGoogleTv && googleTvFocused
    LaunchedEffect(showsGoogleTv) {
        if (showsGoogleTv && takesFocus) {
            googleTv.requestFocus()
        } else if (googleTvLeftWithFocus) {
            settings.requestFocus()
        }
    }
}

/**
 * Says that Home opens Google TV, with a button that opens Accessibility settings, inside the grid's side margins.
 * Focus starts on the apps, so Select still opens one, and Up from the first row reaches the button. With no apps, the
 * button [takesFocus] rather than those above it, since it does what the message asks, so Select presses it.
 */
@Composable
private fun HomeButtonMessage(onUseBcLauncher: () -> Unit, takesFocus: Boolean) {
    val button = remember { FocusRequester() }
    Column(
        modifier = Modifier.padding(
            start = HORIZONTAL_MARGIN,
            top = GAP_BELOW,
            end = HORIZONTAL_MARGIN,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.home_button_message),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.home_button_message_detail),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onUseBcLauncher, modifier = Modifier.focusRequester(button)) {
            Text(text = stringResource(R.string.home_button_use))
        }
    }
    LaunchedEffect(takesFocus) {
        if (takesFocus) button.requestFocus()
    }
}

/**
 * Six 124 dp tiles to a row (PRD.md, Design decisions), inside 58 dp margins at the sides and 28 dp at the bottom, with
 * 20 dp between tiles. These are the margins in Android's TV layout guide. The top is the gap below the buttons.
 *
 * Focus starts on the first tile. Starting another app leaves focus where it was, since the window losing focus
 * doesn't clear it. The app whose tile last had focus is kept, also when the activity is recreated, such as for a
 * configuration change or when its process was stopped to free memory. Focus returns to that app's tile whenever it
 * comes back into the grid, as with Down from a button above it, after a recreation, and each time the message above
 * the grid comes or goes: when the user turns on the service and comes back, the message leaves with focus on its
 * button, and Compose clears focus and moves it nowhere, so Select would do nothing. When that app has left the list,
 * or its tile isn't composed, Down from a button goes where the D-pad sends it, such as to the tile below, and otherwise
 * focus goes to the first tile.
 *
 * The tile is found by its app rather than with Compose's focusRestorer(), which finds it by its node's
 * compositeKeyHash and focuses the first tile with that hash (Compose UI 1.12.1's FocusRestorer.kt). On a TV, a tile
 * node that the grid reuses for another app keeps the hash of the app it was made for (Compose UI and Foundation
 * 1.12.1), so once the grid has scrolled, two tiles on screen can share a hash, and focus could go to the wrong one (#28).
 *
 * Holding Select on a tile opens its app's menu. It's saved too, so it's still open after a configuration change
 * recreates the activity. Otherwise it closes when the screen stops, so Home or coming back from another app shows the
 * grid. It also closes when its app leaves the list, as after the user uninstalls it, and doesn't open again if the app
 * comes back.
 */
@Composable
private fun AppGrid(
    apps: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit,
    onAppSettingsClick: (InstalledApp) -> Unit,
    onUninstallClick: (InstalledApp) -> Unit,
    messageShown: Boolean,
) {
    val grid = remember { FocusRequester() }
    val focus = rememberSaveable(saver = GridFocus.Saver) { GridFocus() }
    var menuFor by rememberSaveable { mutableStateOf<ComponentName?>(null) }
    val menuPress = remember { MenuPress() }
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) menuFor = null
    }
    val state = rememberLazyGridState()
    // Picked during composition, so the tiles see it in the first layout of the new list. Tiles already composed are
    // composed again only when what their content captures changes, so one picked in the SideEffect reached them a
    // frame late, after the focused tile had gone. In Robolectric, Compose had by then moved focus to the first tile
    // and started scrolling to it.
    val target = remember(apps) { focus.targetIn(apps) }
    SideEffect { focus.show(apps, target, state) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focus.inGrid = it.hasFocus }
            .focusRequester(grid)
            // onEnter runs whenever focus comes in from outside the grid, also for a request aimed at one tile, which
            // then goes to the app's tile instead, as it did with focusRestorer(). The only such request here is from
            // the tile that takes a removed tile's place, once the removed app has left the list and has no tile.
            .focusProperties { onEnter = { focus.restore() } }
            .onPreviewKeyEvent { menuPress.onKeyEvent(it) },
        contentPadding = PaddingValues(
            start = HORIZONTAL_MARGIN,
            top = GAP_BELOW,
            end = HORIZONTAL_MARGIN,
            bottom = VERTICAL_MARGIN,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Keyed by component, so focus stays on the same app when the list is replaced.
        items(apps, key = { app -> app.component }) { app ->
            val tile = remember { FocusRequester() }
            val component = app.component
            DisposableEffect(component) {
                focus.tiles[component] = tile
                onDispose { focus.tiles.remove(component, tile) }
            }
            AppTile(
                app = app,
                onClick = { if (!menuPress.endsWithClick()) onAppClick(app) },
                onLongClick = {
                    menuPress.start()
                    menuFor = app.component
                },
                modifier = Modifier
                    .focusRequester(tile)
                    .onFocusChanged {
                        if (it.isFocused) focus.app = app.component
                    },
            )
            if (app.component == target) {
                // Keyed by the list, since the next list can pick the same tile to take another's place. It takes
                // focus only while it's pending: the grid disposes of this tile when it scrolls out of view and
                // composes it again when it comes back, which runs this again, and it took focus back from the tile the
                // user had moved to.
                LaunchedEffect(apps) {
                    if (focus.pending == app.component) {
                        focus.pending = null
                        tile.requestFocus()
                    }
                }
            }
        }
    }
    // The app's tile itself, not the grid: requestFocus() on the grid focuses its first tile when focus is already on a
    // tile, where onEnter doesn't run. It is when the message arrives, and it can be when the message leaves, once
    // Compose has moved focus from the removed button into the grid (Compose UI 1.12.1's FocusTargetNode.kt).
    LaunchedEffect(messageShown) {
        if (!focus.restore()) grid.requestFocus()
    }
    val menuApp = apps.find { it.component == menuFor }
    if (menuApp != null) {
        AppMenu(
            app = menuApp,
            onOptionClick = { option ->
                menuFor = null
                when (option) {
                    AppOption.Launch -> onAppClick(menuApp)
                    AppOption.AppSettings -> onAppSettingsClick(menuApp)
                    AppOption.Uninstall -> onUninstallClick(menuApp)
                }
            },
            onDismiss = { menuFor = null },
        )
    }
    LaunchedEffect(apps) {
        if (apps.none { it.component == menuFor }) menuFor = null
    }
}

/**
 * The long press that opened the menu, until Select goes down again, so the click that ends it doesn't open the app.
 *
 * On a TV the menu's window takes focus while Select is still held, and the grid gets a cancelled release, which resets
 * the Card's long press without a click. If the menu closes before Select is let go, as when the user presses Back
 * first, the rest of the press can reach the grid again. tv-material's Card then clicks on the release, since its long
 * press was reset (handleDPadEnter in tv-material 1.1.0).
 */
private class MenuPress {
    private var open = false

    fun start() {
        open = true
    }

    /** Ends the long press when Select goes down afresh. Never consumes [event]. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val freshPress = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
        if (freshPress && event.key in SELECT_KEYS) open = false
        return false
    }

    /** Whether a tile's click is the end of the long press, which it then ends. */
    fun endsWithClick(): Boolean = open.also { open = false }
}

// The keys tv-material's Card clicks on (SurfaceImpl.kt's AcceptableEnterClickKeys in tv-material 1.1.0).
private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Which tile has focus, kept so that a new list leaves focus on a tile on screen, and so that focus coming back into the
 * grid returns to the same app's tile.
 */
private class GridFocus {
    /** The app whose tile last had focus. */
    var app: ComponentName? = null

    /** The requester of each tile the grid has composed, by its app. */
    val tiles = mutableMapOf<ComponentName, FocusRequester>()

    /** Whether a tile has focus, rather than a button above the grid. */
    var inGrid = false

    /** The app whose tile takes the focused tile's place, until that tile takes focus. */
    var pending: ComponentName? = null

    private var shownApps: List<InstalledApp> = emptyList()

    /**
     * The tile that has focus, or is to take it. Null when focus is on a button above the grid, so a new list leaves
     * focus there.
     */
    private val focused: ComponentName?
        get() = pending ?: app.takeIf { inGrid }

    /**
     * The app whose tile a new list keeps where the focused tile is: the focused one, or when its app has left [apps],
     * as after onLaunchFailed() reloads the list, the one whose tile takes its place. Compose clears focus when the
     * focused node is removed, so without one Select could do nothing.
     */
    fun targetIn(apps: List<InstalledApp>): ComponentName? {
        val focused = focused ?: return null
        return if (apps.any { it.component == focused }) focused else replacementFor(focused, apps)
    }

    /**
     * Focuses [app]'s tile, and says whether it could: not when the app has left the list or its tile isn't composed.
     * The list is checked too, since a removed app's tile stays in [tiles] until the grid disposes of it, which can be a
     * frame after the list changes.
     */
    fun restore(): Boolean {
        val last = app ?: return false
        if (shownApps.none { it.component == last }) return false
        return tiles[last]?.requestFocus() == true
    }

    /** Called before [grid] lays out [apps], with the [target] that [targetIn] picked in them. */
    fun show(apps: List<InstalledApp>, target: ComponentName?, grid: LazyGridState) {
        if (apps == shownApps) return
        val focused = focused
        if (focused != null && target != null) {
            keepInPlace(focused, target, apps.indexOfFirst { it.component == target }, grid)
        }
        // A target other than the focused tile is the one that takes its place.
        if (target != focused) pending = target
        shownApps = apps
    }

    /**
     * The app whose tile takes [focused]'s place: the next app after it in the shown list that's still in [apps], or
     * else the last one. Taking the next app rather than the same position keeps focus beside where it was when tiles
     * before it leave in the same reload.
     */
    private fun replacementFor(focused: ComponentName, apps: List<InstalledApp>): ComponentName? {
        val remaining = apps.mapTo(HashSet()) { it.component }
        val next = shownApps.dropWhile { it.component != focused }.drop(1).firstOrNull {
            it.component in
                remaining
        }
        return (next ?: apps.lastOrNull())?.component
    }

    /**
     * Asks [grid] to lay out the new list with [target], at [index], as far down the screen as [focused] is, or as
     * near as the grid can scroll.
     *
     * LazyGrid otherwise keeps the first visible tile in place. Once there are more tiles than fit on screen, tiles
     * added or moved before the focused one could push it off screen, where it kept focus out of sight, and a
     * replacement off screen wasn't composed to take focus (#20). requestScrollToItem() positions the next layout, the
     * first with the new list, instead of the first visible tile's key, and a negative offset puts the item's row that
     * far down, or the first row at the top (Compose Foundation 1.12.1's LazyGridState.kt and LazyGridMeasure.kt). So
     * nothing waits for that layout, as scrolling only when the tile ends up off screen would have to.
     */
    private fun keepInPlace(
        focused: ComponentName,
        target: ComponentName,
        index: Int,
        grid: LazyGridState,
    ) {
        // A scroll in progress, such as the one that brings a tile the D-pad just focused into view, carries on to the
        // focused tile wherever the new list puts it. Asking would stop it partway, and a list of the same apps, with
        // new Drawables, comes each time the user returns more than 5 s after leaving.
        if (target == focused && grid.isScrollInProgress) return
        // At the top when the focused tile is out of view, as a scroll that leaves focus where it is, such as TalkBack's,
        // can leave it. visibleItemsInfo lists only the tiles in rows on screen, not the focused tile the grid keeps
        // composed out of view (LazyLayoutMeasuredItem.kt's updatedVisibleItems in Compose Foundation 1.12.1).
        val top = grid.layoutInfo.visibleItemsInfo.find { it.key == focused }?.offset?.y ?: 0
        grid.requestScrollToItem(index, -top)
    }

    companion object {
        /** Keeps [app] when the activity is recreated, so focus returns to its tile. */
        val Saver = Saver<GridFocus, ComponentName>(
            save = { it.app },
            restore = { app -> GridFocus().also { it.app = app } },
        )
    }
}

/**
 * The app's banner, or its icon when it has none (PRD.md, Design decisions). The icon comes with the app's name, which
 * a banner already shows.
 */
@Composable
private fun AppTile(
    app: InstalledApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier.aspectRatio(16f / 9f)) {
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
 * Says the apps didn't load, with a Try again button that takes focus, so Select presses it. It takes focus again each
 * time the message above comes or goes, for the reason AppGrid gives.
 */
@Composable
private fun LoadError(onRetry: () -> Unit, messageShown: Boolean) {
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
    LaunchedEffect(messageShown) { retry.requestFocus() }
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
