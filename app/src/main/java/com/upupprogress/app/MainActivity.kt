package com.upupprogress.app

import com.upupprogress.app.ui.screens.home.HomeScreen
import com.upupprogress.app.ui.screens.training.TrainingScreen
import com.upupprogress.app.ui.components.AppBottomBar
import com.upupprogress.app.ui.components.AppDestination
import com.upupprogress.app.ui.screens.login.LoginScreen
import com.upupprogress.app.ui.screens.login.LoginViewModel
import com.upupprogress.app.ui.screens.registration.RegistrationFlow
import com.upupprogress.app.ui.screens.registration.RegistrationViewModel
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val loginViewModel: LoginViewModel by viewModels()
    private val registrationViewModel: RegistrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpUpProgressTheme {
                val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
                val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
                var showRegistration by rememberSaveable { mutableStateOf(false) }
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

                LaunchedEffect(loginState.isAuthenticated) {
                    if (loginState.isAuthenticated) {
                        showRegistration = false
                    } else {
                        selectedDestination = AppDestination.HOME
                        AppDestination.entries.forEach { tabStateHolder.removeState(it.name) }
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }

                LaunchedEffect(loginState.isAuthenticated, loginState.errorMessage) {
                    val message = loginState.errorMessage
                    if (loginState.isAuthenticated && message != null) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }

                BackHandler(enabled = !loginState.isAuthenticated && showRegistration) {
                    registrationViewModel.reset()
                    showRegistration = false
                }

                BackHandler(
                    enabled = loginState.isAuthenticated &&
                        selectedDestination != AppDestination.HOME
                ) {
                    selectedDestination = AppDestination.HOME
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (loginState.isAuthenticated) {
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

                    if (loginState.isAuthenticated) {
                        Column(modifier = screenModifier.fillMaxSize()) {
                            TextButton(
                                onClick = loginViewModel::signOut,
                                enabled = !loginState.isLoading
                            ) {
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
                    } else if (showRegistration) {
                        RegistrationFlow(
                            modifier = screenModifier,
                            onSignUp = registrationViewModel::signUp,
                            onBackToLogin = {
                                registrationViewModel.reset()
                                showRegistration = false
                            },
                            isLoading = registrationState.isLoading,
                            errorMessage = registrationState.errorMessage,
                            result = registrationState.result
                        )
                    } else {
                        LoginScreen(
                            modifier = screenModifier,
                            onSignIn = loginViewModel::signIn,
                            isLoading = loginState.isLoading,
                            errorMessage = loginState.errorMessage,
                            onForgotPassword = {
                                showPendingFeature(
                                    "La recuperación de contraseña estará disponible próximamente."
                                )
                            },
                            onCreateAccount = {
                                registrationViewModel.reset()
                                showRegistration = true
                            },
                            onPreviewHome = {
                                showPendingFeature(
                                    "Inicia sesión para acceder al menú principal."
                                )
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