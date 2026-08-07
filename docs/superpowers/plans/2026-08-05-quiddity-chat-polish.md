# Quiddity 聊天体验优化（11 项）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按设计文档 `docs/superpowers/specs/2026-08-05-quiddity-chat-polish-design.md` 逐条落地 11 项聊天体验优化。

**Architecture:** 提示词类改动集中在 `domain/`（PromptBuilder / QuickSetupPrompt）；持久化在 `Conversation` 加默认值字段（kotlinx `ignoreUnknownKeys` 向后兼容）；UI 改动按组件隔离（QuickSetupPanel / RewriteBottomSheet / ChatInputBar / 设置面板）；延迟逻辑抽纯函数便于单测。

**Tech Stack:** Kotlin 2.0.21 / Jetpack Compose / kotlinx-serialization / kotlin.test（JVM 单元测试，任务 `testDebugUnitTest`）。

**测试命令（Windows PowerShell）：** `.\gradlew.bat testDebugUnitTest --tests "<FQCN>"`；全量 `.\gradlew.bat testDebugUnitTest`。

---

## Task 1: 对话纪律（#6/#7 提示词层）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/domain/PromptBuilder.kt`
- Test: `app/src/test/kotlin/com/quiddity/app/domain/PromptBuilderTest.kt`

- [ ] **Step 1: 写失败测试**（在 PromptBuilderTest 末尾追加）

```kotlin
    // ============================================================
    // 对话纪律（回复认知 + 台词完整性）
    // ============================================================

    @Test
    fun `system prompt includes reply discipline exactly once and at top`() {
        val system = PromptBuilder.buildSystemPrompt(conv())
        assertTrue(system.startsWith("【回复纪律（最高优先级）】"), system.take(40))
        assertEquals(1, "【回复纪律（最高优先级）】".toRegex().findAll(system).count())
        assertTrue(system.contains("只以人设身份对用户说话"))
        assertTrue(system.contains("不要回答自己上一句提出的问题"))
        assertTrue(system.contains("每次回复必须包含实际说出口的台词"))
    }

    @Test
    fun `group rules require actual speech lines`() {
        assertTrue(PromptBuilder.GROUP_RULES.contains("每次发言必须包含至少一句实际台词"))
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.PromptBuilderTest"`
Expected: FAIL（system 提示词中不存在「回复纪律」）

- [ ] **Step 3: 实现**

在 `PromptBuilder` 对象内新增常量（放在 `LET_AI_START_GUIDE` 附近）：

```kotlin
    /**
     * 对话纪律：置于 system 提示词最前（人设之前），随每次请求发送。
     * 与现有提示词无重复（现有内容仅人设/场景/记忆，无回复纪律）。
     */
    const val REPLY_DISCIPLINE = "【回复纪律（最高优先级）】\n" +
        "1. 只以人设身份对用户说话：「用户」的话是用户说的，你自己说过的话是你自己的；不替用户回答，不把自己说过的话当成用户的话。\n" +
        "2. 用户点「继续说」时，接着你自己上一句的话继续叙述，不要回答自己上一句提出的问题。\n" +
        "3. 每次回复必须包含实际说出口的台词；括号内的动作、神态只是辅助，不能只写动作就结束；不提及自己是 AI 或模型（用户明确询问时除外）。"
```

`buildSystemPrompt` 中 `StringBuilder` 创建后立即追加：

```kotlin
        val sb = StringBuilder()

        // ===== 0. 对话纪律（最高优先级，置于人设之前） =====
        sb.append(REPLY_DISCIPLINE).append("\n\n")
```

`GROUP_RULES` 追加第 5 条：

```kotlin
    const val GROUP_RULES =
        "1. 你正在参与一场群聊，群聊中有用户和其他 AI 成员。\n" +
        "2. 每次发言前先完整阅读群聊转述，保持你的人设一致。\n" +
        "3. 只说你作为该角色会说的话，不要替别人发言。\n" +
        "4. 直接输出发言内容，不要输出名字前缀、冒号或任何解释。\n" +
        "5. 每次发言必须包含至少一句实际台词；括号内的动作、神态只是辅助，不能只发动作描写。"
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.PromptBuilderTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/domain/PromptBuilder.kt app/src/test/kotlin/com/quiddity/app/domain/PromptBuilderTest.kt
git commit -m "feat: 对话纪律整合进 system 提示词（说话人认知/继续说/台词完整性）"
```

