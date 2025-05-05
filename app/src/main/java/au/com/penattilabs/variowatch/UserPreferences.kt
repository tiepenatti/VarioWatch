package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) : android.os.Parcelable {
    companion object {
        const val PREFERENCES_KEY = "user_preferences"
        private const val PREF_NAME = "vario_prefs"
        private const val KEY_USE_METRIC_UNITS = "use_metric_units"
        private const val KEY_QNH = "qnh"
        private lateinit var applicationContext: Context
        
        @JvmField
        val CREATOR = object : android.os.Parcelable.Creator<UserPreferences> {
            override fun createFromParcel(parcel: android.os.Parcel): UserPreferences {
                return UserPreferences(applicationContext).also {
                    it.currentAltitude = parcel.readFloat()
                    it.useMetricUnits = parcel.readInt() == 1
                    it.qnh = parcel.readFloat()
                }
            }

            override fun newArray(size: Int): Array<UserPreferences?> {
                return arrayOfNulls(size)
            }
        }
    }
    
    init {
        // Store application context for Parcelable recreation
        applicationContext = context.applicationContext
    }
    
    // Use application context to avoid memory leaks
    private val appContext = context.applicationContext
    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    var useMetricUnits: Boolean
        get() = sharedPreferences.getBoolean(KEY_USE_METRIC_UNITS, true)
        private set(value) = sharedPreferences.edit().putBoolean(KEY_USE_METRIC_UNITS, value).apply()

    var qnh: Float = Constants.ISA_PRESSURE_SEA_LEVEL
        get() = sharedPreferences.getFloat(KEY_QNH, field)
        private set(value) {
            field = value
            sharedPreferences.edit().putFloat(KEY_QNH, value).apply()
        }

    var currentAltitude: Float = 0f
        private set

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeFloat(currentAltitude)
        parcel.writeInt(if (useMetricUnits) 1 else 0)
        parcel.writeFloat(qnh)
    }

    override fun describeContents(): Int = 0

    fun toggleUnitSystem() {
        useMetricUnits = !useMetricUnits
    }

    fun updateQnh(newQnh: Float) {
        qnh = newQnh
    }

    fun updateCurrentAltitude(pressure: Float) {
        currentAltitude = AltitudeCalculator.calculateAltitude(pressure, qnh)
    }

    fun adjustAltitude(increase: Boolean) {
        val step = if (useMetricUnits) 
            Constants.METRIC_ALTITUDE_STEP
        else 
            Constants.IMPERIAL_ALTITUDE_STEP / Constants.METERS_TO_FEET // Convert feet to meters
        
        currentAltitude += if (increase) step else -step
    }
}