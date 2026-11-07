package com.circapin.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.*
import com.circapin.ui.home.*
import com.circapin.ui.settings.*
import com.circapin.ui.theme.CircaPinTheme
import java.io.File
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualVerificationTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Preview(dark: Boolean, scale: Float, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, scale)) {
            CircaPinTheme(content)
        }
    }
    @Test fun settingsLight() {
        compose.setContent { Preview(false, 1f) { SettingsScreen(SettingsUiState(), {}) } }
        compose.onNodeWithText("Screen dimming").assertIsDisplayed()
        save("settings-light")
    }
    @Test fun settingsDarkAtDoubleTextSize() {
        compose.setContent { Preview(true, 2f) { SettingsScreen(SettingsUiState(), {}) } }
        compose.onNodeWithText("First meal").performScrollTo().assertIsDisplayed()
        save("settings-dark-large-text")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Allows reminders at", substring = true).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue(layouts.single().lineCount < 15)
        compose.onNodeWithText("Export JSON").performScrollTo().assertIsDisplayed()
        save("data-controls-dark-large-text")
    }
    @Test fun homeForecastDark() {
        val now = Instant.parse("2026-11-10T09:00:00Z")
        val date = LocalDate(2026, 11, 10)
        val goal = ScheduleGoal(id = 1, targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 24), createdAt = now)
        val plan = DailyPlan(date, 1, TimeWindow(now, now + 2.hours), TimeWindow(now + 11.hours, now + 14.hours), now + 6.hours, now + 14.hours, null)
        val forecast = EnergyForecast(date, List(24) { hour -> (0.5 + 0.3 * kotlin.math.cos((hour - 16) * kotlin.math.PI / 12)).toFloat() })
        val state = HomeUiState(loading = false, goal = goal, goalStatus = GoalStatus.ACTIVE,
            model = ModelState(now + 12.hours, 2f, 1f, 1f, now), day = DayPlan(plan, EatingWindow(date, now, now + 11.hours), forecast))
        compose.setContent { Preview(true, 1f) { HomeScreen(state, now, {}) } }
        compose.onNodeWithText("Energy forecast").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show hourly values").performScrollTo().assertIsDisplayed()
        save("forecast-dark")
        compose.onNodeWithText("Show hourly values").performClick()
        compose.onNodeWithText("Hide hourly values").assertIsDisplayed()
    }
    private fun save(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        compose.waitForIdle()
        // UiAutomation captures the compositor buffer, which may trail Compose's settled layout by a frame.
        android.os.SystemClock.sleep(500)
        var bitmap: Bitmap? = null
        repeat(3) {
            if (bitmap == null) {
                bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (bitmap == null) android.os.SystemClock.sleep(250)
            }
        }
        val captured = requireNotNull(bitmap) { "Android could not capture the verification screen" }
        File(directory, "$name.png").outputStream().use { captured.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
