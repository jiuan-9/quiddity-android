package com.quiddity.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行调试注释等）。
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



/**
 * 对话长图导出器（固定浅色样式，纯 Canvas 自绘）。
 *
 * 用法：
 * 1. [buildSegments] 把消息列表转成可绘制的分段（过滤 isNotice、保持顺序）；
 * 2. [fitSegments] 按最大高度截断（纯逻辑，可单测）；
 * 3. [exportToFile] 生成 PNG 到 cacheDir；
 * 4. [share] 通过 FileProvider 弹系统分享。
 *
 * 样式：白底、用户=蓝色右气泡、AI=浅灰左气泡、彩色圆+首字母代替头像、
 * 时间戳在气泡下方、顶部标题栏显示 AI 名/会话名/生成时间。
 */
object ChatImageExporter {

    /** 画布宽度（px）。 */
    const val IMAGE_WIDTH = 1080

    /** 画布最大高度（px），超过则在最后一条完整消息处截断并追加尾注。 */
    const val MAX_HEIGHT = 12_000

    private const val SIDE_PADDING = 48
    private const val AVATAR_SIZE = 64
    private const val AVATAR_GAP = 16
    private const val BUBBLE_MAX_WIDTH = 760
    private const val BUBBLE_PADDING_H = 20
    private const val BUBBLE_PADDING_V = 16
    private const val BUBBLE_RADIUS = 24f
    private const val TEXT_SIZE = 32f
    private const val LINE_SPACING_EXTRA = 6f
    private const val TIME_TEXT_SIZE = 20f
    private const val ROW_GAP = 14
    private const val HEADER_HEIGHT = 196
    private const val FOOTER_HEIGHT = 64

    // 固定浅色调色板（不随 App 深/浅主题变化）
    private const val COLOR_BACKGROUND = 0xFFFFFFFF.toInt()
    private const val COLOR_HEADER_TITLE = 0xFF1F2329.toInt()
    private const val COLOR_HEADER_SUB = 0xFF8A919F.toInt()
    private const val COLOR_HEADER_DIVIDER = 0xFFEDEFF2.toInt()
    private const val COLOR_AI_BUBBLE = 0xFFF2F3F5.toInt()
    private const val COLOR_AI_TEXT = 0xFF1F2329.toInt()
    private const val COLOR_AI_AVATAR = 0xFFDDE3EA.toInt()
    private const val COLOR_USER_BUBBLE = 0xFF3478F6.toInt()
    private const val COLOR_USER_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_TIME = 0xFF9AA0A6.toInt()
    private const val COLOR_FOOTER = 0xFF9AA0A6.toInt()

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * 单条待绘制消息。
     *
     * @param isUser true=用户消息（右侧蓝色气泡），false=AI 消息（左侧浅灰气泡）
     * @param content 消息正文（含换行）
     * @param timestamp 消息时间戳（毫秒）
     */
    data class Segment(
        val isUser: Boolean,
        val content: String,
        val timestamp: Long
    )

    /** [fitSegments] 的结果。 */
    data class FittingResult(
        val segments: List<Segment>,
        /** true=因超过最大高度被截断，绘制时需追加尾注。 */
        val truncated: Boolean
    )

    /**
     * 把消息列表转为分段：过滤 isNotice 提示气泡、保持原顺序。
     * 纯逻辑，可单测。
     */
    fun buildSegments(messages: List<Message>): List<Segment> =
        messages
            .filterNot { it.isNotice }
            .map { message ->
                Segment(
                    isUser = message.role == Role.USER,
                    content = message.content,
                    timestamp = message.timestamp
                )
            }

    /**
     * 按最大高度截断分段列表（纯逻辑，可单测）。
     *
     * 规则：从前往后累加每段高度（[heightOf] 注入），若"已选高度 + 本段高度 + 尾注高度"
     * 超出 [maxHeight] 则停止；首段即使超高也保留（保证不会导出空图）。
     *
     * @param segments 待截断的分段
     * @param maxHeight 画布最大高度
     * @param footerHeight 截断尾注占用的高度
     * @param heightOf 单段高度计算函数（渲染时用真实测量值，测试时注入假值）
     */
    fun fitSegments(
        segments: List<Segment>,
        maxHeight: Int,
        footerHeight: Int,
        heightOf: (Segment) -> Int
    ): FittingResult {
        var used = 0
        val chosen = mutableListOf<Segment>()
        segments.forEach { segment ->
            val h = heightOf(segment)
            if (used > 0 && used + h + footerHeight > maxHeight) {
                return FittingResult(chosen, truncated = true)
            }
            chosen += segment
            used += h
        }
        return FittingResult(chosen, truncated = false)
    }

