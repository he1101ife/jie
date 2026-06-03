MyFirstKotlinApp - LiteRT AI Demo
一个基于 Kotlin 和 Jetpack Compose 构建的 Android AI 演示应用骨架，展示了使用 TensorFlow Lite（LiteRT）进行图像识别的用户界面原型。

📱 项目简介
本项目是一个 Android 客户端应用，旨在为 AI 图像识别功能提供现代化的界面框架。界面模拟了完整的识别流程：从相机预览、模型推理结果展示，到操作按钮（拍照识别、相册导入、切换模型、清空结果）。当前版本实现了完整的 UI 布局，但尚未接入实际的 TensorFlow Lite 推理逻辑，可作为集成 LiteRT 功能的起点。

✨ 功能特点
现代 UI 设计：使用 Material 3 和 Jetpack Compose 构建，支持深色主题。

完整的交互界面：

顶部标题栏：显示 LiteRT AI Demo。

相机预览区域：灰色占位框模拟实时相机画面。

结果信息卡片：展示模型名称、识别结果、置信度和推理耗时。

底部操作区：

拍照识别：调用相机实时拍照并识别。

相册导入：从相册选取图片进行识别。

切换模型：切换不同的识别模型。

清空结果：清除当前识别结果。

可扩展架构：预留了方法钩子（onClick = {}），便于后续集成相机、图片选择器、TensorFlow Lite 模型加载与推理。

组件化设计：每个 UI 区域都是独立的 Composable 函数，易于维护和复用。