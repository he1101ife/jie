package org.tensorflow.lite.examples.classification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.examples.classification.ui.RecognitionAdapter
import org.tensorflow.lite.examples.classification.util.YuvToRgbConverter
import org.tensorflow.lite.examples.classification.viewmodel.Recognition
import org.tensorflow.lite.examples.classification.viewmodel.RecognitionListViewModel
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.IOException
import java.nio.MappedByteBuffer
import java.util.concurrent.Executors

private const val MAX_RESULT_DISPLAY = 3
private const val TAG = "TFL Classify"
private const val REQUEST_CODE_PERMISSIONS = 999
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

typealias RecognitionListener = (recognition: List<Recognition>) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var preview: Preview
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var camera: Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val resultRecyclerView by lazy {
        findViewById<RecyclerView>(R.id.recognitionResults)
    }
    private val viewFinder by lazy {
        findViewById<PreviewView>(R.id.viewFinder)
    }

    private val recogViewModel: RecognitionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate 开始")

        if (allPermissionsGranted()) {
            Log.d(TAG, "权限已授予，启动相机")
            startCamera()
        } else {
            Log.d(TAG, "请求相机权限")
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        val viewAdapter = RecognitionAdapter(this)
        resultRecyclerView.adapter = viewAdapter
        resultRecyclerView.itemAnimator = null

        recogViewModel.recognitionList.observe(this, Observer { list ->
            Log.d(TAG, "LiveData 更新，结果数量: ${list.size}")
            viewAdapter.submitList(list)
        })
        Log.d(TAG, "onCreate 完成")
    }

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionsResult: code=$requestCode")
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                Log.d(TAG, "权限已授予，启动相机")
                startCamera()
            } else {
                Log.w(TAG, "相机权限被拒绝")
                Toast.makeText(
                    this,
                    getString(R.string.permission_deny_text),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        Log.d(TAG, "startCamera 开始")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            Log.d(TAG, "CameraProvider 获取成功")

            preview = Preview.Builder().build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(224, 224))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase ->
                    Log.d(TAG, "设置 ImageAnalyzer")
                    analysisUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer(this) { items ->
                        recogViewModel.updateData(items)
                    })
                }

            val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            Log.d(TAG, "选择相机: $cameraSelector")

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
                Log.d(TAG, "相机绑定成功")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
        Log.d(TAG, "startCamera 完成")
    }

    /**
     * 自定义图像分析器：加载花卉模型，完成预处理、推理、结果传递。
     */
    private class ImageAnalyzer(ctx: Context, private val listener: RecognitionListener) :
        ImageAnalysis.Analyzer {

        private var tflite: Interpreter? = null
        private var labels: List<String> = emptyList()

        // 修改点 1：图像处理器增加归一化，将 [0,255] 映射到 [-1,1]
        private val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f))   // 对应 MobileNetV2 预处理
            .build()

        init {
            Log.d(TAG, "ImageAnalyzer 初始化开始")
            try {
                // 加载标签文件
                Log.d(TAG, "开始加载 labels.txt")
                labels = FileUtil.loadLabels(ctx, "labels.txt")
                Log.d(TAG, "加载标签成功，类别数: ${labels.size}，标签列表: $labels")

                // 加载模型文件
                Log.d(TAG, "开始加载 model.tflite")
                val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(ctx, "model.tflite")
                Log.d(TAG, "模型文件读取成功，大小: ${modelBuffer.capacity()} 字节")

                // CPU 4 线程推理
                val options = Interpreter.Options().setNumThreads(4)
                Log.d(TAG, "创建 Interpreter 选项: 4 线程 CPU")
                tflite = Interpreter(modelBuffer, options)
                Log.d(TAG, "模型加载成功 (CPU 4线程)")

                // 打印输入/输出张量信息
                val inputTensor = tflite?.getInputTensor(0)
                val outputTensor = tflite?.getOutputTensor(0)
                Log.d(TAG, "模型输入张量形状: ${inputTensor?.shape()?.contentToString()}, 类型: ${inputTensor?.dataType()}")
                Log.d(TAG, "模型输出张量形状: ${outputTensor?.shape()?.contentToString()}, 类型: ${outputTensor?.dataType()}")
            } catch (e: IOException) {
                Log.e(TAG, "模型或标签文件加载失败: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Interpreter 创建失败 (算子兼容性?): ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "初始化时发生未知错误: ${e.message}", e)
            }
            Log.d(TAG, "ImageAnalyzer 初始化完成, tflite=${tflite != null}")
        }

        override fun analyze(imageProxy: ImageProxy) {
            val startTime = SystemClock.elapsedRealtime()
            val items = mutableListOf<Recognition>()
            val interpreter = tflite
            if (interpreter == null) {
                Log.w(TAG, "Interpreter 为空，跳过此帧")
                imageProxy.close()
                return
            }

            try {
                // YUV → Bitmap
                val bitmap = toBitmap(imageProxy)
                if (bitmap == null) {
                    Log.e(TAG, "toBitmap 返回 null，跳过当前帧")
                    imageProxy.close()
                    return
                }
                Log.d(TAG, "当前帧尺寸: ${bitmap.width}x${bitmap.height}")

                // 修改点 2：将 Bitmap 加载为 FLOAT32 类型，并做预处理
                val tfImage = TensorImage(DataType.FLOAT32)   // 显式指定 float32
                tfImage.load(bitmap)                          // 自动将 0~255 转为 0.0f~255.0f
                val processedImage = imageProcessor.process(tfImage) // 缩放 + 归一化

                // 输出数组（形状 [1, 类别数]）
                val output = Array(1) { FloatArray(labels.size) }

                // 执行推理
                val inferenceStart = SystemClock.elapsedRealtime()
                interpreter.run(processedImage.buffer, output)
                val inferenceTime = SystemClock.elapsedRealtime() - inferenceStart
                Log.d(TAG, "推理耗时: ${inferenceTime}ms")

                // 解析结果
                val probabilities = output[0]
                val categoryList = probabilities.mapIndexed { index, score ->
                    Recognition(labels.getOrElse(index) { "Unknown" }, score)
                }
                val topResults = categoryList
                    .sortedByDescending { it.confidence }
                    .take(MAX_RESULT_DISPLAY)

                items.addAll(topResults)
                if (topResults.isNotEmpty()) {
                    Log.d(TAG, "最高置信度: ${topResults[0].label} - ${topResults[0].confidence}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "推理过程中出错: ${e.message}", e)
            }

            // 回调结果
            listener(items.toList())
            imageProxy.close()
            val totalTime = SystemClock.elapsedRealtime() - startTime
            Log.d(TAG, "本帧处理总耗时: ${totalTime}ms")
        }

        // ---------- YUV → Bitmap 转换 ----------
        private val yuvToRgbConverter = YuvToRgbConverter(ctx)
        private lateinit var bitmapBuffer: Bitmap
        private lateinit var rotationMatrix: Matrix

        @SuppressLint("UnsafeExperimentalUsageError")
        private fun toBitmap(imageProxy: ImageProxy): Bitmap? {
            val image = imageProxy.image ?: run {
                Log.e(TAG, "imageProxy.image 为 null")
                return null
            }

            if (!::bitmapBuffer.isInitialized) {
                Log.d(TAG, "初始化 bitmapBuffer 和 rotationMatrix")
                rotationMatrix = Matrix()
                rotationMatrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                )
                Log.d(TAG, "bitmapBuffer 创建: ${bitmapBuffer.width}x${bitmapBuffer.height}, rotationDegrees=${imageProxy.imageInfo.rotationDegrees}")
            }

            yuvToRgbConverter.yuvToRgb(image, bitmapBuffer)

            return Bitmap.createBitmap(
                bitmapBuffer,
                0, 0,
                bitmapBuffer.width, bitmapBuffer.height,
                rotationMatrix,
                false
            )
        }

        fun close() {
            Log.d(TAG, "ImageAnalyzer 关闭")
            tflite?.close()
        }
    }
}