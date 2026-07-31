package com.quiddity.app.domain

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



/**
 * 设计：
 * - 三类核心文档：用户协议、隐私政策、免责声明
 * - 每类文档包含正文 + 引用的法律法规列表
 * - 每条法律法规有名称、简述、官方 URL（可复制）
 * - 引用国内（中国）与国际法律，覆盖数据保护、网络安全、AI 服务等领域
 *
 * 免责声明核心立场：
 * - 本应用仅提供 AI 对话工具，不生成、不存储、不审查用户对话内容
 * - 用户对自身使用行为及生成内容负全部责任
 * - 应用不对 AI 生成内容的准确性、合法性、道德性承担责任
 * - 用户需遵守所在地区法律法规，不得将应用用于违法用途
 */
class LegalDocsProvider {

    /** 单条法律引用。 */
    data class LawReference(
        /** 法律法规全称。 */
        val name: String,
        /** 简要说明（与本应用/用户的关系）。 */
        val description: String,
        /** 官方原文 URL（点击复制）。 */
        val officialUrl: String,
        /** 地域分类：国内 / 国际。 */
        val jurisdiction: Jurisdiction
    )

    enum class Jurisdiction { DOMESTIC, INTERNATIONAL }

    /** 单类法律文档。 */
    data class LegalDocument(
        val title: String,
        val body: String,
        val referencedLaws: List<LawReference>
    )

