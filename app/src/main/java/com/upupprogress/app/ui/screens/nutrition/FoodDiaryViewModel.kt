package com.upupprogress.app.ui.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.FoodDiaryGateway
import com.upupprogress.app.data.repository.FoodDiaryRepository
import io.github.jan.supabase.exceptions.NotFoundRestException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FoodDiaryUiState(
    val todayDate: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = todayDate,
    val selectedDay: FoodDay? = null,
    val recordedDates: Set<LocalDate> = emptySet(),
    val weekError: String? = null,
    val today: FoodDay? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val scannerVisible: Boolean = false,
    val pendingDate: LocalDate? = null,
    val product: FoodProduct? = null,
    val quantity: String = "100",
    val meal: MealType = MealType.SNACK,
    val isLookingUp: Boolean = false,
    val isSaving: Boolean = false,
    val saveAttempted: Boolean = false,
    val dialogError: String? = null,
)

class FoodDiaryViewModel internal constructor(
    private val repository: FoodDiaryGateway,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    constructor() : this(FoodDiaryRepository())

    private val mutableState = MutableStateFlow(FoodDiaryUiState(todayDate = today()))
    val uiState = mutableState.asStateFlow()
    private var owner: String? = null
    private var loadJob: Job? = null
    private var weekJob: Job? = null
    private var lookupJob: Job? = null
    private var saveJob: Job? = null
    private var saveRequest: FoodEntryRequest? = null

    fun activate(userId: String) {
        if (owner == userId) return
        clear()
        owner = userId
        refresh()
    }

    fun clear() {
        loadJob?.cancel()
        weekJob?.cancel()
        lookupJob?.cancel()
        saveJob?.cancel()
        saveRequest = null
        owner = null
        mutableState.value = FoodDiaryUiState(todayDate = today())
    }

    fun selectDate(date: LocalDate) {
        if (date > today() || owner == null) return
        val state = mutableState.value
        val current = today()
        mutableState.value = state.copy(selectedDate = date, selectedDay = null, todayDate = current,
            recordedDates = if (nutritionWeekStart(date) == nutritionWeekStart(state.selectedDate))
                state.recordedDates else emptySet(),
            today = if (current == state.todayDate) state.today else null)
        refresh()
    }

    fun onClockChanged() {
        if (owner == null) return
        val current = today()
        val state = mutableState.value
        if (state.todayDate != current) {
            val selected = if (state.selectedDate == state.todayDate || state.selectedDate > current) current else state.selectedDate
            mutableState.value = state.copy(
                todayDate = current, selectedDate = selected, today = null,
                recordedDates = if (nutritionWeekStart(selected) == nutritionWeekStart(state.selectedDate))
                    state.recordedDates else emptySet(),
                selectedDay = if (selected == state.selectedDate) state.selectedDay else null,
            )
            refresh()
        } else if (loadJob?.isActive != true) refresh()
    }

    private fun refreshWeek(userId: String) {
        weekJob?.cancel()
        val start = nutritionWeekStart(mutableState.value.selectedDate)
        val end = minOf(start.plusDays(6), mutableState.value.todayDate)
        mutableState.value = mutableState.value.copy(weekError = null)
        weekJob = viewModelScope.launch {
            try {
                val dates = withTimeout(30_000L) { repository.loadRecordedDates(start, end) }
                ensureOwner(userId)
                check(dates.all { it in start..end })
                if (nutritionWeekStart(mutableState.value.selectedDate) == start) {
                    mutableState.value = mutableState.value.copy(recordedDates = dates, weekError = null)
                }
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) weekFailed(userId) else throw error
            } catch (_: Exception) { weekFailed(userId) }
        }
    }

    private fun weekFailed(userId: String) {
        if (owner == userId) mutableState.value = mutableState.value.copy(
            weekError = "No se pudieron actualizar los indicadores de alimentos de la semana.",
        )
    }

    fun refresh() {
        val userId = owner ?: return
        refreshWeek(userId)
        loadJob?.cancel()
        val date = mutableState.value.selectedDate
        val current = mutableState.value.todayDate
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val days = withTimeout(30_000L) {
                    val selected = repository.loadDay(date)
                    val currentDay = if (date == current) selected else repository.loadDay(current)
                    selected to currentDay
                }
                ensureOwner(userId)
                check(days.first.date == date && days.second.date == current)
                check((days.first.entries + days.second.entries).all { it.userId == userId })
                mutableState.value = mutableState.value.copy(selectedDay = days.first, today = days.second, isLoading = false)
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) loadFailed(userId) else throw error
            } catch (_: Exception) { loadFailed(userId) }
        }
    }

    private fun loadFailed(userId: String) {
        if (owner == userId) mutableState.value = mutableState.value.copy(
            isLoading = false, error = "No se pudieron actualizar los alimentos. Revisa la conexión y vuelve a intentar.",
        )
    }

    fun startScan(forToday: Boolean = false) {
        val state = mutableState.value
        if (owner == null || state.isSaving || state.isLookingUp || state.scannerVisible) return
        saveRequest = null
        mutableState.value = state.copy(
            scannerVisible = true, pendingDate = if (forToday) today() else state.selectedDate,
            product = null, quantity = "100", meal = MealType.SNACK,
            dialogError = null, saveAttempted = false,
        )
    }

    fun barcodeScanned(barcode: String) {
        val userId = owner ?: return
        if (!mutableState.value.scannerVisible || !validFoodBarcode(barcode)) return
        mutableState.value = mutableState.value.copy(scannerVisible = false, isLookingUp = true, dialogError = null)
        lookupJob = viewModelScope.launch {
            try {
                val product = withTimeout(30_000L) { repository.lookup(barcode) }
                ensureOwner(userId)
                mutableState.value = mutableState.value.copy(product = product, isLookingUp = false)
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) lookupFailed(userId, null) else throw error
            } catch (error: Exception) { lookupFailed(userId, error) }
        }
    }

    private fun lookupFailed(userId: String, error: Exception?) {
        if (owner == userId) mutableState.value = mutableState.value.copy(
            isLookingUp = false,
            dialogError = if (error is NotFoundRestException) "Producto no encontrado. Prueba otro código de barras."
                else "No se pudo consultar el producto. Revisa la conexión y vuelve a escanear.",
        )
    }

    fun changeQuantity(value: String) {
        if (!mutableState.value.isSaving && saveRequest == null && acceptsFoodQuantityEdit(value)) {
            mutableState.value = mutableState.value.copy(quantity = value)
        }
    }

    fun changeMeal(meal: MealType) {
        if (!mutableState.value.isSaving && saveRequest == null) mutableState.value = mutableState.value.copy(meal = meal)
    }

    fun cancelProduct() {
        if (mutableState.value.isSaving) return
        lookupJob?.cancel()
        val uncertain = saveRequest != null
        saveRequest = null
        mutableState.value = mutableState.value.copy(
            scannerVisible = false, isLookingUp = false, pendingDate = null, product = null,
            dialogError = null, saveAttempted = false,
        )
        if (uncertain) refresh()
    }

    fun saveFood() {
        val userId = owner ?: return
        val state = mutableState.value
        if (state.isSaving || state.isLookingUp) return
        val product = state.product?.takeIf { it.canRecord } ?: return
        val quantity = parseFoodQuantity(state.quantity) ?: return
        val request = saveRequest ?: FoodEntryRequest(
            newId(), userId, product.barcode, requireNotNull(product.basis).unit,
            requireNotNull(state.pendingDate), state.meal, quantity,
        ).also { saveRequest = it }
        mutableState.value = state.copy(isSaving = true, saveAttempted = true, dialogError = null)
        saveJob = viewModelScope.launch {
            try {
                val entry = withTimeout(45_000L) { repository.record(request) }
                ensureOwner(userId)
                check(entry.userId == userId && entry.id == request.id && entry.date == request.date)
                fun FoodDay?.includingEntry(date: LocalDate): FoodDay? =
                    if (this?.date == date) copy(entries = (entries.filterNot { it.id == entry.id } + entry)) else this
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    selectedDay = latest.selectedDay.includingEntry(entry.date),
                    recordedDates = if (nutritionWeekStart(entry.date) == nutritionWeekStart(latest.selectedDate))
                        latest.recordedDates + entry.date else latest.recordedDates,
                    today = latest.today.includingEntry(entry.date),
                    product = null, pendingDate = null, isSaving = false, saveAttempted = false, dialogError = null,
                )
                saveRequest = null
                refresh()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) saveFailed(userId) else throw error
            } catch (_: Exception) { saveFailed(userId) }
        }
    }

    private fun saveFailed(userId: String) {
        if (owner == userId) mutableState.value = mutableState.value.copy(
            isSaving = false,
            dialogError = "No se pudo confirmar el guardado. Pulsa Agregar alimento para comprobar y reintentar sin duplicarlo. Si cancelas, revisa la lista: el servidor podría haberlo guardado.",
        )
    }

    private suspend fun ensureOwner(userId: String) {
        currentCoroutineContext().ensureActive()
        if (owner != userId || repository.currentUserId() != userId) throw CancellationException("La sesión cambió")
    }
}
