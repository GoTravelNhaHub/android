package com.gotravelnha.app.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gotravelnha.app.R
import com.gotravelnha.app.ui.community.CommunityScreen
import com.gotravelnha.app.ui.explore.ExploreScreen
import com.gotravelnha.app.ui.planner.PlannerScreen
import com.gotravelnha.app.ui.profile.ProfileScreen
import com.gotravelnha.app.ui.trip.TripDetailScreen
import com.gotravelnha.app.ui.trip.TripMapScreen
import com.gotravelnha.app.ui.trips.MyTripDetailScreen
import com.gotravelnha.app.ui.trips.TripsScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Explore("explore", R.string.tab_explore, Icons.Filled.Home),
    Community("community", R.string.tab_community, Icons.Filled.Face),
    Planner("planner", R.string.tab_planner, Icons.Filled.Star),
    Trips("trips", R.string.tab_trips, Icons.Filled.DateRange),
    Profile("profile", R.string.tab_profile, Icons.Filled.AccountCircle),
}

/** Navigation calls the screens use */
class Routes(private val nav: NavHostController) {
    fun trip(slug: String) = nav.navigate("trip/$slug")
    fun tripMap(slug: String, day: Int?) = nav.navigate("trip/$slug/map?day=${day ?: 0}")
    fun myTrip(id: String) = nav.navigate("mytrip/$id")
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun back() = nav.popBackStack()
}

@Composable
fun AppNav(deepLinkSlug: String?, onDeepLinkHandled: () -> Unit) {
    val state: AppState = viewModel()
    val nav = rememberNavController()
    val routes = Routes(nav)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) state.completeSignIn(result.data) else state.afterSignIn = null
    }
    val signIn: (Boolean) -> Unit = { register ->
        scope.launch {
            runCatching { signInLauncher.launch(state.signInIntent(register)) }
                .onFailure {
                    Log.w("GoTravelNha", "Sign-in could not start", it)
                    state.afterSignIn = null
                    state.authError = context.getString(R.string.signin_unavailable)
                }
        }
    }

    LaunchedEffect(deepLinkSlug) {
        if (deepLinkSlug != null) {
            routes.trip(deepLinkSlug)
            onDeepLinkHandled()
        }
    }

    state.authError?.let { message ->
        AlertDialog(
            onDismissRequest = { state.authError = null },
            confirmButton = { TextButton(onClick = { state.authError = null }) { Text(stringResource(R.string.action_ok)) } },
            title = { Text(stringResource(R.string.signin_failed)) },
            text = { Text(message) },
        )
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // My Trips needs an account: open it after sign-in, and leave it when the session ends
    LaunchedEffect(state.signedIn, current) {
        if (state.signedIn) {
            state.afterSignIn?.let {
                state.afterSignIn = null
                routes.tab(it)
            }
        } else if (current == Tab.Trips.route) {
            routes.tab(Tab.Explore.route)
        }
    }

    CompositionLocalProvider(LocalSignIn provides signIn) {
        Scaffold(
            bottomBar = {
                if (Tab.entries.any { it.route == current }) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    if (tab == Tab.Trips && !state.signedIn) {
                                        state.afterSignIn = tab.route
                                        signIn(false)
                                    } else {
                                        routes.tab(tab.route)
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(stringResource(tab.label), maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.Explore.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable(Tab.Explore.route) {
                    ExploreScreen(state, routes, onCommunityDestination = { destination ->
                        state.communityDestination = destination
                        routes.tab(Tab.Community.route)
                    }, onPlan = { routes.tab(Tab.Planner.route) })
                }
                composable(Tab.Community.route) { CommunityScreen(state, routes) }
                composable(Tab.Planner.route) { PlannerScreen(state, routes) }
                composable(Tab.Trips.route) { TripsScreen(state, routes) }
                composable(Tab.Profile.route) { ProfileScreen(state) }
                composable("trip/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                    TripDetailScreen(state, routes, entry.arguments?.getString("slug").orEmpty(), onCustomize = { seed ->
                        state.plannerSeed = seed
                        routes.tab(Tab.Planner.route)
                    })
                }
                composable(
                    "trip/{slug}/map?day={day}",
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }, navArgument("day") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    TripMapScreen(state, routes, entry.arguments?.getString("slug").orEmpty(), entry.arguments?.getInt("day")?.takeIf { it > 0 })
                }
                composable("mytrip/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    MyTripDetailScreen(state, routes, entry.arguments?.getString("id").orEmpty())
                }
            }
        }
    }
}
