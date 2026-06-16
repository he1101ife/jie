"""
石头剪刀布手势识别 —— 完整训练 + 导出模型
使用本地已解压的数据集（rps 和 rps-test-set）
"""
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from pathlib import Path
import matplotlib.pyplot as plt

# ========== 1. 设置本地数据集路径 ==========
TRAINING_DIR = "C:/Users/jie/Downloads/rps/rps/"
VALIDATION_DIR = "C:/Users/jie/Downloads/rps-test-set/rps-test-set/"

# 检查关键子目录是否存在（确保数据完整）
for class_name in ["rock", "paper", "scissors"]:
    if not Path(TRAINING_DIR + class_name).exists():
        raise FileNotFoundError(f"训练集缺少文件夹: {TRAINING_DIR + class_name}")
    if not Path(VALIDATION_DIR + class_name).exists():
        raise FileNotFoundError(f"测试集缺少文件夹: {VALIDATION_DIR + class_name}")

# ========== 2. 数据增强与生成器 ==========
train_datagen = ImageDataGenerator(
    rescale=1./255,            # 像素值归一化到 0~1
    rotation_range=40,         # 随机旋转角度
    width_shift_range=0.2,     # 水平平移比例
    height_shift_range=0.2,    # 垂直平移比例
    shear_range=0.2,           # 剪切强度
    zoom_range=0.2,            # 随机缩放
    horizontal_flip=True,      # 随机水平翻转
    fill_mode='nearest'        # 填充方式
)

validation_datagen = ImageDataGenerator(rescale=1./255)  # 测试集只归一化

train_generator = train_datagen.flow_from_directory(
    TRAINING_DIR,
    target_size=(150, 150),    # 所有图片统一调整到 150x150
    class_mode='categorical',  # 多分类标签
    batch_size=126
)

validation_generator = validation_datagen.flow_from_directory(
    VALIDATION_DIR,
    target_size=(150, 150),
    class_mode='categorical',
    batch_size=126
)

# ========== 3. 构建卷积神经网络模型 ==========
model = tf.keras.models.Sequential([
    # 输入层：150x150 的 3 通道彩色图片
    tf.keras.layers.Conv2D(64, (3, 3), activation='relu', input_shape=(150, 150, 3)),
    tf.keras.layers.MaxPooling2D(2, 2),

    # 第二组卷积+池化
    tf.keras.layers.Conv2D(64, (3, 3), activation='relu'),
    tf.keras.layers.MaxPooling2D(2, 2),

    # 第三组卷积+池化
    tf.keras.layers.Conv2D(128, (3, 3), activation='relu'),
    tf.keras.layers.MaxPooling2D(2, 2),

    # 第四组卷积+池化
    tf.keras.layers.Conv2D(128, (3, 3), activation='relu'),
    tf.keras.layers.MaxPooling2D(2, 2),

    # 展平后接全连接层
    tf.keras.layers.Flatten(),
    tf.keras.layers.Dropout(0.5),                # 丢弃 50% 神经元防止过拟合
    tf.keras.layers.Dense(512, activation='relu'),
    tf.keras.layers.Dense(3, activation='softmax')  # 输出 3 类（石头/剪刀/布）
])

model.compile(
    loss='categorical_crossentropy',
    optimizer='rmsprop',
    metrics=['accuracy']
)

# 打印模型结构
model.summary()

# ========== 4. 训练模型 ==========
history = model.fit(
    train_generator,
    epochs=25,                     # 迭代 25 轮
    steps_per_epoch=20,            # 每轮从训练集中取 20 个 batch
    validation_data=validation_generator,
    validation_steps=3,            # 每轮验证用 3 个 batch
    verbose=1
)

# ========== 5. 保存模型 ==========
model.save("rps.h5")
print("\n✅ 模型已保存为 rps.h5")

# ========== 6. （可选）画出训练曲线 ==========
acc = history.history['accuracy']
val_acc = history.history['val_accuracy']
loss = history.history['loss']
val_loss = history.history['val_loss']

epochs = range(1, len(acc) + 1)

plt.figure(figsize=(12, 4))
plt.subplot(1, 2, 1)
plt.plot(epochs, acc, 'r', label='Training accuracy')
plt.plot(epochs, val_acc, 'b', label='Validation accuracy')
plt.title('Training and validation accuracy')
plt.legend()

plt.subplot(1, 2, 2)
plt.plot(epochs, loss, 'r', label='Training loss')
plt.plot(epochs, val_loss, 'b', label='Validation loss')
plt.title('Training and validation loss')
plt.legend()

plt.tight_layout()
plt.show()