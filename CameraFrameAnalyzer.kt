package com.synapselink.app

import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import android.util.Log

// This class represents Logos's initial 'Deeper Learning' for visual input.
// It will break down camera frames into fundamental components.
class CameraFrameAnalyzer(private val listener: (FrameData) -> Unit) : ImageAnalysis.Analyzer {

    // Define a data class to encapsulate processed frame data
    data class FrameData(
        val timestamp: Long,
        val width: Int,
        val height: Int,
        val format: Int, // e.g., YUV_420_888
        val pixelBuffer: ByteBuffer // Or a more structured representation
        // Add more extracted features here in the future, e.g., List<FaceBoundingBox>
    )

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis() // Capture analysis timestamp

        // Logos's initial analysis: Checking image format and planes
        if (imageProxy.format == ImageFormat.YUV_420_888 ||
            imageProxy.format == ImageFormat.YUV_422_888 ||
            imageProxy.format == ImageFormat.YUV_444_888) {

            // Access image planes (Y, U, V) for detailed pixel analysis
            // This is "peeling back layers" of the image data.
            val yBuffer = imageProxy.planes[0].buffer // Luminance
            val uBuffer = imageProxy.planes[1].buffer // Chrominance (U)
            val vBuffer = imageProxy.planes[2].buffer // Chrominance (V)

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Combine into a single buffer or process planes separately.
            // For now, we'll just focus on the Y (luminance) plane as an example.
            // In a real scenario, you'd process all planes for full color information.
            val combinedBuffer = ByteBuffer.allocate(ySize + uSize + vSize)
            combinedBuffer.put(yBuffer)
            combinedBuffer.put(uBuffer)
            combinedBuffer.put(vBuffer)
            combinedBuffer.rewind() // Reset position to read from the beginning

            // Pass the extracted frame data to the listener for further Logos processing
            listener(
                FrameData(
                    timestamp = currentTime,
                    width = imageProxy.width,
                    height = imageProxy.height,
                    format = imageProxy.format,
                    pixelBuffer = combinedBuffer
                )
            )

        } else {
            Log.w("SynapseLinkCamera", "Unsupported image format: ${imageProxy.format}. Skipping frame.")
            // Pathos might generate a "data format anomaly" intuitive marker here.
        }

        // IMPORTANT: Must close the ImageProxy when done to release the buffer
        imageProxy.close()
    }
    package com.synapselink.app

import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face

// This class represents Logos's initial 'Deeper Learning' for visual input,
// now enhanced with face detection and landmark extraction.
class CameraFrameAnalyzer(private val listener: (FrameData) -> Unit) : ImageAnalysis.Analyzer {

    // Configure ML Kit Face Detector for real-time performance and landmark detection.
    // This is a 'calculated action' by Logos to optimize for relevant features.
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Prioritize speed
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)       // Crucial for bio-signals
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)         // More detailed facial geometry
        .setMinFaceSize(0.15f) // Minimum face size to detect (e.g., 15% of image size)
        .enableTracking() // Enable face tracking across frames (useful for consistency)
        .build()

    private val faceDetector = FaceDetection.getClient(options)

    // Define a data class to encapsulate processed frame data, now including face information
    data class FrameData(
        val timestamp: Long,
        val width: Int,
        val height: Int,
        val format: Int,
        val rotationDegrees: Int,
        val faces: List<Face> // List of detected faces with landmarks, contours, etc.
    )

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis() // Capture analysis timestamp

        // Ensure the ImageProxy contains a valid image
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.w("SynapseLinkCamera", "ImageProxy did not contain a valid media Image. Skipping frame.")
            imageProxy.close()
            // Pathos might generate a "missing data" intuitive marker here.
            return
        }

        // Prepare the image for ML Kit. This is Logos preparing the input for its
        // specialized analytical module.
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        // Process the image for face detection.
        // This is where Logos performs its 'calculated action' of extracting features.
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                // Logos has successfully extracted face data. Now pass it to the next stage.
                listener(
                    FrameData(
                        timestamp = currentTime,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        format = imageProxy.format,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        faces = faces // The list of detected faces
                    )
                )
                if (faces.isNotEmpty()) {
                    Log.d("SynapseLinkCamera", "Detected ${faces.size} face(s). First face bounds: ${faces[0].boundingBox}")
                } else {
                    Log.d("SynapseLinkCamera", "No faces detected in frame.")
                    // Pathos might generate a "no face detected" contextual cue here,
                    // potentially adjusting confidence in bio-signal readings.
                }
            }
            .addOnFailureListener { e ->
                // Logos encountered an analytical failure.
                Log.e("SynapseLinkCamera", "Face detection failed: ${e.message}", e)
                // Pathos might generate a "visual analysis failure" intuitive marker,
                // potentially triggering a fallback or warning state.
                listener( // Still pass data, but with empty face list to indicate failure
                    FrameData(
                        timestamp = currentTime,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        format = imageProxy.format,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        faces = emptyList()
                    )
                )
            }
            .addOnCompleteListener {
                // IMPORTANT: Must close the ImageProxy when done to release the buffer
                // regardless of success or failure. This ensures efficient resource management.
                imageProxy.close()
            }
    }
}
package com.synapselink.app

