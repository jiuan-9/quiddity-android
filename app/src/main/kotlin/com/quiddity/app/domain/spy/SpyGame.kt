package com.quiddity.app.domain.spy

import kotlin.random.Random

/*
 * 谁是卧底领域模型：词库 + 不可变游戏状态机（发牌 / 发言 / 投票 / PK / 胜负）。
 * 纯 Kotlin，无 UI 依赖，可直接单测。
 *
 * 规则：
 * - 所有玩家各拿一个词，其中卧底（经典 1 名 / 双卧底 2 名）的词与平民不同；
 *   白板模式额外有一名玩家拿到空白牌（无词），归属卧底阵营；
 * - 每轮所有存活玩家依次用一句话描述自己的词（不能说出词本身）；
 * - 发言结束后投票，票数最多者出局；平票进入 PK 对决：平票者各补一句发言，
 *   其余存活玩家在平票者之间二选一，仍平票则无人出局进入下一轮；
 * - 卧底方（卧底 + 白板）全部出局 → 平民胜；存活卧底方人数 ≥ 存活平民人数 → 卧底方胜。
 */

/** 游戏角色：平民 / 卧底 / 白板（无词，归属卧底阵营）。 */
enum class SpyRole { CIVILIAN, SPY, BLANK }

/** 对局阶段：发牌 / 发言 / 投票 / 平票对决 / 终局。 */
enum class SpyPhase { DEAL, SPEAK, VOTE, PK, FINISHED }

/** 玩法模式：经典 1 卧底 / 双卧底 / 白板。 */
enum class SpyGameMode(val label: String) {
    CLASSIC("经典"),
    DOUBLE_SPY("双卧底"),
    BLANK("白板")
}

/** 玩家来源：真人 / LLM 角色 / 本地脚本。 */
enum class SpyPlayerKind { USER, LLM, SCRIPT }

/** 一组词对：平民词 + 卧底词 + 各自的脚本提示特征 + 分类。 */
data class SpyWordPair(
    val id: String,
    val civilian: String,
    val spy: String,
    val civilianHints: List<String>,
    val spyHints: List<String>,
    val category: String = SPY_CATEGORY_OTHER
)

/** 词库：内置 20 组词对，开局随机抽取，可按分类筛选。 */
object SpyWordBank {

