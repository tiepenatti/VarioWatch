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
import androidx.localbroadcastmanager.content.LocalBroadcastManager // Import LocalBroadcastManager
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.coroutines.coroutineContext // Add import for coroutineContext

class SoundSynthesizerService : Service() {

    private val TAG = "SoundSynthesizerService"
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentFrequency = 0.0
    private var currentVolume = SoundConstants.DEFAULT_VOLUME / SoundConstants.VOLUME_LEVELS.toFloat() // Normalized volume
    private var shouldBeepForClimb = false // New state to control beeping for climb

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var toneJob: Job? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SoundSynthesizerService = this@SoundSynthesizerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val verticalSpeedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val verticalSpeed = intent.getFloatExtra(VarioService.EXTRA_VERTICAL_SPEED, Float.NaN)
                Log.d(TAG, "Received vertical speed broadcast: $verticalSpeed") // Log received speed
                if (!verticalSpeed.isNaN()) {
                    updateSound(verticalSpeed)
                } else {
                    Log.w(TAG, "Received NaN vertical speed")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service creating")
        setupAudioTrack()
        // Use LocalBroadcastManager to register the receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            verticalSpeedReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE)
        )
        Log.d(TAG, "Receiver registered with LocalBroadcastManager")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        // Keep the service running
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        // Use LocalBroadcastManager to unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(verticalSpeedReceiver)
        stopSound()
        audioTrack?.release()
        audioTrack = null
        serviceJob.cancel() // Cancel coroutines
        Log.d(TAG, "Service destroyed")
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
        val newFrequency: Double
        var shouldPlayContinuous = false // For sink/alarm sounds
        this.shouldBeepForClimb = false // Reset beeping state each time

        Log.d(TAG, "updateSound called with verticalSpeed: $verticalSpeed")

        when {
            // Climbing
            verticalSpeed > SoundConstants.CLIMB_THRESHOLD_MS -> {
                val steps = ((verticalSpeed - SoundConstants.CLIMB_THRESHOLD_MS) / 0.1f).toInt()
                newFrequency = (SoundConstants.BASE_FREQUENCY_HZ + steps * SoundConstants.FREQUENCY_INCREMENT_HZ_PER_TENTH_MS).toDouble()
                this.shouldBeepForClimb = true // Enable beeping for climb
                Log.d(TAG, "Climbing detected. Steps: $steps, New Frequency: $newFrequency. Beeping enabled.")
            }
            // Sinking (above alarm threshold)
            verticalSpeed <= SoundConstants.SINK_THRESHOLD_MS && (SoundConstants.SINK_ALARM_THRESHOLD_MS == 0f || verticalSpeed > SoundConstants.SINK_ALARM_THRESHOLD_MS) -> {
                 val sinkRange = SoundConstants.SINK_THRESHOLD_MS - SoundConstants.SINK_ALARM_THRESHOLD_MS.coerceAtMost(SoundConstants.SINK_THRESHOLD_MS - 1)
                 val freqRange = SoundConstants.BASE_FREQUENCY_HZ / 2.0
                 val progress = (verticalSpeed - SoundConstants.SINK_THRESHOLD_MS) / sinkRange
                 newFrequency = SoundConstants.BASE_FREQUENCY_HZ + progress * freqRange
                 shouldPlayContinuous = true // Sink sound is continuous
                 Log.d(TAG, "Sinking detected. Progress: $progress, New Frequency: $newFrequency. Continuous sound.")
            }
             // Sink Alarm
            SoundConstants.SINK_ALARM_THRESHOLD_MS != 0f && verticalSpeed <= SoundConstants.SINK_ALARM_THRESHOLD_MS -> {
                // TODO: Implement distinct alarm sound (siren)
                newFrequency = SoundConstants.BASE_FREQUENCY_HZ / 4.0 // For now, a very low fixed frequency
                shouldPlayContinuous = true // Sink alarm is continuous (for now)
                Log.d(TAG, "Sink Alarm detected. New Frequency: $newFrequency. Continuous sound (alarm).")
            }
            // Neutral band
            else -> {
                newFrequency = 0.0
                Log.d(TAG, "Neutral band detected. No sound.")
            }
        }

        val previousFrequency = currentFrequency
        currentFrequency = newFrequency.coerceIn(50.0, SoundConstants.SAMPLE_RATE / 2.0) // Ensure frequency is valid
        if (previousFrequency != currentFrequency) {
             Log.d(TAG, "Frequency updated. Clamped Frequency: $currentFrequency (from $newFrequency)")
        }

