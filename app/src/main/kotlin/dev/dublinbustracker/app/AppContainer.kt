package dev.dublinbustracker.app

import android.app.Application
import dev.dublinbustracker.data.ArrivalsRepository
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.GtfsStaticData
import dev.dublinbustracker.data.RealtimeGtfsClient
import dev.dublinbustracker.data.StopRepository

/** Manual DI container: the app is small enough that a dependency-injection framework isn't worth it. */
class AppContainer(application: Application) {
    private val gtfsStaticData: GtfsStaticData by lazy {
        val json = application.assets.open("gtfs_static.json").bufferedReader().use { it.readText() }
        GtfsStaticData.parse(json)
    }

    val stopRepository: StopRepository by lazy { StopRepository(gtfsStaticData) }

    val favouritesRepository: FavouritesRepository by lazy {
        FavouritesRepository(SharedPreferencesFavouritesStorage(application))
    }

    val arrivalsRepository: ArrivalsRepository by lazy {
        val keys = listOf(BuildConfig.GTFSR_API_KEY, BuildConfig.GTFSR_API_KEY_SECONDARY)
        ArrivalsRepository(RealtimeGtfsClient(keys), gtfsStaticData)
    }
}