    val pairs: List<SpyWordPair> = listOf(
        SpyWordPair(
            id = "apple_pear", civilian = "苹果", spy = "梨",
            civilianHints = listOf("是一种水果", "红色或绿色", "秋天最常见", "能削皮吃"),
            spyHints = listOf("是一种水果", "水分很多", "形状像葫芦", "清脆多汁"),
            category = SPY_CATEGORY_FOOD
        ),
        SpyWordPair(
            id = "coffee_milktea", civilian = "咖啡", spy = "奶茶",
            civilianHints = listOf("是一种饮品", "喝起来发苦", "上班提神", "通常是热的"),
            spyHints = listOf("是一种饮品", "味道偏甜", "有很多口味", "年轻人爱喝"),
            category = SPY_CATEGORY_FOOD
        ),
        SpyWordPair(
            id = "taxi_bus", civilian = "出租车", spy = "公交车",
            civilianHints = listOf("是一种交通工具", "招手就能坐", "按里程计费", "最多坐几个人"),
            spyHints = listOf("是一种交通工具", "有固定线路", "要等站台", "能坐很多人"),
            category = SPY_CATEGORY_TRANSPORT
        ),
        SpyWordPair(
            id = "cat_dog", civilian = "猫", spy = "狗",
            civilianHints = listOf("是一种宠物", "会喵喵叫", "喜欢抓老鼠", "走路很轻"),
            spyHints = listOf("是一种宠物", "会汪汪叫", "忠诚看家", "喜欢散步"),
            category = SPY_CATEGORY_PET
        ),
        SpyWordPair(
            id = "football_basketball", civilian = "足球", spy = "篮球",
            civilianHints = listOf("是一种球", "用脚踢", "比赛是十一人对十一人", "有世界杯"),
            spyHints = listOf("是一种球", "用手拍", "投篮得分", "有 NBA"),
            category = SPY_CATEGORY_SPORT
        ),
        SpyWordPair(
            id = "library_bookstore", civilian = "图书馆", spy = "书店",
            civilianHints = listOf("是一个场所", "书可以免费借", "凭卡进入", "不能大声说话"),
            spyHints = listOf("是一个场所", "书要花钱买", "经常打折", "有畅销书排行榜"),
            category = SPY_CATEGORY_PLACE
        ),
        SpyWordPair(
            id = "piano_guitar", civilian = "钢琴", spy = "吉他",
            civilianHints = listOf("是一种乐器", "黑白琴键", "弹的时候坐姿端正", "有优雅的音色"),
            spyHints = listOf("是一种乐器", "六根弦", "背着弹", "弹唱很流行"),
            category = SPY_CATEGORY_SPORT
        ),
        SpyWordPair(
            id = "phone_tablet", civilian = "手机", spy = "平板电脑",
            civilianHints = listOf("是一种电子产品", "可以打电话", "随身携带", "屏幕不大"),
            spyHints = listOf("是一种电子产品", "屏幕比较大", "看剧很舒服", "不能打电话"),
            category = SPY_CATEGORY_ELECTRONIC
        ),
        SpyWordPair(
            id = "cinema_theater", civilian = "电影院", spy = "剧院",
            civilianHints = listOf("是一个场所", "看新上映的电影", "有爆米花", "银幕很大"),
            spyHints = listOf("是一个场所", "看话剧表演", "有舞台幕布", "演员现场演出"),
            category = SPY_CATEGORY_PLACE
        ),
        SpyWordPair(
            id = "train_subway", civilian = "火车", spy = "地铁",
            civilianHints = listOf("是一种交通工具", "有卧铺车厢", "长途出行", "有站台票"),
            spyHints = listOf("是一种交通工具", "在地下跑", "两三分钟一班", "上下班高峰人多"),
            category = SPY_CATEGORY_TRANSPORT
        ),
        SpyWordPair(
            id = "raincoat_umbrella", civilian = "雨衣", spy = "雨伞",
            civilianHints = listOf("是防雨用品", "穿在身上", "有帽子和袖子", "骑车时用"),
            spyHints = listOf("是防雨用品", "举在头顶", "有伞骨", "有折叠款"),
            category = SPY_CATEGORY_DAILY
        ),
        SpyWordPair(
            id = "beer_soda", civilian = "啤酒", spy = "汽水",
            civilianHints = listOf("是一种饮品", "有酒精", "配烧烤喝", "有泡沫"),
            spyHints = listOf("是一种饮品", "没有酒精", "气泡多", "夏天冰镇好喝"),
            category = SPY_CATEGORY_FOOD
        ),
        SpyWordPair(
            id = "hotpot_malatang", civilian = "火锅", spy = "麻辣烫",
            civilianHints = listOf("是一道美食", "围着锅一起吃", "有牛油锅底", "蘸料很重要"),
            spyHints = listOf("是一道美食", "用竹签串着吃", "按串计费", "麻辣口味"),
            category = SPY_CATEGORY_FOOD
        ),
        SpyWordPair(
            id = "dumpling_wonton", civilian = "饺子", spy = "馄饨",
            civilianHints = listOf("是一道美食", "过年常吃", "皮上有褶子", "蘸醋吃"),
            spyHints = listOf("是一道美食", "汤里有虾皮紫菜", "皮薄半透明", "早餐常见"),
            category = SPY_CATEGORY_FOOD
        ),
        SpyWordPair(
            id = "ebike_bike", civilian = "电动车", spy = "自行车",
            civilianHints = listOf("是一种代步工具", "需要充电", "有油门把手", "速度比较快"),
            spyHints = listOf("是一种代步工具", "靠脚蹬前进", "链条驱动", "绿色环保"),
            category = SPY_CATEGORY_TRANSPORT
        ),
        SpyWordPair(
            id = "gym_pool", civilian = "健身房", spy = "游泳馆",
            civilianHints = listOf("是一个场所", "办卡才能进", "有很多器械", "可以练肌肉"),
            spyHints = listOf("是一个场所", "办卡才能进", "有泳道", "需要换泳衣"),
            category = SPY_CATEGORY_PLACE
        ),
        SpyWordPair(
            id = "badminton_pingpong", civilian = "羽毛球", spy = "乒乓球",
            civilianHints = listOf("是一种球类运动", "中间有网子", "用拍子打", "球上有羽毛"),
            spyHints = listOf("是一种球类运动", "有球台", "球很小", "讲究旋转"),
            category = SPY_CATEGORY_SPORT
        ),
        SpyWordPair(
            id = "wechat_qq", civilian = "微信", spy = "QQ",
            civilianHints = listOf("是一个社交软件", "几乎人人都有", "可以发朋友圈", "用来扫码支付"),
            spyHints = listOf("是一个社交软件", "有等级和会员", "可以换皮肤", "以前玩空间"),
            category = SPY_CATEGORY_ELECTRONIC
        ),
        SpyWordPair(
            id = "lighter_match", civilian = "打火机", spy = "火柴",
            civilianHints = listOf("是一种点火工具", "用液体燃料", "可以反复使用", "有咔嚓声"),
            spyHints = listOf("是一种点火工具", "用木头做的", "基本是一次性", "摩擦就能点燃"),
            category = SPY_CATEGORY_DAILY
        ),
        SpyWordPair(
            id = "alarm_watch", civilian = "闹钟", spy = "手表",
            civilianHints = listOf("是一种计时工具", "早上叫醒用", "有响铃功能", "放在床头"),
            spyHints = listOf("是一种计时工具", "戴在手腕上", "随时看时间", "有的能计步"),
            category = SPY_CATEGORY_DAILY
        ),
        SpyWordPair(
            id = "doctor_nurse", civilian = "医生", spy = "护士",
            civilianHints = listOf("是一种职业", "给人看病开药", "穿白大褂", "医院里常见"),
            spyHints = listOf("是一种职业", "打针输液", "戴护士帽", "照顾病人起居"),
            category = SPY_CATEGORY_PERSON
        ),
        SpyWordPair(
            id = "teacher_coach", civilian = "老师", spy = "教练",
            civilianHints = listOf("是一种职业", "在教室里上课", "布置作业", "有寒暑假"),
            spyHints = listOf("是一种职业", "在训练场指导", "教动作技巧", "带队比赛"),
            category = SPY_CATEGORY_PERSON
        ),
        SpyWordPair(
            id = "actor_singer", civilian = "演员", spy = "歌手",
            civilianHints = listOf("是一种职业", "在影视剧里扮演角色", "有台词", "现场是拍摄"),
            spyHints = listOf("是一种职业", "在舞台上唱歌", "有代表作", "开演唱会"),
            category = SPY_CATEGORY_PERSON
        ),
        SpyWordPair(
            id = "chef_waiter", civilian = "厨师", spy = "服务员",
            civilianHints = listOf("是一种职业", "在厨房掌勺", "做菜讲究火候", "戴厨师帽"),
            spyHints = listOf("是一种职业", "在前厅点菜上菜", "负责接待客人", "穿围裙"),
            category = SPY_CATEGORY_PERSON
        ),
        SpyWordPair(
            id = "movie_tv", civilian = "电影", spy = "电视剧",
            civilianHints = listOf("是娱乐内容", "两小时左右", "在影院大银幕看", "有票房"),
            spyHints = listOf("是娱乐内容", "几十集连续播出", "在家里追着看", "有剧集更新"),
            category = SPY_CATEGORY_ENTERTAINMENT
        ),
        SpyWordPair(
            id = "ktv_bar", civilian = "KTV", spy = "酒吧",
            civilianHints = listOf("是一个娱乐场所", "点歌唱", "有麦克风和包厢", "按小时计费"),
            spyHints = listOf("是一个娱乐场所", "喝东西聊天", "灯光昏暗", "有吧台"),
            category = SPY_CATEGORY_ENTERTAINMENT
        ),
        SpyWordPair(
            id = "script_killer_escape", civilian = "剧本杀", spy = "密室逃脱",
            civilianHints = listOf("是一种线下娱乐", "每人一个角色", "读剧本找线索", "有主持人带"),
            spyHints = listOf("是一种线下娱乐", "被关在房间里", "解谜找钥匙", "限时逃出"),
            category = SPY_CATEGORY_ENTERTAINMENT
        ),
        SpyWordPair(
            id = "concert_festival", civilian = "演唱会", spy = "音乐节",
            civilianHints = listOf("是一种演出活动", "一位歌手的专场", "荧光棒挥舞", "唱成名曲"),
            spyHints = listOf("是一种演出活动", "多位歌手轮番上场", "在户外草坪", "有多个舞台"),
            category = SPY_CATEGORY_ENTERTAINMENT
        ),
        SpyWordPair(
            id = "river_lake", civilian = "河流", spy = "湖泊",
            civilianHints = listOf("是自然地貌", "水在流动", "有源头和入海口", "可以漂流"),
            spyHints = listOf("是自然地貌", "水面平静", "周围常有山", "可以划船"),
            category = SPY_CATEGORY_NATURE
        ),
        SpyWordPair(
            id = "forest_grassland", civilian = "森林", spy = "草原",
            civilianHints = listOf("是自然地貌", "长满树木", "有松针落叶", "空气很清新"),
            spyHints = listOf("是自然地貌", "一眼望不到边", "风吹草低", "适合放牧"),
            category = SPY_CATEGORY_NATURE
        ),
        SpyWordPair(
            id = "star_moon", civilian = "星星", spy = "月亮",
            civilianHints = listOf("是夜空中的天体", "一闪一闪", "数量非常多", "可以许愿"),
            spyHints = listOf("是夜空中的天体", "圆缺变化", "夜晚最亮", "有嫦娥传说"),
            category = SPY_CATEGORY_NATURE
        ),
        SpyWordPair(
            id = "snow_hail", civilian = "雪花", spy = "冰雹",
            civilianHints = listOf("是一种天气现象", "冬天从天上飘下", "六角形", "能堆雪人"),
            spyHints = listOf("是一种天气现象", "夏天也可能出现", "像小冰球", "会砸坏东西"),
            category = SPY_CATEGORY_NATURE
        )
    )

