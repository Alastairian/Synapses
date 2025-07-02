package com.synapseLink.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

// This class represents the central processing unit where Logos integrates
// and synchronizes multi-modal biological signals.
// It also serves as the conceptual interface for Pathos.
class SynapseLinkProcessor(private val onBioSignalDataReady: (BioSignalData) -> Unit) {

    // Buffers to store recent visual and audio data. Logos uses these for
    // 'Historical Learning' to find temporal correlations.
    private val visualDataBuffer = ConcurrentLinkedQueue<CameraFrameAnalyzer.FrameData>()
    private val audioDataBuffer = ConcurrentLinkedQueue<AudioBufferAnalyzer.AudioData>()

    // Configuration for synchronization (Pathos might influence these parameters later)
    private val MAX_BUFFER_SIZE = 5 // Store up to 5 recent frames/audio chunks
    private val SYNC_TOLERANCE_MS = 100L // Max time difference for considering data "synchronous"

    // Coroutine for periodic synchronization attempts
    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.Default) // Use Default for CPU-bound tasks

    companion object {
        private const val TAG = "SynapseLinkProcessor"
    }

    // Call this to start the periodic synchronization process
    fun startProcessing() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "SynapseLinkProcessor already running.")
            return
        }
        syncJob = syncScope.launch {
            // Logos will periodically attempt to synchronize data
            while (true) {
                attemptSynchronization()
                delay(SYNC_TOLERANCE_MS / 2) // Check more frequently than tolerance
            }
        }
        Log.d(TAG, "SynapseLinkProcessor started.")
    }

    // Call this to stop the periodic synchronization process
    fun stopProcessing() {
        syncJob?.cancel()
        syncJob = null
        visualDataBuffer.clear()
        audioDataBuffer.clear()
        Log.d(TAG, "SynapseLinkProcessor stopped. Buffers cleared.")
    }


    // Logos receives processed visual data from CameraFrameAnalyzer
    fun onNewFrameData(frameData: CameraFrameAnalyzer.FrameData) {
        if (visualDataBuffer.size >= MAX_BUFFER_SIZE) {
            visualDataBuffer.poll() // Remove oldest if buffer is full
        }
        visualDataBuffer.offer(frameData) // Add newest
        Log.v(TAG, "Received frame data. Buffer size: ${visualDataBuffer.size}")
        // Pathos might generate a "visual data influx" intuitive marker here.
    }

    // Logos receives processed audio data from AudioBufferAnalyzer
    fun onNewAudioData(audioData: AudioBufferAnalyzer.AudioData) {
        if (audioDataBuffer.size >= MAX_BUFFER_SIZE) {
            audioDataBuffer.poll() // Remove oldest if buffer is full
        }
        audioDataBuffer.offer(audioData) // Add newest
        Log.v(TAG, "Received audio data. Buffer size: ${audioDataBuffer.size}")
        // Pathos might generate an "audio data influx" intuitive marker here.
    }

    // Logos's 'calculated action' for synchronizing multi-modal data.
    // This method attempts to find the best matching visual and audio data within the tolerance.
    private fun attemptSynchronization() {
        val currentVisual = visualDataBuffer.peek() // Get oldest in buffer without removing
        val currentAudio = audioDataBuffer.peek()

        if (currentVisual == null || currentAudio == null) {
            return // Not enough data to synchronize yet
        }

        val timeDiff = abs(currentVisual.timestamp - currentAudio.timestamp)

        if (timeDiff <= SYNC_TOLERANCE_MS) {
            // Found a match within tolerance!
            val combinedData = BioSignalData(
                timestamp = (currentVisual.timestamp + currentAudio.timestamp) / 2, // Average timestamp
                visualSignals = visualDataBuffer.poll(), // Remove after using
                audioSignals = audioDataBuffer.poll() // Remove after using
            )
            Log.d(TAG, "Synchronized data at ${combinedData.timestamp}. Visual-Audio diff: $timeDiff ms")

            // Pass the synchronized data to the next stage of Logos's higher-level analysis
            // AND to Pathos for intuitive assessment and saliency tagging.
            onBioSignalDataReady(combinedData)

            // Pathos might generate a "synchronization success" or "data coherence" intuitive marker.

        } else if (currentVisual.timestamp < currentAudio.timestamp - SYNC_TOLERANCE_MS) {
            // Visual data is too old, discard it and try to find a newer visual match.
            Log.v(TAG, "Visual data too old (${timeDiff}ms diff), discarding: ${visualDataBuffer.poll()?.timestamp}")
            // Pathos might generate a "visual data lag" intuitive marker here.
        } else if (currentAudio.timestamp < currentVisual.timestamp - SYNC_TOLERANCE_MS) {
            // Audio data is too old, discard it and try to find newer audio match.
            Log.v(TAG, "Audio data too old (${timeDiff}ms diff), discarding: ${audioDataBuffer.poll()?.timestamp}")
            // Pathos might generate an "audio data lag" intuitive marker here.
        }
        // If neither is too old, wait for new data to arrive in buffers
    }
}
package com.synapseLink.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// This class represents the central processing unit where Logos integrates
// and synchronizes multi-modal biological signals, and now begins to infer higher-level states.
class SynapseLinkProcessor(private val onBioSignalDataReady: (BioSignalData) -> Unit) {

