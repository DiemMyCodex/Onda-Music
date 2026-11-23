package com.onda.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.homeRecede
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
        enableEdgeToEdge()
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
            listOf("Trang chủ", "Tìm kiếm", "Thư viện")
        } else {
            listOf("Home", "Search", "Library")
        }
    }
    val tabIcons = remember {
        listOf(
            Icons.Rounded.Home,
            Icons.Rounded.Search,
            Icons.Rounded.LibraryMusic
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

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.AmoledBlack)
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
                        else -> LibraryRoute(
                            onOpenSettings = openSettingsFromAny
                        )
                    }
                }
            }
        }

        if (!showSettings) {
            UnifiedDock(
                tabLabels = tabLabels,
                tabIcons = tabIcons,
                selectedTabIndex = selectedTabIndex,
                onSelectTab = { selectedTabIndex = it },
                morphFraction = morphAnimatable.value,
                onMorphFractionChange = onMorphFractionChange,
                onExpandPlayer = { onMorphFractionChange(1f) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            FullPlayerOverlay(
                morphFraction = morphAnimatable.value,
                onMorphFractionChange = onMorphFractionChange,
                onCollapse = { onMorphFractionChange(0f) },
                onOpenSettings = openSettingsFromAny,
                modifier = Modifier.fillMaxSize()
            )
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
