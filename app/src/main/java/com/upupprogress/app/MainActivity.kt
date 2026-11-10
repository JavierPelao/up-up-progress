package com.upupprogress.app

import com.upupprogress.app.ui.screens.home.HomeScreen
import com.upupprogress.app.ui.screens.nutrition.*
import java.time.LocalDate
import com.upupprogress.app.ui.screens.training.TrainingScreen
import com.upupprogress.app.ui.screens.profile.ProfileScreen
import com.upupprogress.app.ui.screens.progress.ProgressScreen
import com.upupprogress.app.ui.screens.progress.ProgressViewModel
import com.upupprogress.app.ui.components.AppHeader
import com.upupprogress.app.ui.components.AppBottomBar
import com.upupprogress.app.ui.components.AppDestination
import com.upupprogress.app.ui.screens.login.LoginScreen
import com.upupprogress.app.ui.screens.login.LoginViewModel
import com.upupprogress.app.ui.screens.registration.RegistrationFlow
import com.upupprogress.app.ui.screens.registration.RegistrationViewModel
import com.upupprogress.app.ui.screens.registration.AccountSetupScreen
import com.upupprogress.app.ui.screens.registration.AccountSetupPhase
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    private val foodDiaryViewModel: FoodDiaryViewModel by viewModels()
    private val progressViewModel: ProgressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpUpProgressTheme {
                val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
                val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
                val foodState by foodDiaryViewModel.uiState.collectAsStateWithLifecycle()
                val progressState by progressViewModel.uiState.collectAsStateWithLifecycle()
                val canEnterHome = loginState.isAuthenticated &&
                    registrationState.setupPhase == AccountSetupPhase.COMPLETE && !registrationState.showSavedConfirmation
                var showRegistration by rememberSaveable { mutableStateOf(false) }
                var showProfile by rememberSaveable { mutableStateOf(false) }
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

                fun showNutritionToday() {
                    foodDiaryViewModel.selectDate(LocalDate.now())
                    selectedDestination = AppDestination.NUTRITION
                }

                fun registerFoodToday() {
                    showNutritionToday()
                    foodDiaryViewModel.startScan(forToday = true)
                }

                fun returnToPreviousScreen() {
                    if (showProfile) showProfile = false
                    else selectedDestination = AppDestination.HOME
                }

                LaunchedEffect(loginState.isAuthenticated) {
                    if (loginState.isAuthenticated) {
                        registrationViewModel.onAuthenticated()
                        showRegistration = false
                    } else {
                        registrationViewModel.onSignedOut()
                        selectedDestination = AppDestination.HOME
                        showProfile = false
                        AppDestination.entries.forEach { tabStateHolder.removeState(it.name) }
                        tabStateHolder.removeState("PROFILE")
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }

                LaunchedEffect(canEnterHome, registrationState.savedStatus?.userId) {
                    if (canEnterHome) {
                        val userId = requireNotNull(registrationState.savedStatus?.userId)
                        foodDiaryViewModel.activate(userId)
                        progressViewModel.activate(userId)
                    } else if (!loginState.isAuthenticated) {
                        foodDiaryViewModel.clear()
                        progressViewModel.clear()
                    }
                }
                if (canEnterHome) ObserveFoodDate(foodDiaryViewModel::onClockChanged)

                LaunchedEffect(loginState.isAuthenticated, loginState.errorMessage) {
                    val message = loginState.errorMessage
                    if (loginState.isAuthenticated && message != null) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }

                BackHandler(enabled = !loginState.isAuthenticated && showRegistration && !registrationState.isLoading) {
                    registrationViewModel.reset()
                    showRegistration = false
                }

                BackHandler(
                    enabled = canEnterHome && (showProfile || selectedDestination != AppDestination.HOME)
                ) {
                    returnToPreviousScreen()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (canEnterHome) {
                            AppHeader(
                                title = when {
                                    showProfile -> "Perfil"
                                    selectedDestination == AppDestination.HOME -> "Up Up Progress"
                                    else -> selectedDestination.label
                                },
                                onBack = if (showProfile || selectedDestination != AppDestination.HOME) {
                                    ::returnToPreviousScreen
                                } else null,
                                onProfile = if (showProfile) null else { { showProfile = true } },
                            )
                        }
                    },
                    bottomBar = {
                        if (canEnterHome && !showProfile) {
                            AppBottomBar(
                                selectedDestination = selectedDestination,
                                onDestinationSelected = { destination ->
                                    selectedDestination = destination
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    val screenModifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)

                    if (loginState.isAuthenticated && !canEnterHome) {
                        AccountSetupScreen(
                            state = registrationState, email = registrationViewModel.authenticatedEmail,
                            onComplete = registrationViewModel::completeProfile,
                            onRetry = registrationViewModel::onAuthenticated,
                            onContinue = registrationViewModel::acknowledgeSaved,
                            onSignOut = loginViewModel::signOut,
                            onExit = { finish() }, modifier = screenModifier,
                        )
                    } else if (canEnterHome) {
                        val status = requireNotNull(registrationState.savedStatus)
                        tabStateHolder.SaveableStateProvider(if (showProfile) "PROFILE" else selectedDestination.name) {
                            if (showProfile) {
                                ProfileScreen(
                                    status = status, email = registrationViewModel.authenticatedEmail,
                                    onSignOut = loginViewModel::signOut, isSigningOut = loginState.isLoading,
                                    modifier = screenModifier,
                                )
                            } else {
                                when (selectedDestination) {
                                    AppDestination.HOME ->
                                        HomeScreen(
                                            targets = requireNotNull(status.targets),
                                            diary = foodState.today,
                                            isNutritionLoading = foodState.isLoading,
                                            nutritionError = foodState.error,
                                            onRetryNutrition = foodDiaryViewModel::refresh,
                                            onRegisterFood = ::registerFoodToday,
                                            onCreateRoutine = {
                                                showPendingFeature("La creación de rutinas estará disponible próximamente.")
                                            },
                                            onViewNutrition = ::showNutritionToday,
                                            modifier = screenModifier,
                                        )
                                    AppDestination.TRAINING ->
                                        TrainingScreen(
                                            modifier = screenModifier,
                                            onCreateRoutine = {
                                                showPendingFeature("La creación de rutinas estará disponible próximamente.")
                                            },
                                            onViewRoutine = { _, _ ->
                                                showPendingFeature("El registro del entrenamiento estará disponible próximamente.")
                                            }
                                        )
                                    AppDestination.NUTRITION ->
                                        NutritionScreen(
                                            state = foodState,
                                            onDateSelected = foodDiaryViewModel::selectDate,
                                            onRegisterFood = { foodDiaryViewModel.startScan() },
                                            onRefresh = foodDiaryViewModel::refresh,
                                            modifier = screenModifier,
                                        )
                                    AppDestination.PROGRESS ->
                                        ProgressScreen(
                                            state = progressState,
                                            onAnalyze = progressViewModel::analyze,
                                            onDismissResult = progressViewModel::dismissResult,
                                            modifier = screenModifier,
                                        )
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
                            result = registrationState.result,
                            loadingMessage = registrationState.loadingMessage
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
                if (canEnterHome) {
                    if (foodState.scannerVisible) {
                        BarcodeScannerDialog(foodDiaryViewModel::barcodeScanned, foodDiaryViewModel::cancelProduct)
                    } else if (foodState.pendingDate != null) {
                        FoodProductDialog(
                            state = foodState, onQuantityChange = foodDiaryViewModel::changeQuantity,
                            onMealChange = foodDiaryViewModel::changeMeal, onAdd = foodDiaryViewModel::saveFood,
                            onCancel = foodDiaryViewModel::cancelProduct,
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