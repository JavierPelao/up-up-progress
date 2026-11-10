package com.upupprogress.app.ui.screens.nutrition

import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.FoodDiaryGateway
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
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class FoodDiaryViewModelTest {
    private val date = LocalDate.of(2026, 11, 8)
    private val product = FoodProduct("product", "12345678", "Pan", null, NutritionBasis.PER_100_GRAMS,
        FoodNutrients(BigDecimal("128"), BigDecimal("6.4"), BigDecimal("21.3"), BigDecimal("1.5")))

    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private class FakeRepository(val product: FoodProduct) : FoodDiaryGateway {
        var actor = "owner"
        val entries = mutableListOf<FoodEntry>()
        val requests = mutableListOf<FoodEntryRequest>()
        var lookups = 0
        var lookupGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var loseResponse = false
        var failLoads = false
        var wrongOwner = false
        override fun currentUserId() = actor
        override suspend fun loadRecordedDates(start: LocalDate, end: LocalDate): Set<LocalDate> {
            if (failLoads) throw IOException("offline")
            return entries.filter { it.userId == actor && it.date in start..end }.map { it.date }.toSet()
        }
        override suspend fun loadDay(date: LocalDate): FoodDay {
            if (failLoads) throw IOException("offline")
            return FoodDay(date, entries.filter { it.date == date && (wrongOwner || it.userId == actor) }, null)
        }
        override suspend fun lookup(barcode: String): FoodProduct {
            lookups++
            lookupGate?.await()
            return product
        }
        override suspend fun record(request: FoodEntryRequest): FoodEntry {
            requests.add(request)
            saveGate?.await()
            val entry = entries.firstOrNull { it.id == request.id } ?: FoodEntry(
                request.id, request.userId, "product", request.date, request.meal, request.quantity, request.unit,
                "Pan guardado", product.perHundred,
            ).also { entries.add(it) }
            if (loseResponse) throw IOException("response lost")
            return entry
        }
    }

    private fun entry(day: LocalDate = date) = FoodEntry("old", "owner", "p", day, MealType.BREAKFAST,
        BigDecimal("100"), "g", "Antes", product.perHundred)

    @Test fun weeklyIndicatorsFollowRecordsSavingWeekChangesAndSignOut() = runTest {
        val repo = FakeRepository(product).apply {
            entries.add(entry(date.minusDays(1)))
            entries.add(entry(date.minusDays(1)).copy(id = "second"))
            entries.add(entry(date.minusWeeks(1)))
        }
        val vm = FoodDiaryViewModel(repo, { date }, { "new" })
        vm.activate("owner"); advanceUntilIdle()
        assertEquals(setOf(date.minusDays(1)), vm.uiState.value.recordedDates)
        vm.startScan(); vm.barcodeScanned(product.barcode); advanceUntilIdle()
        vm.saveFood(); advanceUntilIdle()
        assertEquals(setOf(date.minusDays(1), date), vm.uiState.value.recordedDates)
        vm.selectDate(date.minusWeeks(1)); advanceUntilIdle()
        assertEquals(setOf(date.minusWeeks(1)), vm.uiState.value.recordedDates)
        repo.failLoads = true
        vm.refresh(); advanceUntilIdle()
        assertEquals(setOf(date.minusWeeks(1)), vm.uiState.value.recordedDates)
        assertNotNull(vm.uiState.value.weekError)
        vm.clear(); advanceUntilIdle()
        assertTrue(vm.uiState.value.recordedDates.isEmpty())
    }

    @Test fun addingToPastDateDoesNotChangeTodaysTotals() = runTest {
        val repo = FakeRepository(product)
        val vm = FoodDiaryViewModel(repo, { date }, { "id" })
        vm.activate("owner"); advanceUntilIdle()
        vm.selectDate(date.minusDays(1)); advanceUntilIdle()
        vm.startScan(); vm.barcodeScanned(product.barcode); advanceUntilIdle()
        vm.changeQuantity("50"); vm.changeMeal(MealType.LUNCH)
        vm.saveFood(); advanceUntilIdle()
        assertEquals(date.minusDays(1), repo.requests.single().date)
        assertEquals(MealType.LUNCH, repo.requests.single().meal)
        assertEquals(BigDecimal("64.00"), vm.uiState.value.selectedDay!!.consumed.calories)
        assertTrue(vm.uiState.value.today!!.entries.isEmpty())
        vm.selectDate(date); advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedDay!!.entries.isEmpty())
        vm.selectDate(date.minusDays(1)); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedDay!!.entries.size)
    }

    @Test fun midnightStartsAnEmptyTodayAndKeepsThePreviousDayInHistory() = runTest {
        val repo = FakeRepository(product).apply { entries.add(entry()) }
        var current = date
        val vm = FoodDiaryViewModel(repo, { current })
        vm.activate("owner"); advanceUntilIdle()
        current = date.plusDays(1)
        vm.onClockChanged(); advanceUntilIdle()
        assertEquals(current, vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.today!!.entries.isEmpty())
        assertEquals(1, repo.entries.size)
        vm.selectDate(date); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedDay!!.entries.size)
    }

    @Test fun midnightDoesNotMoveTheDateBeingViewedInHistory() = runTest {
        val repo = FakeRepository(product)
        var current = date
        val vm = FoodDiaryViewModel(repo, { current })
        vm.activate("owner"); advanceUntilIdle()
        vm.selectDate(date.minusDays(4)); advanceUntilIdle()
        current = date.plusDays(1)
        vm.onClockChanged(); advanceUntilIdle()
        assertEquals(date.minusDays(4), vm.uiState.value.selectedDate)
        assertEquals(current, vm.uiState.value.today!!.date)
    }

    @Test fun dateShownInTheConfirmationIsKeptWhenMidnightPasses() = runTest {
        val repo = FakeRepository(product)
        var current = date
        val vm = FoodDiaryViewModel(repo, { current }, { "id" })
        vm.activate("owner"); advanceUntilIdle()
        vm.startScan(); vm.barcodeScanned(product.barcode); advanceUntilIdle()
        current = date.plusDays(1)
        vm.onClockChanged(); advanceUntilIdle()
        assertEquals(date, vm.uiState.value.pendingDate)
        vm.saveFood(); advanceUntilIdle()
        assertEquals(date, repo.requests.single().date)
        assertTrue(vm.uiState.value.today!!.entries.isEmpty())
    }

    @Test fun repeatedFramesOnlyLookupOneProductAndCancelNeverWrites() = runTest {
        val repo = FakeRepository(product).apply { lookupGate = CompletableDeferred() }
        val vm = FoodDiaryViewModel(repo, { date })
        vm.activate("owner"); advanceUntilIdle()
        vm.startScan(); vm.barcodeScanned(product.barcode); runCurrent()
        vm.barcodeScanned(product.barcode); runCurrent()
        assertEquals(1, repo.lookups)
        vm.cancelProduct()
        repo.lookupGate!!.complete(Unit); advanceUntilIdle()
        assertNull(vm.uiState.value.product)
        assertTrue(repo.requests.isEmpty())
    }

    @Test fun doubleTapSaveCannotCreateTwoOperations() = runTest {
        val repo = FakeRepository(product).apply { saveGate = CompletableDeferred() }
        val vm = FoodDiaryViewModel(repo, { date }, { "id" })
        vm.activate("owner"); advanceUntilIdle()
        vm.startScan(); vm.barcodeScanned(product.barcode); advanceUntilIdle()
        vm.saveFood(); runCurrent()
        vm.saveFood(); vm.cancelProduct()
        assertTrue(vm.uiState.value.isSaving)
        assertEquals(1, repo.requests.size)
        repo.saveGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, repo.entries.size)
        assertNull(vm.uiState.value.product)
    }

    @Test fun lostResponseRetriesTheSameIdDateMealAndQuantity() = runTest {
        val repo = FakeRepository(product).apply { loseResponse = true }
        var counter = 0
        val vm = FoodDiaryViewModel(repo, { date }, { "id-${++counter}" })
        vm.activate("owner"); advanceUntilIdle()
        vm.startScan(); vm.barcodeScanned(product.barcode); advanceUntilIdle()
        vm.changeQuantity("50")
        vm.saveFood(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.dialogError)
        vm.changeQuantity("100"); vm.changeMeal(MealType.DINNER)
        assertEquals("50", vm.uiState.value.quantity)
        repo.loseResponse = false
        vm.saveFood(); advanceUntilIdle()
        assertEquals(repo.requests.first(), repo.requests.last())
        assertEquals(1, repo.entries.size)
        assertEquals(BigDecimal("64.00"), vm.uiState.value.today!!.consumed.calories)
    }

    @Test fun failedRefreshRetainsConfirmedRecordsAndDoesNotShowAnEmptyDay() = runTest {
        val repo = FakeRepository(product).apply { entries.add(entry()) }
        val vm = FoodDiaryViewModel(repo, { date })
        vm.activate("owner"); advanceUntilIdle()
        repo.failLoads = true
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.today!!.entries.size)
        assertEquals(BigDecimal("128.00"), vm.uiState.value.today!!.consumed.calories)
    }

    @Test fun signingOutCancelsPendingLookupAndClearsThePreviousAccountsRecords() = runTest {
        val repo = FakeRepository(product).apply { entries.add(entry()) }
        val vm = FoodDiaryViewModel(repo, { date })
        vm.activate("owner"); advanceUntilIdle()
        repo.lookupGate = CompletableDeferred()
        vm.startScan(); vm.barcodeScanned(product.barcode); runCurrent()
        vm.clear()
        repo.actor = "other"
        vm.activate("other")
        repo.lookupGate!!.complete(Unit); advanceUntilIdle()
        assertTrue(vm.uiState.value.today!!.entries.isEmpty())
        assertNull(vm.uiState.value.product)
    }

    @Test fun malformedOwnershipNeverAppearsAsPersonalFoodHistory() = runTest {
        val repo = FakeRepository(product).apply { actor = "other"; entries.add(entry()); wrongOwner = true }
        val vm = FoodDiaryViewModel(repo, { date })
        vm.activate("other"); advanceUntilIdle()
        assertNull(vm.uiState.value.today)
        assertNotNull(vm.uiState.value.error)
        vm.selectDate(date.plusDays(1))
        assertEquals(date, vm.uiState.value.selectedDate)
    }
}
