package com.maurozegarra.master

import android.app.Application
import android.content.Context
import com.maurozegarra.master.audio.AlarmPlayer
import com.maurozegarra.master.data.AutoBackup
import com.maurozegarra.master.data.SettingsStore
import com.maurozegarra.master.data.WorkoutStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

class MasterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MasterApp)
            modules(appModule)
        }
    }
}

val appModule = module {
    single<Application> { get<Context>() as Application }
    singleOf(::WorkoutStore)
    singleOf(::SettingsStore)
    singleOf(::AlarmPlayer)
    singleOf(::AutoBackup)
    viewModelOf(::MasterViewModel)
    viewModelOf(::SettingsViewModel)
}