    /**
     * 生成长图 PNG 并写入 cacheDir（文件名 quiddity_share_<时间戳>.png）。
     * 渲染在后台线程执行。
     */
    suspend fun exportToFile(
        context: Context,
        messages: List<Message>,
        aiName: String,
        conversationTitle: String
    ): File = withContext(Dispatchers.Default) {
        val segments = buildSegments(messages)
        val bitmap = renderBitmap(segments, aiName, conversationTitle)
        val file = File(
            context.cacheDir,
            "quiddity_share_${System.currentTimeMillis()}.png"
        )
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        file
    }

    /**
     * 通过系统分享面板分享图片（FileProvider content:// URI）。
     *
     * @return true=已成功发起分享；false=没有可用应用处理分享
     */
    fun share(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享长图"))
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 渲染（真机环境，非纯逻辑） ====================

    private fun renderBitmap(
        segments: List<Segment>,
        aiName: String,
        conversationTitle: String
    ): Bitmap {
        val fit = fitSegments(segments, MAX_HEIGHT, FOOTER_HEIGHT, ::segmentHeight)
        val list = fit.segments
        val footerH = if (fit.truncated) FOOTER_HEIGHT else 0
        var totalHeight = HEADER_HEIGHT
        list.forEach { totalHeight += segmentHeight(it) + ROW_GAP }
        if (footerH > 0) totalHeight += footerH + ROW_GAP

        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(COLOR_BACKGROUND)

        drawHeader(canvas, aiName, conversationTitle)

        var y = HEADER_HEIGHT
        list.forEach { segment ->
            y += drawSegment(canvas, segment, y, aiName)
            y += ROW_GAP
        }
        if (fit.truncated) {
            drawFooter(canvas, y, fit.segments.size)
        }
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, aiName: String, conversationTitle: String) {
        val name = aiName.ifBlank { "AI" }
        val dateText = dateFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()))

        val titlePaint = textPaint(TEXT_SIZE, bold = true).apply { color = COLOR_HEADER_TITLE }
        val subPaint = textPaint(26f, bold = false).apply { color = COLOR_HEADER_SUB }
        val datePaint = textPaint(22f, bold = false).apply { color = COLOR_HEADER_SUB }

        var y = 42
        y += drawText(canvas, name, SIDE_PADDING, y.toFloat(), titlePaint)
        y += 6
        if (conversationTitle.isNotBlank()) {
            y += drawText(canvas, conversationTitle, SIDE_PADDING, y.toFloat(), subPaint)
            y += 6
        }
        y += drawText(canvas, dateText, SIDE_PADDING, y.toFloat(), datePaint)

