"""
花卉图片分类器：Keras 训练并导出 TensorFlow Lite 模型
-------------------------------------------------------
使用迁移学习（MobileNetV2）训练花卉分类模型，并转换为 .tflite 文件。
数据集默认使用 TensorFlow 官方 flower_photos，也支持自定义按类别分文件夹的图片目录。
"""

import tarfile
from pathlib import Path
import numpy as np
import tensorflow as tf

# ---------------------------- 1. 配置参数 ----------------------------
FLOWER_URL = "https://storage.googleapis.com/download.tensorflow.org/example_images/flower_photos.tgz"

# DATA_DIR 设置为 None 时自动下载并使用官方花卉数据集；
# 若使用自定义数据，请指定为包含类别子文件夹的目录路径，例如 r"D:\path\to\my_images"
DATA_DIR = None

# 模型与训练超参数
EXPORT_DIR = "exported_flower_model"
EPOCHS = 5
BATCH_SIZE = 32
IMAGE_SIZE = 224
LEARNING_RATE = 1e-3

# TFLite 量化方式：可选 "dynamic" (推荐), "float16", "int8", "none"
QUANTIZATION = "dynamic"

# 固定随机种子，使训练/验证集划分尽量可复现
SEED = 123

print("TensorFlow 版本:", tf.__version__)

# ---------------------------- 2. 数据加载与划分 ----------------------------
def load_flower_datasets(data_dir, image_size, batch_size, seed):
    """加载花卉数据集，并划分为训练集、验证集、测试集"""
    if data_dir is None:
        # 下载官方数据集
        archive_path = tf.keras.utils.get_file(
            "flower_photos.tgz", FLOWER_URL, extract=False
        )
        archive_path = Path(archive_path)

        # 检查是否已有解压好的目录，避免重复解压
        candidates = [
            archive_path.parent / "flower_photos",
            archive_path.parent / "flower_photos_extracted" / "flower_photos",
        ]
        data_dir = next((path for path in candidates if path.exists()), None)
        if data_dir is None:
            with tarfile.open(archive_path, "r:gz") as tar:
                tar.extractall(archive_path.parent / "flower_photos_extracted")
            data_dir = archive_path.parent / "flower_photos_extracted" / "flower_photos"
    else:
        data_dir = Path(data_dir)

    # 从目录读取图片，子文件夹名作为类别标签
    train_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=0.2,
        subset="training",
        seed=seed,
        image_size=(image_size, image_size),
        batch_size=batch_size,
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=0.2,
        subset="validation",
        seed=seed,
        image_size=(image_size, image_size),
        batch_size=batch_size,
    )
    class_names = train_ds.class_names

    # 将验证集再拆分为真正的验证集与测试集（各占一半）
    val_batches = int(tf.data.experimental.cardinality(val_ds).numpy())
    test_ds = val_ds.take(val_batches // 2)
    val_ds = val_ds.skip(val_batches // 2)

    # 开启缓存、混洗与预取，提升训练效率
    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.cache().shuffle(1000, seed=seed).prefetch(autotune)
    val_ds = val_ds.cache().prefetch(autotune)
    test_ds = test_ds.cache().prefetch(autotune)

    return train_ds, val_ds, test_ds, class_names


# 加载数据集
train_ds, val_ds, test_ds, class_names = load_flower_datasets(
    DATA_DIR, IMAGE_SIZE, BATCH_SIZE, SEED
)
print("类别数量:", len(class_names))
print("类别名称:", class_names)

# ---------------------------- 3. 构建模型 ----------------------------
def build_model(num_classes, image_size, learning_rate):
    """使用 MobileNetV2 预训练权重构建迁移学习模型"""
    inputs = tf.keras.Input(shape=(image_size, image_size, 3), name="image")

    # MobileNetV2 所需的预处理（将像素值缩放至 [-1,1] 等）
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)

    # 加载预训练模型（不含顶层分类器）
    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(image_size, image_size, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    base_model.trainable = False  # 冻结预训练权重

    x = base_model(x, training=False)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax", name="predictions")(x)

    model = tf.keras.Model(inputs, outputs)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"],
    )
    return model


# 创建模型并查看结构
model = build_model(len(class_names), IMAGE_SIZE, LEARNING_RATE)
model.summary()

# ---------------------------- 4. 训练模型 ----------------------------
history = model.fit(train_ds, validation_data=val_ds, epochs=EPOCHS)

# 测试集评估
loss, accuracy = model.evaluate(test_ds)
print(f"test_loss={loss:.4f}, test_accuracy={accuracy:.4f}")

# ---------------------------- 5. 转换为 TensorFlow Lite ----------------------------
def convert_to_tflite(model, quantization, representative_ds):
    """将 Keras 模型转换为 TFLite 模型，支持多种量化方式"""
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    if quantization == "dynamic":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif quantization == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    elif quantization == "int8":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]

        def representative_data_gen():
            for images, _ in representative_ds.take(100):
                for image in images:
                    yield [tf.expand_dims(tf.cast(image, tf.float32), 0)]

        converter.representative_dataset = representative_data_gen
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.uint8
        converter.inference_output_type = tf.uint8
    elif quantization != "none":
        raise ValueError(f"不支持的量化方式: {quantization}")

    return converter.convert()


# 创建导出目录并保存标签、Keras 模型和 TFLite 模型
export_dir = Path(EXPORT_DIR)
export_dir.mkdir(parents=True, exist_ok=True)

# 保存 labels.txt
labels_path = export_dir / "labels.txt"
labels_path.write_text("\n".join(class_names) + "\n", encoding="utf-8")

# 保存原始 Keras 模型
keras_path = export_dir / "flower_classifier.keras"
model.save(keras_path)

# 转换并保存 TFLite 模型
tflite_model = convert_to_tflite(model, QUANTIZATION, train_ds)
tflite_path = export_dir / "model.tflite"
tflite_path.write_bytes(tflite_model)

print(f"已保存 Keras 模型: {keras_path}")
print(f"已保存 TFLite 模型: {tflite_path}")
print(f"已保存标签文件: {labels_path}")

# ---------------------------- 6. TFLite 快速推理测试 ----------------------------
def smoke_test_tflite(tflite_path, test_ds, class_names):
    """对导出的 TFLite 模型进行简单的前向推理验证"""
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    # 取 8 张测试图片
    images, labels = next(iter(test_ds.unbatch().batch(8)))
    input_data = tf.cast(images, input_details["dtype"]).numpy()

    # 若模型输入为 uint8，需要按量化参数缩放
    if input_details["dtype"] == np.uint8:
        scale, zero_point = input_details["quantization"]
        if scale:
            input_data = images.numpy() / scale + zero_point
            input_data = np.clip(input_data, 0, 255).astype(np.uint8)

    predictions = []
    for image in input_data:
        interpreter.set_tensor(input_details["index"], np.expand_dims(image, 0))
        interpreter.invoke()
        predictions.append(interpreter.get_tensor(output_details["index"])[0])

    predicted_ids = np.argmax(np.asarray(predictions), axis=1)
    for expected, predicted in zip(labels.numpy()[:5], predicted_ids[:5]):
        print(f"真实类别={class_names[expected]}, 预测类别={class_names[predicted]}")


# 运行 TFLite 烟雾测试
smoke_test_tflite(tflite_path, test_ds, class_names)