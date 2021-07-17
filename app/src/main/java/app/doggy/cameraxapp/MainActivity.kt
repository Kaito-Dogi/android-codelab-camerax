package app.doggy.cameraxapp

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.doggy.cameraxapp.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // Bindingクラスを宣言．
    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // カメラのパーミッションを要求する．
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // cameraCaptureButtonをクリックした時の処理．
        binding.cameraCaptureButton.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // requestCodeが正しければ確認する．
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }

        }
    }

    private fun takePhoto() {

        // ImageCaptureユースケースへの参照を取得．
        // ユースケースがnullの場合はこの関数から抜け出す．
        // imageCaptureが設定される前に写真ボタンをタップした場合にnullになる．
        // nullの場合は，return文が無いとアプリがクラッシュする．
        val imageCapture = imageCapture ?: return

        // 画像を格納するファイルを作成．
        // ファイル名が一意になるようにタイムスタンプを入れる．
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        // OutputFileOptionsオブジェクトを生成．
        // これは，出力をどのようにしたいかを指定することができる．
        // 先ほど作成したファイルに出力を保存したいので，photoFileを渡す．
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 写真を撮影した後に呼ばれるリスナの設定．
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // 写真のプレビューにビューファインダーを使用する．
    // ビューファインダーはCameraXのPreviewクラスを使って実装できる．
    // プレビューを使用するには，まず構成を定義する．
    // 構成は，ユースケースのインスタンスを作成するために使用される．
    // 作成されたインスタンスはCameraXのライフサイクルにバインドされる．
    private fun startCamera() {

        // ProcessCameraProviderのインスタンスを作成．
        // これはカメラのライフサイクルをLifecycleOwnerにバインドするために使用する．
        // これによってCameraXはライフサイクルを認識しているため，カメラを開始・終了する作業が不要になる．
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // cameraProviderFutureにリスナーを追加．
        // 第1引数にRunnableを渡す．これは後で記入する．
        // 第2引数にContextCompat.getMainExecutor()を渡す．これはメインスレッドで実行されるExecutorを返す．
        cameraProviderFuture.addListener(Runnable {

            // カメラのライフサイクルをLifecycleOwnerにバインドするために使用する．
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Previewオブジェクトを初期化．
            // viewFinderからsurfaceProviderを取得し，それをPreviewオブジェクトに設定．
            val preview = Preview.Builder()
                .build()
                .also { preview: Preview ->
                    preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // ImageCaptureオブジェクトを初期化．
            imageCapture = ImageCapture.Builder()
                .build()

            // CameraSelectorを生成．
            // DEFAULT_BACK_CAMERAを選択．
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // cameraProviderに何もバインドされていないことを確認．
                cameraProvider.unbindAll()

                // cameraSelectorとpreviewをcameraProviderにバインドする．
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // パーミッションが得られているか確認．
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}