package au.com.penattilabs.variowatch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.coroutines.coroutineContext
import android.content.SharedPreferences

class SoundSynthesizerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener { // Implemented SharedPreferences.OnSharedPreferenceChangeListener

    private val TAG = "SoundSynthesizerService"
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentFrequency = 0.0
    // currentVolume will now be a multiplier from 0.0f to 1.0f, updated from preferences
    private var currentVolume: Float = 1.0f // Default to full volume, will be updated from prefs
    
    // New properties for sound profile
    private var varioSoundConfig: VarioSoundConfig? = null
    private var currentCycleMillis: Int = 1000 // Default cycle duration
    private var currentDutyPercent: Int = 0    // Default duty cycle (0% = silence)
    private var previousVerticalSpeed: Float = 0.0f // For hysteresis logic with thresholds


    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var toneJob: Job? = null

    private val binder = LocalBinder()
    private lateinit var sharedPrefs: SharedPreferences // Added for listening to preference changes

    inner class LocalBinder : Binder() {
        fun getService(): SoundSynthesizerService = this@SoundSynthesizerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val verticalSpeedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val verticalSpeed = intent.getFloatExtra(VarioService.EXTRA_VERTICAL_SPEED, Float.NaN)
                Log.d(TAG, "Received vertical speed broadcast: $verticalSpeed (previous: $previousVerticalSpeed)")
                if (!verticalSpeed.isNaN()) {
                    updateSound(verticalSpeed)
                    this@SoundSynthesizerService.previousVerticalSpeed = verticalSpeed
                } else {
                    Log.w(TAG, "Received NaN vertical speed")
                }
            }
        }
    }

    private lateinit var userPreferences: UserPreferences

    override fun onCreate() {
        super.onCreate()
        userPreferences = (applicationContext as VarioWatchApplication).userPreferences
        // Initialize SharedPreferences for listening to changes
        sharedPrefs = applicationContext.getSharedPreferences("vario_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)


        varioSoundConfig = SoundProfileParser.loadFromAssetsOrInternal(this, "sound_profile.txt")
        if (varioSoundConfig == null) {
            Log.e(TAG, "Failed to load sound_profile.txt. Sound will be disabled or use fallback if implemented.")
        } else {
            Log.i(TAG, "Sound profile loaded successfully.")
        }

        setupAudioTrack()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            verticalSpeedReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE)
        )
        Log.d(TAG, "Receiver registered with LocalBroadcastManager")

        updateCurrentVolumeFromPreferences() // Initial volume setup from preferences
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY // Keep the service running
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(verticalSpeedReceiver)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this) // Unregister listener
        stopSound()
        audioTrack?.release()
        audioTrack = null
        serviceJob.cancel() // Cancel coroutines
        Log.d(TAG, "Service destroyed")
    }

    // This method is called when a shared preference is changed.
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "volume_level") {
            Log.d(TAG, "Volume preference changed, updating volume.")
            updateCurrentVolumeFromPreferences()
        }
    }

    private fun updateCurrentVolumeFromPreferences() {
        val volumeLevel = userPreferences.getVolumeLevel() // Reads from SharedPreferences via UserPreferences instance
        val newVolumeMultiplier = when (volumeLevel) {
            0 -> 0.0f  // Off
            1 -> 0.33f // Low
            2 -> 0.66f // Med
            3 -> 1.0f  // High
            else -> 0.0f // Default to off for unexpected values (or userPreferences default)
        }
        if (this.currentVolume != newVolumeMultiplier) {
            this.currentVolume = newVolumeMultiplier
            Log.i(TAG, "Volume updated: level=$volumeLevel, multiplier=${this.currentVolume}")
        } else {
            Log.d(TAG, "Volume preference read, but currentVolume multiplier is already up-to-date: ${this.currentVolume}")
        }
    }

    private fun setupAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SoundConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
            Log.e(TAG, "Unable to determine buffer size for AudioTrack")
            return
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) // Appropriate for vario sounds
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundConstants.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2) // Use a slightly larger buffer
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        Log.d(TAG, "AudioTrack initialized with buffer size: $bufferSize")
    }

    private fun updateSound(verticalSpeed: Float) {
        Log.d(TAG, "updateSound called with verticalSpeed: $verticalSpeed")

        val config = varioSoundConfig ?: run {
            Log.w(TAG, "VarioSoundConfig not loaded, cannot update sound.")
            if (isPlaying) {
                stopSound()
            }
            return
        }

        val soundParams = config.getSoundParameters(verticalSpeed)
        var playThisIteration = false

        if (soundParams != null && soundParams.first > 0 && soundParams.third > 0) {
            val (hertz, cycleMillis, dutyPercent) = soundParams
            currentFrequency = hertz.toDouble().coerceIn(0.0, SoundConstants.SAMPLE_RATE / 2.0)
            currentCycleMillis = cycleMillis
            currentDutyPercent = dutyPercent.coerceIn(0, 100)

            Log.d(TAG, "Profile params: VSpeed: $verticalSpeed -> Hz: $currentFrequency, Cycle: $currentCycleMillis ms, Duty: $currentDutyPercent%")

            if (isPlaying) {
                // If already playing, check if we should stop based on OffThresholds
                if (verticalSpeed <= config.climbToneOffThreshold && verticalSpeed >= config.sinkToneOffThreshold) {
                    // Entered the "quiet zone" between climb-off and sink-off
                    playThisIteration = false
                    Log.d(TAG, "In quiet zone (vs: $verticalSpeed) while playing. ClimbOff: ${config.climbToneOffThreshold}, SinkOff: ${config.sinkToneOffThreshold}. Will stop.")
                } else {
                    playThisIteration = true // Continue playing
                    Log.d(TAG, "Continue playing (vs: $verticalSpeed). Not in quiet zone.")
                }
            } else {
                // If not playing, check if we should start based on OnThresholds
                if (verticalSpeed > config.climbToneOnThreshold || verticalSpeed < config.sinkToneOnThreshold) {
                    playThisIteration = true
                    Log.d(TAG, "Start playing (vs: $verticalSpeed). ClimbOn: ${config.climbToneOnThreshold}, SinkOn: ${config.sinkToneOnThreshold}.")
                } else {
                    playThisIteration = false
                    Log.d(TAG, "In dead zone for starting (vs: $verticalSpeed). ClimbOn: ${config.climbToneOnThreshold}, SinkOn: ${config.sinkToneOnThreshold}.")
                }
            }
        } else {
            Log.d(TAG, "Profile indicates silence for VSpeed: $verticalSpeed. Params: $soundParams")
            currentFrequency = 0.0
            currentCycleMillis = 1000 // Reset to a default silent cycle
            currentDutyPercent = 0    // Reset to 0% duty
            playThisIteration = false
        }

        if (playThisIteration) {
            if (!isPlaying) {
                startSound()
            }
            // If already playing and playThisIteration is true, parameters (freq, cycle, duty)
            // will be picked up by the running generateToneLoop
        } else {
            if (isPlaying) {
                stopSound()
            }
        }
    }

    private fun startSound() {
        if (isPlaying || audioTrack == null) {
             Log.w(TAG, "startSound called but already playing or audioTrack is null. isPlaying=$isPlaying, audioTrack=$audioTrack")
             return
        }
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
             Log.w(TAG, "startSound called but audioTrack state is already PLAYING.")
             return
        }
        Log.i(TAG, "Attempting to start sound playback...") // Use Info level for important actions
        try {
            audioTrack?.play()
            isPlaying = true
            Log.i(TAG, "AudioTrack playback started successfully. isPlaying=$isPlaying") // Confirm success
            toneJob?.cancel() // Cancel previous job just in case
            toneJob = serviceScope.launch {
                Log.d(TAG, "Launching tone generation coroutine.")
                generateToneLoop()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start AudioTrack playback", e)
            isPlaying = false
            // Attempt to re-initialize or handle error
            // setupAudioTrack() // Be careful with re-initializing here, might cause loops
        }
    }

    private fun stopSound() {
        if (!isPlaying || audioTrack == null) {
             Log.w(TAG, "stopSound called but not playing or audioTrack is null. isPlaying=$isPlaying, audioTrack=$audioTrack")
             return
        }
        Log.i(TAG, "Attempting to stop sound playback...") 
        toneJob?.cancel() 
        toneJob = null
        try {
             audioTrack?.pause()
             audioTrack?.flush()
             audioTrack?.stop()
             Log.i(TAG, "AudioTrack playback stopped successfully.") 
        } catch (e: IllegalStateException) {
             Log.e(TAG, "Exception during AudioTrack stop/pause/flush", e)
        } finally {
            isPlaying = false
             Log.d(TAG, "isPlaying set to false.")
        }
    }

    private suspend fun generateToneLoop() {
        val bufferSize = audioTrack?.bufferSizeInFrames ?: run {
            Log.e(TAG, "generateToneLoop: AudioTrack buffer size is null, exiting loop.")
            return
        }
        if (bufferSize <= 0) {
             Log.e(TAG, "generateToneLoop: Invalid bufferSize ($bufferSize), exiting loop.")
             return
        }
        val buffer = ShortArray(bufferSize)
        var angle = 0.0
        var samplesIntoCurrentCycle = 0

        Log.i(TAG, "Starting new tone generation loop. Buffer size: $bufferSize")

        while (coroutineContext.isActive) { // Use coroutineContext.isActive for cooperative cancellation
            // Capture current parameters safely for this iteration of the loop
            val localFreq = this.currentFrequency
            val localVolMultiplier = this.currentVolume // Use the updated currentVolume
            val localCycleMillis = this.currentCycleMillis
            val localDutyPercent = this.currentDutyPercent

            // Add debug logging to show the frequency being produced
            Log.d(TAG, "generateToneLoop: Producing tone with frequency: $localFreq Hz, cycle: $localCycleMillis ms, duty: $localDutyPercent%")

            if (audioTrack == null || audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.w(TAG, "generateToneLoop: AudioTrack is null or not playing, exiting loop.")
                break // Exit if AudioTrack is not ready
            }

            // If frequency, duty cycle, or cycle duration is zero/invalid, fill buffer with silence.
            // This is a safeguard; updateSound() should manage stopping the sound (and this loop).
            if (localFreq <= 0.0 || localDutyPercent <= 0 || localCycleMillis <= 0) {
                buffer.fill(0) // Fill with silence
            } else {
                val samplesPerMs = SoundConstants.SAMPLE_RATE / 1000.0
                // Ensure totalSamplesInCycle is at least 1 to avoid division by zero if localCycleMillis is very small but non-zero
                val totalSamplesInCycle = max(1, (localCycleMillis * samplesPerMs).toInt())
                val onSamplesInCycle = (totalSamplesInCycle * (localDutyPercent / 100.0)).toInt()

                val angularIncrement = 2.0 * PI * localFreq / SoundConstants.SAMPLE_RATE
                val amplitude = (Short.MAX_VALUE * localVolMultiplier).toInt() // Use localVolMultiplier

                for (i in buffer.indices) {
                    if (samplesIntoCurrentCycle < onSamplesInCycle) {
                        // Sound ON part of the duty cycle
                        buffer[i] = (sin(angle) * amplitude).toInt().toShort()
                    } else {
                        // Sound OFF part of the duty cycle
                        buffer[i] = 0 // Silence
                    }
                    angle += angularIncrement
                    if (angle >= 2.0 * PI) { // Use >= for floating point comparisons
                        angle -= 2.0 * PI
                    }
                    samplesIntoCurrentCycle = (samplesIntoCurrentCycle + 1) % totalSamplesInCycle
                }
            }

            try {
                val written = audioTrack?.write(buffer, 0, buffer.size) ?: -1
                if (written < 0) {
                    Log.e(TAG, "generateToneLoop: AudioTrack write error: $written. Exiting loop.")
                    // This often indicates a problem with the AudioTrack state.
                    // Consider attempting to stop sound gracefully or signal an error.
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during AudioTrack write", e)
                break // Exit loop on write exception
            }
        }
        Log.i(TAG, "Exiting tone generation loop. isPlaying was: $isPlaying")
        // Note: isPlaying is primarily managed by startSound/stopSound.
        // If the loop exits unexpectedly, stopSound() should ideally be called by the error handling logic.
    }

    // Removed setVolume(level: Int) method as volume is now controlled by preferences.
    // Removed amplitudeMultiplier and setAmplitudeMultiplier as currentVolume directly holds the multiplier.
    // Removed updateAmplitudeFromPreferences as its logic is now in updateCurrentVolumeFromPreferences.
}
