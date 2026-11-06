package dev.dublinbustracker.app

import android.app.Application
import dev.dublinbustracker.app.map.MapStartStore
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.RecentStopsRepository
import dev.dublinbustracker.data.RouteProfilesRepository
import dev.dublinbustracker.data.ServerSettings
import dev.dublinbustracker.data.ServerSettingsRepository
import dev.dublinbustracker.data.TransitApiClient

/** Manual DI container: the app is small enough that a dependency-injection framework isn't worth it. */
class AppContainer(application: Application) {

    // local.properties only seeds the first run; anything saved in Settings wins after that.
    private val defaultSettings = ServerSettings(
        baseUrl = ServerSettings.normaliseBaseUrl(BuildConfig.SERVER_BASE_URL),
        headers = ServerSettings.parseHeaderSpec(BuildConfig.SERVER_HEADERS),
    )

    val settingsRepository: ServerSettingsRepository by lazy {
        ServerSettingsRepository(SharedPreferencesServerSettingsStorage(application), defaultSettings)
    }

    val favouritesRepository: FavouritesRepository by lazy {
        FavouritesRepository(SharedPreferencesFavouritesStorage(application))
    }

    val recentStops: RecentStopsRepository by lazy {
        RecentStopsRepository(SharedPreferencesRawStorage(application, "recent_stops"))
    }

    val routeProfiles: RouteProfilesRepository by lazy {
        RouteProfilesRepository(SharedPreferencesRawStorage(application, "route_profiles"))
    }

    val mapStartStore: MapStartStore by lazy { MapStartStore(application) }

    /** The server holds the NTA keys and does the rate-limited polling; the app just asks it. */
    val apiClient: TransitApiClient by lazy {
        TransitApiClient(settingsProvider = { settingsRepository.current() })
    }
}
