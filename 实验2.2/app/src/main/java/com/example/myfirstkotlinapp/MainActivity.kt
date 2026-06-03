package com.example.myfirstkotlinapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstkotlinapp.ui.theme.MyFirstKotlinAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyFirstKotlinAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiDetectPage()
                }
            }
        }
    }
}

// 整体页面：外层Column纵向排布4大区域：标题栏、预览区、结果区、按钮区
@Composable
fun AiDetectPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // 1.顶部标题栏
        TopTitleBar()

        // 2.相机预览占位Box
        CameraPreviewBox(modifier = Modifier.fillMaxWidth())

        // 3.识别结果卡片
        ResultInfoCard(
            modelName = "MobileNet",
            resultText = "Cat",
            confidence = "96.2%",
            inferTime = "28 ms",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp)
        )

        // 4.底部按钮区（两行两列）
        BottomButtonArea(modifier = Modifier.fillMaxWidth())
    }
}

// 顶部标题
@Composable
fun TopTitleBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = "LiteRT AI Demo",
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// 相机预览占位框（灰色背景+相机图标文字）
@Composable
fun CameraPreviewBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
            Text(
                text = "Camera Preview",
                color = Color.White
            )
        }
    }
}

// 结果信息卡片
@Composable
fun ResultInfoCard(
    modelName: String,
    resultText: String,
    confidence: String,
    inferTime: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Model: $modelName", fontSize = 16.sp)
            Text(text = "Result: $resultText", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Confidence: $confidence", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Time: $inferTime", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// 底部按钮布局：2行Row，每行2个按钮
@Composable
fun BottomButtonArea(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // 第一行：拍照识别(蓝)、相册导入(绿)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2167DD)),
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = null)
                Text("拍照识别", modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38A84F)),
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            ) {
                Icon(Icons.Default.Photo, contentDescription = null)
                Text("相册导入", modifier = Modifier.padding(start = 4.dp))
            }
        }
        // 第二行：切换模型(紫)、清空结果(红)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF773DDA)),
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("切换模型", modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("清空结果", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

// 预览
@Preview(showBackground = true, widthDp = 360)
@Composable
fun AiPagePreview() {
    MyFirstKotlinAppTheme {
        AiDetectPage()
    }
}