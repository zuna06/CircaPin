package com.circapin.di

import android.content.Context
import com.circapin.data.healthconnect.HealthConnectSleepSource
import com.circapin.data.healthconnect.SleepSessionSource
import com.circapin.data.local.CircaPinDatabase
import com.circapin.data.repository.GoalRepositoryImpl
import com.circapin.data.repository.ModelRepositoryImpl
import com.circapin.data.repository.PlanRepositoryImpl
import com.circapin.data.repository.SleepRepositoryImpl
import com.circapin.data.repository.UserProfileRepositoryImpl
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.CompleteOnboardingUseCase
import com.circapin.domain.usecases.LogSleepUseCase
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.domain.usecases.UpdateModelUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

// Domain classes stay free of injection annotations, so they are all constructed here.
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CircaPinDatabase = CircaPinDatabase.create(context)

    @Provides
    fun clock(): Clock = Clock.System

    /** Not a singleton, so a change of time zone is picked up by the next screen. */
    @Provides
    fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

    @Provides
    @Singleton
    fun sleepSessionSource(@ApplicationContext context: Context): SleepSessionSource =
        HealthConnectSleepSource(context)

    @Provides
    @Singleton
    fun sleepRepository(database: CircaPinDatabase, source: SleepSessionSource, clock: Clock): SleepRepository =
        SleepRepositoryImpl(database.sleepRecordDao(), source, clock)

    @Provides
    @Singleton
    fun userProfileRepository(database: CircaPinDatabase): UserProfileRepository =
        UserProfileRepositoryImpl(database.userProfileDao())

    @Provides
    @Singleton
    fun modelRepository(database: CircaPinDatabase): ModelRepository = ModelRepositoryImpl(database.modelStateDao())

    @Provides
    fun updateModelUseCase(models: ModelRepository, clock: Clock) = UpdateModelUseCase(models, clock)

    @Provides
    fun logSleepUseCase(repository: SleepRepository, clock: Clock) = LogSleepUseCase(repository, clock)

    @Provides
    fun completeOnboardingUseCase(profiles: UserProfileRepository) = CompleteOnboardingUseCase(profiles)

    @Provides
    @Singleton
    fun goalRepository(database: CircaPinDatabase): GoalRepository = GoalRepositoryImpl(database.scheduleGoalDao())

    @Provides
    @Singleton
    fun planRepository(database: CircaPinDatabase): PlanRepository = PlanRepositoryImpl(
        plans = database.dailyPlanDao(),
        forecasts = database.energyForecastDao(),
        eatingWindows = database.eatingWindowDao(),
        scores = database.sleepScoreDao(),
    )

    @Provides
    fun refreshTodayUseCase(plans: PlanRepository, clock: Clock) = RefreshTodayUseCase(plans, clock)
}
