package dev.dublinbustracker.app.ui

/** Compose test tags; exposed as UiAutomator resource ids (see MainActivity) so Appium can select by them. */
object TestTags {
    const val NAV_SEARCH = "nav_search"
    const val NAV_MAP = "nav_map"
    const val NAV_FAVOURITES = "nav_favourites"
    const val NAV_SETTINGS = "nav_settings"
    const val SEARCH_FIELD = "search_field"
    const val FAVOURITE_STAR = "favourite_star"
    const val SETTINGS_SERVER_ADDRESS = "settings_server_address"
    const val SETTINGS_SAVE_TEST = "settings_save_test"

    fun stopRow(id: String) = "stop_row_$id"
    fun arrivalRow(tripId: String) = "arrival_row_$tripId"
}
