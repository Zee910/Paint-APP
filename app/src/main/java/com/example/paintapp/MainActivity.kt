package com.example.paintapp

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.core.view.WindowCompat
import com.example.paintapp.ui.theme.PaintAppTheme
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display and avoid the content overlaying the status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PaintAppTheme {
                PaintApp()
            }
        }
    }
}

// Data class for drawing lines
data class Line(
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float = 10f,
)
suspend fun savePaintInGallery(context: Context, lines: List<Line>, uri: Uri) {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        drawColor(android.graphics.Color.WHITE)
        lines.forEach { line ->
            val paint = Paint().apply {
                color = line.color.toArgb()
                strokeWidth = line.strokeWidth
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
        }
    }

    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        val isSuccess = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        if (!isSuccess) {
            Toast.makeText(context, "Failed to save the image", Toast.LENGTH_SHORT).show()
        }
    } ?: run {
        Toast.makeText(context, "Something went wrong", Toast.LENGTH_SHORT).show()
    }
}

fun eraserArea(position: Offset, brushSize: Float, target: Offset): Boolean {
    val radius = brushSize * 1.5f
    return (position.x - radius..position.x + radius).contains(target.x) &&
            (position.y - radius..position.y + radius).contains(target.y)
}

// Color Picker UI
@Composable
fun ColorPicker(onColorSelected: (Color) -> Unit) {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Black)

    Row {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(color, CircleShape)
                    .clickable { onColorSelected(color) }
                    .padding(4.dp)
            )
        }
    }
}


@Composable
fun BrushSizeSelector(brushSize: Float, onBrushSizeChange: (Float) -> Unit) {
    var sizeText by remember { mutableStateOf(brushSize.toString()) }

    Row(
        modifier = Modifier
            .background(Color.Gray, CircleShape)
            .padding(8.dp)
    ) {
        BasicTextField(
            value = sizeText,
            onValueChange = {
                sizeText = it
                onBrushSizeChange(it.toFloatOrNull() ?: brushSize)
            },
            textStyle = TextStyle(fontSize = 18.sp),
            modifier = Modifier.width(80.dp)
        )
        Text(text = "px", Modifier.align(Alignment.CenterVertically))
    }
}


@Composable
fun PaintApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentColor by remember { mutableStateOf(Color.Black) }
    var brushSize by remember { mutableStateOf(10f) }
    var isEraser by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<Line>() }

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                savePaintInGallery(context, lines.toList(), uri)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // Ensures UI is below the status bar
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPicker { color ->
                currentColor = color
                isEraser = false
            }
            Spacer(modifier = Modifier.width(12.dp))
            BrushSizeSelector(brushSize) { newSize ->
                brushSize = newSize
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { isEraser = true }) {
                Text(text = "Eraser")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { lines.clear() }) {
                Text(text = "Reset")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = {
                createFileLauncher.launch("Painting_${System.currentTimeMillis()}.png")
            }) {
                Text("Save")
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()

                            if (isEraser) {
                                lines.removeAll { line ->
                                    eraserArea(change.position, brushSize, line.start) ||
                                            eraserArea(change.position, brushSize, line.end)
                                }
                            } else {
                                lines.add(
                                    Line(
                                        start = change.position - dragAmount,
                                        end = change.position,
                                        color = currentColor,
                                        strokeWidth = brushSize
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            lines.forEach { line ->
                drawLine(
                    color = line.color,
                    start = line.start,
                    end = line.end,
                    strokeWidth = line.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