    /**
     * 用户协议。
     *
     * 明确用户使用本应用时的权利与义务，引用相关法律法规作为依据。
     */
    val userAgreement: LegalDocument = LegalDocument(
        title = "《用户协议》",
        body = """
            欢迎使用 Quiddity（以下简称"本应用"）。本应用是一款 AI 对话工具，为用户提供与人工智能进行自然语言交互的能力。

            在使用本应用前，请您仔细阅读并同意以下条款：

            1. 服务性质
            本应用仅作为 AI 对话的载体工具，提供接入第三方 AI 服务的接口。本应用本身不训练、不拥有、不控制任何 AI 模型，AI 回复内容由用户所选的第三方模型服务商生成。

            2. 用户行为规范
            用户在使用本应用时，应遵守中华人民共和国法律法规及所在地区法律，不得利用本应用从事以下行为：
            （1）制作、传播违法信息；
            （2）侵犯他人知识产权、隐私权、名誉权等合法权益；
            （3）制作、传播虚假信息；
            （4）其他违反法律法规或公序良俗的行为。

            3. 用户责任
            用户对自己在使用本应用过程中的行为及生成的全部内容负全部责任。本应用不对用户生成的对话内容承担责任。

            4. 知识产权
            用户在本应用中生成的内容，其知识产权归属按用户与第三方 AI 服务商的协议约定执行。本应用不对生成内容的知识产权主张任何权利。

            5. 服务变更与终止
            本应用保留随时修改、暂停或终止服务的权利，无需另行通知。

            6. 协议修改
            本应用保留修改本协议的权利，修改后的协议自发布之日起生效。继续使用本应用即视为同意修改后的协议。
        """.trimIndent(),
        referencedLaws = listOf(
            LawReference(
                name = "中华人民共和国民法典",
                description = "规定民事主体的合法权益受法律保护，用户行为不得侵犯他人民事权益",
                officialUrl = "http://www.npc.gov.cn/npc/c2/c30834/202006/t20200602_306457.html",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "中华人民共和国网络安全法",
                description = "规定网络运营者与用户的权利义务，用户不得利用网络从事违法活动",
                officialUrl = "http://www.npc.gov.cn/npc/c30834/2017-06/07/content_2023931.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "互联网信息服务管理办法",
                description = "规范互联网信息服务活动，用户不得制作、复制、发布违法信息",
                officialUrl = "https://www.gov.cn/zhengce/2020-12/27/content_5573624.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            )
        )
    )

    /**
     * 隐私政策。
     *
     * 说明本应用收集、使用、存储用户数据的方式，引用个人信息保护相关法律。
     */
    val privacyPolicy: LegalDocument = LegalDocument(
        title = "《隐私政策》",
        body = """
            本应用（Quiddity）高度重视用户隐私保护。本政策说明我们如何收集、使用和保护您的信息。

            1. 信息收集
            本应用收集的信息包括：
            （1）您主动输入的内容：对话文本、人设设置、API 配置信息等；
            （2）本地存储数据：头像、壁纸等图片文件，存储于设备本地；
            （3）API 密钥：经加密后存储于设备本地，不上传至我们的服务器。

            2. 信息使用
            您的信息仅用于：
            （1）提供 AI 对话功能；
            （2）在设备本地保存您的会话历史与设置；
            （3）按您的指示与第三方 AI 服务商交互。

            3. 信息存储
            所有用户数据均存储于您的设备本地（应用内部存储），本应用不运营任何云端服务器收集您的对话数据。您的对话内容直接发送至您配置的第三方 AI 服务商，由该服务商按其隐私政策处理。

            4. 信息安全
            API 密钥使用 AES 加密存储，防止明文泄露。其他数据存储于应用沙箱内，其他应用无法直接访问。

            5. 用户权利
            您有权随时：
            （1）通过"数据导出"功能导出全部数据；
            （2）通过删除应用清除全部本地数据；
            （3）拒绝提供某类信息（将影响对应功能使用）。

            6. 第三方服务
            本应用接入第三方 AI 服务商（如 OpenAI、阿里云、百度等），您的对话内容将发送至这些服务商。各服务商对数据的处理遵循其各自的隐私政策，本应用不对第三方服务商的数据处理行为承担责任。

            7. 政策修改
            本应用保留修改本隐私政策的权利，修改后自发布之日起生效。
        """.trimIndent(),
        referencedLaws = listOf(
            LawReference(
                name = "中华人民共和国个人信息保护法",
                description = "规范个人信息处理活动，保护个人信息权益",
                officialUrl = "http://www.npc.gov.cn/npc/c30834/2021-08/20/content_3136482.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "中华人民共和国数据安全法",
                description = "规范数据处理活动，保障数据安全",
                officialUrl = "http://www.npc.gov.cn/npc/c30834/2021-06/10/content_3103053.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "General Data Protection Regulation (GDPR)",
                description = "欧盟通用数据保护条例，规范个人数据的处理与跨境传输",
                officialUrl = "https://gdpr-info.eu/",
                jurisdiction = Jurisdiction.INTERNATIONAL
            ),
            LawReference(
                name = "California Consumer Privacy Act (CCPA)",
                description = "美国加州消费者隐私法，赋予消费者对个人信息的控制权",
                officialUrl = "https://oag.ca.gov/privacy/ccpa",
                jurisdiction = Jurisdiction.INTERNATIONAL
            )
        )
    )

    /**
     * 免责声明。
     *
     * 明确撇清本应用与用户行为、AI 生成内容的关系，引用 AI 服务管理相关法规。
     */
    val disclaimer: LegalDocument = LegalDocument(
        title = "《免责声明》",
        body = """
            本应用（Quiddity）提供以下免责声明，请用户仔细阅读：

            1. AI 生成内容免责
            本应用提供的 AI 对话功能，其回复内容由第三方 AI 模型生成，不代表本应用的观点或立场。本应用不对 AI 生成内容的准确性、完整性、合法性、道德性作出任何保证或承担责任。用户应自行判断生成内容的真实性与适用性。

            2. 用户行为免责
            用户在使用本应用过程中的所有行为（包括但不限于输入的内容、生成的对话、导出的数据）均由用户个人负责。本应用不对用户的违法行为、侵权行为或其他不当行为承担责任。

            3. 服务中断免责
            本应用依赖第三方 AI 服务商提供模型能力。因第三方服务中断、网络故障、设备问题等导致的服务不可用，本应用不承担责任。

            4. 数据安全免责
            虽然本应用采取了合理的加密与存储措施保护用户数据，但不对因设备丢失、系统漏洞、root 权限等不可控因素导致的数据泄露承担责任。用户应自行做好设备安全管理。

            5. 合规使用义务
            用户在使用本应用时，应遵守所在地区关于人工智能服务的法律法规，包括但不限于：
            （1）不得利用 AI 生成违法、有害、侵权内容；
            （2）不得利用 AI 进行欺诈、误导等行为；
            （3）对 AI 生成内容进行传播时，应标明"AI 生成"。

            6. 第三方服务商责任
            用户与第三方 AI 服务商之间的争议（包括计费、数据使用、服务中断等）由用户与该服务商自行解决，本应用不承担连带责任。

            7. 最终解释权
            本应用保留对本免责声明的最终解释权。
        """.trimIndent(),
        referencedLaws = listOf(
            LawReference(
                name = "生成式人工智能服务管理暂行办法",
                description = "规范生成式 AI 服务，明确提供者与使用者的责任义务",
                officialUrl = "http://www.cac.gov.cn/2023-07/13/c_1690898327029107.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "互联网信息服务深度合成管理规定",
                description = "规范深度合成服务，要求标明 AI 生成内容",
                officialUrl = "http://www.cac.gov.cn/2022-12/11/c_1672221949354811.htm",
                jurisdiction = Jurisdiction.DOMESTIC
            ),
            LawReference(
                name = "EU AI Act（欧盟人工智能法案）",
                description = "欧盟 AI 监管框架，对 AI 系统的风险分级管理",
                officialUrl = "https://artificialintelligenceact.eu/",
                jurisdiction = Jurisdiction.INTERNATIONAL
            )
        )
    )

    /** 所有法律文档（按展示顺序）。 */
    val allDocuments: List<LegalDocument> = listOf(userAgreement, privacyPolicy, disclaimer)
}
