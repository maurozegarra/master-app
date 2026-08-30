package com.maurozegarra.master

import android.app.Application
import android.content.Context
import com.maurozegarra.master.audio.AlarmPlayer
import com.maurozegarra.master.data.AutoBackup
import com.maurozegarra.master.data.ExerciseMediaStore
import com.maurozegarra.master.data.SettingsStore
import com.maurozegarra.master.data.SharedFiles
import com.maurozegarra.master.data.VideoCache
import com.maurozegarra.master.data.VideoRepository
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
    singleOf(::SharedFiles)
    singleOf(::ExerciseMediaStore)
    // El directorio no se pide a Koin: es una decision del almacenamiento, no una
    // dependencia. Queda fuera del respaldo en backup_rules.xml.
    single { VideoCache(java.io.File(androidContext().filesDir, "videos")) }
    singleOf(::VideoRepository)
    viewModelOf(::MasterViewModel)
    viewModelOf(::SettingsViewModel)
}
