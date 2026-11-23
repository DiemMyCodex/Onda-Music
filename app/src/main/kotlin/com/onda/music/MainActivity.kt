package com.onda.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.homeRecede
import com.onda.music.feature.home.DiscoverRoute
import com.onda.music.feature.home.HomeRoute
import com.onda.music.feature.library.LibraryRoute
import com.onda.music.feature.player.FullPlayerOverlay
import com.onda.music.feature.player.UnifiedDock
import com.onda.music.feature.search.SearchRoute
import com.onda.music.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ondaPreferences: OndaPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val userSettings by ondaPreferences.settings.collectAsState(initial = OndaUserSettings())
            OndaTheme {
                RuntimePermissionRequester()
                OndaRootShell(languageCode = userSettings.languageCode)
            }
        }
    }
}

@Composable
private fun RuntimePermissionRequester() {
    val context = LocalContext.current
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(requestedOnce) {
        if (requestedOnce) return@LaunchedEffect
        requestedOnce = true
        val requiredPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (requiredPermissions.isNotEmpty()) {
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }
}

@Composable
private fun OndaRootShell(languageCode: String) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val morphAnimatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reducedMotion = OndaTheme.reducedMotion

    val tabLabels = remember(languageCode) {
        if (languageCode.startsWith("vi", ignoreCase = true)) {
            listOf("Trang chủ", "Tìm kiếm", "Thư viện", "Khám phá")
        } else {
            listOf("Home", "Search", "Library", "Discover")
        }
    }
    val tabIcons = remember {
        listOf(
            Icons.Rounded.Home,
            Icons.Rounded.Search,
            Icons.Rounded.LibraryMusic,
            Icons.Rounded.Explore
        )
    }
    val inactiveTabIcons = remember {
        listOf(
            Icons.Outlined.Home,
            Icons.Outlined.Search,
            Icons.Outlined.LibraryMusic,
            Icons.Outlined.Explore
        )
    }

    val onMorphFractionChange: (Float) -> Unit = { target ->
        val clamped = target.coerceIn(0f, 1f)
        scope.launch {
            if (clamped == 0f || clamped == 1f) {
                morphAnimatable.animateTo(
                    targetValue = clamped,
                    animationSpec = AppMotion.adaptiveSpring(
                        dampingRatio = AppMotion.MorphSpring.dampingRatio,
                        stiffness = AppMotion.MorphSpring.stiffness,
                        reducedMotion = reducedMotion
                    )
                )
            } else {
                morphAnimatable.snapTo(clamped)
            }
        }
    }

    val openSettingsFromAny: () -> Unit = {
        scope.launch {
            morphAnimatable.snapTo(0f)
        }
        showSettings = true
    }

    BackHandler(enabled = showSettings && morphAnimatable.value <= 0.05f) {
        showSettings = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .homeRecede(
                        fractionProvider = { morphAnimatable.value },
                        reducedMotion = reducedMotion
                    )
            ) {
                AnimatedContent(
                    targetState = selectedTabIndex,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "root_tab_content"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> HomeRoute(
                            onOpenSettings = openSettingsFromAny,
                            onOpenSearch = { selectedTabIndex = 1 },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this
                        )
                        1 -> SearchRoute(
                            onOpenSettings = openSettingsFromAny
                        )
                        2 -> LibraryRoute(
                            onOpenSettings = openSettingsFromAny
                        )
                        else -> DiscoverRoute(
                            onOpenSettings = openSettingsFromAny
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + slideInHorizontally { it / 4 },
                    exit = fadeOut() + slideOutHorizontally { it / 4 }
                ) {
                    SettingsRoute(
                        onBack = { showSettings = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        UnifiedDock(
            tabLabels = tabLabels,
            tabIcons = tabIcons,
            selectedTabIndex = selectedTabIndex,
            onSelectTab = { index ->
                selectedTabIndex = index
                showSettings = false
            },
            morphFraction = morphAnimatable.value,
            onMorphFractionChange = onMorphFractionChange,
            onExpandPlayer = { onMorphFractionChange(1f) },
            modifier = Modifier.align(Alignment.BottomCenter),
            inactiveTabIcons = inactiveTabIcons
        )

        FullPlayerOverlay(
            morphFraction = morphAnimatable.value,
            onMorphFractionChange = onMorphFractionChange,
            onCollapse = { onMorphFractionChange(0f) },
            onOpenSettings = openSettingsFromAny,
            modifier = Modifier.fillMaxSize()
        )
    }
}
