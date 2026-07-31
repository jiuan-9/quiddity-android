package com.quiddity.app.domain

/**
 * 集中维护应用内所有专业名词的通俗解释，让用户更好理解。
 * 内容面向非技术用户，避免使用过多术语。
 */
class GlossaryProvider {

    /** 单条名词解释。 */
    data class Term(
        /** 名词本身。 */
        val name: String,
        /** 拼音（用于搜索匹配，不含空格小写）。 */
        val pinyin: String,
        /** 通俗解释。 */
        val explanation: String,
        /** 所属分类。 */
        val category: String
    )

    /** 所有名词解释条目（按分类分组）。 */
    fun allTerms(): List<Term> = listOf(
        // ===== 基础概念 =====
        Term(
            name = "API",
            pinyin = "api",
            explanation = "可以理解为「点菜窗口」。AI 模型本身在云端服务器上运行，" +
                "你的手机通过 API 这个窗口向云端发送请求，云端处理后把结果送回来。" +
                "你需要一个有效的 API Key（密钥）才能使用这个窗口。",
            category = "基础概念"
        ),
        Term(
            name = "API Key",
            pinyin = "apikeymimayaoshi",
            explanation = "API 密钥，相当于你访问 AI 服务的「通行证」。" +
                "每个服务商都会给你一个独特的字符串，凭此计费和限制访问。" +
                "请妥善保管，不要泄露给他人，否则他人可以盗用你的额度。",
            category = "基础概念"
        ),
        Term(
            name = "模型",
            pinyin = "moxing",
            explanation = "AI 的「大脑」。不同的模型有不同的能力、速度和价格。" +
                "比如通义千问、文心一言、DeepSeek、GPT 等都是不同的模型。" +
                "你可以在「模型配置」中添加自己常用的模型。",
            category = "基础概念"
        ),
        Term(
            name = "服务商",
            pinyin = "fuwushang",
            explanation = "提供 AI 模型服务的企业，比如阿里云、百度、DeepSeek、智谱等。" +
                "每个服务商都有自己的 API 接口和计费方式。",
            category = "基础概念"
        ),
        Term(
            name = "会话",
            pinyin = "huihua",
            explanation = "你和 AI 的一次完整对话。每个会话独立保存人设、消息记录、设置。" +
                "可以理解为「一个聊天窗口」。在主页可以创建多个会话。",
            category = "基础概念"
        ),
        Term(
            name = "消息",
            pinyin = "xiaoxi",
            explanation = "会话中的一条对话内容。可以是你说的话（用户消息），" +
                "也可以是 AI 回复的话（AI 消息）。",
            category = "基础概念"
        ),

        // ===== Token 与上下文 =====
        Term(
            name = "Token",
            pinyin = "tokenlingpai",
            explanation = "AI 处理文字的最小单位，可以粗略理解为「词」。" +
                "中文字 1 个字约等于 1-2 个 Token，英文 1 个单词约等于 1-2 个 Token。" +
                "服务商按 Token 数量计费。本应用显示的 Token 数都是估算值，" +
                "实际计费以服务商账单为准。",
            category = "Token 与上下文"
        ),
        Term(
            name = "Token 用量",
            pinyin = "tokenyongliang",
            explanation = "本会话中 AI 回复所消耗的 Token 总数（估算）。" +
                "切换 API 时会清零，仅统计当前 API 的用量。",
            category = "Token 与上下文"
        ),
        Term(
            name = "上下文",
            pinyin = "shangxiawen",
            explanation = "AI 在回复你时能「记住」的前面对话内容。" +
                "上下文越长，AI 越能理解来龙去脉，但消耗的 Token 也越多。",
            category = "Token 与上下文"
        ),
        Term(
            name = "上下文记忆轮数",
            pinyin = "shangxiawenjiyi lunshu",
            explanation = "AI 在回复时能记住的对话轮数。1 轮 = 1 次你说 + 1 次 AI 回。" +
                "数值越大 AI 记得越多但 Token 消耗越高。" +
                "默认根据模型分级自动设置：完全级 80 轮、进阶级 40 轮、基础级 12 轮。" +
                "切换模型时自动重置为该级别的默认值。",
            category = "Token 与上下文"
        ),
        Term(
            name = "最大回复 Token",
            pinyin = "zuidahui fu token",
            explanation = "AI 单次回复最多能输出的 Token 数。" +
                "数值越大 AI 可以回复越长，但消耗也越多。" +
                "默认 4096，可在「Token 设置」中调整。",
            category = "Token 与上下文"
        ),
        Term(
            name = "单条消息 Token",
            pinyin = "dantiaoxiaoxi token",
            explanation = "AI 单条消息达到此 Token 数时切分为多条发送。" +
                "默认 800，避免 AI 一次输出过长难以阅读。",
            category = "Token 与上下文"
        ),
        Term(
            name = "记忆库",
            pinyin = "jiyiku",
            explanation = "达到指定轮数后，自动把历史对话交给 AI 压缩为关键信息摘要。" +
                "下次发送时只发摘要 + 最近消息，大幅节省 Token。" +
                "默认 40 轮触发一次，仅对当前会话生效。",
            category = "Token 与上下文"
        ),

        // ===== 人设与精调 =====
        Term(
            name = "人设",
            pinyin = "renshe",
            explanation = "AI 角色的设定，包括名字、身份背景、性格、外观、世界背景等。" +
                "人设决定 AI 扮演的角色类型。每个会话有独立的人设。",
            category = "人设与精调"
        ),
        Term(
            name = "人设精调",
            pinyin = "renshejingdiao",
            explanation = "把人设字段交给 AI 编译为结构化的系统提示词，让 AI 更精准地扮演角色。" +
                "精调后的内容缓存在会话中，下次发送直接使用，无需重复编译。" +
                "修改人设字段（除名字和头像外）会清空缓存，需要重新精调。",
            category = "人设与精调"
        ),
        Term(
            name = "你希望 ta 是什么样的",
            pinyin = "nixiwangtashishenmeyangde",
            explanation = "人设的最高优先级字段，描述你对 AI 角色的核心期望。" +
                "仅「完全级」模型可用。精调时会优先处理此字段。",
            category = "人设与精调"
        ),
        Term(
            name = "用户人设",
            pinyin = "yonghurenshe",
            explanation = "你自己（用户）的设定，包括名字、身份、性别、年龄、外观等。" +
                "让 AI 知道它在和「什么样的人」对话，回复更贴合你的身份。",
            category = "人设与精调"
        ),
        Term(
            name = "场景",
            pinyin = "changjing",
            explanation = "对话发生的故事场景描述，比如「深夜的咖啡馆」、" +
                "「公司会议室」等。让 AI 在回复时融入场景氛围。",
            category = "人设与精调"
        ),
        Term(
            name = "记忆",
            pinyin = "jiyi",
            explanation = "你想让 AI 始终记住的固定信息，比如「我的生日是 5 月 1 日」、" +
                "「我最喜欢蓝色」。每次发送都会附带这些信息。",
            category = "人设与精调"
        ),
        Term(
            name = "精调预览",
            pinyin = "jingdiaoyulan",
            explanation = "精调完成后弹出的预览界面，显示 AI 编译后的系统提示词。" +
                "你可以选择「采用」保存，「返回重调」则不保存。",
            category = "人设与精调"
        ),

        // ===== 模型分级 =====
        Term(
            name = "完全级",
            pinyin = "wanquanjijixing",
            explanation = "能力最强的模型分级。所有人设字段可用，" +
                "默认上下文记忆 80 轮。通常对应旗舰模型如 GPT-4、Claude 3.5 等。",
            category = "模型分级"
        ),
        Term(
            name = "进阶级",
            pinyin = "jinjijijixing",
            explanation = "中等能力的模型分级。可用除「你希望 ta 是什么样的」" +
                "外的所有人设字段，默认上下文记忆 40 轮。",
            category = "模型分级"
        ),
        Term(
            name = "基础级",
            pinyin = "jichujijixing",
            explanation = "能力较弱的模型分级。仅可编辑名字、身份、性格，" +
                "默认上下文记忆 12 轮。适合轻量对话场景。",
            category = "模型分级"
        ),

        // ===== 数据与备份 =====
        Term(
            name = "数据导出",
            pinyin = "shujudaochu",
            explanation = "把应用内所有数据（设置、会话、消息、壁纸等）打包为一个 JSON 文件，" +
                "保存到手机本地或云盘。建议定期备份。",
            category = "数据与备份"
        ),
        Term(
            name = "数据导入",
            pinyin = "shujudaochu",
            explanation = "从备份 JSON 文件恢复数据。会覆盖当前的设置和会话，" +
                "但保留你的用户头像。",
            category = "数据与备份"
        ),
        Term(
            name = "人设卡导出",
            pinyin = "rensekadaochu",
            explanation = "仅导出人设相关数据（AI 人设、用户人设、场景、记忆），" +
                "不包含消息记录。方便把人设分享给其他会话使用。",
            category = "数据与备份"
        ),
        Term(
            name = "对话记录导出",
            pinyin = "duihuajiludaochu",
            explanation = "把单个会话导出为 Markdown 或纯文本文件，方便阅读和分享。" +
                "不包含设置数据，仅用于查看对话内容。",
            category = "数据与备份"
        ),

        // ===== 交互功能 =====
        Term(
            name = "多行文本自动切分",
            pinyin = "duohangwenben zidongqiefen",
            explanation = "AI 回复时按段落自动切分为多条消息，模拟人类分多条发送的体验。" +
                "应用会在段落边界（空行）或长句末尾自动切分，" +
                "并保护代码块和括号内容不被破坏。",
            category = "交互功能"
        ),
        Term(
            name = "延迟输出",
            pinyin = "yanchishuchu",
            explanation = "根据 AI 输出字数延迟显示，营造对面是真人打字的感觉。" +
                "默认每个字延迟 20ms。可在「总设置」中关闭。",
            category = "交互功能"
        ),
        Term(
            name = "发送延迟",
            pinyin = "fasongyanchi",
            explanation = "发送消息后等待 N 秒再发出 API 请求。" +
                "期间若输入框非空则继续等待，直到清空才发送。" +
                "可避免连续输入触发多次请求，节省 Token。默认 3 秒。",
            category = "交互功能"
        ),
        Term(
            name = "回车键发送",
            pinyin = "huituijianfasong",
            explanation = "开启后按回车键直接发送消息；关闭后回车键换行，" +
                "需要点击发送按钮才能发送。",
            category = "交互功能"
        ),
        Term(
            name = "括号内容灰化",
            pinyin = "kuohaoneironghuihua",
            explanation = "把消息中括号内的文字显示为灰色，模拟剧本的「旁白」" +
                "或「内心独白」视觉效果。开启后 ()、（）、[]、【】 内的文字都会灰化。",
            category = "交互功能"
        ),
        Term(
            name = "撤回",
            pinyin = "chehui",
            explanation = "点击你自己的消息气泡，会弹出「撤回」按钮。" +
                "点击后删除该消息及其后所有消息（包括 AI 回复）。",
            category = "交互功能"
        ),
        Term(
            name = "重说",
            pinyin = "chongshuo",
            explanation = "重新生成最后一条 AI 回复。删除原 AI 回复，" +
                "用相同的上下文重新请求 AI。",
            category = "交互功能"
        ),
        Term(
            name = "继续说",
            pinyin = "jixushuo",
            explanation = "让 AI 接着上一条回复继续说，不追加你的消息。" +
                "AI 会自然地延续上一段内容。",
            category = "交互功能"
        ),
        Term(
            name = "改写",
            pinyin = "gaixie",
            explanation = "单击 AI 最后一条回复，会弹出「改写」按钮。" +
                "点击后进入改写界面，可直接编辑 AI 的回复内容并保存替换原消息。",
            category = "交互功能"
        ),

        // ===== 视觉设置 =====
        Term(
            name = "深色模式",
            pinyin = "shensemoshi",
            explanation = "应用界面使用暗色调主题，对眼睛更友好，" +
                "在夜间或低光环境下尤其适合。默认开启。",
            category = "视觉设置"
        ),
        Term(
            name = "会话壁纸",
            pinyin = "huihuabizhi",
            explanation = "为单个会话设置背景图片。每个会话独立设置，" +
                "互不影响。可以调整壁纸暗化程度以确保文字可读。",
            category = "视觉设置"
        ),
        Term(
            name = "会话列表壁纸",
            pinyin = "huihuualiebiaobizhi",
            explanation = "为会话列表主页设置背景图片。属于全局设置，" +
                "影响所有会话列表的显示。",
            category = "视觉设置"
        ),
        Term(
            name = "壁纸暗化",
            pinyin = "bizhianhua",
            explanation = "0.0 - 1.0 之间的数值。数值越大壁纸越暗，" +
                "文字可读性越好但壁纸本身被遮挡越多。建议深色壁纸设置 0.3-0.5。",
            category = "视觉设置"
        ),
        Term(
            name = "AI 头像",
            pinyin = "aitouxiang",
            explanation = "AI 角色的头像，每个会话独立设置。" +
                "可在「AI 人设」中点击头像更换，支持裁剪。",
            category = "视觉设置"
        ),
        Term(
            name = "用户头像",
            pinyin = "yonghutouxiang",
            explanation = "你自己的头像，全局设置。" +
                "在所有会话中显示，可在「总设置」顶部更换。",
            category = "视觉设置"
        )
    )

    /** 所有分类。 */
    val categories: List<String> = listOf(
        "基础概念",
        "Token 与上下文",
        "人设与精调",
        "模型分级",
        "数据与备份",
        "交互功能",
        "视觉设置"
    )
}