        // Logic to start/stop sound based on whether it's beeping or continuous
        if (this.shouldBeepForClimb || shouldPlayContinuous) {
            if (!isPlaying) {
                Log.d(TAG, "Condition met to start sound (beeping or continuous).")
                startSound()
            }
        } else {
            if (isPlaying) {
                Log.d(TAG, "Condition met to stop sound.")
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
        Log.i(TAG, "Attempting to stop sound playback...") // Use Info level
        toneJob?.cancel() // Stop the generation loop
        toneJob = null
        try {
             audioTrack?.pause()
             audioTrack?.flush()
             audioTrack?.stop()
             Log.i(TAG, "AudioTrack playback stopped successfully.") // Confirm success
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
        var lastBeepEventTime = 0L // Tracks time for both beep and silence phases
        var isCurrentlyInBeepPhase = false // True if currently outputting sound for a beep

        Log.i(TAG, "Starting tone generation loop. Buffer size: $bufferSize")

        while (coroutineContext.isActive) {
            val currentFreq = this.currentFrequency // Capture for this iteration
            val currentVol = this.currentVolume   // Capture for this iteration

            if (audioTrack == null || audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.w(TAG, "generateToneLoop: AudioTrack is null or not playing, exiting loop.")
                break
            }

            val generateSoundThisCycle: Boolean

            if (this.shouldBeepForClimb && currentFreq > 0) {
                val currentTime = System.currentTimeMillis()
                if (isCurrentlyInBeepPhase) { // Currently in a beep phase
                    if (currentTime - lastBeepEventTime < SoundConstants.BEEP_DURATION_MS) {
                        generateSoundThisCycle = true
                    } else { // Beep duration ended, switch to silence
                        isCurrentlyInBeepPhase = false
                        lastBeepEventTime = currentTime // Reset timer for silence duration
                        generateSoundThisCycle = false
                        Log.v(TAG, "Beep ended, starting silence.")
                    }
                } else { // Currently in a silence phase (between beeps)
                    if (currentTime - lastBeepEventTime >= SoundConstants.SILENCE_DURATION_MS) {
                        isCurrentlyInBeepPhase = true
                        lastBeepEventTime = currentTime // Reset timer for beep duration
                        generateSoundThisCycle = true
                        Log.v(TAG, "Silence ended, starting beep.")
                    } else {
                        generateSoundThisCycle = false
                    }
                }
            } else if (currentFreq > 0) { // Not beeping for climb, but frequency is positive (e.g., continuous sink tone)
                generateSoundThisCycle = true
            } else { // Frequency is zero or not beeping for climb
                generateSoundThisCycle = false
                if (isCurrentlyInBeepPhase) { // If we were beeping and freq/climb state changed, reset
                    isCurrentlyInBeepPhase = false
                    lastBeepEventTime = System.currentTimeMillis()
                    Log.v(TAG, "Climb/frequency condition ended during beep, stopping beep.")
                }
            }

            if (generateSoundThisCycle) {
                val angularIncrement = 2.0 * PI * currentFreq / SoundConstants.SAMPLE_RATE
                val amplitude = (Short.MAX_VALUE * currentVol).toInt()
                for (i in buffer.indices) {
                    buffer[i] = (sin(angle) * amplitude).toInt().toShort()
                    angle += angularIncrement
                    if (angle > 2.0 * PI) {
                        angle -= 2.0 * PI
                    }
                }
            } else {
                buffer.fill(0) // Fill with silence
            }

            try {
                val written = audioTrack?.write(buffer, 0, buffer.size) ?: -1
                if (written < 0) {
                    Log.e(TAG, "generateToneLoop: AudioTrack write error: $written. Exiting loop.")
                    // Consider calling stopSound() here if appropriate, but be mindful of re-entry
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during AudioTrack write", e)
                // Consider calling stopSound()
                break
            }
        }
        Log.i(TAG, "Exiting tone generation loop")
    }

    // TODO: Add methods to control volume if needed via binding
    fun setVolume(level: Int) {
        val normalizedLevel = level.coerceIn(0, SoundConstants.VOLUME_LEVELS)
        currentVolume = normalizedLevel / SoundConstants.VOLUME_LEVELS.toFloat()
        Log.d(TAG, "Volume set to level $level (normalized: $currentVolume)")
        // Amplitude is updated dynamically in the generation loop
    }
}
