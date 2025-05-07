package au.com.penattilabs.variowatch

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.abs

data class TonePoint(
    val verticalSpeed: Float,
    val hertz: Int,
    val cycleMillis: Int,
    val dutyPercent: Int
) {
    companion object {
        fun interpolate(speed: Float, p1: TonePoint, p2: TonePoint): Triple<Int, Int, Int> {
            // Ensure p1.verticalSpeed is not equal to p2.verticalSpeed to avoid division by zero.
            // This case should ideally be handled by the calling logic (e.g., if speed matches a point exactly).
            if (abs(p1.verticalSpeed - p2.verticalSpeed) < 0.001f) {
                return Triple(p1.hertz, p1.cycleMillis, p1.dutyPercent)
            }

            val ratio = (speed - p1.verticalSpeed) / (p2.verticalSpeed - p1.verticalSpeed)

            val hertz = (p1.hertz + ratio * (p2.hertz - p1.hertz)).toInt()
            val cycleMillis = (p1.cycleMillis + ratio * (p2.cycleMillis - p1.cycleMillis)).toInt()
            val dutyPercent = (p1.dutyPercent + ratio * (p2.dutyPercent - p1.dutyPercent)).toInt()

            return Triple(hertz, cycleMillis, dutyPercent)
        }
    }
}

data class VarioSoundConfig(
    val climbToneOnThreshold: Float,
    val climbToneOffThreshold: Float,
    val sinkToneOnThreshold: Float,
    val sinkToneOffThreshold: Float,
    val tonePoints: List<TonePoint> // Should be kept sorted by verticalSpeed
) {
    fun getSoundParameters(verticalSpeed: Float): Triple<Int, Int, Int>? {
        if (tonePoints.isEmpty()) return null

        // Find the two points that bracket the current vertical speed
        // The tonePoints list is assumed to be sorted by verticalSpeed

        // Handle speeds outside the defined range by clamping to the nearest point's parameters
        if (verticalSpeed <= tonePoints.first().verticalSpeed) {
            val point = tonePoints.first()
            return Triple(point.hertz, point.cycleMillis, point.dutyPercent)
        }
        if (verticalSpeed >= tonePoints.last().verticalSpeed) {
            val point = tonePoints.last()
            return Triple(point.hertz, point.cycleMillis, point.dutyPercent)
        }

        // Find the bracketing points
        var p1: TonePoint? = null
        var p2: TonePoint? = null

        for (i in 0 until tonePoints.size - 1) {
            if (verticalSpeed >= tonePoints[i].verticalSpeed && verticalSpeed < tonePoints[i+1].verticalSpeed) {
                p1 = tonePoints[i]
                p2 = tonePoints[i+1]
                break
            }
        }
        
        // Handle exact match with a point (already covered by loop logic if points are distinct)
        // For robustness, explicitly check if speed matches any point exactly.
        val exactMatch = tonePoints.find { abs(it.verticalSpeed - verticalSpeed) < 0.001f }
        if (exactMatch != null) {
            return Triple(exactMatch.hertz, exactMatch.cycleMillis, exactMatch.dutyPercent)
        }

        return if (p1 != null && p2 != null) {
            TonePoint.interpolate(verticalSpeed, p1, p2)
        } else {
            // This case should ideally not be reached if the list is sorted and speed is within the overall range.
            // Fallback: find the closest point if no direct bracket is found (e.g. if list isn't perfectly sorted or has gaps)
            // However, the initial clamping should handle out-of-bounds cases.
            // If p1 and p2 are null here, it implies an issue with the data or logic.
            // For now, returning null or parameters of the closest point.
            // A simple fallback could be to return the parameters of the point closest to the verticalSpeed.
            val closestPoint = tonePoints.minByOrNull { abs(it.verticalSpeed - verticalSpeed) }
            closestPoint?.let { Triple(it.hertz, it.cycleMillis, it.dutyPercent) }
        }
    }
}

