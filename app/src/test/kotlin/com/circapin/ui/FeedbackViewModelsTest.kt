package com.circapin.ui

import com.circapin.data.UserDataActions
import com.circapin.domain.entities.*
import com.circapin.domain.repository.ModelSnapshot
import com.circapin.domain.usecases.ProcessCheckInUseCase
import com.circapin.ui.checkin.CheckInViewModel
import com.circapin.ui.progress.ProgressViewModel
import com.circapin.ui.settings.*
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FeedbackViewModelsTest {
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val clock = object : Clock { override fun now() = this@FeedbackViewModelsTest.now }
    private val date = LocalDate(2026, 11, 10)
    private val models = FakeModelRepository()
    private val plans = FakePlanRepository()
    private val checks = FakeCheckInRepository()
    private val reminders = FakeReminders()
    private val prefs = FakePreferencesRepository()
    private val profile = UserProfile(14, 8f, true)
    private val state = ModelState(now + 12.hours, 3f, 1.5f, 1f, now)

    @Test fun `check-in submits once and cancels today's prompt`() = runTest(UnconfinedTestDispatcher()) {
        models.state.value = state
        val plan = DailyPlan(date, 1, TimeWindow(now, now + 1.hours), TimeWindow(now + 11.hours, now + 14.hours), now + 6.hours, now + 14.hours, null)
        plans.saveDay(plan, EatingWindow(date, now, now + 11.hours), EnergyForecast(date, List(24) { 0.5f }))
        val vm = CheckInViewModel(ProcessCheckInUseCase(checks, models), checks, models, plans, clock, reminders) { TimeZone.UTC }
        backgroundScope.launch { vm.state.collect {} }
        vm.submit(3); vm.submit(1)
        assertEquals(1, checks.records.value.size)
        assertEquals(3, checks.records.value.single().alertness)
        assertTrue(vm.state.value.alreadyCheckedIn)
        assertTrue(reminders.cancelled)
    }
    @Test fun `check-in without forecast remains unavailable`() = runTest(UnconfinedTestDispatcher()) {
        models.state.value = state
        val vm = CheckInViewModel(ProcessCheckInUseCase(checks, models), checks, models, plans, clock, reminders) { TimeZone.UTC }
        backgroundScope.launch { vm.state.collect {} }
        vm.submit(3)
        assertFalse(vm.state.value.available)
        assertTrue(checks.records.value.isEmpty())
    }
    @Test fun `progress starts from the current goal and includes weekly score and debt`() = runTest(UnconfinedTestDispatcher()) {
        val goals = FakeGoalRepository()
        goals.setActive(ScheduleGoal(targetWakeTime = LocalTime(7, 0), targetDate = date, createdAt = now - 24.hours))
        models.state.value = state
        val snapshots = FakeSnapshotRepository()
        snapshots.snapshots.value = listOf(
            ModelSnapshot(now - 48.hours, now + 4.hours, 0f, 0f, 1f),
            ModelSnapshot(now - 24.hours, now + 13.hours, 0f, 0f, 1f),
            ModelSnapshot(now - 23.hours, now + 13.hours, 1f, 0f, 1f),
            ModelSnapshot(now, now + 12.hours, 3f, 1.5f, 1f))
        plans.saveScore(SleepScore(date, 80, 80, null, null, null, SleepScoreNote(SleepScoreNoteKind.ON_TRACK)))
        val vm = ProgressViewModel(goals, models, snapshots, FakeUserProfileRepository(profile), plans, clock) { TimeZone.UTC }
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(LocalTime(22, 0), vm.state.value.startDlmo)
        assertEquals(3f, vm.state.value.sleepDebtHours)
        assertEquals(1.5f, vm.state.value.socialJetlagHours)
        assertEquals(80, vm.state.value.averageScore)
        assertEquals(3, vm.state.value.snapshots.size)
        assertEquals(1f, vm.state.value.shiftProgress)
    }
    @Test fun `settings refreshes permissions and stores independent reminder toggles`() = runTest(UnconfinedTestDispatcher()) {
        val permissions = object : PermissionReader { override suspend fun read() = PermissionState(exact = true, usage = true) }
        val data = FakeData()
        val vm = SettingsViewModel(prefs, permissions, data)
        backgroundScope.launch { vm.state.collect {} }
        vm.refreshPermissions()
        vm.updatePrefs(ReminderPreferences(firstMealReminder = true, mealReminder = false, dimReminder = false))
        assertTrue(vm.state.value.permissions.usage)
        assertTrue(vm.state.value.exactAlarmsGranted)
        assertTrue(vm.state.value.prefs.firstMealReminder)
        assertFalse(vm.state.value.prefs.mealReminder)
        assertFalse(vm.state.value.prefs.dimReminder)
        vm.export(true)
        assertEquals("export.zip", vm.state.value.exported?.name)
        vm.consumeExport()
        assertNull(vm.state.value.exported)
        vm.deleteAll()
        assertTrue(data.deleted)
        assertTrue(vm.state.value.resetDone)
    }
    @Test fun `failed export surfaces an error and releases busy state`() = runTest(UnconfinedTestDispatcher()) {
        val vm = SettingsViewModel(prefs, object : PermissionReader { override suspend fun read() = PermissionState() }, FakeData(fail = true))
        backgroundScope.launch { vm.state.collect {} }
        vm.export(false)
        assertTrue(vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }
    private class FakeData(val fail: Boolean = false) : UserDataActions {
        var deleted = false
        override suspend fun export(csv: Boolean): File { if (fail) error("disk full"); return File(if (csv) "export.zip" else "export.json") }
        override suspend fun deleteAll() { deleted = true }
    }
}