    // Buffers to store recent visual and audio data. Logos uses these for
    // 'Historical Learning' to find temporal correlations.
    private val visualDataBuffer = ConcurrentLinkedQueue<CameraFrameAnalyzer.FrameData>()
    private val audioDataBuffer = ConcurrentLinkedQueue<AudioBufferAnalyzer.AudioData>()

    // Configuration for synchronization (Pathos might influence these parameters later)
    private val MAX_BUFFER_SIZE = 5 // Store up to 5 recent frames/audio chunks
    private val SYNC_TOLERANCE_MS = 100L // Max time difference for considering data "synchronous"

    // Coroutine for periodic synchronization attempts
    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.Default) // Use Default for CPU-bound tasks

    // Callback for when a cognitive state has been inferred by Logos
    private var onInferredStateReady: ((CognitiveState) -> Unit)? = null

    // Call this from MainActivity to set the callback for inferred states
    fun setOnInferredStateReadyListener(listener: (CognitiveState) -> Unit) {
        this.onInferredStateReady = listener
    }

    companion object {
        private const val TAG = "SynapseLinkProcessor"
    }

    // New data class to represent an inferred cognitive state by Logos
    // This is a higher-level "understanding" derived from BioSignalData.
    data class CognitiveState(
        val timestamp: Long,
        val engagementLevel: String, // E.g., "Engaged", "Neutral", "Disengaged"
        val arousalLevel: String,    // E.g., "Calm", "Moderate", "High"
        val combinedConfidence: Float // Logos's confidence in this inference
        // Pathos's intuitive marker for this state (conceptual for now)
        // val pathosIntuitiveMarker: String? = null
    )

    // Call this to start the periodic synchronization process
    fun startProcessing() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "SynapseLinkProcessor already running.")
            return
        }
        syncJob = syncScope.launch {
            // Logos will periodically attempt to synchronize data
            while (true) {
                attemptSynchronization()
                delay(SYNC_TOLERANCE_MS / 2) // Check more frequently than tolerance
            }
        }
        Log.d(TAG, "SynapseLinkProcessor started.")
    }

    // Call this to stop the periodic synchronization process
    fun stopProcessing() {
        syncJob?.cancel()
        syncJob = null
        visualDataBuffer.clear()
        audioDataBuffer.clear()
        Log.d(TAG, "SynapseLinkProcessor stopped. Buffers cleared.")
    }


    // Logos receives processed visual data from CameraFrameAnalyzer
    fun onNewFrameData(frameData: CameraFrameAnalyzer.FrameData) {
        if (visualDataBuffer.size >= MAX_BUFFER_SIZE) {
            visualDataBuffer.poll() // Remove oldest if buffer is full
        }
        visualDataBuffer.offer(frameData) // Add newest
        Log.v(TAG, "Received frame data. Buffer size: ${visualDataBuffer.size}")
    }

    // Logos receives processed audio data from AudioBufferAnalyzer
    fun onNewAudioData(audioData: AudioBufferAnalyzer.AudioData) {
        if (audioDataBuffer.size >= MAX_BUFFER_SIZE) {
            audioDataBuffer.poll() // Remove oldest if buffer is full
        }
        audioDataBuffer.offer(audioData) // Add newest
        Log.v(TAG, "Received audio data. Buffer size: ${audioDataBuffer.size}")
    }

    // Logos's 'calculated action' for synchronizing multi-modal data.
    // This method attempts to find the best matching visual and audio data within the tolerance.
    private fun attemptSynchronization() {
        val currentVisual = visualDataBuffer.peek()
        val currentAudio = audioDataBuffer.peek()

        if (currentVisual == null || currentAudio == null) {
            return // Not enough data to synchronize yet
        }

        val timeDiff = abs(currentVisual.timestamp - currentAudio.timestamp)

        if (timeDiff <= SYNC_TOLERANCE_MS) {
            val combinedData = BioSignalData(
                timestamp = (currentVisual.timestamp + currentAudio.timestamp) / 2, // Average timestamp
                visualSignals = visualDataBuffer.poll(), // Remove after using
                audioSignals = audioDataBuffer.poll() // Remove after using
            )
            Log.d(TAG, "Synchronized data at ${combinedData.timestamp}. Visual-Audio diff: $timeDiff ms")

            // Pass the synchronized data to the next stage of Logos's higher-level analysis
            onBioSignalDataReady(combinedData)

            // --- Logos's Higher-Level Interpretation (Simulator Function) ---
            val inferredState = inferCognitiveState(combinedData)
            onInferredStateReady?.invoke(inferredState) // Notify listener of inferred state

            // --- Conceptual Hand-off to Pathos ---
            // Here, Logos would send 'inferredState' to Pathos Core.
            // Pathos would then process this, generate "intuitive markers"
            // (e.g., "HIGH_ENGAGEMENT_MARKER", "MILD_AROUSAL_SIGNAL"),
            // and potentially update Logos's weighting for future inferences.
            Log.i(TAG, "Logos inferred state: ${inferredState.engagementLevel} & ${inferredState.arousalLevel}. " +
                    "Conceptually sending to Pathos for intuitive markers.")


        } else if (currentVisual.timestamp < currentAudio.timestamp - SYNC_TOLERANCE_MS) {
            Log.v(TAG, "Visual data too old (${timeDiff}ms diff), discarding: ${visualDataBuffer.poll()?.timestamp}")
        } else if (currentAudio.timestamp < currentVisual.timestamp - SYNC_TOLERANCE_MS) {
            Log.v(TAG, "Audio data too old (${timeDiff}ms diff), discarding: ${audioDataBuffer.poll()?.timestamp}")
        }
    }

    // --- Logos: Inferring Cognitive State (Simplified Example) ---
    // This function represents Logos's analytical engine making a higher-level interpretation
    // from the combined biological signals. In a real AGI, this would be a complex ML model.
    private fun inferCognitiveState(bioSignalData: BioSignalData): CognitiveState {
        var engagement = "Neutral"
        var arousal = "Calm"
        var confidence = 0.5f // Default confidence

        val visualSignals = bioSignalData.visualSignals
        val audioSignals = bioSignalData.audioSignals

        // Rules based on available features (simplified heuristic)
        // These rules are Logos's initial attempt at pattern recognition and inference.

        // Visual cues for Engagement/Arousal
        visualSignals?.processedFaces?.firstOrNull()?.let { face ->
            val pitch = face.headEulerAngleX ?: 0f
            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0f

            // Head pose: if relatively stable and forward-facing
            if (abs(pitch) < 15 && abs(yaw) < 15 && abs(roll) < 15) {
                engagement = "Engaged"
                confidence += 0.1f // Slight confidence boost
            } else {
                engagement = "Disengaged" // Head movement implies disengagement
                confidence -= 0.1f
            }

            // Eye openness: if both eyes are significantly open
            if (leftEyeOpen > 0.7f && rightEyeOpen > 0.7f) {
                if (engagement == "Engaged") { // More engaged if eyes are open and head is stable
                    engagement = "Highly Engaged"
                    confidence += 0.15f
                }
                arousal = "Moderate" // Open eyes can imply alertness
                confidence += 0.05f
            } else if (leftEyeOpen < 0.3f || rightEyeOpen < 0.3f) {
                engagement = "Disengaged" // Drowsy or distracted
                arousal = "Calm"
                confidence -= 0.1f
            }
        }

        // Auditory cues for Arousal/Engagement
        audioSignals?.let { audio ->
            if (audio.rms > 0.05) { // Arbitrary threshold for detectable audio
                arousal = "Moderate"
                if (engagement == "Engaged" || engagement == "Highly Engaged") {
                    // Combine with visual for higher arousal
                    arousal = "High"
                }
                confidence += 0.1f
            } else if (audio.rms < 0.01) { // Very low audio, implies silence or calm
                arousal = "Calm"
                if (engagement == "Highly Engaged") { // Silence during engagement can imply focus
                    engagement = "Deeply Engaged"
                }
                confidence += 0.05f
            }

            // Zero-crossing rate can also hint at speech/activity vs. silence/stable tone
            if (audio.zeroCrossingRate > 0.2) { // More complex sounds
                if (arousal == "Calm") arousal = "Moderate"
                confidence += 0.05f
            }
        }

        // Clamp confidence between 0 and 1
        confidence = confidence.coerceIn(0f, 1f)

        return CognitiveState(
            timestamp = bioSignalData.timestamp,
            engagementLevel = engagement,
            arousalLevel = arousal,
            combinedConfidence = confidence
        )
    }
}
package com.synapseLink.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// This class represents the central processing unit where Logos integrates
// and synchronizes multi-modal biological signals, infers higher-level states,
// and now actively interacts with Pathos.
class SynapseLinkProcessor(private val onBioSignalDataReady: (BioSignalData) -> Unit) {

