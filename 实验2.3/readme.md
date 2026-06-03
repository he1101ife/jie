🧪 实验内容（课程实验报告）
实验目的
掌握 Android 布局（ConstraintLayout）的用法。

学会 Android 硬件权限的动态申请流程。

熟练使用 CameraX 库实现 预览（Preview）、拍照（ImageCapture） 和 录像（VideoCapture）。

了解 CameraX 的扩展功能 图像分析（ImageAnalysis）。

实验步骤概要
创建 Empty Activity 项目，设置最小 SDK 为 24（原教程为 21，此处调整以适配 CameraX）。

添加 CameraX 及布局依赖，启用 viewBinding。

设计 activity_main.xml：

PreviewView 作为预览窗口，顶部留出安全区。

两个圆形按钮（拍照、录像）通过 Guideline 左右分布。

Barrier 确保预览区域不遮挡按钮。

在 AndroidManifest.xml 中声明权限和 AppCompat 主题。

实现 MainActivity：

检查并请求权限，处理用户拒绝场景。

初始化 ProcessCameraProvider，绑定 Preview、ImageCapture、VideoCapture 三个用例。

拍照逻辑：生成文件名 → 创建 OutputFileOptions → 调用 takePicture()。

录像逻辑：点击开始 → 创建录制会话 → 处理 VideoRecordEvent（切换按钮文字）；再次点击停止。

修复主题兼容性问题：将父主题改为 Theme.AppCompat.Light.NoActionBar，避免 AppCompatButton 崩溃。

测试并上传至 GitHub，编写 README 文档。

扩展实验方向
Preview + VideoCapture + ImageAnalysis
添加 ImageAnalysis.Analyzer，在预览的同时对每帧图像进行实时处理（例如灰度转换、QR 码识别）。
示例代码片段：

kotlin
val imageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
imageAnalysis.setAnalyzer(cameraExecutor, { imageProxy ->
    // 处理 imageProxy，如调用 ML Kit 或 OpenCV
    imageProxy.close()
})
cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture, imageAnalysis)
Preview + ImageCapture + VideoCapture 三合一（基础实验已实现）

结合传感器（如重力感应）自动旋转图片或调节曝光。