object SoundProfileParser {
    private const val DEFAULT_PROFILE_CONTENT = """ClimbToneOnThreshold=0.2
ClimbToneOffThreshold=0.15
SinkToneOnThreshold=-3
SinkToneOffThreshold=-3
tone=-10.0,200,100,100
tone=-3.0,280,100,100
tone=-0.51,300,500,100
tone=-0.50,200,800,5
tone=0.09,400,600,10
tone=0.11,401,600,50
tone=1.16,550,552,52
tone=2.67,763,483,55
tone=4.24,985,412,58
tone=6.00,1234,322,62
tone=8.00,1517,241,66
tone=10.00,2000,150,70"""

    fun parse(configString: String): VarioSoundConfig {
        val lines = configString.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        var climbToneOnThreshold = 0.2f // Default value
        var climbToneOffThreshold = 0.15f // Default value
        var sinkToneOnThreshold = -3.0f // Default value
        var sinkToneOffThreshold = -3.0f // Default value
        val tonePoints = mutableListOf<TonePoint>()

        lines.forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                try {
                    when (key) {
                        "ClimbToneOnThreshold" -> climbToneOnThreshold = value.toFloat()
                        "ClimbToneOffThreshold" -> climbToneOffThreshold = value.toFloat()
                        "SinkToneOnThreshold" -> sinkToneOnThreshold = value.toFloat()
                        "SinkToneOffThreshold" -> sinkToneOffThreshold = value.toFloat()
                        "tone" -> {
                            val params = value.split(",").map { it.trim() }
                            if (params.size == 4) {
                                tonePoints.add(
                                    TonePoint(
                                        verticalSpeed = params[0].toFloat(),
                                        hertz = params[1].toInt(),
                                        cycleMillis = params[2].toInt(),
                                        dutyPercent = params[3].toInt()
                                    )
                                )
                            } else {
                                System.err.println("SoundProfileParser: Skipping malformed tone line: $line")
                            }
                        }
                        // else -> System.err.println("SoundProfileParser: Unknown key: $key") // Optional: log unknown keys
                    }
                } catch (e: NumberFormatException) {
                    System.err.println("SoundProfileParser: Skipping malformed line (NumberFormatException for value '$value'): $line")
                }
            }
        }
        return VarioSoundConfig(
            climbToneOnThreshold,
            climbToneOffThreshold,
            sinkToneOnThreshold,
            sinkToneOffThreshold,
            tonePoints.sortedBy { it.verticalSpeed } // Ensure points are sorted for interpolation logic
        )
    }

    fun loadFromAssets(context: Context, fileName: String): VarioSoundConfig? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val content = reader.readText()
                    parse(content)
                }
            }
        } catch (e: IOException) {
            System.err.println("SoundProfileParser: Error loading config file '$fileName' from assets: ${e.message}")
            e.printStackTrace()
            // Optionally, return a default configuration or re-throw to indicate critical failure
            createDefaultProfile(context, fileName)
            null
        }
    }

    fun loadFromAssetsOrInternal(context: Context, fileName: String): VarioSoundConfig? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val content = reader.readText()
                    return parse(content)
                }
            }
        } catch (e: IOException) {
            System.err.println("SoundProfileParser: Error loading config file '$fileName' from assets: ${e.message}")
            e.printStackTrace()

            val internalFile = File(context.filesDir, fileName)
            if (internalFile.exists()) {
                return try {
                    internalFile.bufferedReader().use { reader ->
                        val content = reader.readText()
                        parse(content)
                    }
                } catch (e: IOException) {
                    System.err.println("SoundProfileParser: Error loading config file '$fileName' from internal storage: ${e.message}")
                    null
                }
            } else {
                createDefaultProfile(context, fileName)
                null
            }
        }
    }

    private fun createDefaultProfile(context: Context, fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write(DEFAULT_PROFILE_CONTENT)
                }
            }
            System.out.println("SoundProfileParser: Default sound profile created at: ${file.absolutePath}")
        } catch (e: IOException) {
            System.err.println("SoundProfileParser: Failed to create default sound profile: ${e.message}")
        }
    }
}