## Task 2: 快速设定提示词重写（#3/#4）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/domain/QuickSetupPrompt.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/domain/QuickSetupTier.kt`
- Create: `app/src/test/kotlin/com/quiddity/app/domain/QuickSetupPromptTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickSetupPromptTest {

    @Test
    fun `parse full comprehensive output extracts all fields`() {
        val raw = """
            【AI人设】
            [名字]林夕
            [身份背景]温柔学姐，中文系大三
            [性格]说话轻声细语，喜欢用语气词
            [外观]长发，戴圆框眼镜
            [世界背景]都市世界，普通大学校园
            [期望特质]对用户永远温柔耐心
            【用户人设】
            [名字]小明
            [身份]大一新生
            [性别]男
            [年龄]18
            [外观]运动装
            【场景设置】
            [当前场景]黄昏时的图书馆，两人靠窗而坐
            【记忆设置】
            [需要记住的事]小明喜欢喝拿铁
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertEquals("林夕", result.persona.name)
        assertEquals("温柔学姐，中文系大三", result.persona.persona)
        assertEquals("对用户永远温柔耐心", result.persona.desired)
        assertEquals("小明", result.userPersona.name)
        assertEquals("男", result.userPersona.gender)
        assertEquals("黄昏时的图书馆，两人靠窗而坐", result.scene)
        assertEquals("小明喜欢喝拿铁", result.memory)
    }

    @Test
    fun `parse missing fields returns blanks and gender fallback`() {
        val raw = "【AI人设】\n[名字]林夕\n【场景设置】\n[当前场景]图书馆"
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertEquals("", result.persona.character)
        assertEquals("暂不设置", result.userPersona.gender)
        assertEquals("", result.memory)
    }

    @Test
    fun `user prompt includes tier density requirement`() {
        val rough = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.ROUGH)
        val full = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.COMPREHENSIVE)
        assertTrue(rough.contains("精炼"))
        assertTrue(full.contains("详尽"))
    }

    @Test
    fun `system prompt emphasizes divergence and density`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        assertTrue(sys.contains("合理发散"))
        assertTrue(sys.contains("具体胜于抽象"))
        assertTrue(sys.contains("密度优先"))
        assertTrue(sys.contains("每个字段都必须填充"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.QuickSetupPromptTest"`
Expected: FAIL（`QUICK_SETUP_SYSTEM_PROMPT` 不含新原则 / 无充实度要求）

- [ ] **Step 3: 实现**

`QuickSetupTier` 枚举新增属性：

```kotlin
enum class QuickSetupTier(
    val chineseName: String,
    val maxChars: Int,
    val maxWords: Int,
    val requiredTier: ApiCatalogManager.ModelTier,
    val includesMemory: Boolean,
    val densityRequirement: String
) {
    ROUGH(
        chineseName = "粗略",
        maxChars = 500,
        maxWords = 300,
        requiredTier = ApiCatalogManager.ModelTier.BASIC,
        includesMemory = false,
        densityRequirement = "内容精炼：每个字段用 1-3 句具体信息填充完整，禁止敷衍。"
    ),
    CONCRETE(
        chineseName = "具体",
        maxChars = 2000,
        maxWords = 1200,
        requiredTier = ApiCatalogManager.ModelTier.ADVANCED,
        includesMemory = true,
        densityRequirement = "内容充实：每个字段展开为一段有细节的完整描述。"
    ),
    COMPREHENSIVE(
        chineseName = "全面",
        maxChars = 5000,
        maxWords = 3500,
        requiredTier = ApiCatalogManager.ModelTier.FULL,
        includesMemory = true,
        densityRequirement = "内容详尽：每个字段充分展开，细节丰富、自成体系。"
    );
```

`QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT` 整体替换为（解析标记不变）：

