package com.quiddity.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateDialogTest {
    @Test
    fun parseReleaseNotes_splitsSectionsBulletsAndPlain() {
        val raw = """
            **本版本内容**
            - 群聊：双 Tab 建群加成员
            - 温度调节

            **兼容性**
            本地数据完全保留
        """.trimIndent()

        val items = parseReleaseNotes(raw)
        assertEquals(
            "本版本内容|群聊：双 Tab 建群加成员|温度调节|兼容性|本地数据完全保留",
            items.joinToString("|") { item ->
                when (item) {
                    is ReleaseNoteItem.Section -> item.title
                    is ReleaseNoteItem.Bullet -> item.text
                    is ReleaseNoteItem.Plain -> item.text
                }
            }
        )
    }

    @Test
    fun parseReleaseNotes_ignoresBlankLines() {
        val items = parseReleaseNotes("\n\n  \n")
        assertEquals(0, items.size)
    }

    @Test
    fun parseReleaseNotes_handlesMarkdownHeadingsQuotesAndInlineSymbols() {
        val raw = """
            # Quiddity-Android v1.5.1 发布说明

            > 版本号递增：versionCode 13 → 14

            ## 本版本内容

            - **温度设置调整**：API 名册不再单独设置温度
            - `:app:assembleRelease` 构建成功

            **兼容性：**
            - 本地数据完全保留
        """.trimIndent()

        val items = parseReleaseNotes(raw)
        assertEquals(
            "Quiddity-Android v1.5.1 发布说明|版本号递增：versionCode 13 → 14|" +
                "本版本内容|温度设置调整：API 名册不再单独设置温度|" +
                ":app:assembleRelease 构建成功|兼容性：|本地数据完全保留",
            items.joinToString("|") { item ->
                when (item) {
                    is ReleaseNoteItem.Section -> item.title
                    is ReleaseNoteItem.Bullet -> item.text
                    is ReleaseNoteItem.Plain -> item.text
                }
            }
        )
    }
}
