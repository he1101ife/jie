/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.classification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
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
import org.tensorflow.lite.examples.classification.ml.FlowerModel
import org.tensorflow.lite.examples.classification.ui.RecognitionAdapter
import org.tensorflow.lite.examples.classification.util.YuvToRgbConverter
import org.tensorflow.lite.examples.classification.viewmodel.Recognition
import org.tensorflow.lite.examples.classification.viewmodel.RecognitionListViewModel
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.gpu.CompatibilityList
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

        if (allPermissionsGranted()) {
            startCamera()
        } else {
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
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(224, 224))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase: ImageAnalysis ->
                    analysisUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer(this) { items ->
                        recogViewModel.updateData(items)
                    })
                }

            val cameraSelector =
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private class ImageAnalyzer(ctx: Context, private val listener: RecognitionListener) :
        ImageAnalysis.Analyzer {

        private val flowerModel: FlowerModel by lazy {
            Log.d(TAG, "开始初始化 FlowerModel")
            val compatList = CompatibilityList()
            val isGpu = compatList.isDelegateSupportedOnThisDevice
            Log.d(TAG, "GPU 兼容性: $isGpu")

            val options = if (isGpu) {
                Log.d(TAG, "使用 GPU 委托")
                Model.Options.Builder().setDevice(Model.Device.GPU).build()
            } else {
                Log.d(TAG, "GPU 不可用，使用 CPU 4 线程")
                Model.Options.Builder().setNumThreads(4).build()
            }

            FlowerModel.newInstance(ctx, options).also {
                Log.d(TAG, "FlowerModel 初始化成功")
            }
        }

        override fun analyze(imageProxy: ImageProxy) {
            val items = mutableListOf<Recognition>()

            try {
                // 1. 转换图像
                val bitmap = toBitmap(imageProxy)
                if (bitmap == null) {
                    Log.e(TAG, "toBitmap 返回 null，跳过当前帧")
                    imageProxy.close()
                    return
                }
                Log.d(TAG, "当前帧尺寸: ${bitmap.width}x${bitmap.height}")

                // 2. 缩放至模型要求的 224x224（如果实际尺寸不符）
                val scaledBitmap = if (bitmap.width != 224 || bitmap.height != 224) {
                    Log.d(TAG, "将图像从 ${bitmap.width}x${bitmap.height} 缩放至 224x224")
                    Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                } else {
                    bitmap
                }

                // 3. 转换为 TensorImage
                val tfImage = TensorImage.fromBitmap(scaledBitmap)
                Log.d(TAG, "开始模型推理...")

                // 4. 推理
                val allOutputs = flowerModel.process(tfImage).probabilityAsCategoryList
                Log.d(TAG, "推理完成，总类别数: ${allOutputs.size}")

                if (allOutputs.isEmpty()) {
                    Log.w(TAG, "probabilityAsCategoryList 为空！请检查模型标签文件或元数据。")
                }

                // 5. 排序并取前 MAX_RESULT_DISPLAY
                val outputs = allOutputs
                    .apply { sortByDescending { it.score } }
                    .take(MAX_RESULT_DISPLAY)

                if (outputs.isNotEmpty()) {
                    Log.d(TAG, "最高置信度: ${outputs[0].label} - ${outputs[0].score}")
                } else {
                    Log.w(TAG, "处理后的结果列表为空。")
                }

                // 6. 转换为 Recognition 列表
                for (output in outputs) {
                    items.add(Recognition(output.label, output.score))
                }
                Log.d(TAG, "本帧识别结果数量: ${items.size}")

            } catch (e: Exception) {
                Log.e(TAG, "推理过程中抛出异常: ${e.message}", e)
            }

            // 回调结果（即使为空也回调）
            listener(items.toList())

            // 关闭图像
            imageProxy.close()
        }

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
    }
}