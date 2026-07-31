package com.quiddity.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import com.quiddity.app.util.CrashLogger
import com.quiddity.app.util.IdGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



private const val DEFAULT_OUTPUT_SIZE = 512
private const val JPEG_QUALITY = 90
// 降低源图最大边长，减小大图的内存峰值，降低低端机 OOM / native crash 概率。
private const val MAX_SOURCE_DIM = 1536
// 裁剪缩放范围：1% - 600%（相对原图 Fit 进容器后的显示大小）。
// 1% 允许用户缩小到看全图再裁剪；600% 提供足够放大空间精修细节。
// 缩到小于裁剪框时，裁剪结果按裁剪框内可见内容输出，图未覆盖区域填白。
private const val MIN_ZOOM_SCALE = 0.01f
private const val MAX_ZOOM_SCALE = 6f

/**
 * 通用图片裁剪界面（支持自定义宽高比）。
 *
 * 全屏显示用户选定的图片，叠加可配置宽高比的裁剪框，用户可拖动 / 双指缩放
 * 调整图片位置以选择最优裁剪区域。确认后将裁剪结果缩放为目标尺寸的 JPEG
 * 文件，并通过 [onCropComplete] 返回文件 URI。
 *
 * 防闪退设计：
 * - 裁剪进行中禁止返回键关闭界面（[BackHandler] 在 cropping 时禁用）。
 * - DisposableEffect 在 dispose 时若仍在裁剪，延迟回收 Bitmap 避免 native SIGSEGV。
 * - 深拷贝原始 Bitmap（ARGB_8888 mutable），裁剪线程独立持有像素数据。
 *
 * @param imageUri 用户选定的原始图片 URI（建议为 file:// 内部存储 URI）
 * @param outputName 可选的稳定输出文件名（不含扩展名）。传入后裁剪结果覆盖写入
 *   `filesDir/{outputSubdir}/{outputName}.jpg`，避免旧文件被下一次裁剪清理掉；
 *   不传时按随机 UUID 生成临时文件。
 * @param aspectRatio 裁剪框宽高比（宽/高）。默认 1.0f（正方形，头像用）。
 *   壁纸场景传 9f/16f（竖屏）或屏幕实际宽高比。
 * @param outputSubdir 输出子目录名。默认 "avatars"，壁纸场景传 "wallpapers" 或 "list_wallpapers"。
 * @param outputWidth 输出图片宽度（像素）。默认 512（头像），壁纸可传 1080。
 * @param outputHeight 输出图片高度（像素）。默认 512（头像），壁纸可传 1920。
 * @param title 标题栏文案。默认"裁剪头像"。
 * @param onCropComplete 裁剪完成，回调裁剪后文件的 URI
 * @param onCancel 用户取消裁剪
 */
