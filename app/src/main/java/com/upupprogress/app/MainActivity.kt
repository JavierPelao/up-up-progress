package com.upupprogress.app

import com.upupprogress.app.ui.screens.home.HomeScreen
import com.upupprogress.app.ui.screens.training.TrainingScreen
import com.upupprogress.app.ui.components.AppBottomBar
import com.upupprogress.app.ui.components.AppDestination
import com.upupprogress.app.ui.screens.login.LoginScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.upupprogress.app.ui.theme.UpUpProgressTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpUpProgressTheme {
                var showHomePreview by rememberSaveable { mutableStateOf(false) }
                var selectedDestination by rememberSaveable {
                    mutableStateOf(AppDestination.HOME)
                }
                val tabStateHolder = rememberSaveableStateHolder()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                fun showPendingFeature(message: String) {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }

                BackHandler(enabled = showHomePreview) {
                    if (selectedDestination != AppDestination.HOME) {
                        selectedDestination = AppDestination.HOME
                    } else {
                        showHomePreview = false
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showHomePreview) {
                            AppBottomBar(
                                selectedDestination = selectedDestination,
                                onDestinationSelected = { destination ->
                                    when (destination) {
                                        AppDestination.HOME, AppDestination.TRAINING ->
                                            selectedDestination = destination
                                        AppDestination.NUTRITION ->
                                            showPendingFeature("Nutrición estará disponible próximamente.")
                                        AppDestination.PROGRESS ->
                                            showPendingFeature("Progreso estará disponible próximamente.")
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    val screenModifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)

                    if (showHomePreview) {
                        Column(modifier = screenModifier.fillMaxSize()) {
                            TextButton(onClick = { showHomePreview = false }) {
                                Text("← Volver al Login")
                            }
                            tabStateHolder.SaveableStateProvider(selectedDestination.name) {
                                when (selectedDestination) {
                                    AppDestination.HOME ->
                                        HomeScreen(modifier = Modifier.weight(1f))
                                    AppDestination.TRAINING ->
                                        TrainingScreen(
                                            modifier = Modifier.weight(1f),
                                            onCreateRoutine = {
                                                showPendingFeature(
                                                    "La creación de rutinas estará disponible próximamente."
                                                )
                                            },
                                            onViewRoutine = { _, _ ->
                                                showPendingFeature(
                                                    "El registro del entrenamiento estará disponible próximamente."
                                                )
                                            }
                                        )
                                    AppDestination.NUTRITION, AppDestination.PROGRESS -> Unit
                                }
                            }
                        }
                    } else {
                        LoginScreen(
                            modifier = screenModifier,
                            onSignIn = { _, _ ->
                                // TODO: Conectar Supabase Auth y navegar tras autenticar.
                                showPendingFeature(
                                    "El inicio de sesión estará disponible próximamente."
                                )
                            },
                            onForgotPassword = {
                                showPendingFeature(
                                    "La recuperación de contraseña estará disponible próximamente."
                                )
                            },
                            onCreateAccount = {
                                showPendingFeature(
                                    "La creación de cuentas estará disponible próximamente."
                                )
                            },
                            onPreviewHome = {
                                selectedDestination = AppDestination.HOME
                                showHomePreview = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UpUpProgressTheme {
        Greeting("Up Up Progress")
    }
}