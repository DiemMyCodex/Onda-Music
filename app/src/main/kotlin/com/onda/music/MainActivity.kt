package com.onda.music

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.onda.music.core.database.OndaPreferences
import com.onda.music.core.design.AppMotion
import com.onda.music.core.design.LocalOndaBackdropState
import com.onda.music.core.design.LocalOndaLaunchChoreography
import com.onda.music.core.design.LocalOndaToastState
import com.onda.music.core.design.OndaLaunchSplashOverlay
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.OndaTopToastHost
import com.onda.music.core.design.homeRecede
import com.onda.music.core.design.ondaBlurSource
import com.onda.music.core.design.ondaLaunchRise
import com.onda.music.core.design.rememberOndaBackdropState
import com.onda.music.core.design.rememberOndaLaunchChoreographyState
import com.onda.music.core.design.rememberOndaToastState
import com.onda.music.core.playback.PlaybackController
import com.onda.music.feature.home.DiscoverRoute
import com.onda.music.feature.home.HomeRoute
import com.onda.music.feature.library.LibraryRoute
import com.onda.music.feature.player.FullPlayerOverlay
import com.onda.music.feature.player.UnifiedDock
import com.onda.music.feature.search.SearchRoute
import com.onda.music.feature.settings.SettingsRoute
import com.onda.music.onboarding.OndaOnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ondaPreferences: OndaPreferences

    @Inject
    lateinit var playbackController: PlaybackController

    private var settingsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !settingsReady }
        super.onCreate(savedInstanceState)
        playbackController.warmUp()
        window.decorView.postDelayed({ settingsReady = true }, 600L)
        splashScreen.setOnExitAnimationListener { provider ->
            val fadeOut = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            fadeOut.duration = 80L
            fadeOut.interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            fadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    provider.remove()
                }
            })
            fadeOut.start()
        }
        val playLaunchIntro = savedInstanceState == null
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val loadedSettings by ondaPreferences.settings.collectAsState(initial = null)
            LaunchedEffect(loadedSettings) {
                if (loadedSettings != null) {
                    settingsReady = true
                }
            }
            var replayingOnboarding by rememberSaveable { mutableStateOf(false) }
            var dismissedOnboardingThisSession by rememberSaveable { mutableStateOf(false) }
            val activeSettings = loadedSettings

            LaunchedEffect(activeSettings?.keepScreenOn) {
                if (activeSettings?.keepScreenOn == true) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            OndaTheme {
                if (activeSettings == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF000000))
                    )
                } else {
                    val showOnboarding = !activeSettings.onboardingCompleted || replayingOnboarding
                    val reducedMotion = OndaTheme.reducedMotion
                    AnimatedContent(
                        targetState = showOnboarding,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(140)) togetherWith fadeOut(tween(120))
                            } else {
                                (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                                        initialScale = 0.96f
                                    )) togetherWith
                                    (fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                                            targetScale = 1.03f
                                        ))
                            }
                        },
                        label = "OndaFirstLaunchGate"
                    ) { onboardingVisible ->
                        if (onboardingVisible) {
                            OndaOnboardingScreen(
                                initialSettings = activeSettings,
                                isReplay = replayingOnboarding,
                                onComplete = { selection ->
                                    dismissedOnboardingThisSession = true
                                    replayingOnboarding = false
                                    lifecycleScope.launch {
                                        ondaPreferences.completeOnboarding(
                                            languageCode = selection.languageCode,
                                            playerStyle = selection.playerStyle,
                                            lyricsMotionEnabled = selection.lyricsMotionEnabled,
                                            monochromeEnabled = selection.monochromeEnabled,
                                            hapticsEnabled = selection.hapticsEnabled
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            RuntimePermissionRequester()
                            OndaRootShell(
                                languageCode = activeSettings.languageCode,
                                playLaunchIntro = playLaunchIntro && !dismissedOnboardingThisSession,
                                playbackController = playbackController,
                                onReplayOnboarding = { replayingOnboarding = true }
                            )
                        }
                    }
                }
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
private fun OndaRootShell(
    languageCode: String,
    playLaunchIntro: Boolean,
    playbackController: PlaybackController,
    onReplayOnboarding: () -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val morphAnimatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reducedMotion = OndaTheme.reducedMotion
    val toastState = rememberOndaToastState()
    val rootBackdropState = rememberOndaBackdropState()
    val launchState = rememberOndaLaunchChoreographyState(playOnStart = playLaunchIntro)

    LaunchedEffect(playbackController, languageCode) {
        playbackController.outputProfileSwitchEvents.collect { event ->
            val isVi = languageCode.startsWith("vi", ignoreCase = true)
            val eqSummary = if (event.eqEnabled) {
                event.presetLabel
            } else if (event.normalizeLoudness) {
                if (isVi) "Cân bằng âm lượng" else "Normalized"
            } else {
                if (isVi) "Phẳng (Flat)" else "Flat"
            }
            toastState.show(
                title = event.device.displayName,
                subtitle = if (isVi) {
                    "Đã áp dụng cấu hình: $eqSummary"
                } else {
                    "Applied audio profile: $eqSummary"
                },
                durationMs = 2_400L
            )
        }
    }

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

    val canCloseSettingsByBack by remember {
        derivedStateOf { showSettings && morphAnimatable.value <= 0.05f }
    }
    BackHandler(enabled = canCloseSettingsByBack) {
        showSettings = false
    }

    CompositionLocalProvider(
        LocalOndaToastState provides toastState,
        LocalOndaBackdropState provides rootBackdropState,
        LocalOndaLaunchChoreography provides launchState
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .ondaBlurSource(
                            state = rootBackdropState,
                            isActive = { morphAnimatable.value < 0.65f }
                        )
                        .homeRecede(
                            fractionProvider = { morphAnimatable.value },
                            reducedMotion = reducedMotion
                        )
                ) {
                    AnimatedContent(
                        targetState = selectedTabIndex,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(100))
                            } else {
                                val dir = if (targetState >= initialState) 1 else -1
                                (slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { dir * (it / 10) } +
                                    fadeIn(tween(190, easing = FastOutSlowInEasing)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -dir * (it / 10) } +
                                            fadeOut(tween(140))
                                    )
                            }
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
                        enter = if (reducedMotion) {
                            fadeIn(tween(120))
                        } else {
                            fadeIn(tween(180)) + slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f)
                            ) { it / 4 }
                        },
                        exit = if (reducedMotion) {
                            fadeOut(tween(100))
                        } else {
                            fadeOut(tween(150)) + slideOutHorizontally(
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            ) { it / 4 }
                        }
                    ) {
                        SettingsRoute(
                            onBack = { showSettings = false },
                            onReplayOnboarding = {
                                showSettings = false
                                onReplayOnboarding()
                            },
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
                morphFractionProvider = { morphAnimatable.value },
                onMorphFractionChange = onMorphFractionChange,
                onExpandPlayer = { onMorphFractionChange(1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .ondaLaunchRise(order = 3, distance = 40.dp),
                inactiveTabIcons = inactiveTabIcons
            )

            FullPlayerOverlay(
                morphFractionProvider = { morphAnimatable.value },
                onMorphFractionChange = onMorphFractionChange,
                onCollapse = { onMorphFractionChange(0f) },
                onOpenSettings = openSettingsFromAny,
                modifier = Modifier.fillMaxSize()
            )

            OndaLaunchSplashOverlay(
                state = launchState,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize()
            )

            OndaTopToastHost(
                state = toastState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
