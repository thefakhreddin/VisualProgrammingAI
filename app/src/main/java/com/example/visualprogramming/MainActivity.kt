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
import java.util.*
import kotlin.math.abs
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

    private lateinit var mainSequence: MutableList<DetectionResult>
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


        btnOpenCamera.setOnClickListener {
            mainSequence = mutableListOf<DetectionResult>()
            //intent to open camera app
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            resultLauncher.launch(cameraIntent)
        }

        btnSampleImage.setOnClickListener {
            mainSequence = mutableListOf<DetectionResult>()
            if(turn < 6)
                turn = turn + 1
            else turn = 1
            if(turn == 1)
                setViewAndDetect(getSampleImage(R.drawable.sample_4))
            else if(turn == 2)
                setViewAndDetect(getSampleImage(R.drawable.sample_5))
            else if(turn == 3)
                setViewAndDetect(getSampleImage(R.drawable.sample_6))
            else if(turn == 4)
                setViewAndDetect(getSampleImage(R.drawable.smaple_1))
            else if(turn == 5)
                setViewAndDetect(getSampleImage(R.drawable.sample_2))
            else if(turn == 6)
                setViewAndDetect(getSampleImage(R.drawable.sample_3))
        }

        mainSequence = mutableListOf<DetectionResult>()
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
        interpretResult(resultToDisplay)
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
    private fun getSampleImage(drawable: Int): Bitmap {
        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    private fun interpretResult(detectionResults: List<DetectionResult>) {
        val runBlock: DetectionResult? = detectionResults.firstOrNull{it.text.startsWith("run")}
        if(runBlock!=null) {
            appendToSequence(runBlock)
            val firstBlock = validOverlap(runBlock!!,detectionResults, true)
            if(firstBlock!=null) {
                appendToSequence(firstBlock)
                while (true) {
                    val nextBlock = validOverlap(
                        mainSequence.last(),
                        detectionResults,
                        false,
                        mainSequenceLine(runBlock!!, firstBlock),
                        )
                    if(nextBlock!=null)
                        appendToSequence(nextBlock)
                    else break
                }
            }
        }
    }


    private fun mainSequenceLine(runBlock: DetectionResult, firstBlock: DetectionResult): Line {
        val runBlockCenter = BoxCenter(
            (runBlock.boundingBox.top + runBlock.boundingBox.bottom) /2,
            (runBlock.boundingBox.left + runBlock.boundingBox.right) /2,
        )
        val firstBlockCenter = BoxCenter(
            (firstBlock.boundingBox.top + firstBlock.boundingBox.bottom) /2,
            (firstBlock.boundingBox.left + firstBlock.boundingBox.right) /2,
        )
        val slope = (firstBlockCenter.y - runBlockCenter.y)/(firstBlockCenter.x - runBlockCenter.x)
        return Line(
            runBlockCenter,
            slope
        )
    }

    private fun closeToMainLine(line: Line, block: DetectionResult): Boolean {
        val blockCenter = BoxCenter(
            (block.boundingBox.top + block.boundingBox.bottom) /2,
            (block.boundingBox.left + block.boundingBox.right) /2,
        )
        val a = line.slope
        val b = -1
        val c = -line.slope*line.coordinate.x+line.coordinate.y

        val dist = abs(a*blockCenter.x + b*blockCenter.y + c) / sqrt(a.toDouble().pow(2.0) + b.toDouble().pow(2.0))
        if(dist < 200) return true
        return false
    }
    private fun validOverlap(
        block: DetectionResult,
        blockList: List<DetectionResult>,
        isFirst: Boolean = false,
        line: Line? = null): DetectionResult? {
        val currentRect = Rect(block.boundingBox.left,
                             block.boundingBox.right,
                             block.boundingBox.top,
                             block.boundingBox.bottom)
        blockList.forEach{
            val rect = Rect(it.boundingBox.left,
                            it.boundingBox.right,
                            it.boundingBox.top,
                            it.boundingBox.bottom)

            if(currentRect.left < rect.right &&
                currentRect.right > rect.left &&
                currentRect.bottom < rect.top &&
                currentRect.top > rect.bottom) {
                if(!mainSequence.contains(it)) {
                    if (isFirst) return it
                    else {
                        if(line!=null)
                            if(closeToMainLine(line, it)){
                                return it
                            }
                    }
                }
            }
        }
        return null
    }

    private fun checkOverlapping(currentBlock: DetectionResult, destinationBlock: DetectionResult): Boolean{
        val rect1 = Rect(currentBlock.boundingBox.left,
                         currentBlock.boundingBox.right,
                         currentBlock.boundingBox.top,
                         currentBlock.boundingBox.bottom)
        val rect2 = Rect(destinationBlock.boundingBox.left,
                         destinationBlock.boundingBox.right,
                         destinationBlock.boundingBox.top,
                         destinationBlock.boundingBox.bottom)

        if(rect1.left < rect2.right &&
                rect1.right > rect2.left &&
                rect1.bottom < rect2.top &&
                rect1.top > rect2.bottom) {
            return true
        }
        return  false
    }


    private fun blockType(block: DetectionResult): String {
        val type = block.text
        if(type == "0"
        || type == "1"
        || type == "2"
        || type == "3"
        || type == "4"
        || type == "5"
        || type == "6"
        || type == "7"
        || type == "8"
        || type == "9")
            return "numeric"
        return "function"
    }

    private fun appendToSequence(newBlock: DetectionResult?) {
        if (newBlock != null) {
            mainSequence.add(newBlock)
        }
    }
}

data class DetectionResult(val boundingBox: RectF, val text: String)
data class Rect(val left: Float, val right: Float, val bottom: Float, val top: Float)
data class Line(val coordinate: BoxCenter, val slope: Float)
data class BoxCenter(val x: Float, val y: Float)