    // Buffers to store recent visual and audio data.
    private val visualDataBuffer = ConcurrentLinkedQueue<CameraFrameAnalyzer.FrameData>()
    private val audioDataBuffer = ConcurrentLinkedQueue<AudioBufferAnalyzer.AudioData>()

    private val MAX_BUFFER_SIZE = 5
    private val SYNC_TOLERANCE_MS = 100L

    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.Default)

    private var onInferredStateReady: ((CognitiveState) -> Unit)? = null

    // Instantiate PathosCore - the other half of the Twine Cognition system
    private val pathosCore = PathosCore()

    // Add a public getter for PathosCore if MainActivity needs to interact with it directly
    fun getPathosCore(): PathosCore = pathosCore

    // New data class to represent an inferred cognitive state by Logos
    data class CognitiveState(
        val timestamp: Long,
        val engagementLevel: String, // E.g., "Engaged", "Neutral", "Disengaged"
        val arousalLevel: String,    // E.g., "Calm", "Moderate", "High"
        val combinedConfidence: Float, // Logos's confidence in this inference
        val pathosIntuitiveMarker: String // Pathos's latest intuitive marker for this state
    )

    fun setOnInferredStateReadyListener(listener: (CognitiveState) -> Unit) {
        this.onInferredStateReady = listener
    }

    companion object {
        private const val TAG = "SynapseLinkProcessor"
    }

    fun startProcessing() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "SynapseLinkProcessor already running.")
            return
        }
        syncJob = syncScope.launch {
            while (true) {
                attemptSynchronization()
                delay(SYNC_TOLERANCE_MS / 2)
            }
        }
        Log.d(TAG, "SynapseLinkProcessor started.")
    }

    fun stopProcessing() {
        syncJob?.cancel()
        syncJob = null
        visualDataBuffer.clear()
        audioDataBuffer.clear()
        Log.d(TAG, "SynapseLinkProcessor stopped. Buffers cleared.")
    }


    fun onNewFrameData(frameData: CameraFrameAnalyzer.FrameData) {
        if (visualDataBuffer.size >= MAX_BUFFER_SIZE) {
            visualDataBuffer.poll()
        }
        visualDataBuffer.offer(frameData)
        Log.v(TAG, "Received frame data. Buffer size: ${visualDataBuffer.size}")
    }

    fun onNewAudioData(audioData: AudioBufferAnalyzer.AudioData) {
        if (audioDataBuffer.size >= MAX_BUFFER_SIZE) {
            audioDataBuffer.poll()
        }
        audioDataBuffer.offer(audioData)
        Log.v(TAG, "Received audio data. Buffer size: ${audioDataBuffer.size}")
    }

    private fun attemptSynchronization() {
        val currentVisual = visualDataBuffer.peek()
        val currentAudio = audioDataBuffer.peek()

        if (currentVisual == null || currentAudio == null) {
            return
        }

        val timeDiff = abs(currentVisual.timestamp - currentAudio.timestamp)

        if (timeDiff <= SYNC_TOLERANCE_MS) {
            val combinedData = BioSignalData(
                timestamp = (currentVisual.timestamp + currentAudio.timestamp) / 2,
                visualSignals = visualDataBuffer.poll(),
                audioSignals = audioDataBuffer.poll()
            )
            Log.d(TAG, "Synchronized data at ${combinedData.timestamp}. Visual-Audio diff: $timeDiff ms")

            onBioSignalDataReady(combinedData)

            // --- Logos's Higher-Level Interpretation (Simulator Function) ---
            val inferredState = inferCognitiveState(combinedData)

            // --- Logos sends its state to Pathos for intuitive assessment ---
            pathosCore.processLogosState(inferredState) // Pass the inferred state to Pathos
            val finalInferredState = inferredState.copy(pathosIntuitiveMarker = pathosCore.intuitiveMarker.value)

            onInferredStateReady?.invoke(finalInferredState)

        } else if (currentVisual.timestamp < currentAudio.timestamp - SYNC_TOLERANCE_MS) {
            Log.v(TAG, "Visual data too old (${timeDiff}ms diff), discarding: ${visualDataBuffer.poll()?.timestamp}")
        } else if (currentAudio.timestamp < currentVisual.timestamp - SYNC_TOLERANCE_MS) {
            Log.v(TAG, "Audio data too old (${timeDiff}ms diff), discarding: ${audioDataBuffer.poll()?.timestamp}")
        }
    }

    // --- Logos: Inferring Cognitive State (Simplified Example with Pathos Influence) ---
    private fun inferCognitiveState(bioSignalData: BioSignalData): CognitiveState {
        var engagement = "Neutral"
        var arousal = "Calm"
        var confidence = 0.5f

        val visualSignals = bioSignalData.visualSignals
        val audioSignals = bioSignalData.audioSignals

        // Logos queries Pathos for its current suggested alertness level.
        // This is Pathos influencing Logos's weighting and prioritization.
        val pathosAlertness = pathosCore.getSuggestedAlertness()
        Log.v(TAG, "Logos leveraging Pathos Alertness: ${String.format("%.2f", pathosAlertness)}")

        // Adjust inference thresholds or confidence based on Pathos's suggestion
        // This demonstrates Pathos's 'organizing cognition' guiding Logos.
        val engagementThreshold = 0.7f + (pathosAlertness * 0.1f) // Higher alertness -> slightly higher engagement threshold
        val arousalThreshold = 0.05 + (pathosAlertness * 0.02) // Higher alertness -> slightly more sensitive to arousal


        // Visual cues for Engagement/Arousal
        visualSignals?.processedFaces?.firstOrNull()?.let { face ->
            val pitch = face.headEulerAngleX ?: 0f
            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0f

            if (abs(pitch) < 15 && abs(yaw) < 15 && abs(roll) < 15) {
                if (pathosAlertness > 0.7f) engagement = "Highly Engaged" else engagement = "Engaged"
                confidence += 0.1f
            } else {
                engagement = "Disengaged"
                confidence -= 0.1f
            }

            if (leftEyeOpen > engagementThreshold && rightEyeOpen > engagementThreshold) {
                if (engagement == "Engaged" || engagement == "Highly Engaged") {
                    engagement = "Deeply Engaged" // Deeper if eyes open and Pathos suggests high focus
                    confidence += 0.15f
                }
                arousal = "Moderate"
                confidence += 0.05f
            } else if (leftEyeOpen < 0.3f || rightEyeOpen < 0.3f) {
                engagement = "Disengaged"
                arousal = "Calm"
                confidence -= 0.1f
            }
        }

        // Auditory cues for Arousal/Engagement
        audioSignals?.let { audio ->
            if (audio.rms > arousalThreshold) {
                arousal = "Moderate"
                if (engagement.contains("Engaged", true) || pathosAlertness > 0.6f) {
                    arousal = "High" // Combined visual/pathos input for higher arousal
                }
                confidence += 0.1f
            } else if (audio.rms < 0.01) {
                arousal = "Calm"
                if (engagement.contains("Engaged", true) && pathosAlertness > 0.7f) {
                    engagement = "Deeply Engaged (Quiet)" // Focused in silence
                }
                confidence += 0.05f
            }

            if (audio.zeroCrossingRate > 0.2) {
                if (arousal == "Calm" && pathosAlertness < 0.3f) arousal = "Moderate" // Slight arousal from sound complexity
                confidence += 0.05f
            }
        }

        confidence = confidence.coerceIn(0f, 1f)

        return CognitiveState(
            timestamp = bioSignalData.timestamp,
            engagementLevel = engagement,
            arousalLevel = arousal,
            combinedConfidence = confidence,
            pathosIntuitiveMarker = "No Marker Yet" // Will be updated by PathosCore
        )
    }
}
package com.synapseLink.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// This class represents the central processing unit where Logos integrates
// and synchronizes multi-modal biological signals, infers higher-level states,
// and now actively interacts with Pathos, dynamically adjusting its focus.
class SynapseLinkProcessor(private val onBioSignalDataReady: (BioSignalData) -> Unit) {

