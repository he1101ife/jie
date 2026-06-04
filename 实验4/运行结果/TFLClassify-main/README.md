TensorFlow Lite 实时花卉识别 Android 示例
概述
本项目是一个基于 TensorFlow Lite 的 Android 示例应用，使用设备自带的摄像头实时识别花卉种类。应用通过 CameraX 获取预览帧，调用预训练的 TensorFlow Lite 模型进行推理，并在屏幕上显示置信度最高的前三个分类结果（花卉名称和置信度分数）。支持在 GPU 或 CPU 上运行推理，并提供带详细日志的完整实现，适合学习和二次开发。

功能特点
📷 实时相机识别：使用 CameraX 连续获取相机帧，无延迟分类。

🧠 设备端推理：完全在本地运行，无需网络，保护隐私。

🚀 GPU 加速：自动检测设备是否支持 GPU 委托，支持则启用，否则回退到多线程 CPU。

📊 Top‑3 结果展示：底部半透明面板以列表形式显示最可能的三个花卉标签及置信度。

🛠 详细日志输出：可跟踪图像转换、推理耗时、分类结果等关键环节。

🔧 易于替换模型：仅需替换 .tflite 模型文件和标签文件，即可用于其他图像分类任务。

构建要求
Android Studio 4.0 或更高版本

Android SDK 29 或更高（实际 compileSdkVersion 29）

最低支持 Android 5.0（API 21）

Kotlin 1.4+ 及 Gradle 6.5+

依赖项
本项目依赖以下主要库（完整列表见 app/build.gradle）：

库	用途
androidx.camera:camera-camera2:1.0.0-beta10	CameraX 核心实现
androidx.camera:camera-lifecycle:1.0.0-beta10	CameraX 生命周期管理
androidx.camera:camera-view:1.0.0-alpha17	相机预览视图
org.tensorflow:tensorflow-lite:2.14.0	TensorFlow Lite 运行时
org.tensorflow:tensorflow-lite-support:0.4.4	TensorFlow Lite 支持库（TensorImage 等）
org.tensorflow:tensorflow-lite-metadata:0.4.4	模型元数据支持
org.tensorflow:tensorflow-lite-gpu:2.14.0	GPU 委托（可选加速）
androidx.recyclerview:recyclerview:1.1.0	结果显示列表
快速开始
1. 克隆仓库
bash
2. 导入项目
用 Android Studio 打开项目根目录，等待 Gradle 同步完成。

3. 准备模型文件
确保以下文件已存在于 app/src/main/assets/ 目录：

flower_model.tflite – 训练好的 TensorFlow Lite 模型

labels.txt – 每行一个标签，顺序与模型输出对应

（若缺失，请从官方 TensorFlow Lite 示例或自行训练获取）

4. 运行应用
连接 Android 设备或启动模拟器（模拟器可能无摄像头，建议真机测试）

点击 Run 按钮

首次启动时授予相机权限

使用说明
启动应用后，相机预览将全屏显示。

将摄像头对准任意花卉。

屏幕底部会出现半透明黑底的识别结果面板，实时显示：

最可能的三种花卉名称（如 tulips）

对应的置信度分数（0~1之间）

识别结果会随相机移动实时更新。

注意：当前预置模型为 TensorFlow Lite 官方花卉分类模型，仅能识别几种常见花卉（雏菊、蒲公英、玫瑰、向日葵、郁金香等）。若需识别更多类别，请替换模型。

项目结构
text
app/
├── src/main/
│   ├── java/org/tensorflow/lite/examples/classification/
│   │   ├── MainActivity.kt              # 主活动，管理相机和生命周期
│   │   ├── ml/FlowerModel.java          # 自动生成的模型包装类（含 process 方法）
│   │   ├── ui/RecognitionAdapter.kt     # RecyclerView 适配器
│   │   ├── util/YuvToRgbConverter.kt    # YUV 到 RGB 转换工具
│   │   └── viewmodel/
│   │       ├── Recognition.kt           # 数据类（标签 + 置信度）
│   │       └── RecognitionListViewModel.kt  # 用于保存识别结果的 ViewModel
│   ├── res/
│   │   └── layout/
│   │       ├── activity_main.xml        # 主布局（相机预览、工具栏、结果列表）
│   │       └── item_recognition.xml     # 单条识别结果的布局
│   └── assets/
│       ├── flower_model.tflite          # TensorFlow Lite 模型
│       └── labels.txt                   # 标签文件
└── build.gradle                         # 应用级构建脚本
