/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.navigation.compose.samples

import androidx.annotation.Sampled
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollableColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigate
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String, @StringRes val resourceId: Int) {
    object Profile : Screen("profile", R.string.profile)
    object Dashboard : Screen("dashboard", R.string.dashboard)
    object Scrollable : Screen("scrollable", R.string.scrollable)
}

@Sampled
@Composable
fun BasicNav() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Profile.route) {
        composable(Screen.Profile.route) { Profile(navController) }
        composable(Screen.Dashboard.route) { Dashboard(navController) }
        composable(Screen.Scrollable.route) { Scrollable(navController) }
    }
}

@Sampled
@Composable
fun NestedNavStartDestination() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "nested") {
        navigation(startDestination = Screen.Profile.route, route = "nested") {
            composable(Screen.Profile.route) { Profile(navController) }
        }
        composable(Screen.Dashboard.route) { Dashboard(navController) }
        composable(Screen.Scrollable.route) { Scrollable(navController) }
    }
}

@Sampled
@Composable
fun NestedNavInGraph() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Profile.route) {
        composable(Screen.Profile.route) { Profile(navController) }
        navigation(startDestination = "nested", route = Screen.Dashboard.route) {
            composable("nested") { Dashboard(navController) }
        }
        composable(Screen.Scrollable.route) { Scrollable(navController) }
    }
}

@Composable
fun Profile(navController: NavHostController) {
    Column(Modifier.fillMaxSize().then(Modifier.padding(8.dp))) {
        Text(text = stringResource(Screen.Profile.resourceId))
        NavigateButton(stringResource(Screen.Dashboard.resourceId)) {
            navController.navigate(Screen.Dashboard.route)
        }
        Divider(color = Color.Black)
        NavigateButton(stringResource(Screen.Scrollable.resourceId)) {
            navController.navigate(Screen.Scrollable.route)
        }
        Spacer(Modifier.weight(1f))
        NavigateBackButton(navController)
    }
}

@Composable
fun Dashboard(navController: NavController, title: String? = null) {
    Column(Modifier.fillMaxSize().then(Modifier.padding(8.dp))) {
        Text(text = title ?: stringResource(Screen.Dashboard.resourceId))
        Spacer(Modifier.weight(1f))
        NavigateBackButton(navController)
    }
}

@Composable
fun Scrollable(navController: NavController) {
    Column(Modifier.fillMaxSize().then(Modifier.padding(8.dp))) {
        NavigateButton(stringResource(Screen.Dashboard.resourceId)) {
            navController.navigate(Screen.Dashboard.route)
        }
        ScrollableColumn(Modifier.weight(1f)) {
            phrases.forEach { phrase ->
                Text(phrase, fontSize = 30.sp)
            }
        }
        NavigateBackButton(navController)
    }
}

@Composable
fun NavigateButton(
    text: String,
    listener: () -> Unit = { }
) {
    Button(
        onClick = listener,
        colors = ButtonDefaults.buttonColors(backgroundColor = LightGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Navigate to $text")
    }
}

@Composable
fun NavigateBackButton(navController: NavController) {
    if (navController.previousBackStackEntry != null) {
        Button(
            onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(backgroundColor = LightGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Go to Previous screen")
        }
    }
}

private val phrases = listOf(
    "Easy As Pie",
    "Wouldn't Harm a Fly",
    "No-Brainer",
    "Keep On Truckin'",
    "An Arm and a Leg",
    "Down To Earth",
    "Under the Weather",
    "Up In Arms",
    "Cup Of Joe",
    "Not the Sharpest Tool in the Shed",
    "Ring Any Bells?",
    "Son of a Gun",
    "Hard Pill to Swallow",
    "Close But No Cigar",
    "Beating a Dead Horse",
    "If You Can't Stand the Heat, Get Out of the Kitchen",
    "Cut To The Chase",
    "Heads Up",
    "Goody Two-Shoes",
    "Fish Out Of Water",
    "Cry Over Spilt Milk",
    "Elephant in the Room",
    "There's No I in Team",
    "Poke Fun At",
    "Talk the Talk",
    "Know the Ropes",
    "Fool's Gold",
    "It's Not Brain Surgery",
    "Fight Fire With Fire",
    "Go For Broke"
)
