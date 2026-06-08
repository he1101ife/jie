🌼 花卉图片分类器 – Keras 迁移学习 + TensorFlow Lite 部署
基于 MobileNetV2 迁移学习的轻量级花卉图像分类模型。
训练完成后可将模型导出为 TensorFlow Lite 格式，并支持多种量化策略，适合在移动端或边缘设备上部署。

📂 项目结构
text
.
├── flower_classifier.py         # 主训练与转换脚本（本代码）
├── exported_flower_model/       # 训练输出目录（自动创建）
│   ├── labels.txt               # 类别标签列表
│   ├── flower_classifier.keras  # 完整 Keras 模型
│   └── model.tflite             # 转换后的 TensorFlow Lite 模型
└── README.md                    # 本说明文档
🚀 快速开始
1. 环境准备
推荐 Python 3.8–3.11，安装依赖：

bash
pip install tensorflow numpy
脚本会自动下载花卉数据集，无需手动准备。

2. 运行训练与转换
bash
python flower_classifier.py
程序将依次执行：

下载并解压官方花卉数据集（或使用自定义数据）

构建 MobileNetV2 迁移学习模型

训练模型（默认 5 个 epoch）

在测试集上评估性能

导出 Keras 模型（.keras）

转换为 TFLite 模型（model.tflite）

运行一次 TFLite 推理烟雾测试

⚙️ 主要参数配置
所有可调参数均集中在脚本顶部的 配置参数 区域：

参数	默认值	说明
DATA_DIR	None	自定义数据集路径（含分类子文件夹）。为 None 时自动下载官方花卉数据集。
EXPORT_DIR	"exported_flower_model"	模型与标签输出目录
EPOCHS	5	训练轮数
BATCH_SIZE	32	批量大小
IMAGE_SIZE	224	输入图片尺寸（长宽一致）
LEARNING_RATE	1e-3	优化器初始学习率
QUANTIZATION	"dynamic"	TFLite 量化方式（见下文）
SEED	123	数据划分随机种子
量化方式说明
选项	描述
"dynamic"	动态范围量化（推荐，模型大小减少约 4 倍，精度几乎不变）
"float16"	半精度浮点量化（需硬件支持 FP16）
"int8"	整数量化（需要提供代表性数据集，推理快，模型最小）
"none"	不量化，导出普通 float32 模型
🗂 数据集说明
官方数据集
脚本默认使用 TensorFlow 官方 flower_photos，包含 5 类 花卉：
daisy, dandelion, roses, sunflowers, tulips，约 3670 张图片。

自定义数据集
如果你的图片已按以下结构组织：

text
my_images/
├── cat/
│   ├── cat001.jpg
│   └── ...
├── dog/
│   ├── dog001.jpg
│   └── ...
└── ...
只需将 DATA_DIR 设置为该目录路径即可（注意使用原始字符串，如 r"D:\data\my_images"）。
类别名称将自动采用子文件夹名称。

📤 模型输出与使用
训练完成后，exported_flower_model/ 目录下将生成：

labels.txt – 每行一个类别名，顺序对应模型输出的索引

flower_classifier.keras – 完整 Keras 模型，可用于重训练或检查

model.tflite – 可直接在 Android、iOS、嵌入式设备上运行的轻量模型