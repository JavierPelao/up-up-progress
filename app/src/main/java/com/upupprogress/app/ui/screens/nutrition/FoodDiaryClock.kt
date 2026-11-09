package com.upupprogress.app.ui.screens.nutrition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.upupprogress.app.data.model.millisUntilNextFoodDay
import java.time.ZonedDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Solo observa el reloj mientras la app está visible; no requiere alarmas ni trabajo en segundo plano. */
@Composable
fun ObserveFoodDate(onChanged: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onChanged)
    LaunchedEffect(context, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            callbackFlow {
                val clockChanges = Channel<Unit>(Channel.CONFLATED)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) { clockChanges.trySend(Unit) }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                val boundary = launch {
                    while (isActive) {
                        trySend(Unit)
                        withTimeoutOrNull(millisUntilNextFoodDay(ZonedDateTime.now())) { clockChanges.receive() }
                    }
                }
                awaitClose {
                    boundary.cancel()
                    clockChanges.close()
                    context.unregisterReceiver(receiver)
                }
            }.collect { callback() }
        }
    }
}
