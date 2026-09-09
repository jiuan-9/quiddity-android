package com.quiddity.app.domain.spy

import kotlin.random.Random

/*
 * 谁是卧底本地脚本 AI：预置发言模板 + 简单投票策略。
 * 脚本玩家不调用 LLM，保证即使离线/未配 API 也能满员开局。
 * 平民用自己词的特征提示造句；卧底用模糊模板藏词。
 */
object SpyScriptBot {

    /** 平民发言模板：%s 处填入词的特征提示。 */
    private val civilianTemplates = listOf(
        "我的词%s，大家应该都不陌生。",
        "我拿到的这个词%s，平时挺常见的。",
        "这么说吧，我的词%s，你懂的。",
        "我这个词%s，跟生活关系挺大的。",
        "我抽到的词%s，具体是啥我不能说。",
        "我这边的词%s，猜不出来我再多给点提示。",
        "我的词%s，反正不是冷门的东西。"
    )

    /** 卧底模糊发言模板：不透露任何特征，避免露馅。 */
    private val spyTemplates = listOf(
        "嗯……我的词嘛，挺普通的，大家应该都能猜到。",
        "我这个词不好形容，反正很常见就是了。",
        "我的词简单说就是那种很日常的东西。",
        "这轮我就不多说了，听你们说能对得上。",
        "我的词跟大家的词应该差不太多。",
        "嗯……差不多就是那个意思吧，你们都懂的。"
    )

    /** 白板伪装发言模板：没有词，只能附和与模仿。 */
    private val blankTemplates = listOf(
        "我这张牌嘛……嗯，跟你们说的应该是一回事。",
        "我拿到的就是那种很常见的东西，大家都懂的。",
        "我也不多描述，反正跟你们刚才说的能对得上。",
        "我的词啊，就是你们说的那种，不用猜了。",
        "嗯……差不多吧，反正不是冷门的东西。"
    )

    /** PK 平民自证模板：强调自己说得准、像自己人。 */
    private val pkCivilianTemplates = listOf(
        "我再说一次，我的词就是刚才描述的那个，特征完全对得上。",
        "我对我的词很有把握，谁要是含糊其辞谁才可疑。",
        "我拿到的就是大家说的那个东西，这一点我确定。",
        "我的描述从头到尾都很具体，你们细想就知道我是自己人。"
    )

    /** PK 卧底狡辩模板：继续模糊，把水搅浑。 */
    private val pkSpyTemplates = listOf(
        "我的词真的跟你们一样，只是我不太会形容而已。",
        "越是这个时候我越要说，我和大家拿的是同一个词。",
        "我刚才说得含蓄，是怕说得太直白被你们当成卧底。",
        "你们要是把我投出去，就真的错怪好人了。"
    )

    /** PK 白板继续装傻。 */
    private val pkBlankTemplates = listOf(
        "我跟大家拿的就是同一个词，这一点我可以保证。",
        "我的牌和你们说的完全对得上，只是我嘴笨不会描述。",
        "别投我，我真是自己人，不信你们再听我说一句。"
    )

    /** 脚本发言：平民引一条特征，卧底/白板用模糊模板；PK 时用对应辩护模板。 */
    fun speak(
        state: SpyGameState,
        playerIndex: Int,
        random: Random = Random.Default,
        pk: Boolean = false
    ): String {
        val player = state.player(playerIndex) ?: return ""
        if (pk) {
            return when (player.role) {
                null -> ""
                SpyRole.CIVILIAN -> pkCivilianTemplates[random.nextInt(pkCivilianTemplates.size)]
                SpyRole.SPY -> pkSpyTemplates[random.nextInt(pkSpyTemplates.size)]
                SpyRole.BLANK -> pkBlankTemplates[random.nextInt(pkBlankTemplates.size)]
            }
        }
        if (player.role == SpyRole.SPY) {
            return spyTemplates[random.nextInt(spyTemplates.size)]
        }
        if (player.role == SpyRole.BLANK) {
            return blankTemplates[random.nextInt(blankTemplates.size)]
        }
        val hints = state.wordPair?.civilianHints ?: emptyList()
        if (hints.isEmpty()) {
            return civilianTemplates[random.nextInt(civilianTemplates.size)].format("")
        }
        val hint = hints[random.nextInt(hints.size)]
        return civilianTemplates[random.nextInt(civilianTemplates.size)].format("，$hint")
    }

    /** 脚本投票：排除自己与出局者，随机投一个（也排除上一轮刚被投出的人）。 */
    fun vote(state: SpyGameState, playerIndex: Int, random: Random = Random.Default): Int? {
        val candidates = state.alivePlayers
            .filter { it.index != playerIndex }
            .map { it.index }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    /** 脚本 PK 投票：只投给平票候选人，随机二选一。 */
    fun pkVote(state: SpyGameState, playerIndex: Int, random: Random = Random.Default): Int? {
        val candidates = state.pkCandidates.filter { it != playerIndex }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }
}