    // Buffers to store recent visual and audio data.
    private val visualDataBuffer = ConcurrentLinkedQueue<CameraFrameAnalyzer.FrameData>()
    private val audioDataBuffer = ConcurrentLinkedQueue<AudioBufferAnalyzer.AudioData>()

    private val MAX_BUFFER_SIZE = 5
    private val SYNC_TOLERANCE_MS = 100L

    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.Default)

    private var onInferredStateReady: ((CognitiveState) -> Unit)? = null

    // Instantiate PathosCore - the other half of the Twine Cognition system
    private val pathosCore = PathosCore()

    // Add a public getter for PathosCore if MainActivity needs to interact with it directly
    fun getPathosCore(): PathosCore = pathosCore

    // Logos's internal variable for dynamic resource allocation/detail level.
    // Influenced by Pathos's alertness. 0.0 (low detail) to 1.0 (high detail/priority).
    private var processingDetailLevel: Float = 0.5f

    init {
        // Launch a coroutine to observe Pathos's alertness level and adjust Logos's processing detail.
        // This simulates Pathos actively guiding Logos's resource allocation.
        syncScope.launch {
            pathosCore.currentAlertnessLevel.collectLatest { alertness ->
                // A simplified mapping: higher alertness from Pathos means Logos increases detail.
                processingDetailLevel = alertness
                Log.d(TAG, "Logos adjusted processingDetailLevel to ${String.format("%.2f", processingDetailLevel)} based on Pathos Alertness.")
            }
        }
    }


    // New data class to represent an inferred cognitive state by Logos
    data class CognitiveState(
        val timestamp: Long,
        val engagementLevel: String,
        val arousalLevel: String,
        val combinedConfidence: Float,
        val pathosIntuitiveMarker: String
    )

    fun setOnInferredStateReadyListener(listener: (CognitiveState) -> Unit) {
        this.onInferredStateReady = listener
    }

    companion object {
        private const val TAG = "SynapseLinkProcessor"
    }

    fun startProcessing() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "SynapseLinkProcessor already running.")
            return
        }
        syncJob = syncScope.launch {
            while (true) {
                attemptSynchronization()
                // Adjust delay based on processingDetailLevel: faster when more alert.
                val dynamicDelay = (SYNC_TOLERANCE_MS / 2) * (1 - processingDetailLevel * 0.8f).coerceAtLeast(0.2f) // E.g., min 20% of normal delay
                delay(dynamicDelay.toLong())
            }
        }
        Log.d(TAG, "SynapseLinkProcessor started.")
    }

    fun stopProcessing() {
        syncJob?.cancel()
        syncJob = null
        visualDataBuffer.clear()
        audioDataBuffer.clear()
        Log.d(TAG, "SynapseLinkProcessor stopped. Buffers cleared.")
    }


    fun onNewFrameData(frameData: CameraFrameAnalyzer.FrameData) {
        if (visualDataBuffer.size >= MAX_BUFFER_SIZE) {
            visualDataBuffer.poll()
        }
        visualDataBuffer.offer(frameData)
        if (processingDetailLevel > 0.7) { // Only log verbose data influx when highly alert
            Log.v(TAG, "Logos (High Detail): Received frame data. Buffer size: ${visualDataBuffer.size}")
        }
    }

    fun onNewAudioData(audioData: AudioBufferAnalyzer.AudioData) {
        if (audioDataBuffer.size >= MAX_BUFFER_SIZE) {
            audioDataBuffer.poll()
        }
        audioDataBuffer.offer(audioData)
        if (processingDetailLevel > 0.7) { // Only log verbose data influx when highly alert
            Log.v(TAG, "Logos (High Detail): Received audio data. Buffer size: ${audioDataBuffer.size}")
        }
    }

    private fun attemptSynchronization() {
        val currentVisual = visualDataBuffer.peek()
        val currentAudio = audioDataBuffer.peek()

        if (currentVisual == null || currentAudio == null) {
            return
        }

        val timeDiff = abs(currentVisual.timestamp - currentAudio.timestamp)

        if (timeDiff <= SYNC_TOLERANCE_MS) {
            val combinedData = BioSignalData(
                timestamp = (currentVisual.timestamp + currentAudio.timestamp) / 2,
                visualSignals = visualDataBuffer.poll(),
                audioSignals = audioDataBuffer.poll()
            )
            if (processingDetailLevel > 0.3) { // Log synchronization info based on detail level
                Log.d(TAG, "Logos (Detail ${String.format("%.2f", processingDetailLevel)}): Synchronized data at ${combinedData.timestamp}. Visual-Audio diff: $timeDiff ms")
            }

            onBioSignalDataReady(combinedData)

            val inferredState = inferCognitiveState(combinedData)
            pathosCore.processLogosState(inferredState)
            val finalInferredState = inferredState.copy(pathosIntuitiveMarker = pathosCore.intuitiveMarker.value)

            onInferredStateReady?.invoke(finalInferredState)

        } else if (currentVisual.timestamp < currentAudio.timestamp - SYNC_TOLERANCE_MS) {
            if (processingDetailLevel > 0.5) { // Log discarding older data when more attentive
                Log.v(TAG, "Logos (Detail ${String.format("%.2f", processingDetailLevel)}): Visual data too old (${timeDiff}ms diff), discarding: ${visualDataBuffer.poll()?.timestamp}")
            } else {
                visualDataBuffer.poll() // Still discard, just don't log verbosely
            }
        } else if (currentAudio.timestamp < currentVisual.timestamp - SYNC_TOLERANCE_MS) {
            if (processingDetailLevel > 0.5) { // Log discarding older data when more attentive
                Log.v(TAG, "Logos (Detail ${String.format("%.2f", processingDetailLevel)}): Audio data too old (${timeDiff}ms diff), discarding: ${audioDataBuffer.poll()?.timestamp}")
            } else {
                audioDataBuffer.poll() // Still discard
            }
        }
    }

    // --- Logos: Inferring Cognitive State (Simplified Example with Pathos Influence) ---
    private fun inferCognitiveState(bioSignalData: BioSignalData): CognitiveState {
        var engagement = "Neutral"
        var arousal = "Calm"
        var confidence = 0.5f

        val visualSignals = bioSignalData.visualSignals
        val audioSignals = bioSignalData.audioSignals

        val pathosAlertness = pathosCore.getSuggestedAlertness()

        // Adjust inference thresholds or confidence based on Pathos's suggestion
        val engagementThreshold = 0.7f + (pathosAlertness * 0.1f)
        val arousalThreshold = 0.05 + (pathosAlertness * 0.02)

        // Visual cues for Engagement/Arousal
        visualSignals?.processedFaces?.firstOrNull()?.let { face ->
            val pitch = face.headEulerAngleX ?: 0f
            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0f

            if (abs(pitch) < 15 && abs(yaw) < 15 && abs(roll) < 15) {
                if (pathosAlertness > 0.7f) engagement = "Highly Engaged" else engagement = "Engaged"
                confidence += 0.1f
            } else {
                engagement = "Disengaged"
                confidence -= 0.1f
            }

            if (leftEyeOpen > engagementThreshold && rightEyeOpen > engagementThreshold) {
                if (engagement == "Engaged" || engagement == "Highly Engaged") {
                    engagement = "Deeply Engaged"
                    confidence += 0.15f
                }
                arousal = "Moderate"
                confidence += 0.05f
            } else if (leftEyeOpen < 0.3f || rightEyeOpen < 0.3f) {
                engagement = "Disengaged"
                arousal = "Calm"
                confidence -= 0.1f
            }
        }

        // Auditory cues for Arousal/Engagement
        audioSignals?.let { audio ->
            if (audio.rms > arousalThreshold) {
                arousal = "Moderate"
                if (engagement.contains("Engaged", true) || pathosAlertness > 0.6f) {
                    arousal = "High"
                }
                confidence += 0.1f
            } else if (audio.rms < 0.01) {
                arousal = "Calm"
                if (engagement.contains("Engaged", true) && pathosAlertness > 0.7f) {
                    engagement = "Deeply Engaged (Quiet)"
                }
                confidence += 0.05f
            }

            if (audio.zeroCrossingRate > 0.2) {
                if (arousal == "Calm" && pathosAlertness < 0.3f) arousal = "Moderate"
                confidence += 0.05f
            }
        }

        confidence = confidence.coerceIn(0f, 1f)

        // Log Logos's detailed inference based on the processingDetailLevel
        if (processingDetailLevel > 0.8) {
            Log.d(TAG, "Logos (High Detail): Inferred - Engagement: $engagement, Arousal: $arousal, Confidence: ${String.format("%.2f", confidence)}")
        } else if (processingDetailLevel > 0.4) {
            Log.d(TAG, "Logos (Medium Detail): Inferred - Engagement: $engagement, Arousal: $arousal")
        } else {
            Log.d(TAG, "Logos (Low Detail): Inferred State Update.")
        }


        return CognitiveState(
            timestamp = bioSignalData.timestamp,
            engagementLevel = engagement,
            arousalLevel = arousal,
            combinedConfidence = confidence,
            pathosIntuitiveMarker = "No Marker Yet" // Will be updated by PathosCore
        )
    }
}



