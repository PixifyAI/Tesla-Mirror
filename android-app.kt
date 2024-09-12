import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

class ScreenMirrorActivity : AppCompatActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var webSocket: WebSocket
    private lateinit var imageReader: ImageReader
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private val gson = Gson()
    private val isStreaming = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_mirror)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        findViewById<Button>(R.id.startMirroringButton).setOnClickListener {
            if (isStreaming.get()) {
                stopScreenCapture()
            } else {
                startScreenCapture()
            }
        }
    }

    private fun startScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }

    private fun stopScreenCapture() {
        isStreaming.set(false)
        mediaProjection?.stop()
        virtualDisplay?.release()
        webSocket.close(1000, "Streaming stopped")
        Toast.makeText(this, "Screen mirroring stopped", Toast.LENGTH_SHORT).show()
        findViewById<Button>(R.id.startMirroringButton).text = "Start Mirroring"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            startVirtualDisplay()
            connectWebSocket()
            isStreaming.set(true)
            findViewById<Button>(R.id.startMirroringButton).text = "Stop Mirroring"
            Toast.makeText(this, "Screen mirroring started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenMirror",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )
    }

    private fun connectWebSocket() {
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url("ws://your-server-url:3001")
            .addHeader("Authorization", "Bearer your-auth-token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                startSendingScreenContent()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e("WebSocket", "Connection failed", t)
                runOnUiThread {
                    Toast.makeText(this@ScreenMirrorActivity, "Connection failed. Retrying...", Toast.LENGTH_SHORT).show()
                }
                Handler(Looper.getMainLooper()).postDelayed({ connectWebSocket() }, 5000)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                handleInteraction(text)
            }
        })
    }

    private fun startSendingScreenContent() {
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isStreaming.get()) {
                val image = imageReader.acquireLatestImage()
                image?.use {
                    val bitmap = imageToBitmap(it)
                    val compressed = compressBitmap(bitmap)
                    val metadata = ScreenMetadata(screenWidth, screenHeight, screenDensity)
                    val payload = ScreenPayload(metadata, compressed)
                    val json = gson.toJson(payload)
                    webSocket.send(json)
                }
                delay(33) // ~30 fps
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return stream.toByteArray()
    }

    private fun handleInteraction(json: String) {
        val interaction = gson.fromJson(json, Interaction::class.java)
        when (interaction.type) {
            "tap" -> performTap(interaction.x, interaction.y)
            "swipe" -> performSwipe(interaction.startX, interaction.startY, interaction.endX, interaction.endY)
            "type" -> performType(interaction.text)
        }
    }

    private fun performTap(x: Float, y: Float) {
        // Implement tap using AccessibilityService or other method
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        // Implement swipe using AccessibilityService or other method
    }

    private fun performType(text: String) {
        // Implement typing using AccessibilityService or other method
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
    }

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }
}

data class ScreenMetadata(val width: Int, val height: Int, val density: Int)
data class ScreenPayload(val metadata: ScreenMetadata, val imageData: ByteArray)
data class Interaction(val type: String, val x: Float, val y: Float, val startX: Float, val startY: Float, val endX: Float, val endY: Float, val text: String)
