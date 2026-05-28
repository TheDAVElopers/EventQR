package com.thedavelopers.eventqr.features.staff

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.thedavelopers.eventqr.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
open class StaffCameraScannerActivity : AppCompatActivity(), SurfaceHolder.Callback, android.hardware.Camera.PreviewCallback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private var camera: android.hardware.Camera? = null
    private val reader = MultiFormatReader()
    private val decoding = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_camera_scanner)

        surfaceView = findViewById(R.id.surfaceCameraPreview)
        statusText = findViewById(R.id.txtCameraStatus)
        surfaceView.holder.addCallback(this)

        findViewById<android.view.View>(R.id.btnCloseCamera).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (surfaceView.holder.surface.isValid) {
            startCameraPreview(surfaceView.holder)
        }
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startCameraPreview(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        releaseCamera()
        startCameraPreview(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseCamera()
    }

    override fun onPreviewFrame(data: ByteArray?, camera: android.hardware.Camera?) {
        if (data == null || camera == null || !decoding.compareAndSet(false, true)) {
            return
        }
        val previewSize = camera.parameters.previewSize
        val width = previewSize.width
        val height = previewSize.height

        executor.execute {
            val decoded = decodeFrame(data, width, height)
            runOnUiThread {
                if (!decoded.isNullOrBlank()) {
                    val payload = android.content.Intent().apply {
                        putExtra(StaffScreenExtras.EXTRA_QR_VALUE, decoded)
                    }
                    setResult(RESULT_OK, payload)
                    finish()
                }
                decoding.set(false)
            }
        }
    }

    private fun startCameraPreview(holder: SurfaceHolder) {
        if (camera != null) {
            return
        }
        statusText.text = "Point camera at attendee QR"
        runCatching {
            camera = android.hardware.Camera.open().apply {
                setPreviewDisplay(holder)
                setDisplayOrientation(90)
                setPreviewCallback(this@StaffCameraScannerActivity)
                startPreview()
            }
        }.onFailure {
            statusText.text = "Unable to start camera"
        }
    }

    private fun releaseCamera() {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int): String? {
        val primary = decodeBinaryBitmap(data, width, height)
        if (!primary.isNullOrBlank()) {
            return primary
        }
        // Some devices provide rotated preview frames; retry with swapped dimensions.
        return decodeBinaryBitmap(data, height, width)
    }

    private fun decodeBinaryBitmap(data: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
