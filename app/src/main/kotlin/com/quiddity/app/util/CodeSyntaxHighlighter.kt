package com.quiddity.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle

/**
 * 代码语法高亮引擎（Android 端，对齐 PC 端 syntax-highlight.js）。
 *
 * 设计原则：
 * - 纯 Kotlin 实现，不依赖第三方高亮库
 * - 支持 JS/TS/Java/Kotlin/C/C++/Go/Rust/Python/SQL/Bash/YAML/JSON 等常见语言
 * - 使用占位符法保护字符串/注释，避免关键字误伤
 * - 输出 [AnnotatedString]，可直接渲染到 Compose Text
 *
 * 算法步骤（对齐 PC 端 highlightCode）：
 * 1. HTML 转义（PC 端有；Android 端 Compose Text 不需要）
 * 2. 保护注释（// 和 /* */、# 风格、-- 风格）
 * 3. 保护字符串（"..."、'...'、`...`、"""..."""、'''...'''）
 * 4. 染色关键字
 * 5. 染色数字
 * 6. 染色函数调用
 * 7. 还原占位符
 */
object CodeSyntaxHighlighter {

    // ===== 关键字表 =====

    private val KEYWORDS_COMMON = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "break",
        "continue", "return", "try", "catch", "finally", "throw", "new",
        "typeof", "instanceof", "void", "delete", "in", "of", "async", "await",
        "class", "extends", "super", "import", "export", "default", "from",
        "const", "let", "var", "function", "yield", "static", "get", "set",
        "public", "private", "protected", "interface", "type", "enum",
        "true", "false", "null", "undefined", "this",
        "struct", "union", "namespace", "using", "template", "typename",
        "virtual", "override", "final", "inline", "explicit", "operator",
        "friend", "volatile", "register", "auto", "extern", "sizeof",
        "func", "package", "data", "object", "sealed", "internal", "open",
        "by", "is", "as", "when", "companion", "init", "lateinit", "val",
        "vararg", "crossinline", "noinline", "reified", "tailrec", "suspend",
        "annotation", "fun", "typealias", "where"
    )

    private val KEYWORDS_PYTHON = setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else",
        "for", "while", "try", "except", "finally", "with", "as", "pass",
        "break", "continue", "yield", "lambda", "and", "or", "not", "in", "is",
        "global", "nonlocal", "assert", "raise", "del",
        "None", "True", "False", "self", "print", "range", "len", "type",
        "int", "str", "float", "bool", "list", "dict", "set", "tuple",
        "async", "await", "asyncio"
    )

    private val KEYWORDS_SQL = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE",
        "ALTER", "DROP", "TABLE", "INDEX", "INTO", "VALUES", "SET",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY",
        "ORDER", "ASC", "DESC", "HAVING", "LIMIT", "OFFSET", "UNION",
        "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "DISTINCT", "AS",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
        "TRANSACTION", "COMMIT", "ROLLBACK", "BEGIN", "VIEW", "TRIGGER", "PROCEDURE"
    )

    private val KEYWORDS_BASH = setOf(
        "if", "then", "else", "elif", "fi", "for", "do", "done", "while",
        "until", "case", "esac", "function", "return", "exit", "break",
        "continue", "local", "export", "readonly", "declare", "typeset",
        "echo", "printf", "read", "set", "unset", "shift", "source", "alias",
        "true", "false", "test", "cd", "pwd", "ls", "mkdir", "rm", "cp", "mv",
        "cat", "grep", "sed", "awk", "find", "chmod", "chown", "sudo", "apt",
        "yum", "brew", "git", "npm", "node", "python", "python3", "pip", "java"
    )

    private val KEYWORDS_GO = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer",
        "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
        "interface", "map", "package", "range", "return", "select", "struct",
        "switch", "type", "var", "nil", "true", "false", "iota", "make",
        "new", "len", "cap", "append", "copy", "delete", "panic", "recover"
    )

    private val KEYWORDS_RUST = setOf(
        "fn", "let", "mut", "if", "else", "for", "while", "loop", "match",
        "return", "break", "continue", "struct", "enum", "trait", "impl",
        "pub", "use", "mod", "crate", "self", "Self", "super", "as", "in",
        "ref", "move", "where", "type", "const", "static", "unsafe",
        "true", "false", "Some", "None", "Ok", "Err", "Result", "Option",
        "Vec", "String", "Box", "Rc", "Arc", "RefCell", "Cell"
    )

    /**
     * 根据语言标识获取关键字集合。
     */
    private fun getKeywords(lang: String): Set<String> {
        return when (lang.lowercase()) {
            "python", "py" -> KEYWORDS_PYTHON
            "sql" -> KEYWORDS_SQL
            "bash", "sh", "shell", "zsh" -> KEYWORDS_BASH
            "go", "golang" -> KEYWORDS_GO
            "rust", "rs" -> KEYWORDS_RUST
            else -> KEYWORDS_COMMON
        }
    }

    /**
     * 判断语言是否使用 # 风格注释（Python/Bash/YAML/Ruby）。
     */
    private fun usesHashComment(lang: String): Boolean {
        return when (lang.lowercase()) {
            "python", "py", "sh", "bash", "shell", "zsh",
            "yaml", "yml", "rb", "ruby", "toml", "perl", "pl" -> true
            else -> false
        }
    }

    /**
     * 判断语言是否使用 -- 风格注释（SQL/Lua/Haskell）。
     */
    private fun usesDoubleDashComment(lang: String): Boolean {
        return when (lang.lowercase()) {
            "sql", "lua", "haskell", "hs" -> true
            else -> false
        }
    }

    // ===== 颜色定义（暗色主题优先，亮色主题也能识别） =====
    // 这些颜色来自 PC 端 hl-cmt / hl-str / hl-kw / hl-num / hl-fn / hl-built 的 CSS

    /** 注释颜色：灰色 */
    private val COLOR_COMMENT = Color(0xFF8C9BAE)
    /** 字符串颜色：绿色 */
    private val COLOR_STRING = Color(0xFF7CB342)
    /** 关键字颜色：紫色/粉红 */
    private val COLOR_KEYWORD = Color(0xFFC792EA)
    /** 数字颜色：橙色 */
    private val COLOR_NUMBER = Color(0xFFF78C6C)
    /** 函数调用名称颜色：蓝色 */
    private val COLOR_FUNCTION = Color(0xFF82AAFF)
    /** 类型/内置名称颜色：青色 */
    private val COLOR_BUILTIN = Color(0xFF89DDFF)
    /** 普通代码文本颜色 */
    val COLOR_PLAIN = Color(0xFFEEFFFF)
    /** 标点符号颜色 */
    private val COLOR_PUNCT = Color(0xFF89DDFF)

    /**
     * 对代码进行语法高亮，返回 [AnnotatedString] 供 Compose Text 直接渲染。
     *
     * @param code 原始代码
     * @param lang 语言标识（如 "kotlin"、"python"、"js"）
     * @return 高亮后的 AnnotatedString
     */
    fun highlight(code: String, lang: String): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        // 占位符表：index → SpanStyle
        // 占位符格式：\u0000HL<index>\u0000（与 PC 端一致）
        val placeholders = mutableListOf<AnnotatedString>()
        var nextId = 0
        fun makePlaceholder(content: String, style: SpanStyle): String {
            val id = nextId++
            // 占位符内嵌样式索引；最终还原时替换为带样式的子串
            placeholders.add(
                buildAnnotatedString {
                    withStyle(style) { append(content) }
                }
            )
            return "\u0000HL$id\u0000"
        }

        // 处理过程：每次 replace 后文本里只剩占位符 + 未染色文本
        var text: String = code

        // ===== 步骤 1：保护注释 =====

        // 多行注释 /* */（JS/TS/C/Java/Kotlin 等）
        if (usesSlashStarComment(lang)) {
            text = replaceAll(text, Regex("/\\*[\\s\\S]*?\\*/")) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_COMMENT, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            }
        }

        // 单行 // 注释（JS/TS/C/Java/Kotlin/Rust/Go 等）
        if (usesSlashSlashComment(lang)) {
            text = replaceAll(text, Regex("//[^\\n]*")) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_COMMENT, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            }
        }

        // # 风格注释（Python/Bash/YAML/Ruby 等）
        if (usesHashComment(lang)) {
            text = replaceAll(text, Regex("(?:^|\\s)#[^\\n]*")) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_COMMENT, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            }
        }

        // -- 风格注释（SQL/Lua/Haskell）
        if (usesDoubleDashComment(lang)) {
            text = replaceAll(text, Regex("--[^\\n]*")) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_COMMENT, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            }
        }

        // ===== 步骤 2：保护字符串 =====

        // Python 三引号字符串
        if (lang.lowercase() == "python" || lang.lowercase() == "py") {
            text = replaceAll(text, Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''")) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_STRING))
            }
        }

        // 模板字符串 `...`
        text = replaceAll(text, Regex("`(?:[^`\\\\]|\\\\.)*`")) { m ->
            makePlaceholder(m, SpanStyle(color = COLOR_STRING))
        }

        // 双引号字符串 "..."
        text = replaceAll(text, Regex("\"(?:[^\"\\\\]|\\\\.)*\"")) { m ->
            makePlaceholder(m, SpanStyle(color = COLOR_STRING))
        }

        // 单引号字符串 '...'
        text = replaceAll(text, Regex("'(?:[^'\\\\]|\\\\.)*'")) { m ->
            makePlaceholder(m, SpanStyle(color = COLOR_STRING))
        }

        // ===== 步骤 3：染色关键字 =====

        val keywords = getKeywords(lang)
        // 按长度降序匹配，避免短关键字误匹配长关键字的前缀
        val sortedKeywords = keywords.sortedByDescending { it.length }
        for (kw in sortedKeywords) {
            val escaped = Regex.escape(kw)
            // \b 边界匹配（注意 SQL 关键字全大写时大小写敏感）
            val pattern = if (lang.lowercase() == "sql") {
                Regex("\\b($escaped)\\b")
            } else {
                Regex("\\b($escaped)\\b", RegexOption.IGNORE_CASE)
            }
            text = replaceAll(text, pattern) { m ->
                makePlaceholder(m, SpanStyle(color = COLOR_KEYWORD, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            }
        }

        // ===== 步骤 4：染色数字 =====
        text = replaceAll(text, Regex("\\b(\\d+\\.?\\d*[fLdD]?)\\b")) { m ->
            makePlaceholder(m, SpanStyle(color = COLOR_NUMBER))
        }

        // ===== 步骤 5：染色函数调用名称 =====
        // 形如 identifier(
        text = replaceAll(text, Regex("\\b([a-zA-Z_]\\w*)\\s*\\(")) { m ->
            // 提取标识符（去掉末尾的 ( ）
            val name = m.trimEnd('(', ' ', '\t')
            val placeholder = makePlaceholder(name, SpanStyle(color = COLOR_FUNCTION))
            "$placeholder("
        }

        // ===== 步骤 6：染色布尔/None/nil 等内置常量（已被关键字覆盖，跳过） =====

        // ===== 步骤 7：染色标点（轻量级，仅匹配常见符号） =====
        // 注意：标点染色会让代码"花哨"，PC 端 CSS 也有此效果，但对 Android
        // 小屏阅读体验反而干扰，因此默认关闭。如需开启可解开下方代码。
        // text = replaceAll(text, Regex("[{}()\\[\\].,;:?=+\\-*/%<>!&|^~]")) { m ->
        //     makePlaceholder(m, SpanStyle(color = COLOR_PUNCT))
        // }

        // ===== 步骤 8：还原占位符，构建最终 AnnotatedString =====
        return buildAnnotatedString {
            var i = 0
            val len = text.length
            while (i < len) {
                // 检测占位符开始
                if (text[i] == '\u0000' && i + 3 < len && text.substring(i, i + 3) == "\u0000HL") {
                    // 找到下一个 \u0000
                    val end = text.indexOf('\u0000', i + 3)
                    if (end > i) {
                        val idStr = text.substring(i + 3, end)
                        val id = idStr.toIntOrNull()
                        if (id != null && id < placeholders.size) {
                            append(placeholders[id])
                            i = end + 1
                            continue
                        }
                    }
                }
                // 普通字符：累积到下一个占位符或字符串末尾
                val nextPh = text.indexOf("\u0000HL", i)
                val endPos = if (nextPh > i) nextPh else len
                // 普通代码文本染色为 COLOR_PLAIN（避免在彩色背景上太亮）
                withStyle(SpanStyle(color = COLOR_PLAIN, fontFamily = FontFamily.Monospace)) {
                    append(text.substring(i, endPos))
                }
                i = endPos
            }
        }
    }

    /**
     * 替换字符串中所有匹配 [regex] 的子串为 [transform] 的结果。
     * 与 JS 的 String.replace(regex, callback) 等价。
     */
    private inline fun replaceAll(
        text: String,
        regex: Regex,
        transform: (matched: String) -> String
    ): String {
        val sb = StringBuilder()
        var lastEnd = 0
        for (match in regex.findAll(text)) {
            sb.append(text, lastEnd, match.range.first)
            sb.append(transform(match.value))
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            sb.append(text, lastEnd, text.length)
        }
        return sb.toString()
    }

    /**
     * 判断语言是否使用 /* */ 注释。
     */
    private fun usesSlashStarComment(lang: String): Boolean {
        return when (lang.lowercase()) {
            "javascript", "js", "typescript", "ts", "jsx", "tsx",
            "java", "kotlin", "kt", "kts", "c", "cpp", "c++", "cc", "cxx",
            "csharp", "cs", "go", "golang", "rust", "rs", "swift",
            "scala", "dart", "json", "json5", "css", "less", "scss", "php" -> true
            else -> false
        }
    }

    /**
     * 判断语言是否使用 // 注释。
     */
    private fun usesSlashSlashComment(lang: String): Boolean {
        return when (lang.lowercase()) {
            "javascript", "js", "typescript", "ts", "jsx", "tsx",
            "java", "kotlin", "kt", "kts", "c", "cpp", "c++", "cc", "cxx",
            "csharp", "cs", "go", "golang", "rust", "rs", "swift",
            "scala", "dart", "php" -> true
            else -> false
        }
    }
}