```kotlin
    internal val QUICK_SETUP_SYSTEM_PROMPT = """
你是一个"人设生成器"。用户会给你一段可能十分模糊的人设描述，你要基于它生成一份结构完整、可直接被 AI 使用的人设卡。

【输出格式】严格按以下章节输出，每段文字独占一段。分节符不计入字数，仅填空项内容计入字数上限：
【AI人设】
[名字]……
[身份背景]……
[性格]……
[外观]……
[世界背景]……
[期望特质]……
【用户人设】
[名字]……
[身份]……
[性别]……
[年龄]……
[外观]……
【场景设置】
[当前场景]……
【记忆设置】
[需要记住的事]……

生成原则（按优先级执行）：
1. 忠实于用户意图——用户明确说过的设定一字不改；描述模糊或缺失的部分允许你合理发散补全，让角色有血有肉、自成一体，但发散必须贴合用户原意，不得违背用户明确给出的设定。
2. 每个字段都必须填充且内容充实——即使档位较低，每个字段也要有具体信息量；禁止空字段，禁止一句话敷衍。
3. 具体胜于抽象——用具体细节说话：口头禅、习惯性小动作、说话节奏、喜好与厌恶、藏在心里的矛盾、坚持的信念、害怕或渴望的东西。禁止只写"温柔""傲娇"这类空洞形容词而不展开。
4. 多样且不落俗套——根据用户描述量身定制，避免千人一面的模板；不要用雷同的开头句式，不要堆砌流行词或烂梗。
5. 密度优先——充分利用字数上限写出有信息量的内容，但拒绝凑字、空话、套话和重复；每个字都要有用，宁可精炼也不注水。
6. 世界类型——[世界背景]字段必须以恰好 4 个汉字的"世界类型"开头（如：都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界/古风世界/星际世界），紧接详细的世界背景描述。
7. 性别规则——【用户人设】的[性别]仅在用户描述中明确出现时填写；未明确时一律填"暂不设置"，绝不根据名字或语气猜测。
8. 格式严格——只输出上述章节与字段；不得新增任何章节或字段，缺失字段视为生成失败。

现在等待用户的人设描述。
""".trim()
```

`buildQuickSetupUserPrompt` 在「完整度级别」行后追加：

```kotlin
        sb.append("【完整度级别】").append(tier.chineseName).append("\n")
        sb.append("【本档位充实度要求】").append(tier.densityRequirement).append("\n\n")
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.QuickSetupPromptTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/domain/QuickSetupPrompt.kt app/src/main/kotlin/com/quiddity/app/domain/QuickSetupTier.kt app/src/test/kotlin/com/quiddity/app/domain/QuickSetupPromptTest.kt
git commit -m "feat: 快速设定提示词重写——发散补全/具体细节/密度优先/档位充实度"
```

## Task 3: 快速设定描述持久化（#5）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/data/model/Models.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/panels/QuickSetupPanel.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/HamburgerMenu.kt`
- Create: `app/src/test/kotlin/com/quiddity/app/data/model/ConversationQuickSetupDraftTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.quiddity.app.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationQuickSetupDraftTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `old json without draft field decodes to empty draft`() {
        val old = """
            {"id":"c1","title":"测试","createdAt":1,"updatedAt":1}
        """.trimIndent()
        val conv = json.decodeFromString(Conversation.serializer(), old)
        assertEquals("", conv.quickSetupDraft)
    }

    @Test
    fun `draft field round trips`() {
        val conv = Conversation(
            id = "c1",
            createdAt = 1,
            updatedAt = 1,
            quickSetupDraft = "温柔的学姐"
        )
        val encoded = json.encodeToString(Conversation.serializer(), conv)
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals("温柔的学姐", decoded.quickSetupDraft)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.data.model.ConversationQuickSetupDraftTest"`
Expected: FAIL（`Conversation` 无 `quickSetupDraft` 字段）

- [ ] **Step 3: 实现**

`Models.kt` 的 `Conversation` 在 `memory` 字段后新增：

```kotlin
    /**
     * 快速设定面板的用户描述草稿（持久化以便回看/重新生成）。
     * 每次输入防抖写入；空字符串表示从未填写。
     */
    val quickSetupDraft: String = "",
```

`ChatViewModel.kt` 新增字段与方法（放在「发送延迟」区块附近）：

```kotlin
    // ===== 快速设定草稿 =====
    private var quickSetupDraftJob: Job? = null

    fun updateQuickSetupDraft(draft: String) {
        val conv = conversation.value ?: return
        if (conv.quickSetupDraft == draft) return
        quickSetupDraftJob?.cancel()
        quickSetupDraftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            persistQuickSetupDraft(conversation.value ?: conv, draft)
        }
    }

    private fun persistQuickSetupDraft(conv: com.quiddity.app.data.model.Conversation, draft: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                conversationRepository.updateConversation(conv.copy(quickSetupDraft = draft))
            }
        }
    }
```

