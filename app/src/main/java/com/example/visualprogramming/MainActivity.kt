package com.example.visualprogramming

import android.app.Activity
import android.content.Intent
import android.graphics.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {
    companion object {
        private const val MAX_FONT_SIZE = 70F
    }
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    private lateinit var btnOpenCamera: Button
    private lateinit var btnSampleImage: Button
    private lateinit var ivPhoto: ImageView
    var turn = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOpenCamera = findViewById(R.id.btnOpenCamera)
        btnSampleImage = findViewById(R.id.btnSampleImage)
        ivPhoto = findViewById(R.id.ivImage)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleCameraImage(result.data)
            }
        }

//        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        resultLauncher.launch(cameraIntent)


        btnOpenCamera.setOnClickListener {
            //intent to open camera app
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            resultLauncher.launch(cameraIntent)
        }

        btnSampleImage.setOnClickListener {
            if(turn < 3)
                turn = turn + 1
            else turn = 1
            if(turn == 1)
                setViewAndDetect(getSampleImage(R.drawable.smaple_1))
            else if(turn == 2)
                setViewAndDetect(getSampleImage(R.drawable.sample_2))
            else if(turn == 3)
                setViewAndDetect(getSampleImage(R.drawable.sample_3))
        }
    }


    private fun handleCameraImage(intent: Intent?) {
        val bitmap = intent?.extras?.get("data") as Bitmap
        setViewAndDetect(bitmap)
    }

    private fun setViewAndDetect(bitmap: Bitmap) {
        ivPhoto.setImageBitmap(bitmap)
        lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(bitmap) }
    }

    private fun runObjectDetection(bitmap: Bitmap) {
        val image = TensorImage.fromBitmap(bitmap)
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(50)
            .setScoreThreshold(0.3f)
            .build()
        val detector = ObjectDetector.createFromFileAndOptions(
            this, // the application context
            "model.tflite", // must be same as the filename in assets folder
            options
        )
        val results = detector.detect(image)
        val resultToDisplay = results.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Create a data object to display the detection result
            DetectionResult(it.boundingBox, text)
        }
        // Draw the detection result on the bitmap and show it.
        val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)
        runOnUiThread {
            ivPhoto.setImageBitmap(imgWithResult)
        }
        detectProximity(resultToDisplay)
    }

    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 4F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL
            pen.color = Color.YELLOW
            pen.strokeWidth = 1F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }

    private fun detectProximity(detectionResults: List<DetectionResult>) {
        detectionResults.forEach { currentBlock ->
            val currentBlockCenter = BlockCenter(
                currentBlock.boundingBox.right - currentBlock.boundingBox.left,
                currentBlock.boundingBox.top - currentBlock.boundingBox.bottom
            )
            detectionResults.forEach { destinationBlock ->
                if(currentBlock != destinationBlock) {
                    val itCenter = BlockCenter(
                        destinationBlock.boundingBox.right - destinationBlock.boundingBox.left,
                        destinationBlock.boundingBox.top - destinationBlock.boundingBox.bottom
                    )
                    val distance = euclideanDistance(currentBlockCenter, itCenter)
                }
            }
        }
    }

    private fun getSampleImage(drawable: Int): Bitmap {
        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    private fun euclideanDistance(aCenter: BlockCenter, bCenter: BlockCenter): Float {
        return sqrt(
            (aCenter.x - bCenter.x).pow(2) + (aCenter.y - bCenter.y).pow(2)
        )
    }
}

data class DetectionResult(val boundingBox: RectF, val text: String)
data class  BlockCenter(val x: Float, val y: Float)
