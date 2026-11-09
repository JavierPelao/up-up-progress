package com.upupprogress.app.ui.screens.registration

import androidx.lifecycle.SavedStateHandle
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.*
import com.upupprogress.app.ui.screens.onboarding.*
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private val date = LocalDate.of(2026, 11, 8)
    private val account = AuthenticatedAccount("user-a", "example@example.invalid")
    private val form = OnboardingFormState(
        name = "Ejemplo", ageYears = 30, heightCm = 175, sexForFormula = FormulaSexUi.MALE,
        weightHundredthsKg = 7000, activityLevel = ActivityLevelUi.ACTIVE,
        personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT, nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC,
    )

    private class FakeAuth : RegistrationAuthGateway {
        var account: AuthenticatedAccount? = null
        var result: RegistrationResult = RegistrationResult.EmailConfirmationRequired
        var deferred: CompletableDeferred<RegistrationResult>? = null
        var calls = 0
        override fun currentAccount() = account
        override suspend fun signUp(email: String, password: String): RegistrationResult {
            calls++
            return deferred?.await() ?: result
        }
    }

    private fun completeStatus() = OnboardingStatus(
        isComplete = true, userId = account.id, displayName = "Guardado",
        weightKg = BigDecimal("70.00"), measuredOn = date, nutritionGoalMode = "automatic",
        targets = NutritionTargets(2473, BigDecimal("126.00"), BigDecimal("306.78"), BigDecimal("82.43")),
    )

    private class FakeOnboarding(var state: OnboardingStatus, val completed: OnboardingStatus) : OnboardingGateway {
        var calls = 0
        var submissions = mutableListOf<OnboardingSubmission>()
        var loseResponse = false
        var failBeforeSaving = false
        override suspend fun loadStatus() = state
        override suspend fun complete(submission: OnboardingSubmission): OnboardingStatus {
            calls++
            submissions.add(submission)
            if (failBeforeSaving) throw IOException("Sin conexión")
            state = completed
            if (loseResponse) throw IOException("Respuesta perdida")
            return state
        }
    }

    @Test fun confirmedSessionSavesDataAndShowsOnlyReturnedTargets() = runTest {
        val auth = FakeAuth().apply { account = this@RegistrationViewModelTest.account; result = RegistrationResult.SessionStarted }
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus())
        val handle = SavedStateHandle()
        val vm = RegistrationViewModel(handle, auth, repository) { date }
        vm.signUp(account.email!!, "synthetic-credential", form)
        advanceUntilIdle()
        assertEquals(1, repository.calls)
        assertEquals("high", repository.submissions.single().activityLevel)
        assertEquals(date, repository.submissions.single().measuredOn)
        assertEquals(AccountSetupPhase.COMPLETE, vm.uiState.value.setupPhase)
        assertTrue(vm.uiState.value.showSavedConfirmation)
        assertEquals(BigDecimal("306.78"), vm.uiState.value.savedStatus!!.targets!!.carbsGrams)
        assertFalse(handle.keys().any { it.contains("password") || it.contains("token") })
        assertFalse(handle.get<ArrayList<Any>>("pending_form").toString().contains("synthetic-credential"))
        vm.acknowledgeSaved()
        assertNull(handle.get<Any>("pending_form"))
    }

    @Test fun emailConfirmationResumesAfterRecreationWithoutSavingCredentials() = runTest {
        val auth = FakeAuth()
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus())
        val handle = SavedStateHandle()
        val vm = RegistrationViewModel(handle, auth, repository) { date }
        vm.signUp(account.email!!, "synthetic-credential", form)
        advanceUntilIdle()
        assertEquals(RegistrationResult.EmailConfirmationRequired, vm.uiState.value.result)
        assertEquals(0, repository.calls)
        vm.reset()
        auth.account = account.copy(email = "EXAMPLE@EXAMPLE.INVALID")
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val resumed = RegistrationViewModel(restored, auth, repository) { date.plusDays(1) }
        resumed.onAuthenticated()
        advanceUntilIdle()
        assertEquals(date, repository.submissions.single().measuredOn)
        assertEquals(AccountSetupPhase.COMPLETE, resumed.uiState.value.setupPhase)
    }

    @Test fun anotherAccountNeverReceivesThePendingDraft() = runTest {
        val auth = FakeAuth()
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus())
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.signUp(account.email!!, "synthetic", form)
        advanceUntilIdle()
        auth.account = AuthenticatedAccount("user-b", "different@example.invalid")
        vm.onAuthenticated()
        advanceUntilIdle()
        assertEquals(0, repository.calls)
        assertEquals(AccountSetupPhase.REQUIRED, vm.uiState.value.setupPhase)
        assertTrue(vm.uiState.value.setupForm.name.isEmpty())
    }

    @Test fun anExistingCompleteProfileIsNeverOverwrittenByDuplicateSignup() = runTest {
        val auth = FakeAuth()
        val repository = FakeOnboarding(completeStatus(), completeStatus())
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.signUp(account.email!!, "synthetic", form.copy(name = "Nuevo"))
        advanceUntilIdle()
        auth.account = account
        vm.onAuthenticated()
        advanceUntilIdle()
        assertEquals(0, repository.calls)
        assertEquals("Guardado", vm.uiState.value.savedStatus!!.displayName)
    }

    @Test fun lostResponseIsRecoveredByCheckingStatusBeforeRetrying() = runTest {
        val auth = FakeAuth().apply { account = this@RegistrationViewModelTest.account; result = RegistrationResult.SessionStarted }
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus()).apply { loseResponse = true }
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.signUp(account.email!!, "synthetic", form)
        advanceUntilIdle()
        assertEquals(AccountSetupPhase.REQUIRED, vm.uiState.value.setupPhase)
        assertNotNull(vm.uiState.value.errorMessage)
        vm.completeProfile(form)
        advanceUntilIdle()
        assertEquals(1, repository.calls)
        assertEquals(AccountSetupPhase.COMPLETE, vm.uiState.value.setupPhase)
    }

    @Test fun retryKeepsTheOriginalDateWhenTheFirstWriteDidNotReachTheServer() = runTest {
        val auth = FakeAuth().apply { account = this@RegistrationViewModelTest.account; result = RegistrationResult.SessionStarted }
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus()).apply { failBeforeSaving = true }
        var currentDate = date
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { currentDate }
        vm.signUp(account.email!!, "synthetic", form)
        advanceUntilIdle()
        currentDate = date.plusDays(1)
        repository.failBeforeSaving = false
        vm.completeProfile(form)
        advanceUntilIdle()
        assertEquals(listOf(date, date), repository.submissions.map { it.measuredOn })
        assertEquals(AccountSetupPhase.COMPLETE, vm.uiState.value.setupPhase)
    }

    @Test fun loadingPreventsRepeatingTheAccountCreation() = runTest {
        val auth = FakeAuth().apply { deferred = CompletableDeferred() }
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus())
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.signUp(account.email!!, "synthetic", form)
        runCurrent()
        vm.signUp(account.email!!, "synthetic", form)
        assertEquals(1, auth.calls)
        auth.deferred!!.complete(RegistrationResult.EmailConfirmationRequired)
        advanceUntilIdle()
    }

    @Test fun guestStateAfterActivityRecreationPreservesSignupAndConfirmation() = runTest {
        val auth = FakeAuth().apply { deferred = CompletableDeferred() }
        val repository = FakeOnboarding(OnboardingStatus(false), completeStatus())
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.signUp(account.email!!, "synthetic", form)
        runCurrent()
        // MainActivity vuelve a observar isAuthenticated=false al recrearse.
        vm.onSignedOut()
        assertTrue(vm.uiState.value.isLoading)
        auth.deferred!!.complete(RegistrationResult.EmailConfirmationRequired)
        advanceUntilIdle()
        vm.onSignedOut()
        assertEquals(RegistrationResult.EmailConfirmationRequired, vm.uiState.value.result)
        assertEquals(1, auth.calls)
        assertEquals(0, repository.calls)
    }

    @Test fun malformedOwnershipCannotOpenTheMainMenu() = runTest {
        val auth = FakeAuth().apply { account = this@RegistrationViewModelTest.account }
        val repository = FakeOnboarding(completeStatus().copy(userId = "user-b"), completeStatus())
        val vm = RegistrationViewModel(SavedStateHandle(), auth, repository) { date }
        vm.onAuthenticated()
        advanceUntilIdle()
        assertEquals(AccountSetupPhase.CHECKING, vm.uiState.value.setupPhase)
        assertNotNull(vm.uiState.value.errorMessage)
    }
}