`QuickSetupPanel.kt` 签名追加两个参数，`description` 用 `initialDraft` 初始化：

```kotlin
fun QuickSetupPanel(
    currentTier: ApiCatalogManager.ModelTier,
    hasExistingContent: Boolean,
    initialDraft: String = "",
    onDraftChange: (String) -> Unit = {},
    onGenerate: suspend (String, QuickSetupTier) -> String,
    onApply: (String, QuickSetupTier) -> Unit,
    onBack: () -> Unit
) {
```

```kotlin
    var description by rememberSaveable(initialDraft) { mutableStateOf(initialDraft) }
```

`QuiddityTextField` 的 `onValueChange` 追加回调：

```kotlin
        QuiddityTextField(
            value = description,
            onValueChange = {
                description = it
                onDraftChange(it)
            },
```

`HamburgerMenu.kt` 的 QuickSetupPanel 调用处传入：

```kotlin
                                QuickSetupPanel(
                                    currentTier = tier,
                                    hasExistingContent = hasExisting,
                                    initialDraft = conv.quickSetupDraft,
                                    onDraftChange = { draft ->
                                        viewModel.updateQuickSetupDraft(draft)
                                    },
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.data.model.ConversationQuickSetupDraftTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/data/model/Models.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatViewModel.kt app/src/main/kotlin/com/quiddity/app/ui/chat/components/panels/QuickSetupPanel.kt app/src/main/kotlin/com/quiddity/app/ui/chat/components/HamburgerMenu.kt app/src/test/kotlin/com/quiddity/app/data/model/ConversationQuickSetupDraftTest.kt
git commit -m "feat: 快速设定描述草稿持久化，关闭面板后可回看/重新生成"
```

## Task 4: 快速设定必填校验 + 填入后退出（#1/#2）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/domain/QuickSetupPrompt.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/panels/QuickSetupPanel.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/HamburgerMenu.kt`
- Modify: `app/src/test/kotlin/com/quiddity/app/domain/QuickSetupPromptTest.kt`

- [ ] **Step 1: 写失败测试**（QuickSetupPromptTest 追加）

```kotlin
    @Test
    fun `missing required field keys per tier`() {
        val empty = QuickSetupResult(
            persona = Persona.Empty,
            userPersona = UserPersona.Empty,
            scene = "",
            memory = ""
        )
        val roughMissing = empty.missingRequiredFieldKeys(QuickSetupTier.ROUGH)
        assertTrue("ai_name" in roughMissing)
        assertTrue("ai_persona" in roughMissing)
        assertTrue("ai_character" in roughMissing)
        assertTrue("user_name" in roughMissing)
        assertTrue("scene" in roughMissing)
        assertTrue("memory" !in roughMissing)

        val fullMissing = empty.missingRequiredFieldKeys(QuickSetupTier.COMPREHENSIVE)
        assertTrue("ai_appearance" in fullMissing)
        assertTrue("ai_world_background" in fullMissing)
        assertTrue("ai_desired" in fullMissing)
        assertTrue("memory" in fullMissing)
    }

    @Test
    fun `filled persona has no missing keys`() {
        val filled = QuickSetupResult(
            persona = Persona(
                name = "林夕",
                persona = "学姐",
                character = "温柔",
                appearance = "长发",
                worldBackground = "都市世界，大学",
                desired = "耐心",
                compiledPersona = null,
                aiAvatarUri = null
            ),
            userPersona = UserPersona(
                name = "小明",
                identity = "新生",
                gender = "暂不设置",
                age = "18",
                appearance = "运动装"
            ),
            scene = "图书馆",
            memory = "喜欢拿铁"
        )
        assertEquals(emptySet(), filled.missingRequiredFieldKeys(QuickSetupTier.COMPREHENSIVE))
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.QuickSetupPromptTest"`
Expected: FAIL（`missingRequiredFieldKeys` 未定义）

- [ ] **Step 3: 实现**

`QuickSetupPrompt.kt` 新增扩展函数（放在 `parseQuickSetupResult` 之后）：

