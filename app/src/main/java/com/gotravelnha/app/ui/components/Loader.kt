package com.gotravelnha.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.gotravelnha.app.R
import kotlin.coroutines.cancellation.CancellationException

/** Remote data for a screen: loads when `keys` change; `reload()` fetches again */
class Loader<T>(val state: MutableState<Load<T>>, private val attempt: MutableState<Int>) {
    val value: Load<T> get() = state.value
    fun reload() { attempt.value++ }
    fun update(transform: (T) -> T) {
        (state.value as? Load.Ready)?.let { state.value = Load.Ready(transform(it.value)) }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    val attempt = remember { mutableIntStateOf(0) }
    LaunchedEffect(*keys, attempt.intValue) {
        if (state.value !is Load.Ready) state.value = Load.Loading
        state.value = try {
            Load.Ready(block())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Load.Failed(error.userMessage())
        }
    }
    return remember(state) { Loader(state, attempt) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