    val categories: List<String> = listOf(
        SPY_CATEGORY_FOOD,
        SPY_CATEGORY_TRANSPORT,
        SPY_CATEGORY_PLACE,
        SPY_CATEGORY_PET,
        SPY_CATEGORY_SPORT,
        SPY_CATEGORY_ELECTRONIC,
        SPY_CATEGORY_DAILY,
        SPY_CATEGORY_PERSON,
        SPY_CATEGORY_ENTERTAINMENT,
        SPY_CATEGORY_NATURE
    )

    /** 随机抽一组词对；指定分类时优先在该分类内抽取，分类无词则退回全库。 */
    fun randomPair(random: Random = Random.Default, category: String? = null): SpyWordPair {
        val pool = if (category.isNullOrBlank()) pairs else pairs.filter { it.category == category }
        val source = pool.ifEmpty { pairs }
        return source[random.nextInt(source.size)]
    }
}

const val SPY_CATEGORY_FOOD = "饮食"
const val SPY_CATEGORY_TRANSPORT = "交通"
const val SPY_CATEGORY_PLACE = "场所"
const val SPY_CATEGORY_PET = "萌宠"
const val SPY_CATEGORY_SPORT = "文体"
const val SPY_CATEGORY_ELECTRONIC = "电子"
const val SPY_CATEGORY_DAILY = "日常"
const val SPY_CATEGORY_PERSON = "人物"
const val SPY_CATEGORY_ENTERTAINMENT = "娱乐"
const val SPY_CATEGORY_NATURE = "自然"
const val SPY_CATEGORY_OTHER = "其他"

