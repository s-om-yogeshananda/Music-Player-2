package com.yourname.musicplayer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.yourname.musicplayer.data.AppSettings
import com.yourname.musicplayer.data.K
import com.yourname.musicplayer.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private lateinit var c: AppContainer
    private lateinit var actions: Actions
    private val ui = UiState()
    private var perm by mutableStateOf(false)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        perm = c.hasAudioPermission()
        if (perm) c.rescan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c = (application as MusicApplication).container
        WindowCompat.setDecorFitsSystemWindows(window, false)
        actions = Actions(this, c)
        perm = c.hasAudioPermission()
        if (perm) c.rescan()
        setContent { AppRoot() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun requestPerms() {
        val l = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(Manifest.permission.READ_MEDIA_AUDIO); l.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= 28) l.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permLauncher.launch(l.toTypedArray())
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            "com.yourname.musicplayer.SHUFFLE_ALL" -> lifecycleScope.launch {
                withTimeoutOrNull(8000) { c.songs.first { it.isNotEmpty() } }
                delay(400)
                vm.shuffleAll(c.songs.value)
            }
            "com.yourname.musicplayer.RECENT" -> { ui.stack.clear(); ui.rootTab = 0; ui.libTab = 7 }
            "com.yourname.musicplayer.FAVORITES" -> { ui.stack.clear(); ui.rootTab = 0; ui.libTab = 6 }
            Intent.ACTION_VIEW -> i.data?.let { uri -> lifecycleScope.launch { delay(700); vm.playUri(uri) } }
        }
    }

    @Composable
    private fun AppRoot() {
        var splashVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(3_000)
            splashVisible = false
        }
        val s0 by produceState<AppSettings?>(null) { c.settings.flow.collect { value = it } }
        val s = s0
        if (s == null || splashVisible) {
            SplashScreen()
            return
        }
        val now by vm.now.collectAsState()
        var dyn by remember { mutableStateOf<List<Color>?>(null) }
        LaunchedEffect(now?.mediaId, s.theme) {
            dyn = if (s.theme == "dynamic") (paletteColors(c, now?.song) ?: dyn) else null
        }
        val bn = s.language == "bn" || (s.language == "system" && Locale.getDefault().language == "bn")

        CompositionLocalProvider(
            LocalContainer provides c, LocalSettings provides s, LocalVm provides vm,
            LocalUi provides ui, LocalActions provides actions, LocalBn provides bn
        ) {
            AppTheme(s, dyn) { colors ->
                val dark = LocalIsDark.current
                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                GlassBackground(colors) {
                    when {
                        !s.onboarded -> OnboardingScreen { c.set(K.ONBOARDED, true) }
                        !perm -> PermissionScreen { requestPerms() }
                        else -> MainScaffold()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainScaffold() {
        val np by vm.now.collectAsState()
        BackHandler(enabled = ui.playerOpen || ui.stack.isNotEmpty() || ui.rootTab != 0) {
            when {
                ui.playerOpen -> ui.playerOpen = false
                ui.stack.isNotEmpty() -> ui.back()
                else -> ui.rootTab = 0
            }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { ScreenHost() }
                if (np != null) MiniPlayer()
                GlassBottomNav()
            }
            AnimatedVisibility(
                visible = ui.playerOpen,
                enter = slideInVertically(tween(380)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(250))
            ) { FullPlayer() }
        }
        ui.menuSong?.let { SongMenu(it) { ui.menuSong = null } }
        ui.playlistPick?.let { AddToPlaylistDialog(it) { ui.playlistPick = null } }
    }

    @Composable
    private fun ScreenHost() {
        AnimatedContent(
            targetState = ui.stack.lastOrNull() ?: Route.Root(ui.rootTab),
            transitionSpec = { (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 10 }) togetherWith fadeOut(tween(140)) },
            label = "nav"
        ) { r ->
            when (r) {
                is Route.Root -> when (r.tab) {
                    0 -> LibraryScreen()
                    1 -> SearchScreen()
                    else -> SettingsScreen()
                }
                is Route.Folder -> FolderScreen(r.path)
                is Route.Album -> AlbumScreen(r.id, r.name)
                is Route.Artist -> ArtistScreen(r.name)
                is Route.Genre -> GenreScreen(r.name)
                is Route.Playlist -> PlaylistDetailScreen(r.id)
                is Route.Details -> DetailsScreen(r.id)
                is Route.TagEdit -> TagEditScreen(r.id)
                is Route.Trim -> TrimScreen(r.id)
                is Route.LyricsEdit -> LyricsEditScreen(r.id)
                Route.Queue -> QueueScreen()
                Route.Eq -> EqScreen()
                Route.Themes -> ThemesScreen()
                Route.FolderFilter -> FolderFilterScreen()
                Route.Developer -> DeveloperScreen()
            }
        }
    }
}


@Composable
private fun SplashScreen() {
    val infinite = rememberInfiniteTransition(label = "splash")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splash-pulse"
    )
    val alpha by animateFloatAsState(1f, tween(700), label = "splash-alpha")
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF10101D), Color(0xFF30205B), Color(0xFF10101D)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(116.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF9B7BFF), Color(0xFF36D9C5))),
                        RoundedCornerShape(34.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(66.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Music Player", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("S Om Yogeshananda", color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.width(46.dp).height(3.dp).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF9B7BFF), Color(0xFF36D9C5)))))
        }
    }
}