@Composable
fun ImageCropper(
    imageUri: Uri,
    outputName: String? = null,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit,
    aspectRatio: Float = 1f,
    outputSubdir: String = "avatars",
    outputWidth: Int = DEFAULT_OUTPUT_SIZE,
    outputHeight: Int = DEFAULT_OUTPUT_SIZE,
    title: String = "裁剪头像"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf(false) }
    var contentSize by remember { mutableStateOf<IntSize?>(null) }
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri) { mutableFloatStateOf(0f) }
    var cropping by remember { mutableStateOf(false) }
    // 防闪退：裁剪进行期间禁止回收原始 Bitmap。
    // 若 dispose 时仍在裁剪，把原图暂存到 pendingRecycle，等裁剪结束后再回收。
    var pendingRecycleBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 裁剪进行中禁止返回键关闭界面，避免后台裁剪线程读取已被回收的 Bitmap 导致崩溃。
    BackHandler(enabled = !cropping) { onCancel() }

    LaunchedEffect(imageUri) {
        val loaded = withContext(Dispatchers.IO) { loadBitmap(context, imageUri) }
        bitmap = loaded
        loadError = loaded == null
    }

    // Bitmap 生命周期管理：
    // - 使用 `DisposableEffect(Unit)`，仅在 Composable 离开组合树时执行 onDispose
    // - bitmap 从 null → 非 null 不会触发 dispose，新加载的 bitmap 不会被错误回收
    // - 离开组合时读取当前 bitmap 状态值并回收（此时不再有 UI 绘制需求）
    // - minSdk=26（Android 8.0+），Bitmap 在 Java 堆上分配，GC 可自动回收未显式 recycle 的 Bitmap
    DisposableEffect(Unit) {
        onDispose {
            val bmp = bitmap
            // 空安全 + isRecycled 双重判定：bitmap 可能为 null，
            // 也可能在 dispose 之前已被 cropAndSave 流程中 recycle。
            if (bmp != null && !bmp.isRecycled) {
                if (cropping) {
                    // 裁剪尚未结束，延迟回收，避免后台线程读取被释放的像素缓冲区。
                    pendingRecycleBitmap = bmp
                } else {
                    bmp.recycle()
                }
            }
        }
    }

    // 延迟回收触发器：当裁剪结束且存在待回收原图时，执行回收。
    LaunchedEffect(cropping, pendingRecycleBitmap) {
        if (!cropping && pendingRecycleBitmap != null) {
            pendingRecycleBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            pendingRecycleBitmap = null
        }
    }

    val bmp = bitmap
    val cs = contentSize
    // 根据 aspectRatio 计算裁剪框尺寸。
    //
    // 算法：
    // 1. 容器可用区域为 cw × ch；
    // 2. 裁剪框需满足 cropW / cropH = aspectRatio，且在容器内尽可能大（占 80%）；
    // 3. 分两种情况：
    //    - 若 aspectRatio >= cw/ch（裁剪框比容器更宽/扁）：以宽度为基准，cropW = cw*0.8，
    //      cropH = cropW / aspectRatio；
    //    - 若 aspectRatio < cw/ch（裁剪框比容器更高/窄）：以高度为基准，cropH = ch*0.8，
    //      cropW = cropH * aspectRatio。
    // 4. fitScale 将原图 Fit 进容器，baseW/baseH 为原图显示尺寸；
    // 5. minScale 确保原图至少填满裁剪框（任意一边都不能小于裁剪框对应边）。
    val derived: DerivedParams? = if (bmp != null && cs != null) {
        val cw = cs.width.toFloat()
        val ch = cs.height.toFloat()
        val containerRatio = cw / ch
        val cropW: Float
        val cropH: Float
        if (aspectRatio >= containerRatio) {
            // 裁剪框比容器更宽：以宽度为基准
            cropW = cw * 0.8f
            cropH = cropW / aspectRatio
        } else {
            // 裁剪框比容器更高：以高度为基准
            cropH = ch * 0.8f
            cropW = cropH * aspectRatio
        }
        val fitScale = min(cw / bmp.width, ch / bmp.height)
        val baseW = bmp.width * fitScale
        val baseH = bmp.height * fitScale
        // 缩放范围 1% - 600%（相对原图 Fit 后的显示大小）。
        // 不再强制图片填满裁剪框——允许缩到比裁剪框更小，方便先看全图再裁。
        // 裁剪时图未覆盖的区域按白色填充（JPEG 不支持透明）。
        DerivedParams(cropW, cropH, baseW, baseH, MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
    } else null

    // 图片或容器尺寸变化时，将缩放与偏移约束到合法范围
    LaunchedEffect(derived?.minScale, derived?.maxScale) {
        val d = derived ?: return@LaunchedEffect
        scale = scale.coerceIn(d.minScale, d.maxScale)
    }
    // 偏移约束按裁剪框宽高分别计算
    LaunchedEffect(scale, derived) {
        val d = derived ?: return@LaunchedEffect
        val maxOX = (d.baseW * scale - d.cropW) / 2f
        val maxOY = (d.baseH * scale - d.cropH) / 2f
        // 缩到小于裁剪框时（maxOX/maxOY < 0）重置偏移为 0，避免 coerceIn 空范围异常
        if (maxOX >= 0f) offsetX = offsetX.coerceIn(-maxOX, maxOX) else offsetX = 0f
        if (maxOY >= 0f) offsetY = offsetY.coerceIn(-maxOY, maxOY) else offsetY = 0f
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏：取消 / 标题 / 确认
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onCancel,
                    enabled = !cropping
                ) {
                    Text(
                        "取消",
                        color = if (!cropping) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = {
                        val d = derived
                        if (bmp != null && d != null && !cropping) {
                            cropping = true
                            // 协程异常加固：用 try/catch 包裹整个协程体，
                            // 确保任何异常都被记录而非导致应用闪退。
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        cropAndSave(
                                            context = context,
                                            bitmap = bmp,
                                            outputName = outputName,
                                            derived = d,
                                            scale = scale,
                                            offsetX = offsetX,
                                            offsetY = offsetY,
                                            outputSubdir = outputSubdir,
                                            outputWidth = outputWidth,
                                            outputHeight = outputHeight
                                        )
                                    }
                                    result?.let(onCropComplete)
                                } catch (e: Throwable) {
                                    // 包括 Exception 和 Error（如 OOM），确保不会导致协程未捕获异常闪退
                                    CrashLogger.logException(context, e, "ImageCropper.coroutine")
                                } finally {
                                    cropping = false
                                }
                            }
                        }
                    },
                    enabled = bmp != null && derived != null && !cropping
                ) {
                    Text(
                        "确认",
                        color = if (bmp != null && derived != null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }
            }

            // 内容区：图片 + 裁剪框遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { contentSize = it.size }
                    .then(
                        if (bmp != null && derived != null)
                            Modifier
                                // 双击缩放：
                                // - 双击在 minScale 和 2.5x minScale 之间切换
                                // - detectTapGestures 仅消费 tap 事件，不影响 detectTransformGestures 的拖拽/双指缩放
                                .pointerInput(bmp, derived) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            val targetScale = if (scale > derived.minScale * 1.5f) {
                                                derived.minScale
                                            } else {
                                                (derived.minScale * 2.5f).coerceAtMost(derived.maxScale)
                                            }
                                            scale = targetScale
                                            val maxOX = (derived.baseW * targetScale - derived.cropW) / 2f
                                            val maxOY = (derived.baseH * targetScale - derived.cropH) / 2f
                                            // 缩到小于裁剪框时重置偏移为 0（与手势处理一致）
                                            if (maxOX >= 0f) offsetX = offsetX.coerceIn(-maxOX, maxOX) else offsetX = 0f
                                            if (maxOY >= 0f) offsetY = offsetY.coerceIn(-maxOY, maxOY) else offsetY = 0f
                                        }
                                    )
                                }
                                // 双指缩放 + 拖拽
                                .pointerInput(bmp, derived) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(derived.minScale, derived.maxScale)
                                        scale = newScale
                                        val maxOX = (derived.baseW * newScale - derived.cropW) / 2f
                                        val maxOY = (derived.baseH * newScale - derived.cropH) / 2f
                                        // 图片缩到小于裁剪框时（maxOX/maxOY < 0），不允许平移——
                                        // 否则 coerceIn(-maxOX, maxOX) 会变成 coerceIn(正, 负) 抛
                                        // IllegalArgumentException: Cannot coerce value to an empty range。
                                        // 与 Slider 处理保持一致的守卫。
                                        if (maxOX >= 0f) {
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOX, maxOX)
                                        } else {
                                            offsetX = 0f
                                        }
                                        if (maxOY >= 0f) {
                                            offsetY = (offsetY + pan.y).coerceIn(-maxOY, maxOY)
                                        } else {
                                            offsetY = 0f
                                        }
                                    }
                                }
                        else Modifier
                    )
            ) {
                if (bmp != null && derived != null) {
                    val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "待裁剪图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(
                                width = with(density) { derived.baseW.toDp() },
                                height = with(density) { derived.baseH.toDp() }
                            )
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                    CropOverlay(cropW = derived.cropW, cropH = derived.cropH)

                    // 缩放滑块（底部半透明条，用户可拖动精确控制缩放）
                    // 滑块提供明确的缩放交互入口，与双击/双指缩放三者互补。
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val sliderPosition = ((scale - derived.minScale) / (derived.maxScale - derived.minScale))
                            .coerceIn(0f, 1f)
                        val zoomPercent = (scale * 100).roundToInt()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "缩放",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "${zoomPercent}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Slider(
                                value = sliderPosition,
                                onValueChange = { pos ->
                                    val newScale = derived.minScale +
                                        pos * (derived.maxScale - derived.minScale)
                                    scale = newScale
                                    val maxOX = (derived.baseW * newScale - derived.cropW) / 2f
                                    val maxOY = (derived.baseH * newScale - derived.cropH) / 2f
                                    if (maxOX >= 0f) offsetX = offsetX.coerceIn(-maxOX, maxOX)
                                    if (maxOY >= 0f) offsetY = offsetY.coerceIn(-maxOY, maxOY)
                                },
                                valueRange = 0f..1f
                            )
                        }
                    }
                } else if (loadError) {
                    // 图片加载失败：显示错误提示，用户可点"取消"返回
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "图片加载失败",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }

        // 裁剪进行中的遮罩（置于外层 Box，避免 ColumnScope.AnimatedVisibility 歧义）
        AnimatedVisibility(
            visible = cropping,
            enter = fadeIn(tween(Motion.DurationShort, easing = Motion.EasingStandard)),
            exit = fadeOut(tween(Motion.DurationShort, easing = Motion.EasingStandard)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/**
 * 由容器尺寸与原始 Bitmap 计算出的布局派生参数（支持非正方形裁剪框）。
 *
 * @param cropW 裁剪框宽度（显示像素）
 * @param cropH 裁剪框高度（显示像素）
 * @param baseW 原图 Fit 进容器后的显示宽度
 * @param baseH 原图 Fit 进容器后的显示高度
 * @param minScale 最小缩放（确保原图填满裁剪框）
 * @param maxScale 最大缩放（minScale × 6，提供足够放大空间）
 */
private data class DerivedParams(
    val cropW: Float,
    val cropH: Float,
    val baseW: Float,
    val baseH: Float,
    val minScale: Float,
    val maxScale: Float
)

/**
 * 任意宽高比裁剪框：白色边框 + 框外半透明遮罩（矩形）。
 *
 * @param cropW 裁剪框宽度（显示像素）
 * @param cropH 裁剪框高度（显示像素）
 */
@Composable
private fun CropOverlay(cropW: Float, cropH: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val left = cx - cropW / 2f
        val top = cy - cropH / 2f
        val right = cx + cropW / 2f
        val bottom = cy + cropH / 2f
        val mask = Color.Black.copy(alpha = 0.5f)
        // 四周遮罩
        drawRect(mask, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(mask, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(mask, topLeft = Offset(0f, top), size = Size(left, cropH))
        drawRect(mask, topLeft = Offset(right, top), size = Size(size.width - right, cropH))
        // 白色边框
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(cropW, cropH),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * 根据当前变换状态从 [bitmap] 中截取目标区域，缩放到指定输出尺寸，
 * 以 JPEG（质量 90）写入 filesDir/{outputSubdir}/，返回文件 URI。
 *
 * 支持任意宽高比的裁剪框。
 *
 * @param outputSubdir 输出子目录名（avatars / wallpapers / list_wallpapers）
 * @param outputWidth 输出图片宽度（像素）
 * @param outputHeight 输出图片高度（像素）
 */
private fun cropAndSave(
    context: Context,
    bitmap: Bitmap,
    outputName: String?,
    derived: DerivedParams,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    outputSubdir: String,
    outputWidth: Int,
    outputHeight: Int
): Uri? {
    // 深拷贝原始 Bitmap，避免异步裁剪时原图被回收导致崩溃。
    // 深拷贝（mutable = true）让裁剪线程完全独立持有像素数据。
    // 防护：先判定 isRecycled，再用 try/catch 兜底；即使原图异常也仅返回 null 并记录日志。
    if (bitmap.isRecycled) {
        Log.w("ImageCropper", "原图已被回收，取消裁剪")
        return null
    }
    val sourceBitmap = try {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    } catch (e: OutOfMemoryError) {
        Log.e("ImageCropper", "复制原图时内存不足", e)
        CrashLogger.logException(context, e, "ImageCropper")
        null
    } catch (e: Exception) {
        Log.e("ImageCropper", "复制原图失败", e)
        CrashLogger.logException(context, e, "ImageCropper")
        null
    } ?: return null
    try {
        val dispW = derived.baseW * scale
        val dispH = derived.baseH * scale
        // 裁剪框左上角相对于图片显示左上角的偏移（显示像素）
        val deltaX = dispW / 2f - derived.cropW / 2f - offsetX
        val deltaY = dispH / 2f - derived.cropH / 2f - offsetY
        // 输出画布与裁剪框的缩放比（裁剪框显示像素 → 输出像素）
        val drawScale = outputWidth.toFloat() / derived.cropW
        // 图片左上角在输出画布坐标系中的位置：
        // 图片左上角相对裁剪框左上角 = -deltaX, -deltaY；再乘 drawScale 转为输出像素
        val drawX = -deltaX * drawScale
        val drawY = -deltaY * drawScale
        val drawW = dispW * drawScale
        val drawH = dispH * drawScale
        // 创建输出画布：白色背景填充（JPEG 不支持透明，未覆盖区域需有底色）
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bgPaint = Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), bgPaint)
        // 把原图绘制到画布上：超出画布的部分自动裁掉，画布内未覆盖部分保持白色
        val srcRect = Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
        val dstRect = RectF(drawX, drawY, drawX + drawW, drawY + drawH)
        canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)
        // 保存到 filesDir/{outputSubdir}/；若调用方传入 [outputName] 则使用稳定文件名，
        // 直接覆盖旧文件，既避免文件无限累积，也不会误删正在显示的头像。
        val outDir = File(context.filesDir, outputSubdir).apply { mkdirs() }
        val file = if (!outputName.isNullOrBlank()) {
            File(outDir, "$outputName.jpg")
        } else {
            File(outDir, "${IdGenerator.newId(IdGenerator.Prefix.AVATAR_TEMP)}.jpg")
        }
        try {
            // 仅在未指定稳定文件名时清理其他临时文件，避免误删当前显示的文件。
            if (outputName.isNullOrBlank()) {
                outDir.listFiles()?.forEach { existing ->
                    if (existing.name.startsWith(IdGenerator.Prefix.AVATAR_TEMP.value) && existing != file) {
                        existing.delete()
                    }
                }
            }
            FileOutputStream(file).use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } finally {
            // 输出画布写入后立即回收，避免每次裁剪泄漏一张输出尺寸的 Bitmap。
            if (!output.isRecycled) output.recycle()
        }
        // 使用 FileProvider 生成 content:// URI，避免 file:// URI 在部分场景下被系统限制。
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: OutOfMemoryError) {
        Log.e("ImageCropper", "裁剪过程中内存不足", e)
        CrashLogger.logException(context, e, "ImageCropper")
        return null
    } catch (e: Exception) {
        Log.e("ImageCropper", "裁剪或保存失败: subdir=$outputSubdir", e)
        CrashLogger.logException(context, e, "ImageCropper")
        return null
    } finally {
        // 回收深拷贝的原始 Bitmap，避免内存泄漏
        if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
    }
}

/** 解码 URI 对应的 Bitmap，过大时按 2 的幂次抽样以保证最大边长不超过 [MAX_SOURCE_DIM]。 */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > MAX_SOURCE_DIM) sampleSize *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // 统一使用 ARGB_8888，避免某些格式解码出 HARDWARE/RGBA_F16 后 copy 异常。
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: OutOfMemoryError) {
        Log.e("ImageCropper", "加载图片时内存不足: $uri", e)
        CrashLogger.logException(context, e, "ImageCropper")
        null
    } catch (e: Exception) {
        Log.e("ImageCropper", "加载图片失败: $uri", e)
        CrashLogger.logException(context, e, "ImageCropper")
        null
    }
}