```kotlin
    /**
     * 必填校验：返回当前档位下缺失字段的 key 集合。
     * 预览弹窗据此禁用「填入」并标红缺失项。
     */
    fun QuickSetupResult.missingRequiredFieldKeys(tier: QuickSetupTier): Set<String> {
        val keys = mutableSetOf<String>()
        tier.aiPersonaFields().forEach { field ->
            val value = when (field) {
                AiPersonaField.NAME -> persona.name
                AiPersonaField.PERSONA -> persona.persona
                AiPersonaField.CHARACTER -> persona.character
                AiPersonaField.APPEARANCE -> persona.appearance
                AiPersonaField.WORLD_BACKGROUND -> persona.worldBackground
                AiPersonaField.DESIRED -> persona.desired
            }
            if (value.isBlank()) keys += "ai_${field.name.lowercase()}"
        }
        if (userPersona.name.isBlank()) keys += "user_name"
        if (userPersona.identity.isBlank()) keys += "user_identity"
        if (userPersona.gender.isBlank()) keys += "user_gender"
        if (userPersona.age.isBlank()) keys += "user_age"
        if (userPersona.appearance.isBlank()) keys += "user_appearance"
        if (scene.isBlank()) keys += "scene"
        if (tier.includesMemory && memory.isBlank()) keys += "memory"
        return keys
    }
```

`QuickSetupPanel.kt`：

新增 key → 中文名映射辅助函数：

```kotlin
private fun missingLabel(key: String): String = when (key) {
    "ai_name" -> "AI 名字"
    "ai_persona" -> "AI 身份背景"
    "ai_character" -> "AI 性格"
    "ai_appearance" -> "AI 外观"
    "ai_world_background" -> "AI 世界背景"
    "ai_desired" -> "AI 期望特质"
    "user_name" -> "用户名字"
    "user_identity" -> "用户身份"
    "user_gender" -> "用户性别"
    "user_age" -> "用户年龄"
    "user_appearance" -> "用户外观"
    "scene" -> "当前场景"
    "memory" -> "需要记住的事"
    else -> key
}
```

`QuickSetupResultDialog` 中 `buildText()` 定义后计算缺失集：

```kotlin
    val currentMissing = remember(initialText, aiName, aiPersona, aiCharacter, aiAppearance,
        aiWorld, aiDesired, userName, userIdentity, userGender, userAge, userAppearance,
        scene, memory) {
        QuickSetupPrompt.parseQuickSetupResult(buildText(), tier).missingRequiredFieldKeys(tier)
    }
```

提示文本（放在滚动区上方）：

```kotlin
                if (currentMissing.isNotEmpty()) {
                    Text(
                        text = "以下字段不能为空：" + currentMissing.map { missingLabel(it) }.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
```

「填入」按钮改为 `enabled = currentMissing.isEmpty()`。

`SectionField` 增加 `isError: Boolean = false`，`OutlinedTextField` 传 `isError = isError`，label 文字在 `isError` 时用 `MaterialTheme.colorScheme.error`。各字段调用处按 key 传参（如 AI 名字 `isError = "ai_name" in currentMissing`、用户名字 `isError = "user_name" in currentMissing`、场景 `isError = "scene" in currentMissing`、记忆 `isError = "memory" in currentMissing`）。

`HamburgerMenu.kt` 的 `onApply` 追加退出：

```kotlin
                                    onApply = { rawText, selectedTier ->
                                        viewModel.applyQuickSetupResult(rawText, selectedTier)
                                        currentPanel = null
                                        onDismiss()
                                    },
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.QuickSetupPromptTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/domain/QuickSetupPrompt.kt app/src/main/kotlin/com/quiddity/app/ui/chat/components/panels/QuickSetupPanel.kt app/src/main/kotlin/com/quiddity/app/ui/chat/components/HamburgerMenu.kt app/src/test/kotlin/com/quiddity/app/domain/QuickSetupPromptTest.kt
git commit -m "feat: 快速设定每项必填校验 + 填入后直接退出设置进入会话"
```

## Task 5: 改写弹窗光标落末尾（#8）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/RewriteBottomSheet.kt`

- [ ] **Step 1: 实现**（Compose UI 改动，项目无 UI 测试框架，以编译 + 真机清单验证）

新增 import：

```kotlin
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
```

状态初始化改为：

```kotlin
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
```

