package com.cijian.app.ui.nav

import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.cijian.app.ui.screens.calc.CalcScreen
import com.cijian.app.ui.screens.favorites.FavoritesScreen
import com.cijian.app.ui.screens.home.HomeScreen
import com.cijian.app.ui.screens.path.PathScreen
import com.cijian.app.ui.screens.quiz.QuizScreen
import com.cijian.app.ui.screens.segment.SegmentScreen
import com.cijian.app.ui.screens.starmap.StarMapScreen
import com.cijian.app.ui.screens.word.WordDetailScreen
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper

object Routes {
    const val HOME = "home"
    const val STARMAP = "starmap?focus={focus}"
    const val FAVORITES = "favorites"
    const val WORD = "word/{word}"
    const val TOOLS = "tools/{feature}?from={from}&to={to}"

    fun word(w: String) = "word/${Uri.encode(w)}"

    fun tools(feature: String, from: String? = null, to: String? = null): String {
        val f = Uri.encode(from ?: "")
        val t = Uri.encode(to ?: "")
        return "tools/$feature?from=$f&to=$t"
    }
}

private data class Tab(
    val base: String,        // 用于选中的路由前缀
    val navRoute: String,    // 点击导航的目标
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
)

private val tabs = listOf(
    Tab("home", Routes.HOME, "首页", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("tools", Routes.tools("calc"), "词途", Icons.Outlined.Store, Icons.Filled.Store),
    Tab("starmap", Routes.STARMAP, "星图", Icons.Outlined.Map, Icons.Filled.Map),
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
    val showBar = route == Routes.HOME || route == Routes.STARMAP ||
            route == Routes.FAVORITES || route.startsWith("tools")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                val base = if (route.startsWith("tools")) "tools" else route
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
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(
                    Routes.TOOLS,
                    arguments = listOf(
                        navArgument("feature") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; defaultValue = "" },
                        navArgument("to") { type = NavType.StringType; defaultValue = "" },
                    )
                ) { entry ->
                    when (entry.arguments?.getString("feature") ?: "calc") {
                        "path" -> PathScreen(
                            nav,
                            entry.arguments?.getString("from").orEmpty(),
                            entry.arguments?.getString("to").orEmpty()
                        )
                        "segment" -> SegmentScreen(nav)
                        "quiz" -> QuizScreen(nav)
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
