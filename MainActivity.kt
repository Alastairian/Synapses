package com.synapselink.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synapselink.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.nio.ByteBuffer // Already there, just for emphasis
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null

    // IAI-IPS Component Instances
    private lateinit var cameraFrameAnalyzer: CameraFrameAnalyzer
    private lateinit var audioBufferAnalyzer: AudioBufferAnalyzer

    companion object {
        private const val TAG = "SynapseLinkApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        // Audio configuration for raw biological signal capture
        const val AUDIO_SAMPLE_RATE = 44100 // Hz, standard for high quality audio
        const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE_FACTOR = 2 // Factor for AudioRecord buffer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize our IAI-IPS Logos core analysis components
        // These listeners will be the next step in our processing pipeline
        cameraFrameAnalyzer = CameraFrameAnalyzer { frameData ->
            // This callback is where Logos will receive processed FrameData.
            // Here, we would pass 'frameData' to the next stage of Logos's
            // analytical pipeline for deeper feature extraction (e.g., face detection,
            // landmark tracking, micro-expression analysis).
            Log.d(TAG, "Logos received camera frame for deeper analysis: " +
                    "Size=${frameData.width}x${frameData.height}, " +
                    "Buffer_len=${frameData.pixelBuffer.remaining()}")
            // Pathos might also receive a signal here about visual quality and stability.
        }

        audioBufferAnalyzer = AudioBufferAnalyzer { audioData ->
            // This callback is where Logos will receive processed AudioData.
            // Here, we would pass 'audioData' to the next stage of Logos's
            // analytical pipeline for deeper feature extraction (e.g., frequency analysis,
            // vocal tremor detection, prosody analysis).
            Log.d(TAG, "Logos received audio buffer for deeper analysis: " +
                    "Samples=${audioData.pcmBuffer.size}, " +
                    "Rate=${audioData.sampleRate}Hz")
            // Pathos might also receive a signal here about audio clarity and potential patterns.
        }

        // Request camera and audio permissions
        if (allPermissionsGranted()) {
            startSynapseLinkSensors()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.toggleProcessingButton.setOnClickListener {
            if (isAudioRecording) {
                stopProcessing()
            } else {
                startProcessing()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSynapseLinkSensors()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSynapseLinkSensors() {
        startCamera()
        updateStatus("Camera and Microphone Initialized. Ready for Processing.")
    }

    private fun startProcessing() {
        viewBinding.toggleProcessingButton.text = "Stop Processing"
        startAudioRecording()
        // Bind the ImageAnalysis use case to the camera lifecycle to activate frame processing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                // Ensure imageAnalysis is bound
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis // Only imageAnalysis for now to save resources
                )
                Log.d(TAG, "Image analysis use case bound for processing.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))
        updateStatus("Processing biological signals...")
    }

    private fun stopProcessing() {
        viewBinding.toggleProcessingButton.text = "Start Processing"
        stopAudioRecording()
        // Unbind ImageAnalysis to stop frame processing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbind(imageAnalysis) // Only unbind imageAnalysis
                Log.d(TAG, "Image analysis use case unbound. Processing stopped.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to unbind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))
        updateStatus("Processing stopped. Ready to restart.")
    }

    // --- Camera Initialization and Setup (Logos: Data Acquisition) ---
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview (always active to show user what's being captured)
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Image Analysis (Crucial for real-time biological signal extraction)
            // This is where Logos will perform its 'Deeper Learning'
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST_FROM_PIPELINE)
                .setTargetResolution(android.util.Size(1280, 720)) // High resolution for detailed analysis
                .build()
                .also {
                    // Pass the analyzer created earlier
                    it.setAnalyzer(cameraExecutor, cameraFrameAnalyzer)
                }

            // Select front camera as a default for facial signals
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind all use cases initially to ensure clean state
                cameraProvider.unbindAll()

                // Bind preview immediately.
                // ImageAnalysis will be bound/unbound with toggleProcessingButton.
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

                Log.d(TAG, "Camera initialized successfully with preview.")

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateStatus("Camera initialization failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --- Audio Recording Setup (Logos: Data Acquisition) ---
    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * AUDIO_BUFFER_SIZE_FACTOR

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord: Invalid buffer size or error occurred.")
            updateStatus("Audio recording setup failed: Invalid buffer size.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord: Initialization failed.")
                updateStatus("Audio recording initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAudioRecording = true
            Log.d(TAG, "Audio recording started with buffer size: $bufferSize bytes")
            updateStatus("Audio recording active...")

            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize / 2)
                while (isActive && isAudioRecording) {
                    val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (shortsRead > 0) {
                        // Pass the raw audio data to our AudioBufferAnalyzer
                        audioBufferAnalyzer.analyze(audioBuffer, shortsRead)
                    }
                }
                Log.d(TAG, "Audio recording job finished.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission not granted.", e)
            updateStatus("Audio recording failed: Permission denied.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            updateStatus("Audio recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel()
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Audio recording stopped.")
        updateStatus("Audio recording stopped.")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            viewBinding.statusTextView.text = message
            Log.i(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAudioRecording()
        // Unbind all camera use cases explicitly if activity is destroyed
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }
}
package com.synapselink.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synapselink.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null

    // IAI-IPS Component Instances
    private lateinit var cameraFrameAnalyzer: CameraFrameAnalyzer
    private lateinit var audioBufferAnalyzer: AudioBufferAnalyzer
    private lateinit var synapseLinkProcessor: SynapseLinkProcessor // New central processor

    companion object {
        private const val TAG = "SynapseLinkApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        // Audio configuration for raw biological signal capture
        const val AUDIO_SAMPLE_RATE = 44100 // Hz, standard for high quality audio
        const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE_FACTOR = 2 // Factor for AudioRecord buffer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize our IAI-IPS Logos core analysis components
        // The listeners now direct data to the SynapseLinkProcessor
        cameraFrameAnalyzer = CameraFrameAnalyzer { frameData ->
            synapseLinkProcessor.onNewFrameData(frameData)
            // Log.d(TAG, "Logos received camera frame for deeper analysis. Passing to processor.")
        }

        audioBufferAnalyzer = AudioBufferAnalyzer { audioData ->
            synapseLinkProcessor.onNewAudioData(audioData)
            // Log.d(TAG, "Logos received audio buffer for deeper analysis. Passing to processor.")
        }

        // Initialize the central SynapseLinkProcessor.
        // The callback here is where Logos will get its synchronized multi-modal data.
        synapseLinkProcessor = SynapseLinkProcessor { bioSignalData ->
            // This callback receives the synchronized BioSignalData.
            // This is the input for the next layer of Logos's analytical pipeline,
            // where higher-level interpretation (e.g., emotional state, cognitive load) occurs.
            // It's also the conceptual hand-off point for Pathos to perform its intuitive assessment.
            Log.d(TAG, "IAI-IPS: Received synchronized BioSignalData at ${bioSignalData.timestamp}. " +
                    "Visual available: ${bioSignalData.visualSignals != null}, " +
                    "Audio available: ${bioSignalData.audioSignals != null}")

            // Pathos might generate a "coherent data stream" intuitive marker here,
            // or if a signal is missing, a "partial data" marker.

            // Example: Accessing combined data
            // bioSignalData.visualSignals?.processedFaces?.firstOrNull()?.let { face ->
            //    Log.i(TAG, "First synchronized face yaw: ${face.headEulerAngleY}")
            // }
            // bioSignalData.audioSignals?.let { audio ->
            //    Log.i(TAG, "Synchronized audio RMS: ${audio.rms}")
            // }
        }


        // Request camera and audio permissions
        if (allPermissionsGranted()) {
            startSynapseLinkSensors()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.toggleProcessingButton.setOnClickListener {
            if (isAudioRecording) {
                stopProcessing()
            } else {
                startProcessing()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSynapseLinkSensors()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSynapseLinkSensors() {
        startCamera()
        updateStatus("Camera and Microphone Initialized. Ready for Processing.")
    }

    private fun startProcessing() {
        viewBinding.toggleProcessingButton.text = "Stop Processing"
        startAudioRecording()
        // Bind the ImageAnalysis use case to the camera lifecycle to activate frame processing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis
                )
                Log.d(TAG, "Image analysis use case bound for processing.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.startProcessing() // Start the central processor
        updateStatus("Processing biological signals...")
    }

    private fun stopProcessing() {
        viewBinding.toggleProcessingButton.text = "Start Processing"
        stopAudioRecording()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbind(imageAnalysis)
                Log.d(TAG, "Image analysis use case unbound. Processing stopped.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to unbind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.stopProcessing() // Stop the central processor
        updateStatus("Processing stopped. Ready to restart.")
    }

    // --- Camera Initialization and Setup ---
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST_FROM_PIPELINE)
                .setTargetResolution(android.util.Size(1280, 720))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, cameraFrameAnalyzer)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
                Log.d(TAG, "Camera initialized successfully with preview.")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateStatus("Camera initialization failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --- Audio Recording Setup ---
    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT
        ) * AUDIO_BUFFER_SIZE_FACTOR

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord: Invalid buffer size or error occurred.")
            updateStatus("Audio recording setup failed: Invalid buffer size.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord: Initialization failed.")
                updateStatus("Audio recording initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAudioRecording = true
            Log.d(TAG, "Audio recording started with buffer size: $bufferSize bytes")
            updateStatus("Audio recording active...")

            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize / 2)
                while (isActive && isAudioRecording) {
                    val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (shortsRead > 0) {
                        audioBufferAnalyzer.analyze(audioBuffer, shortsRead)
                    }
                }
                Log.d(TAG, "Audio recording job finished.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission not granted.", e)
            updateStatus("Audio recording failed: Permission denied.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            updateStatus("Audio recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel()
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Audio recording stopped.")
        updateStatus("Audio recording stopped.")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            viewBinding.statusTextView.text = message
            Log.i(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAudioRecording()
        synapseLinkProcessor.stopProcessing() // Ensure processor resources are released
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }
}
package com.synapselink.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synapselink.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null

    // IAI-IPS Component Instances
    private lateinit var cameraFrameAnalyzer: CameraFrameAnalyzer
    private lateinit var audioBufferAnalyzer: AudioBufferAnalyzer
    private lateinit var synapseLinkProcessor: SynapseLinkProcessor // New central processor

    companion object {
        private const val TAG = "SynapseLinkApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        // Audio configuration for raw biological signal capture
        const val AUDIO_SAMPLE_RATE = 44100 // Hz, standard for high quality audio
        const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE_FACTOR = 2 // Factor for AudioRecord buffer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize our IAI-IPS Logos core analysis components
        // The listeners now direct data to the SynapseLinkProcessor
        cameraFrameAnalyzer = CameraFrameAnalyzer { frameData ->
            synapseLinkProcessor.onNewFrameData(frameData)
        }

        audioBufferAnalyzer = AudioBufferAnalyzer { audioData ->
            synapseLinkProcessor.onNewAudioData(audioData)
        }

        // Initialize the central SynapseLinkProcessor.
        // The callback here is where Logos will get its synchronized multi-modal data.
        synapseLinkProcessor = SynapseLinkProcessor { bioSignalData ->
            // This is where BioSignalData is received. It's then passed for higher-level inference within processor.
            // Log.d(TAG, "IAI-IPS: Received synchronized BioSignalData at ${bioSignalData.timestamp}.")
        }

        // Set the listener for inferred cognitive states
        synapseLinkProcessor.setOnInferredStateReadyListener { inferredState ->
            // This callback receives the CognitiveState from Logos's higher-level interpretation.
            // This is the output that would be displayed to the user or used by other systems.
            Log.i(TAG, "IAI-IPS Inferred State: " +
                    "Engagement: ${inferredState.engagementLevel}, " +
                    "Arousal: ${inferredState.arousalLevel}, " +
                    "Confidence: ${String.format("%.2f", inferredState.combinedConfidence)}")
            updateStatus("State: ${inferredState.engagementLevel}, ${inferredState.arousalLevel}")

            // This is the point where the IAI-IPS would output its "understanding"
            // to a "common readable medium" or external system.
        }

        // Request camera and audio permissions
        if (allPermissionsGranted()) {
            startSynapseLinkSensors()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.toggleProcessingButton.setOnClickListener {
            if (isAudioRecording) {
                stopProcessing()
            } else {
                startProcessing()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSynapseLinkSensors()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSynapseLinkSensors() {
        startCamera()
        updateStatus("Camera and Microphone Initialized. Ready for Processing.")
    }

    private fun startProcessing() {
        viewBinding.toggleProcessingButton.text = "Stop Processing"
        startAudioRecording()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis
                )
                Log.d(TAG, "Image analysis use case bound for processing.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.startProcessing() // Start the central processor
        updateStatus("Processing biological signals...")
    }

    private fun stopProcessing() {
        viewBinding.toggleProcessingButton.text = "Start Processing"
        stopAudioRecording()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbind(imageAnalysis)
                Log.d(TAG, "Image analysis use case unbound. Processing stopped.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to unbind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.stopProcessing() // Stop the central processor
        updateStatus("Processing stopped. Ready to restart.")
    }

    // --- Camera Initialization and Setup ---
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST_FROM_PIPELINE)
                .setTargetResolution(android.util.Size(1280, 720))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, cameraFrameAnalyzer)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
                Log.d(TAG, "Camera initialized successfully with preview.")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateStatus("Camera initialization failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --- Audio Recording Setup ---
    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT
        ) * AUDIO_BUFFER_SIZE_FACTOR

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord: Invalid buffer size or error occurred.")
            updateStatus("Audio recording setup failed: Invalid buffer size.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord: Initialization failed.")
                updateStatus("Audio recording initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAudioRecording = true
            Log.d(TAG, "Audio recording started with buffer size: $bufferSize bytes")
            updateStatus("Audio recording active...")

            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize / 2)
                while (isActive && isAudioRecording) {
                    val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (shortsRead > 0) {
                        audioBufferAnalyzer.analyze(audioBuffer, shortsRead)
                    }
                }
                Log.d(TAG, "Audio recording job finished.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission not granted.", e)
            updateStatus("Audio recording failed: Permission denied.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            updateStatus("Audio recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel()
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Audio recording stopped.")
        updateStatus("Audio recording stopped.")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            viewBinding.statusTextView.text = message
            Log.i(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAudioRecording()
        synapseLinkProcessor.stopProcessing()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }
}
package com.synapselink.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synapselink.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null

    // IAI-IPS Component Instances
    private lateinit var cameraFrameAnalyzer: CameraFrameAnalyzer
    private lateinit var audioBufferAnalyzer: AudioBufferAnalyzer
    private lateinit var synapseLinkProcessor: SynapseLinkProcessor

    companion object {
        private const val TAG = "SynapseLinkApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE_FACTOR = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraFrameAnalyzer = CameraFrameAnalyzer { frameData ->
            synapseLinkProcessor.onNewFrameData(frameData)
        }

        audioBufferAnalyzer = AudioBufferAnalyzer { audioData ->
            synapseLinkProcessor.onNewAudioData(audioData)
        }

        synapseLinkProcessor = SynapseLinkProcessor { bioSignalData ->
            // This is where BioSignalData is received. It's then passed for higher-level inference within processor.
        }

        // Set the listener for inferred cognitive states from Logos (with Pathos marker)
        synapseLinkProcessor.setOnInferredStateReadyListener { inferredState ->
            Log.i(TAG, "IAI-IPS Inferred State: " +
                    "Engagement: ${inferredState.engagementLevel}, " +
                    "Arousal: ${inferredState.arousalLevel}, " +
                    "Confidence: ${String.format("%.2f", inferredState.combinedConfidence)}, " +
                    "Pathos Marker: '${inferredState.pathosIntuitiveMarker}'") // Display Pathos's marker

            updateStatus("L: ${inferredState.engagementLevel}, ${inferredState.arousalLevel}\nP: '${inferredState.pathosIntuitiveMarker}'")
        }

        // Collect Pathos's alertness level and update UI (Demonstrates Logos receiving Pathos's guidance)
        lifecycleScope.launchWhenStarted {
            synapseLinkProcessor.getPathosCore().currentAlertnessLevel.collectLatest { alertness ->
                viewBinding.pathosAlertnessTextView.text = "Pathos Alertness: ${String.format("%.2f", alertness)}"
            }
        }
        // Collect Pathos's intuitive marker and update UI
        lifecycleScope.launchWhenStarted {
            synapseLinkProcessor.getPathosCore().intuitiveMarker.collectLatest { marker ->
                viewBinding.pathosMarkerTextView.text = "Pathos Marker: '${marker}'"
            }
        }


        // Request camera and audio permissions
        if (allPermissionsGranted()) {
            startSynapseLinkSensors()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.toggleProcessingButton.setOnClickListener {
            if (isAudioRecording) {
                stopProcessing()
            } else {
                startProcessing()
            }
        }

        // --- Simulate External Crisis Alert Button ---
        viewBinding.crisisButton.setOnClickListener {
            synapseLinkProcessor.getPathosCore().triggerCrisisAlert() // Pathos reacts to external stimulus
            Toast.makeText(this, "Crisis Alert Triggered by Pathos!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSynapseLinkSensors()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSynapseLinkSensors() {
        startCamera()
        updateStatus("Camera and Microphone Initialized. Ready for Processing.")
    }

    private fun startProcessing() {
        viewBinding.toggleProcessingButton.text = "Stop Processing"
        startAudioRecording()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis
                )
                Log.d(TAG, "Image analysis use case bound for processing.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.startProcessing()
        updateStatus("Processing biological signals...")
    }

    private fun stopProcessing() {
        viewBinding.toggleProcessingButton.text = "Start Processing"
        stopAudioRecording()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbind(imageAnalysis)
                Log.d(TAG, "Image analysis use case unbound. Processing stopped.")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to unbind image analysis use case.", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        synapseLinkProcessor.stopProcessing()
        updateStatus("Processing stopped. Ready to restart.")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST_FROM_PIPELINE)
                .setTargetResolution(android.util.Size(1280, 720))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, cameraFrameAnalyzer)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
                Log.d(TAG, "Camera initialized successfully with preview.")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateStatus("Camera initialization failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT
        ) * AUDIO_BUFFER_SIZE_FACTOR

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord: Invalid buffer size or error occurred.")
            updateStatus("Audio recording setup failed: Invalid buffer size.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord: Initialization failed.")
                updateStatus("Audio recording initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAudioRecording = true
            Log.d(TAG, "Audio recording started with buffer size: $bufferSize bytes")
            updateStatus("Audio recording active...")

            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize / 2)
                while (isActive && isAudioRecording) {
                    val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (shortsRead > 0) {
                        audioBufferAnalyzer.analyze(audioBuffer, shortsRead)
                    }
                }
                Log.d(TAG, "Audio recording job finished.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission not granted.", e)
            updateStatus("Audio recording failed: Permission denied.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            updateStatus("Audio recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel()
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Audio recording stopped.")
        updateStatus("Audio recording stopped.")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            viewBinding.statusTextView.text = message
            // viewBinding.pathosMarkerTextView.text = message // This should be updated separately
            Log.i(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAudioRecording()
        synapseLinkProcessor.stopProcessing()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }
}