`TextField` 的 `value`/`onValueChange` 改为 `textFieldValue` / `{ textFieldValue = it }`；`canSave` 与保存逻辑中的 `text` 全部改为 `textFieldValue.text`。

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/ui/chat/components/RewriteBottomSheet.kt
git commit -m "fix: 改写弹窗光标默认落在文本末尾"
```

## Task 6: 发送延迟防抖（#9）

**Files:**
- Create: `app/src/main/kotlin/com/quiddity/app/domain/SendDelayGate.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatViewModel.kt`
- Create: `app/src/test/kotlin/com/quiddity/app/domain/SendDelayGateTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendDelayGateTest {

    @Test
    fun `blank input and silent for full delay fires`() {
        assertTrue(SendDelayGate.shouldFire("", 1000L, 4000L, 3000L))
    }

    @Test
    fun `recent edit does not fire`() {
        assertFalse(SendDelayGate.shouldFire("", 3500L, 4000L, 3000L))
    }

    @Test
    fun `non blank input never fires`() {
        assertFalse(SendDelayGate.shouldFire("还在打字", 1000L, 4000L, 3000L))
    }

    @Test
    fun `backspace to empty then wait full delay fires`() {
        assertFalse(SendDelayGate.shouldFire("", 3500L, 6000L, 3000L))
        assertTrue(SendDelayGate.shouldFire("", 3500L, 6500L, 3000L))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.SendDelayGateTest"`
Expected: FAIL（`SendDelayGate` 未定义）

- [ ] **Step 3: 实现**

```kotlin
package com.quiddity.app.domain

/**
 * 发送延迟放行判定（纯函数，便于单测）。
 *
 * 语义：仅当输入框为空，且距用户最后一次编辑（含退格）已静默满 delayMs 时才放行。
 * 任何编辑都会重新计时——修复"退格清空输入框后立即触发加载"的问题。
 */
object SendDelayGate {
    fun shouldFire(text: String, lastEditAt: Long, now: Long, delayMs: Long): Boolean =
        text.isBlank() && now - lastEditAt >= delayMs
}
```

`ChatViewModel.kt`：

```kotlin
    /** 发送延迟检测：输入框最后一次编辑时间（含退格，用于防抖重计时）。 */
    private var lastInputEditAt = 0L
```

`updateInputText` 改为：

```kotlin
    fun updateInputText(text: String) {
        if (_inputBarText.value == text) return
        _inputBarText.value = text
        lastInputEditAt = System.currentTimeMillis()
    }
```

`sendMessage` 的延迟段改为：

```kotlin
            // 发送延迟——等待用户停止输入后再发出 API 请求（编辑感知防抖）
            val settings = settingsRepository.currentSnapshot()
            if (settings.sendDelayEnabled) {
                val delayMs = settings.sendDelaySeconds * 1000L
                while (true) {
                    if (SendDelayGate.shouldFire(
                            _inputBarText.value,
                            lastInputEditAt,
                            System.currentTimeMillis(),
                            delayMs
                        )
                    ) {
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
            }
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.SendDelayGateTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/domain/SendDelayGate.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatViewModel.kt app/src/test/kotlin/com/quiddity/app/domain/SendDelayGateTest.kt
git commit -m "fix: 发送延迟改为编辑感知防抖（退格清空不再立即触发加载）"
```

## Task 7: 设置页键盘遮挡（#10）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsBottomSheet.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/components/ApiEditBottomSheet.kt`

- [ ] **Step 1: 实现**（Compose UI 改动，编译 + 真机清单验证）

`SettingsBottomSheet.kt`：面板 Surface 高度改为自适应（键盘收起最多 80%，弹起收缩到键盘上方）：

```kotlin
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.8f)
                    .fillMaxHeight()
                    .graphicsLayer {
                    // 整个面板跟随拖动偏移（1:1，draw phase 读取，零重组）
                    translationY = dragOffsetYState.floatValue.coerceAtLeast(0f)
                },
```

补充 import：`androidx.compose.foundation.layout.fillMaxHeight`、`androidx.compose.foundation.layout.heightIn`（如缺失）。

`ApiEditBottomSheet.kt`：同样将 `.height(screenHeight * 0.85f)` 改为 `.heightIn(max = screenHeight * 0.85f).fillMaxHeight()` 并补齐 import。

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsBottomSheet.kt app/src/main/kotlin/com/quiddity/app/ui/components/ApiEditBottomSheet.kt
git commit -m "fix: 设置面板高度自适应键盘，底部设置项不再被遮挡"
```

## Task 8: 输入框行数（#11）

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/components/ChatInputBar.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 实现**（Compose UI 改动，编译 + 真机清单验证）

`ChatInputBar.kt` 签名追加三个参数：

```kotlin
    header: (@Composable () -> Unit)? = null,
    maxLines: Int = 4,
    maxFieldHeight: Dp? = null,
    onContentHeightChanged: ((Int) -> Unit)? = null
```

补充 import：`androidx.compose.ui.layout.onSizeChanged`、`androidx.compose.ui.platform.LocalDensity`、`androidx.compose.ui.unit.Dp`。

函数体开头计算有效行数：

```kotlin
    val density = LocalDensity.current
    val effectiveMaxLines = if (maxFieldHeight != null) {
        val lineHeightPx = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.roundToPx() }
        val availablePx = with(density) { (maxFieldHeight - 24.dp).roundToPx() }
        (availablePx / lineHeightPx).coerceAtLeast(1)
    } else {
        maxLines
    }
```

内层 `Column` 的 modifier 末尾追加：

```kotlin
                .onSizeChanged { onContentHeightChanged?.invoke(it.height) }
```

`TextField`：

```kotlin
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = maxFieldHeight ?: 140.dp),
```

```kotlin
                    maxLines = effectiveMaxLines,