/** 一名玩家（身份与词在发牌后确定，仅游戏内部可见）。 */
data class SpyPlayer(
    val index: Int,
    val name: String,
    val kind: SpyPlayerKind,
    val persona: String? = null,
    val avatarUri: String? = null,
    val role: SpyRole? = null,
    val word: String? = null,
    val alive: Boolean = true
)

/** 一轮中的一次发言。 */
data class SpySpeech(
    val round: Int,
    val playerIndex: Int,
    val text: String
)

/** 一轮中的一次投票（from 投给 to）。 */
data class SpyVote(
    val round: Int,
    val fromIndex: Int,
    val toIndex: Int
)

/** 谁是卧底对局状态（不可变快照，逐操作替换）。 */
data class SpyGameState(
    val id: String = "",
    val players: List<SpyPlayer>,
    val round: Int = 1,
    val phase: SpyPhase = SpyPhase.DEAL,
    val mode: SpyGameMode = SpyGameMode.CLASSIC,
    val wordPair: SpyWordPair? = null,
    /** 本轮发言/投票的玩家顺序（index 列表，每轮随机打乱），避免顺序固定乏味。 */
    val roundOrder: List<Int> = emptyList(),
    val speeches: List<SpySpeech> = emptyList(),
    val votes: List<SpyVote> = emptyList(),
    val pkCandidates: List<Int> = emptyList(),
    val pkSpeeches: List<SpySpeech> = emptyList(),
    val pkVotes: List<SpyVote> = emptyList(),
    val eliminated: List<Int> = emptyList(),
    val winner: SpyRole? = null
) {

    val alivePlayers: List<SpyPlayer>
        get() = players.filter { it.alive }

    /** 卧底玩家 index（经典模式单卧底；多卧底取第一个，发牌后才有）。 */
    val spyIndex: Int?
        get() = spyIndices.firstOrNull()

    /** 全部卧底玩家 index。 */
    val spyIndices: List<Int>
        get() = players.filter { it.role == SpyRole.SPY }.map { it.index }

    /** 白板玩家 index（白板模式才有）。 */
    val blankIndex: Int?
        get() = players.firstOrNull { it.role == SpyRole.BLANK }?.index

    /** 本轮的发言记录。 */
    val roundSpeeches: List<SpySpeech>
        get() = speeches.filter { it.round == round }

    /** 本轮的投票记录。 */
    val roundVotes: List<SpyVote>
        get() = votes.filter { it.round == round }

    /** PK 平票者是否已全部补完发言。 */
    val pkSpeakComplete: Boolean
        get() = pkCandidates.isNotEmpty() &&
            pkCandidates.all { index -> pkSpeeches.any { it.playerIndex == index } }

    fun player(index: Int): SpyPlayer? = players.getOrNull(index)

    /** 本轮已发言的玩家集合。 */
    private fun speakersThisRound(): Set<Int> =
        roundSpeeches.map { it.playerIndex }.toSet()

    /** 本轮已投票的玩家集合。 */
    private fun votersThisRound(): Set<Int> =
        roundVotes.map { it.fromIndex }.toSet()

    /** 本轮按随机顺序排队的存活玩家 index（无记录时回退 index 升序）。 */
    private fun roundTurnOrder(): List<Int> {
        val order = roundOrder.ifEmpty { players.filter { it.alive }.map { it.index } }
        return order.filter { index -> players.getOrNull(index)?.alive == true }
    }

    /** 本轮下一个应发言的存活玩家（按 [roundOrder] 随机顺序）；本轮已全部发言完返回 null。 */
    fun nextSpeaker(): SpyPlayer? {
        val next = roundTurnOrder().firstOrNull { it !in speakersThisRound() }
        return next?.let { players.first { p -> p.index == it } }
    }

    /** 本轮下一个应投票的存活玩家（按 [roundOrder] 随机顺序）；全部投完返回 null。 */
    fun nextVoter(): SpyPlayer? {
        val next = roundTurnOrder().firstOrNull { it !in votersThisRound() }
        return next?.let { players.first { p -> p.index == it } }
    }

    /** PK 阶段下一个应补发言的平票玩家；全部说完返回 null。 */
    fun nextPkSpeaker(): SpyPlayer? =
        if (pkSpeakComplete) {
            null
        } else {
            alivePlayers.firstOrNull {
                it.index in pkCandidates && pkSpeeches.none { s -> s.playerIndex == it.index }
            }
        }

    /** PK 阶段下一个应投票的存活玩家（平票者本人不投票）；全部投完返回 null。 */
    fun nextPkVoter(): SpyPlayer? =
        if (!pkSpeakComplete) {
            null
        } else {
            alivePlayers.firstOrNull {
                it.index !in pkCandidates && pkVotes.none { v -> v.fromIndex == it.index }
            }
        }
}

