package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
/**
 * 快速设定提示词中枢：系统提示词、user 消息拼装、结构化结果解析。
 *
 * 输出格式（分节符不计入字数，仅计填空项内容）：
 * 【AI人设】
 * [AI名字]…
 * [身份背景]…
 * [性格]…
 * [外观]…
 * [世界背景]…（必须以恰好4个字的世界类型开头，如 都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界）
 * [期望特质]…
 *
 * 【用户人设】
 * [用户名字]…
 * [用户身份]…
 * [用户性别]…
 * [用户年龄]…
 * [用户外观]…
 *
 * 【场景设置】
 * [当前场景]…
 *
 * 【记忆设置】
 * [需要记住的事]…
 *
 * 粗略档省略 [外观][世界背景][期望特质] 与整个【记忆设置】节；【场景设置】所有档位均生成。
 */
object QuickSetupPrompt {

    /** 全部已知字段标签（AI / 用户 / 场景 / 记忆），解析切分点只认这些标签。 */
    private val allFieldLabels = buildSet {
        AiPersonaField.entries.forEach { add(it.label) }
        UserPersonaField.entries.forEach { add(it.label) }
        add("[当前场景]")
        add("[需要记住的事]")
    }