```

`ChatScreen.kt`：输入栏区域前新增状态：

```kotlin
    var groupInputBarContentHeightPx by remember { mutableIntStateOf(0) }
```

群聊 ChatInputBar 调用追加：

```kotlin
                        maxLines = 2,
                        onContentHeightChanged = { px -> groupInputBarContentHeightPx = px },
```

私聊 ChatInputBar 调用追加：

```kotlin
                        maxFieldHeight = if (groupInputBarContentHeightPx > 0) {
                            with(LocalDensity.current) { (groupInputBarContentHeightPx.toDp() - 16.dp) }
                        } else {
                            null
                        },
```

若 `ChatScreen.kt` 缺 `mutableIntStateOf` / `LocalDensity` import，一并补充。

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/ui/chat/components/ChatInputBar.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatScreen.kt
git commit -m "feat: 群聊输入框2行，私聊行数按群聊整体高度自适应"
```

## Task 9: 切分器回归测试（#7 兜底）

**Files:**
- Modify: `app/src/test/kotlin/com/quiddity/app/domain/MessageStreamCoordinatorTest.kt`

- [ ] **Step 1: 追加回归测试**（预期现有实现已通过，作为防回归护栏）

```kotlin
    @Test
    fun `action bracket followed by speech keeps both messages`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("（把脸埋在你胸口，声音闷闷的）")
        coord.accept("我真的好想你。")
        val contents = coord.snapshot().map { it.content }
        assertEquals(
            listOf("（把脸埋在你胸口，声音闷闷的）", "我真的好想你。"),
            contents,
            "动作+台词连续流不应丢字：$contents"
        )
    }
```

- [ ] **Step 2: 运行确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.quiddity.app.domain.MessageStreamCoordinatorTest"`
Expected: PASS（全部 38 个用例）

- [ ] **Step 3: 提交**

```bash
git add app/src/test/kotlin/com/quiddity/app/domain/MessageStreamCoordinatorTest.kt
git commit -m "test: 切分器动作+台词连续流回归测试"
```

## Task 10: 全量验证 + APK 打包

- [ ] **Step 1: 全量单元测试**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 全部 PASS（基线 191 + 新增约 15 个）

- [ ] **Step 2: Debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 3: Release APK（keystore 存在时）**

Run: `.\gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL，产物 `app/build/outputs/apk/release/app-release.apk`

- [ ] **Step 4: 真机验收清单（交付用户）**

1. 快速设定填入 → 菜单关闭回到会话；描述草稿重开面板仍在
2. 预览弹窗空字段标红不可填入；LLM 生成内容具体且不雷同
3. 继续说不再自问自答；回复均含台词，无"只有括号动作"的回复
4. 改写光标在末尾
5. 私聊延迟期间退格不立即加载
6. 设置页底部项键盘弹起后可滚动可见
7. 群聊输入框 2 行；私聊行数随群聊高度自适应