/** 发牌结果。 */
sealed interface SpyDeal {
    data class Done(val state: SpyGameState) : SpyDeal
}

/** 发言结果。 */
sealed interface SpySpeak {
    data class Done(val state: SpyGameState) : SpySpeak
    data class Rejected(val reason: String) : SpySpeak
}

/** 投票结果。 */
sealed interface SpyVoteOutcome {
    /** 票已计，本轮结束：出局者（可能为 null=平票无人出局）与终局状态。 */
    data class Done(
        val state: SpyGameState,
        val eliminatedIndex: Int?,
        val tie: Boolean,
        val gameOver: Boolean
    ) : SpyVoteOutcome

    data class Rejected(val reason: String) : SpyVoteOutcome
}

/** PK 补发言结果。 */
sealed interface SpyPkSpeak {
    data class Done(val state: SpyGameState) : SpyPkSpeak
    data class Rejected(val reason: String) : SpyPkSpeak
}

/** PK 二选一投票结果。 */
sealed interface SpyPkVoteOutcome {
    data class Done(
        val state: SpyGameState,
        val eliminatedIndex: Int?,
        val tie: Boolean,
        val gameOver: Boolean
    ) : SpyPkVoteOutcome

    data class Rejected(val reason: String) : SpyPkVoteOutcome
}

