package com.synapselink.app

import android.util.Log

// This class represents Logos's initial 'Deeper Learning' for auditory input.
// It will break down audio buffers into fundamental components.
class AudioBufferAnalyzer(private val listener: (AudioData) -> Unit) {

    // Define a data class to encapsulate processed audio data
    data class AudioData(
        val timestamp: Long,
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int, // e.g., ENCODING_PCM_16BIT
        val pcmBuffer: ShortArray // Raw PCM data
        // Add more extracted features here in the future, e.g., List<FrequencyBandIntensity>
    )

    // Logos's initial analysis: Receiving and preparing audio data
    fun analyze(audioBuffer: ShortArray, shortsRead: Int) {
        val currentTime = System.currentTimeMillis() // Capture analysis timestamp

        if (shortsRead > 0) {
            // Create a copy of the relevant part of the buffer to avoid issues
            // with the underlying buffer being reused by AudioRecord.
            // This is part of Logos ensuring "logical consistency" of data.
            val relevantPcmData = audioBuffer.copyOf(shortsRead)

            // Pass the extracted audio data to the listener for further Logos processing
            listener(
                AudioData(
                    timestamp = currentTime,
                    sampleRate = MainActivity.AUDIO_SAMPLE_RATE,
                    channelConfig = MainActivity.AUDIO_CHANNEL_CONFIG,
                    audioFormat = MainActivity.AUDIO_FORMAT,
                    pcmBuffer = relevantPcmData
                )
            )
        } else {
            Log.w("SynapseLinkAudio", "Received empty audio buffer or no shorts read.")
            // Pathos might generate an "audio silence/data gap" intuitive marker here.
        }
    }
}
package com.synapselink.app

import android.util.Log
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.abs

// This class represents Logos's ongoing 'Deeper Learning' for auditory input,
// now extracting specific fundamental audio features.
class AudioBufferAnalyzer(private val listener: (AudioData) -> Unit) {

    // Define an enhanced data class to encapsulate processed audio data,
    // now with calculated features like RMS and Zero-Crossing Rate.
    data class AudioData(
        val timestamp: Long,
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int, // e.g., ENCODING_PCM_16BIT
        val pcmBuffer: ShortArray, // Raw PCM data
        val rms: Double, // Root Mean Square (volume/energy)
        val zeroCrossingRate: Double // Basic pitch estimation/signal complexity
        // In the future, we would add: val fftSpectrum: FloatArray // Frequency spectrum data
        // And more extracted features like: vocal tremor, prosody metrics
    )

    // Logos's initial analysis: Receiving and preparing audio data and extracting features.
    fun analyze(audioBuffer: ShortArray, shortsRead: Int) {
        val currentTime = System.currentTimeMillis() // Capture analysis timestamp

        if (shortsRead > 0) {
            val relevantPcmData = audioBuffer.copyOf(shortsRead)

            // --- Logos: Feature Extraction (Deeper Learning) ---

            // 1. Calculate Root Mean Square (RMS) - Measure of audio signal amplitude/energy
            val rms = calculateRMS(relevantPcmData)

            // 2. Calculate Zero-Crossing Rate (ZCR) - Measures the number of times the signal
            // changes sign (from positive to negative or vice versa).
            // Useful for basic pitch detection and indicating signal noisiness/complexity.
            val zeroCrossingRate = calculateZeroCrossingRate(relevantPcmData, MainActivity.AUDIO_SAMPLE_RATE)

            // 3. Conceptual FFT Processing (Placeholder for future enhancement)
            // For full frequency spectrum analysis, an optimized FFT library would be integrated here.
            // This is where Logos would 'peel' the audio signal into its frequency components.
            // For example: val fftResult = performFFT(relevantPcmData)
            // This part might require a C++/NDK implementation for performance on mobile devices.
            // Log.d("SynapseLinkAudio", "Conceptual FFT: Would process ${relevantPcmData.size} samples.")


            // Pass the extracted audio data and features to the listener for further Logos processing
            listener(
                AudioData(
                    timestamp = currentTime,
                    sampleRate = MainActivity.AUDIO_SAMPLE_RATE,
                    channelConfig = MainActivity.AUDIO_CHANNEL_CONFIG,
                    audioFormat = MainActivity.AUDIO_FORMAT,
                    pcmBuffer = relevantPcmData,
                    rms = rms,
                    zeroCrossingRate = zeroCrossingRate
                )
            )

            Log.d("SynapseLinkAudio", "Processed Audio Buffer: " +
                    "Samples=${shortsRead}, RMS=${String.format("%.4f", rms)}, " +
                    "ZCR=${String.format("%.4f", zeroCrossingRate)}")

        } else {
            Log.w("SynapseLinkAudio", "Received empty audio buffer or no shorts read.")
            // Pathos might generate an "audio silence/data gap" intuitive marker here,
            // influencing the confidence in auditory bio-signals.
            // If we get an empty buffer, provide default/zero values for features
            listener(
                AudioData(
                    timestamp = currentTime,
                    sampleRate = MainActivity.AUDIO_SAMPLE_RATE,
                    channelConfig = MainActivity.AUDIO_CHANNEL_CONFIG,
                    audioFormat = MainActivity.AUDIO_FORMAT,
                    pcmBuffer = shortArrayOf(),
                    rms = 0.0,
                    zeroCrossingRate = 0.0
                )
            )
        }
    }

    // --- Helper Functions for Audio Feature Extraction ---

    // Logos's 'calculated action' for quantifying signal energy
    private fun calculateRMS(buffer: ShortArray): Double {
        if (buffer.isEmpty()) return 0.0
        var sumOfSquares = 0.0
        for (s in buffer) {
            sumOfSquares += s.toDouble().pow(2)
        }
        return sqrt(sumOfSquares / buffer.size)
    }

    // Logos's 'calculated action' for quantifying signal complexity/frequency changes
    private fun calculateZeroCrossingRate(buffer: ShortArray, sampleRate: Int): Double {
        if (buffer.size < 2) return 0.0
        var numZeroCrossings = 0
        for (i in 0 until buffer.size - 1) {
            if ((buffer[i] >= 0 && buffer[i+1] < 0) || (buffer[i] < 0 && buffer[i+1] >= 0)) {
                numZeroCrossings++
            }
        }
        // Normalize by the number of samples in the buffer for a rate
        return numZeroCrossings.toDouble() / buffer.size
    }

    // Placeholder for future FFT implementation
    // private fun performFFT(buffer: ShortArray): FloatArray {
    //    // This would involve a dedicated FFT algorithm/library.
    //    // For optimal performance, consider using a native NDK implementation or
    //    // a highly optimized JVM library for real-time spectral analysis.
    //    return FloatArray(0) // Return an empty array for now
    // }
}

