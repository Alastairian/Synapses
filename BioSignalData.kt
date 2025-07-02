package com.synapselink.app

// This data class represents a synchronized snapshot of biological signals
// derived from both visual and auditory inputs at a given timestamp.
// It is the unified output of Logos's initial multi-modal 'Deeper Learning'.
data class BioSignalData(
    val timestamp: Long,
    val visualSignals: CameraFrameAnalyzer.FrameData?, // The processed camera frame data
    val audioSignals: AudioBufferAnalyzer.AudioData?    // The processed audio buffer data
    // Future additions here will include higher-level interpretations,
    // e.g., inferred emotional state, cognitive load, attention level.
)