/**
 * 谁是卧底游戏状态机（与 BoardGame 同风格：不可变 + 纯函数）。
 */
object SpyGame {

    /**
     * 开局：按模式分配身份与词，进入发牌阶段。
     * - 经典：1 名卧底；
     * - 双卧底：2 名卧底（同词）；
     * - 白板：1 名卧底 + 1 名白板（无词）。
     */
    fun deal(
        players: List<SpyPlayer>,
        wordPair: SpyWordPair,
        random: Random = Random.Default,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): SpyDeal {
        val specialCount = when (mode) {
            SpyGameMode.CLASSIC -> 1
            SpyGameMode.DOUBLE_SPY, SpyGameMode.BLANK -> 2
        }
        require(players.size > specialCount) { "至少需要 ${specialCount + 1} 名玩家" }
        val specialIndices = players.indices.shuffled(random).take(specialCount)
        val blankIndex = if (mode == SpyGameMode.BLANK) {
            specialIndices[random.nextInt(specialIndices.size)]
        } else {
            null
        }
        val dealt = players.mapIndexed { index, p ->
            val role = when {
                index == blankIndex -> SpyRole.BLANK
                index in specialIndices -> SpyRole.SPY
                else -> SpyRole.CIVILIAN
            }
            p.copy(
                role = role,
                word = when (role) {
                    SpyRole.CIVILIAN -> wordPair.civilian
                    SpyRole.SPY -> wordPair.spy
                    SpyRole.BLANK -> ""
                }
            )
        }
        return SpyDeal.Done(
            SpyGameState(
                id = java.util.UUID.randomUUID().toString(),
                players = dealt,
                round = 1,
                phase = SpyPhase.DEAL,
                mode = mode,
                wordPair = wordPair,
                roundOrder = dealt.filter { it.alive }.map { it.index }.shuffled(random)
            )
        )
    }

    /** 全员看过词后，进入第一轮发言。 */
    fun startSpeaking(state: SpyGameState): SpyGameState =
        state.copy(phase = SpyPhase.SPEAK, speeches = emptyList(), votes = emptyList())

    /** 记录一次发言；本轮全部存活玩家发言完毕自动进入投票阶段。 */
    fun speak(state: SpyGameState, playerIndex: Int, text: String): SpySpeak {
        val player = state.player(playerIndex)
        if (state.phase != SpyPhase.SPEAK) return SpySpeak.Rejected("当前不在发言阶段")
        if (player == null || !player.alive) return SpySpeak.Rejected("该玩家已出局")
        if (text.isBlank()) return SpySpeak.Rejected("发言内容为空")
        if (state.roundSpeeches.any { it.playerIndex == playerIndex }) {
            return SpySpeak.Rejected("本轮已发过言")
        }
        val speech = SpySpeech(round = state.round, playerIndex = playerIndex, text = text.trim())
        val speeches = state.speeches + speech
        val allSpoken = state.alivePlayers.all { it.index in speeches.filter { s -> s.round == state.round }.map { it.playerIndex }.toSet() }
        val phase = if (allSpoken) SpyPhase.VOTE else SpyPhase.SPEAK
        return SpySpeak.Done(state.copy(speeches = speeches, phase = phase))
    }

