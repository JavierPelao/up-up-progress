package com.upupprogress.app.ui.screens.registration

import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.ui.screens.onboarding.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OnboardingFormMappingTest {
    private val form = OnboardingFormState(
        name = " Ejemplo ", ageYears = 30, heightCm = 175, sexForFormula = FormulaSexUi.MALE,
        weightHundredthsKg = 7025, activityLevel = ActivityLevelUi.ACTIVE,
        personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT, nutritionGoalMode = NutritionGoalModeUi.MANUAL,
        manualNutritionDraft = ManualNutritionDraft("2200", "123,45", "250,01", "70,99"),
    )

    @Test fun manualPayloadUsesNumericDecimalsAndExplicitWireValues() {
        val body = form.toSubmission(LocalDate.of(2026,11,8)).toRequest()
        assertEquals("high", body["activityLevel"]!!.jsonPrimitive.content)
        assertEquals("male", body["sexForFormula"]!!.jsonPrimitive.content)
        assertEquals("Ejemplo", body["displayName"]!!.jsonPrimitive.content)
        assertEquals("70.25", body["weightKg"]!!.jsonPrimitive.content)
        assertEquals("123.45", body["dailyProteinTargetG"]!!.jsonPrimitive.content)
        assertFalse(body["dailyProteinTargetG"]!!.jsonPrimitive.isString)
        assertFalse(body.keys.any { it in setOf("password","confirmation","email","userId","service_role") })
    }

    @Test fun automaticPayloadDoesNotSubmitTheLocalEstimateOrTheManualDraft() {
        val body = form.copy(nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC)
            .toSubmission(LocalDate.of(2026,11,8)).toRequest()
        assertEquals("automatic", body["nutritionGoalMode"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("dailyCalorieTarget"))
        assertFalse(body.containsKey("dailyProteinTargetG"))
    }

    @Test fun serverValuesKeepTheirDecimalPrecisionAndMissingValuesStayMissing() {
        val response = Json.parseToJsonElement(
            """{"onboarding":{"is_complete":true,"profile":{"user_id":"synthetic","display_name":"Ejemplo","age_years":30,"height_cm":175,"sex_for_formula":"male"},"weight":{"weight_kg":70.25,"measured_on":"2026-11-08"},"goal":{"activity_level":"high","goal_type":"maintain_weight","nutrition_goal_mode":"manual","daily_calorie_target":2200,"daily_protein_target_g":123.45,"daily_carbs_target_g":250.01,"daily_fat_target_g":70.99}}}"""
        ).jsonObject
        val status = OnboardingStatus.fromResponse(response)
        assertEquals(BigDecimal("123.45"), status.targets!!.proteinGrams)
        assertEquals(7025, status.toForm().weightHundredthsKg)
        val empty = OnboardingStatus.fromResponse(Json.parseToJsonElement("""{"onboarding":{"is_complete":false,"profile":null,"weight":null,"goal":null}}""").jsonObject)
        assertNull(empty.targets)
        assertNull(empty.weightKg)
    }
}
