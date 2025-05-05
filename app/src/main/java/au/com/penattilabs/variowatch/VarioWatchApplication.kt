package au.com.penattilabs.variowatch

import android.app.Application

class VarioWatchApplication : Application() {
    lateinit var userPreferences: UserPreferences
        private set  // Make setter private but property public

    override fun onCreate() {
        super.onCreate()
        userPreferences = UserPreferences(this)
    }

    // For testing purposes only
    internal fun setUserPreferencesForTesting(preferences: UserPreferences) {
        userPreferences = preferences
    }
}