    /** 记录一次投票；全部存活玩家投完后立即计票并推进终局/下一轮。 */
    fun vote(
        state: SpyGameState,
        fromIndex: Int,
        toIndex: Int,
        random: Random = Random.Default
    ): SpyVoteOutcome {
        val voter = state.player(fromIndex)
        val target = state.player(toIndex)
        if (state.phase != SpyPhase.VOTE) return SpyVoteOutcome.Rejected("当前不在投票阶段")
        if (voter == null || !voter.alive) return SpyVoteOutcome.Rejected("投票者已出局")
        if (target == null || !target.alive) return SpyVoteOutcome.Rejected("被投玩家已出局")
        if (fromIndex == toIndex) return SpyVoteOutcome.Rejected("不能投自己")
        if (state.roundVotes.any { it.fromIndex == fromIndex }) {
            return SpyVoteOutcome.Rejected("本轮已投过票")
        }
        val vote = SpyVote(round = state.round, fromIndex = fromIndex, toIndex = toIndex)
        val votes = state.votes + vote
        val allVoted = state.alivePlayers.all { it.index in votes.filter { v -> v.round == state.round }.map { it.fromIndex }.toSet() }
        if (!allVoted) return SpyVoteOutcome.Done(state.copy(votes = votes), null, false, false)

        return tally(state.copy(votes = votes), random)
    }

    /** 计票：票数最多的玩家出局；平票进入 PK 对决（无人可当投票人时无人出局进下一轮）。 */
    private fun tally(state: SpyGameState, random: Random = Random.Default): SpyVoteOutcome {
        val roundVotes = state.roundVotes
        val tallies = roundVotes.groupBy { it.toIndex }.mapValues { it.value.size }
        val maxVotes = tallies.values.maxOrNull() ?: 0
        val top = tallies.filterValues { it == maxVotes }.keys
        val tie = top.size != 1
        val eliminatedIndex = if (tie) null else top.first()

        if (eliminatedIndex == null) {
            // 平票：有足够投票人时进入 PK 对决，否则无人出局直接下一轮
            if (top.size in 2 until state.alivePlayers.size) {
                val pk = state.copy(
                    phase = SpyPhase.PK,
                    pkCandidates = top.sorted(),
                    pkSpeeches = emptyList(),
                    pkVotes = emptyList()
                )
                return SpyVoteOutcome.Done(pk, null, true, false)
            }
            return SpyVoteOutcome.Done(nextRound(state, random = random), null, true, false)
        }

        val eliminated = state.player(eliminatedIndex) ?: return SpyVoteOutcome.Rejected("计票异常")
        val players = state.players.map { if (it.index == eliminatedIndex) it.copy(alive = false) else it }

        val winner = checkWinner(players)

        val next = if (winner == null) {
            nextRound(state, players, random)
        } else {
            state.copy(players = players, phase = SpyPhase.FINISHED, winner = winner)
        }
        return SpyVoteOutcome.Done(next, eliminatedIndex, false, winner != null)
    }

    /** 记录一次 PK 补发言；全部平票者说完后等待其余玩家投票。 */
    fun pkSpeak(state: SpyGameState, playerIndex: Int, text: String): SpyPkSpeak {
        val player = state.player(playerIndex)
        if (state.phase != SpyPhase.PK) return SpyPkSpeak.Rejected("当前不在 PK 对决阶段")
        if (player == null || !player.alive) return SpyPkSpeak.Rejected("该玩家已出局")
        if (playerIndex !in state.pkCandidates) return SpyPkSpeak.Rejected("只有平票者需要补发言")
        if (text.isBlank()) return SpyPkSpeak.Rejected("发言内容为空")
        if (state.pkSpeeches.any { it.playerIndex == playerIndex }) {
            return SpyPkSpeak.Rejected("本轮 PK 已发过言")
        }
        return SpyPkSpeak.Done(
            state.copy(pkSpeeches = state.pkSpeeches + SpySpeech(round = state.round, playerIndex = playerIndex, text = text.trim()))
        )
    }

