package com.cijian.app.ui.nav

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cijian.app.EngineState
import com.cijian.app.ErrorScreen
import com.cijian.app.LoadingScreen
import com.cijian.app.ciJian
import com.cijian.app.ui.screens.blitz.BlitzScreen
import com.cijian.app.ui.screens.bridge.BridgeScreen
import com.cijian.app.ui.screens.calc.CalcScreen
import com.cijian.app.ui.screens.distill.DistillScreen
import com.cijian.app.ui.screens.favorites.FavoritesScreen
import com.cijian.app.ui.screens.guess.GuessScreen
import com.cijian.app.ui.screens.home.HomeScreen
import com.cijian.app.ui.screens.path.PathScreen
import com.cijian.app.ui.screens.quiz.QuizScreen
import com.cijian.app.ui.screens.segment.SegmentScreen
import com.cijian.app.ui.screens.starmap.StarMapScreen
import com.cijian.app.ui.screens.tools.ToolsHubScreen
import com.cijian.app.ui.screens.word.WordDetailScreen
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import kotlin.math.abs

object Routes {
    const val HOME = "home"
    const val TOOLS_HUB = "tools"                                  // 词途·工具集（Tab）
    const val TOOL = "tool/{feature}?from={from}&to={to}"          // 玩法页（独立、带返回键、无底栏）
    const val STARMAP = "starmap?focus={focus}"                    // 星图（Tab）
    const val FAVORITES = "favorites"
    const val WORD = "word/{word}"

    fun word(w: String) = "word/${Uri.encode(w)}"

    fun tool(feature: String, from: String? = null, to: String? = null): String {
        val f = Uri.encode(from ?: "")
        val t = Uri.encode(to ?: "")
        return "tool/$feature?from=$f&to=$t"
    }

    fun starmap(focus: String? = null): String =
        if (focus.isNullOrBlank()) "starmap" else "starmap?focus=${Uri.encode(focus)}"
}

private data class Tab(
    val base: String,        // 选中态匹配的路由前缀
    val navRoute: String,    // 点击导航的目标
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
)

private val tabs = listOf(
    Tab("home", Routes.HOME, "首页", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("tools", Routes.TOOLS_HUB, "词途", Icons.Outlined.Store, Icons.Filled.Store),
    Tab("starmap", Routes.starmap(), "星图", Icons.Outlined.Map, Icons.Filled.Map),
    Tab("favorites", Routes.FAVORITES, "收藏", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
)

@Composable
fun AppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.ciJian
    when (val st = app.state) {
        is EngineState.Loading -> LoadingScreen(st.progress)
        is EngineState.Error -> ErrorScreen(st.message) { app.loadModel() }
        EngineState.Ready -> MainScaffold()
    }
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME

    // 只有四个 Tab 页显示底栏；玩法页（tool/...）与词详情页全屏沉浸
    val showBar = route == Routes.HOME || route == Routes.TOOLS_HUB ||
            route == Routes.FAVORITES || route.startsWith("starmap")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                val base = when {
                    route == Routes.HOME -> "home"
                    route == Routes.TOOLS_HUB -> "tools"
                    route == Routes.FAVORITES -> "favorites"
                    else -> "starmap"
                }
                NavigationBar(containerColor = Paper) {
                    for (tab in tabs) {
                        val selected = tab.base == base
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.navRoute) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.activeIcon else tab.icon, tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cinnabar,
                                selectedTextColor = Cinnabar,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = InkSoft,
                                unselectedTextColor = InkSoft,
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 全局上下过冲：列表滚到头继续拖，整页轻微位移并弹回
        val bounce = remember { mutableFloatStateOf(0f) }
        val bounceConn = rememberBounceOverscroll(bounce)
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(bounceConn)
                .graphicsLayer { translationY = bounce.floatValue }
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
                // 页面切换：新页自右侧轻滑入，旧页向左淡出；返回时反向
                enterTransition = {
                    slideInHorizontally(
                        tween(320, easing = FastOutSlowInEasing)
                    ) { it / 4 } + fadeIn(tween(320))
                },
                exitTransition = {
                    slideOutHorizontally(
                        tween(320, easing = FastOutSlowInEasing)
                    ) { -it / 10 } + fadeOut(tween(220))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        tween(320, easing = FastOutSlowInEasing)
                    ) { -it / 10 } + fadeIn(tween(320))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        tween(320, easing = FastOutSlowInEasing)
                    ) { it / 4 } + fadeOut(tween(220))
                }
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.TOOLS_HUB) { ToolsHubScreen(nav) }
                composable(
                    Routes.TOOL,
                    arguments = listOf(
                        navArgument("feature") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; defaultValue = "" },
                        navArgument("to") { type = NavType.StringType; defaultValue = "" },
                    )
                ) { entry ->
                    val feature = entry.arguments?.getString("feature") ?: "calc"
                    val from = entry.arguments?.getString("from").orEmpty()
                    val to = entry.arguments?.getString("to").orEmpty()
                    when (feature) {
                        "path" -> PathScreen(nav, from, to)
                        "segment" -> SegmentScreen(nav)
                        "quiz" -> QuizScreen(nav)
                        "guess" -> GuessScreen(nav)
                        "bridge" -> BridgeScreen(nav)
                        "distill" -> DistillScreen(nav)
                        "blitz" -> BlitzScreen(nav)
                        else -> CalcScreen(nav)
                    }
                }
                composable(
                    Routes.STARMAP,
                    arguments = listOf(navArgument("focus") { type = NavType.StringType; defaultValue = "" })
                ) { entry ->
                    StarMapScreen(nav, entry.arguments?.getString("focus").orEmpty())
                }
                composable(Routes.FAVORITES) { FavoritesScreen(nav) }
                composable(
                    Routes.WORD,
                    arguments = listOf(navArgument("word") { type = NavType.StringType })
                ) { entry ->
                    WordDetailScreen(nav, entry.arguments?.getString("word").orEmpty())
                }
            }
        }
    }
}

/**
 * 全局「果冻」过冲：任何内部列表滚到尽头后继续拖动，整页以橡胶阻尼位移，
 * 松手后弹回。只用 onPostScroll 拿列表吃剩的量，正常滚动零干扰。
 */
@Composable
private fun rememberBounceOverscroll(offset: MutableFloatState): NestedScrollConnection {
    val density = LocalDensity.current
    val maxPx = with(density) { 120.dp.toPx() }
    val anim = remember { Animatable(0f) }
    return remember(maxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val cur = offset.floatValue
                val want = available.y
                if (cur == 0f || want == 0f) return Offset.Zero
                // 与过冲方向相反的拖动：先原路回弹到 0，再放行给列表
                val restore = if (cur > 0f && want < 0f) (-want).coerceAtMost(cur)
                else if (cur < 0f && want > 0f) want.coerceAtMost(-cur)
                else 0f
                if (restore > 0f) {
                    offset.floatValue = cur + (if (cur > 0f) -restore else restore)
                    return Offset(0f, if (cur > 0f) -restore else restore)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val d = available.y
                if (d == 0f) return Offset.Zero
                if (anim.isRunning) anim.stop()
                val cur = offset.floatValue
                val room = ((maxPx - abs(cur)) / maxPx).coerceIn(0f, 1f)
                offset.floatValue = (cur + d * 0.42f * room).coerceIn(-maxPx, maxPx)
                return Offset(0f, d)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset.floatValue != 0f) {
                    anim.stop()
                    anim.snapTo(offset.floatValue)
                    anim.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) { _, _, _ -> offset.floatValue = anim.value }
                }
                return Velocity.Zero
            }
        }
    }
}
