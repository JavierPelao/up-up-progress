package com.upupprogress.app.ui.screens.progress

import com.upupprogress.app.data.repository.WorkoutAnalysisRepository
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Solo HTTP simulado en memoria. Nunca llama a Supabase ni al proveedor de IA. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class ProgressViewModelTest {
    private val owner = "synthetic-owner"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Before
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    // El motor y el ViewModel comparten el reloj virtual de coroutines-test.
    private fun mockEngine(handler: MockRequestHandler) = MockEngine(MockEngineConfig().apply {
        dispatcher = Dispatchers.Main
        addHandler(handler)
    })

    private fun client(engine: MockEngine) = createSupabaseClient("https://example.invalid", "synthetic-key") {
        httpEngine = engine
        coroutineDispatcher = Dispatchers.Main
        defaultLogLevel = LogLevel.NONE
        osInformation = null
        install(Functions) { requireValidSession = false }
    }

    @Test
    fun manualAnalysisSendsOnePostDespiteDoubleTapAndClearsOnAccountChange() = runTest {
        var requests = 0
        val responseGate = CompletableDeferred<Unit>()
        val engine = mockEngine { request ->
            requests++
            assertEquals("example.invalid", request.url.host)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/functions/v1/analyze-workouts", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())
            responseGate.await()
            respond(
                """{"ok":true,"status":"analyzed","source":"groq","test_mode":false,
                    "summary":"Resumen sintético de registros.",
                    "suggestions":["Sugerencia sintética."],
                    "quota":{"limit":10,"remaining":9,"resets_at":"2026-11-11T00:00:00Z"}}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val client = client(engine)
        try {
            val viewModel = ProgressViewModel(WorkoutAnalysisRepository(client) { owner })
            viewModel.activate(owner)
            runCurrent()
            assertEquals(0, requests)
            viewModel.analyze()
            viewModel.analyze()
            runCurrent()
            assertEquals(viewModel.uiState.value.error, 1, requests)
            assertTrue(viewModel.uiState.value.isLoading)
            responseGate.complete(Unit)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.dialogVisible)
            assertEquals("Resumen sintético de registros.", state.analysis?.summary)
            assertEquals(listOf("Sugerencia sintética."), state.analysis?.suggestions)
            assertEquals(9, state.analysis?.quota?.remaining)
            assertTrue(state.analysis?.generatedByAi == true)
            assertNull(state.error)
            viewModel.dismissResult()
            viewModel.activate(owner)
            advanceUntilIdle()
            assertEquals(viewModel.uiState.value.error, 1, requests)
            assertFalse(viewModel.uiState.value.dialogVisible)
            viewModel.activate("another-synthetic-owner")
            assertNull(viewModel.uiState.value.analysis)
            viewModel.clear()
            viewModel.analyze()
            advanceUntilIdle()
            assertEquals(viewModel.uiState.value.error, 1, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun dailyQuotaFailureShowsAnErrorWithoutRetryingOrInventingAiSuggestions() = runTest {
        var requests = 0
        val engine = mockEngine {
            requests++
            respond(
                """{"error":"Alcanzaste el máximo de 10 análisis diarios","code":"DAILY_LIMIT_REACHED",
                    "quota":{"limit":10,"remaining":0,"resets_at":"2026-11-11T00:00:00Z"}}""",
                HttpStatusCode.TooManyRequests,
                jsonHeaders,
            )
        }
        val client = client(engine)
        try {
            val viewModel = ProgressViewModel(WorkoutAnalysisRepository(client) { owner })
            viewModel.activate(owner)
            viewModel.analyze()
            viewModel.analyze()
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals(viewModel.uiState.value.error, 1, requests)
            assertFalse(state.isLoading)
            assertTrue(state.dialogVisible)
            assertNull(state.analysis)
            assertTrue(state.error?.contains("10 análisis diarios") == true)
            assertTrue(state.error?.contains("UTC") == true)
            viewModel.dismissResult()
            viewModel.activate(owner)
            advanceUntilIdle()
            assertEquals(viewModel.uiState.value.error, 1, requests)
            assertFalse(viewModel.uiState.value.dialogVisible)
            viewModel.clear()
            assertNull(viewModel.uiState.value.error)
        } finally {
            client.close()
        }
    }
}