    /** 记录一次 PK 二选一投票；全部非平票存活玩家投完后计票。 */
    fun pkVote(
        state: SpyGameState,
        fromIndex: Int,
        toIndex: Int,
        random: Random = Random.Default
    ): SpyPkVoteOutcome {
        val voter = state.player(fromIndex)
        val target = state.player(toIndex)
        if (state.phase != SpyPhase.PK) return SpyPkVoteOutcome.Rejected("当前不在 PK 对决阶段")
        if (!state.pkSpeakComplete) return SpyPkVoteOutcome.Rejected("平票者发言未结束")
        if (voter == null || !voter.alive) return SpyPkVoteOutcome.Rejected("投票者已出局")
        if (fromIndex in state.pkCandidates) return SpyPkVoteOutcome.Rejected("平票者本人不参与 PK 投票")
        if (target == null || !target.alive || toIndex !in state.pkCandidates) {
            return SpyPkVoteOutcome.Rejected("只能投给平票者")
        }
        if (state.pkVotes.any { it.fromIndex == fromIndex }) {
            return SpyPkVoteOutcome.Rejected("本轮 PK 已投过票")
        }
        val pkVotes = state.pkVotes + SpyVote(round = state.round, fromIndex = fromIndex, toIndex = toIndex)
        val allVoted = state.alivePlayers.all {
            it.index in state.pkCandidates || pkVotes.any { v -> v.fromIndex == it.index }
        }
        if (!allVoted) return SpyPkVoteOutcome.Done(state.copy(pkVotes = pkVotes), null, false, false)
        return tallyPk(state.copy(pkVotes = pkVotes), random)
    }

    /** PK 计票：票多者出局；仍平票则无人出局进入下一轮。 */
    private fun tallyPk(state: SpyGameState, random: Random = Random.Default): SpyPkVoteOutcome {
        val tallies = state.pkVotes.groupBy { it.toIndex }.mapValues { it.value.size }
        val maxVotes = tallies.values.maxOrNull() ?: 0
        val top = tallies.filterValues { it == maxVotes }.keys
        val tie = top.size != 1
        val eliminatedIndex = if (tie) null else top.first()

        if (eliminatedIndex == null) {
            return SpyPkVoteOutcome.Done(nextRound(state, random = random), null, true, false)
        }
        val players = state.players.map { if (it.index == eliminatedIndex) it.copy(alive = false) else it }
        val winner = checkWinner(players)
        val next = if (winner == null) {
            nextRound(state, players, random)
        } else {
            state.copy(players = players, phase = SpyPhase.FINISHED, winner = winner)
        }
        return SpyPkVoteOutcome.Done(next, eliminatedIndex, false, winner != null)
    }

    /** 胜负判定：卧底方（卧底 + 白板）全出局 → 平民胜；存活卧底方 ≥ 存活平民 → 卧底方胜。 */
    private fun checkWinner(players: List<SpyPlayer>): SpyRole? {
        val alive = players.filter { it.alive }
        val spies = alive.count { it.role == SpyRole.SPY }
        val blanks = alive.count { it.role == SpyRole.BLANK }
        val civilians = alive.count { it.role == SpyRole.CIVILIAN }
        return when {
            spies == 0 && blanks == 0 -> SpyRole.CIVILIAN
            spies + blanks >= civilians -> SpyRole.SPY
            else -> null
        }
    }

    /** 进入下一轮发言：清空 PK 现场，保留发言/投票历史。 */
    private fun nextRound(
        state: SpyGameState,
        players: List<SpyPlayer> = state.players,
        random: Random = Random.Default
    ): SpyGameState = state.copy(
        players = players,
        round = state.round + 1,
        phase = SpyPhase.SPEAK,
        pkCandidates = emptyList(),
        pkSpeeches = emptyList(),
        pkVotes = emptyList(),
        roundOrder = players.filter { it.alive }.map { it.index }.shuffled(random)
    )
}