        val dividerPaint = Paint().apply {
            color = COLOR_HEADER_DIVIDER
            strokeWidth = 2f
        }
        canvas.drawLine(
            SIDE_PADDING.toFloat(),
            HEADER_HEIGHT - 4f,
            (IMAGE_WIDTH - SIDE_PADDING).toFloat(),
            HEADER_HEIGHT - 4f,
            dividerPaint
        )
    }

    private fun drawSegment(canvas: Canvas, segment: Segment, top: Int, aiName: String): Int {
        val isUser = segment.isUser
        val bubbleW = BUBBLE_MAX_WIDTH
        val textW = bubbleW - BUBBLE_PADDING_H * 2
        val textH = measureTextHeight(segment.content, textW, TEXT_SIZE, bold = false)
        val bubbleH = textH + BUBBLE_PADDING_V * 2
        val rowH = maxOf(bubbleH, AVATAR_SIZE) + 28

        // 头像：彩色圆 + 首字符
        val avatarLeft = if (isUser) {
            IMAGE_WIDTH - SIDE_PADDING - AVATAR_SIZE
        } else {
            SIDE_PADDING
        }
        val avatarTop = top + 2
        drawAvatar(canvas, avatarLeft, avatarTop, isUser, aiName)

        // 气泡
        val bubbleLeft = if (isUser) {
            IMAGE_WIDTH - SIDE_PADDING - AVATAR_SIZE - AVATAR_GAP - bubbleW
        } else {
            SIDE_PADDING + AVATAR_SIZE + AVATAR_GAP
        }
        val bubbleRect = RectF(
            bubbleLeft.toFloat(),
            top.toFloat(),
            (bubbleLeft + bubbleW).toFloat(),
            (top + bubbleH).toFloat()
        )
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isUser) COLOR_USER_BUBBLE else COLOR_AI_BUBBLE
        }
        canvas.drawRoundRect(bubbleRect, BUBBLE_RADIUS, BUBBLE_RADIUS, bubblePaint)

        val textPaint = textPaint(TEXT_SIZE, bold = false).apply {
            color = if (isUser) COLOR_USER_TEXT else COLOR_AI_TEXT
        }
        drawText(
            canvas,
            segment.content,
            bubbleLeft + BUBBLE_PADDING_H,
            (top + BUBBLE_PADDING_V).toFloat(),
            textPaint,
            textW
        )

        // 时间戳
        val timeText = timeFormatter.format(
            Instant.ofEpochMilli(segment.timestamp).atZone(ZoneId.systemDefault())
        )
        val timePaint = textPaint(TIME_TEXT_SIZE, bold = false).apply { color = COLOR_TIME }
        val timeX = if (isUser) {
            (IMAGE_WIDTH - SIDE_PADDING - bubbleW - measureTextWidth(timeText, timePaint)).toInt()
        } else {
            bubbleLeft
        }
        drawText(canvas, timeText, timeX, (top + bubbleH + 24).toFloat(), timePaint)

        return rowH
    }

    private fun drawAvatar(canvas: Canvas, left: Int, top: Int, isUser: Boolean, aiName: String) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isUser) COLOR_USER_BUBBLE else COLOR_AI_AVATAR
        }
        val cx = (left + AVATAR_SIZE / 2).toFloat()
        val cy = (top + AVATAR_SIZE / 2).toFloat()
        canvas.drawCircle(cx, cy, AVATAR_SIZE / 2f, bgPaint)

        val initial = if (isUser) {
            "我"
        } else {
            aiName.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "AI"
        }
        val textPaint = textPaint(26f, bold = true).apply {
            color = if (isUser) COLOR_USER_TEXT else COLOR_AI_TEXT
            textAlign = Paint.Align.CENTER
        }
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, cx, baseline, textPaint)
    }

    private fun drawFooter(canvas: Canvas, top: Int, exportedCount: Int) {
        val footerText = "内容过长，仅导出前 $exportedCount 条"
        val paint = textPaint(22f, bold = false).apply {
            color = COLOR_FOOTER
            textAlign = Paint.Align.CENTER
        }
        val baseline = top + 36f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(footerText, IMAGE_WIDTH / 2f, baseline, paint)
    }

    private fun segmentHeight(segment: Segment): Int {
        val textW = BUBBLE_MAX_WIDTH - BUBBLE_PADDING_H * 2
        val textH = measureTextHeight(segment.content, textW, TEXT_SIZE, bold = false)
        val bubbleH = textH + BUBBLE_PADDING_V * 2
        return maxOf(bubbleH, AVATAR_SIZE) + 28
    }

    private fun textPaint(size: Float, bold: Boolean): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun measureTextHeight(
        text: String,
        width: Int,
        textSize: Float,
        bold: Boolean
    ): Int {
        val paint = textPaint(textSize, bold)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setLineSpacing(LINE_SPACING_EXTRA, 1f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
            .height
    }

    private fun measureTextWidth(text: String, paint: TextPaint): Float =
        paint.measureText(text)

    /** 绘制单行/多行文本，返回实际占用高度。 */
    private fun drawText(
        canvas: Canvas,
        text: String,
        left: Int,
        top: Float,
        paint: TextPaint,
        width: Int? = null
    ): Int {
        val w = width ?: min(BUBBLE_MAX_WIDTH, IMAGE_WIDTH - SIDE_PADDING * 2)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, w)
            .setLineSpacing(LINE_SPACING_EXTRA, 1f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(left.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }
}
