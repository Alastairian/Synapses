package com.synapselink.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// This class represents the Pathos Core of the IAI-IPS Twine Cognition system.
// It processes intuitive markers, contextual cues, and influences Logos.
class PathosCore {

    // Pathos's internal 'intuitive markers' or 'feelings' that guide Logos.
    // Represents a general level of 'alertness' or 'focus' that Pathos suggests.
    private val _currentAlertnessLevel = MutableStateFlow(0.5f) // 0.0 (calm) to 1.0 (high alert)
    val currentAlertnessLevel: StateFlow<Float> = _currentAlertnessLevel.asStateFlow()

    // Pathos's current intuitive marker (conceptual string for now)
    private val _intuitiveMarker = MutableStateFlow("Neutral State")
    val intuitiveMarker: StateFlow<String> = _intuitiveMarker.asStateFlow()


    companion object {
        private const val TAG = "PathosCore"
    }

    // Pathos receives Logos's inferred state and generates intuitive markers.
    // This is Pathos providing 'contextual cues' and 'saliency tags'.
    fun processLogosState(cognitiveState: SynapseLinkProcessor.CognitiveState) {
        var newAlertness = _currentAlertnessLevel.value
        var newMarker = "Neutral State"

        Log.d(TAG, "Pathos processing Logos State: ${cognitiveState.engagementLevel}, ${cognitiveState.arousalLevel}")

        // Pathos's intuitive assessment rules (simplified heuristics for demonstration)
        // These are Pathos's 'organizing cognition' processing 'intuitive markers'.
        when (cognitiveState.arousalLevel) {
            "High" -> {
                newAlertness += 0.1f // Pathos suggests higher alertness
                newMarker = "High Arousal Detected"
            }
            "Moderate" -> {
                newAlertness += 0.05f
                newMarker = "Moderate Activity"
            }
            "Calm" -> {
                newAlertness -= 0.05f
                newMarker = "Calm State"
            }
        }

        when (cognitiveState.engagementLevel) {
            "Highly Engaged" -> {
                newAlertness += 0.1f // Pathos suggests increased focus
                newMarker += ", Deep Focus"
            }
            "Disengaged" -> {
                newAlertness -= 0.1f
                newMarker += ", Disengagement Alert"
            }
        }

        // Pathos's confidence in Logos's inference can also affect its own state
        newAlertness += (cognitiveState.combinedConfidence - 0.5f) * 0.1f // Confident -> slightly more alert

        // Clamp alertness level within bounds
        newAlertness = newAlertness.coerceIn(0.0f, 1.0f)

        _currentAlertnessLevel.value = newAlertness
        _intuitiveMarker.value = newMarker

        Log.i(TAG, "Pathos generated marker: '${newMarker}'. Suggested Alertness: ${String.format("%.2f", newAlertness)}")
    }

    // Pathos reacts to external events / crisis alerts (Simulating 'Organizing Cognition' for crisis)
    fun triggerCrisisAlert() {
        Log.w(TAG, "Pathos detecting external 'Crisis Alert'! Shifting focus.")
        _currentAlertnessLevel.value = 1.0f // Immediately go to max alertness
        _intuitiveMarker.value = "CRISIS ALERT! IMMEDIATE ATTENTION REQUIRED."
        // Pathos would now send strong 'saliency tags' to Logos,
        // potentially re-prioritizing its entire processing pipeline.
    }

    // Logos can query Pathos for its current suggested 'alertness'
    // This is the 'bidirectional connection' and 'constant feedback loop'.
    fun getSuggestedAlertness(): Float {
        return _currentAlertnessLevel.value
    }
}