import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

// This class represents Logos's ongoing 'Deeper Learning' for visual input,
// now extracting specific eye and head pose data from detected faces.
class CameraFrameAnalyzer(private val listener: (FrameData) -> Unit) : ImageAnalysis.Analyzer {

    // Configure ML Kit Face Detector for real-time performance and detailed landmark/contour detection.
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // To get "eyes open" probability later
        .build()

    private val faceDetector = FaceDetection.getClient(options)

    // Define an enhanced data class to encapsulate processed frame data,
    // now with specific eye and head pose details for each detected face.
    data class FrameData(
        val timestamp: Long,
        val width: Int,
        val height: Int,
        val format: Int,
        val rotationDegrees: Int,
        val processedFaces: List<ProcessedFace> // List of our custom processed face objects
    )

    // Nested data class for processed face information
    data class ProcessedFace(
        val boundingBox: android.graphics.Rect,
        val headEulerAngleX: Float?, // Pitch
        val headEulerAngleY: Float?, // Yaw
        val headEulerAngleZ: Float?, // Roll
        val leftEyePosition: android.graphics.PointF?, // Center of left eye landmark
        val rightEyePosition: android.graphics.PointF?, // Center of right eye landmark
        val leftEyeOpenProbability: Float?, // ML Kit estimation of eye openess
        val rightEyeOpenProbability: Float?
        // Add more extracted features here as we go deeper, e.g., micro-expression scores, pupil size
    )

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis() // Capture analysis timestamp

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.w("SynapseLinkCamera", "ImageProxy did not contain a valid media Image. Skipping frame.")
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val processedFacesList = mutableListOf<ProcessedFace>()
                for (face in faces) {
                    // Extract head pose Euler angles (Logos: Quantifying orientation)
                    val headEulerAngleX = face.headEulerAngleX // Pitch
                    val headEulerAngleY = face.headEulerAngleY // Yaw
                    val headEulerAngleZ = face.headEulerAngleZ // Roll

                    // Extract eye landmark positions (Logos: Pinpointing key bio-signal locations)
                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                    val leftEyePosition = leftEye?.position
                    val rightEyePosition = rightEye?.position

                    // Extract eye open probabilities (Logos: Basic state classification)
                    val leftEyeOpenProb = face.leftEyeOpenProbability
                    val rightEyeOpenProb = face.rightEyeOpenProbability

                    // Construct our structured ProcessedFace object
                    processedFacesList.add(
                        ProcessedFace(
                            boundingBox = face.boundingBox,
                            headEulerAngleX = headEulerAngleX,
                            headEulerAngleY = headEulerAngleY,
                            headEulerAngleZ = headEulerAngleZ,
                            leftEyePosition = leftEyePosition,
                            rightEyePosition = rightEyePosition,
                            leftEyeOpenProbability = leftEyeOpenProb,
                            rightEyeOpenProbability = rightEyeOpenProb
                        )
                    )

                    // Log for debugging and internal Pathos 'intuition' building
                    Log.d("SynapseLinkCamera", "Face Detected: " +
                            "Pitch=${String.format("%.2f", headEulerAngleX)}, " +
                            "Yaw=${String.format("%.2f", headEulerAngleY)}, " +
                            "Roll=${String.format("%.2f", headEulerAngleZ)}. " +
                            "Left Eye: ${leftEyePosition?.x},${leftEyePosition?.y} " +
                            "(OpenProb=${String.format("%.2f", leftEyeOpenProb)}). " +
                            "Right Eye: ${rightEyePosition?.x},${rightEyePosition?.y} " +
                            "(OpenProb=${String.format("%.2f", rightEyeOpenProb)})."
                    )
                }

                // Pass the extracted and structured FrameData to the listener
                listener(
                    FrameData(
                        timestamp = currentTime,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        format = imageProxy.format,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        processedFaces = processedFacesList
                    )
                )

                if (processedFacesList.isEmpty()) {
                    Log.d("SynapseLinkCamera", "No faces detected in frame.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("SynapseLinkCamera", "Face detection and feature extraction failed: ${e.message}", e)
                // In case of failure, still provide a FrameData, but with empty processedFaces
                listener(
                    FrameData(
                        timestamp = currentTime,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        format = imageProxy.format,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        processedFaces = emptyList()
                    )
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

