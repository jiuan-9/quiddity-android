# Quiddity Android

> Quiddity AI 澶氭ā鍨嬪璇?Android 瀹㈡埛绔?鈥?涓€涓鎴风锛岃仛鍚堟墍鏈変富娴佸ぇ妯″瀷銆?
>
> 鐭ユ墍涓嶅敖锛屽線澶嶄笉姝?鈥?Know no bounds, repeat no end.

[![Release](https://img.shields.io/badge/release-v1.6.2-blue)](#涓嬭浇)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](#绯荤粺瑕佹眰)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](#鎶€鏈爤)

## 椤圭洰绠€浠?

Quiddity Android 鏄?Quiddity 绉诲姩绔殑鐙珛浜у搧锛堜笌 Quiddity-Chat銆丵uiddity-Agent 妗岄潰绔畬鍏ㄥ垎绂伙紝涓変釜浜у搧鏁版嵁浜掍笉浜掗€氾級銆傛湰浠撳簱涓?Android 绔殑鐙珛瀹炵幇銆?

### 鏍稿績鐗规€?

- **9 瀹跺唴缃?AI 鏈嶅姟鍟?+ 鑷畾涔?*锛?2 涓唴缃ā鍨嬶紙鍩虹绾?/ 杩涢樁绾?/ 瀹屾暣绾э級
- **鐙珛瑙嗚 OCR 鍚嶅唽**锛氳瘑鍥句笉璧拌亰澶╂ā鍨嬫。浣嶏紝qwen-vl / GLM-4V / 璞嗗寘瑙嗚 / DeepSeek-OCR 绛夊崟鐙厤缃?
- **妯″瀷鍒嗛厤鏂规**锛氭寜鍦烘櫙鑷姩鍖归厤鏈€浼樻ā鍨嬶紙鍐欎綔 / 缂栫▼ / 缈昏瘧 / 瑙嗚鈥︼級
- **澶氳疆瀵硅瘽 + 涓婁笅鏂囪蹇?*锛氬彲閰嶇疆涓婁笅鏂囪疆鏁帮紙1-200锛?
- **浼氳瘽鍘嬬缉锛堣蹇嗗簱锛?*锛氳繘闃剁骇妯″瀷榛樿姣?20 杞嚜鍔ㄥ帇缂╀竴娆★紝鑺傜渷 token
- **瑙掕壊鍗?/ System Prompt**锛氬畬鍏ㄨ嚜瀹氫箟 AI 韬唤涓庝汉璁?
- **Markdown 娓叉煋 + 浠ｇ爜楂樹寒**锛氬唴缃娉曢珮浜笌鏁板鍏紡
- **鍥惧儚璇嗗埆锛圴ision 妯″瀷锛?*锛氫笂浼犲浘鐗囪嚜鍔ㄨ皟鐢ㄥ妯℃€佹ā鍨嬶紝绾枃鏈ā鍨嬪彲寮€鍚?OCR 鍏滃簳
- **AI 鍥炲鎮诞绐?*锛氬簲鐢ㄥ垏鍚庡彴鏃舵皵娉″睍绀哄洖澶嶈繘搴︼紝鏀寔蹇嵎鍥炲涓庢嫋鍔ㄦ敹璧?
- **鏆楅粦 / 娴呰壊涓婚**锛氳窡闅忕郴缁熸垨鎵嬪姩鍒囨崲
- **绂荤嚎鑽夌 / 娑堟伅鎼滅储 / 浼氳瘽瀵煎嚭 / 涓€閿垎浜?*
- **鏈湴瀛樺偍**锛欰PI Key 浣跨敤 AES-GCM锛圓ndroid Keystore锛夊姞瀵嗕繚瀛橈紱瀵硅瘽璁板綍浠呭瓨鏈湴 JSON锛屼笉涓婁紶
- **缁х画璇?/ 寤惰繜鍙戦€?/ 閲嶆柊鐢熸垚 / 鎾ゅ洖娑堟伅** 绛夊畬澶囩殑鍙戦€佹帶鍒?

## 涓嬭浇

鍓嶅線瀹樼綉 [https://quiddity-3by.pages.dev/](https://quiddity-3by.pages.dev/) 涓嬭浇鏈€鏂扮増鏈€?

鏈€鏂?APK 浠ュ畼缃戜笅杞介〉涓哄噯锛堝綋鍓嶇増鏈?1.6.2锛夈€?

## 绯荤粺瑕佹眰

- **鏈€浣?Android 鐗堟湰**锛欰ndroid 8.0锛圓PI Level 26锛?
- **鐩爣 Android 鐗堟湰**锛欰ndroid 14锛圓PI Level 34锛?
- **鏋舵瀯**锛歛rm64-v8a锛堟帹鑽愶級/ armeabi-v7a / x86_64
- **瀛樺偍**锛氱害 50 MB
- **缃戠粶**锛氶渶瑕佽仈缃戣闂?AI API锛堥櫎鏈湴妯″瀷澶栵級

## 鎶€鏈爤

- **璇█**锛欿otlin 2.0.21
- **UI**锛欽etpack Compose锛圔OM 2024.10.01锛孧aterial 3锛?
- **鏋舵瀯**锛歁VVM + Repository + ServiceLocator 鎵嬪姩渚濊禆娉ㄥ叆
- **鏁版嵁鎸佷箙鍖?*锛?
  - DataStore Preferences锛堣缃」锛?
  - AES-256-GCM锛圓ndroid Keystore 瀵嗛挜锛孉PI Key 鍔犲瘑锛?
  - 鑷湁 JSON 鎸佷箙鍖栵紙瀵硅瘽涓庢秷鎭紱涓嶄緷璧?Room锛?
- **缃戠粶**锛歄kHttp 4.12 + okhttp-sse锛堟祦寮忓搷搴旓級
- **鍥剧墖鍔犺浇**锛欳oil 2.7
- **鍗忕▼**锛歬otlinx-coroutines 1.9
- **搴忓垪鍖?*锛歬otlinx-serialization-json
- **瀵艰埅**锛歂avigation Compose 2.8
- **鏋勫缓宸ュ叿**锛欸radle 8.9 + AGP 8.6 + KSP 2.0.21

## 椤圭洰缁撴瀯

```
Quiddity-android/
鈹溾攢鈹€ app/                                 # 搴旂敤妯″潡
鈹?  鈹溾攢鈹€ build.gradle.kts                 # 妯″潡鏋勫缓鑴氭湰
鈹?  鈹溾攢鈹€ proguard-rules.pro               # R8/ProGuard 瑙勫垯
鈹?  鈹斺攢鈹€ src/
鈹?      鈹溾攢鈹€ main/
鈹?      鈹?  鈹溾攢鈹€ AndroidManifest.xml      # 搴旂敤娓呭崟
鈹?      鈹?  鈹溾攢鈹€ kotlin/com/quiddity/app/ # Kotlin 婧愪唬鐮?
鈹?      鈹?  鈹?  鈹溾攢鈹€ data/                # 鏁版嵁灞?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ local/           # 鏈湴鎸佷箙鍖栵紙DataStore銆佸姞瀵嗘枃浠讹級
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ model/           # 鏁版嵁妯″瀷锛圕onversation銆丮essage鈥︼級
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ remote/          # 缃戠粶灞傦紙API銆丼SE锛?
鈹?      鈹?  鈹?  鈹?  鈹斺攢鈹€ repo/            # 浠撳偍灞傦紙ChatRepository 绛夛級
鈹?      鈹?  鈹?  鈹溾攢鈹€ di/                  # ServiceLocator 鎵嬪姩渚濊禆娉ㄥ叆
鈹?      鈹?  鈹?  鈹溾攢鈹€ domain/              # 涓氬姟閫昏緫锛圓piCatalogManager 绛夛級
鈹?      鈹?  鈹?  鈹溾攢鈹€ ui/                  # UI 灞?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ chat/            # 瀵硅瘽椤碉紙鏍稿績锛?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ components/      # 閫氱敤缁勪欢
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ conversations/   # 浼氳瘽鍒楄〃
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ home/            # 棣栭〉
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ navigation/      # 瀵艰埅鍥?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ settings/        # 璁剧疆椤?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ agent/           # Agent 妯″紡锛堝伐鍏锋墽琛屻€佽鑹查€夋嫨锛?
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ miniapps/        # 灏忓簲鐢紙闂磋皪娓告垙 / 鎷嗗脊 / 妫嬬洏绛夛級
鈹?      鈹?  鈹?  鈹?  鈹溾攢鈹€ start/           # 鍚姩椤?
鈹?      鈹?  鈹?  鈹?  鈹斺攢鈹€ theme/           # 涓婚
鈹?      鈹?  鈹?  鈹溾攢鈹€ active/              # 鏃犻殰纰嶈灞?/ 涓诲姩娑堟伅 / 閫氱煡妗?
鈹?      鈹?  鈹?  鈹斺攢鈹€ util/                # 宸ュ叿绫伙紙QuiddityConstants 绛夛級
鈹?      鈹?  鈹斺攢鈹€ res/                     # 璧勬簮鏂囦欢
鈹?      鈹?      鈹溾攢鈹€ drawable/            # 鐭㈤噺鍥?
鈹?      鈹?      鈹溾攢鈹€ mipmap-*/            # 鍚姩鍥炬爣
鈹?      鈹?      鈹溾攢鈹€ values/              # 瀛楃涓层€侀鑹层€佷富棰?
鈹?      鈹?      鈹斺攢鈹€ ...
鈹?      鈹斺攢鈹€ test/                        # 鍗曞厓娴嬭瘯
鈹溾攢鈹€ build.gradle.kts                     # 椤跺眰鏋勫缓鑴氭湰
鈹溾攢鈹€ settings.gradle.kts                  # Gradle 璁剧疆
鈹溾攢鈹€ gradle.properties                    # Gradle 閰嶇疆
鈹溾攢鈹€ gradle/
鈹?  鈹溾攢鈹€ libs.versions.toml               # 鐗堟湰鐩綍锛坴ersion catalog锛?
鈹?  鈹斺攢鈹€ wrapper/                         # Gradle wrapper
鈹溾攢鈹€ gradlew / gradlew.bat                # Gradle wrapper 鑴氭湰
鈹溾攢鈹€ keystore.properties.example          # 绛惧悕閰嶇疆妯℃澘锛堜笉鍚瘑鐮侊級
鈹斺攢鈹€ README.md                            # 鏈枃浠?
```

## 蹇€熷紑濮?

### 鐜瑕佹眰

- **JDK 17**锛坄gradle.properties` 宸查璁?`org.gradle.java.home=D:\jdk-17`锛?
- **Android SDK 34**锛坄local.properties` 鎸囧悜 `D:\android-sdk`锛?
- **Gradle 8.9**锛堥€氳繃 wrapper 鑷姩涓嬭浇锛?
- **Kotlin 2.0.21**
- 绯荤粺宸插湪 `gradle.properties` 涓璁?`org.gradle.java.home`

### 鍏嬮殕涓庢瀯寤?

```bash
git clone https://github.com/jiuan-9/quiddity-android.git
cd quiddity-android

# 1. 鍑嗗绛惧悕锛堝彲閫夛細debug 鏋勫缓涓嶉渶瑕侊級
cp keystore.properties.example keystore.properties
# 缂栬緫 keystore.properties 濉叆鐪熷疄绛惧悕淇℃伅

# 2. 浣跨敤椤圭洰鑷甫鐨?Gradle wrapper 鏋勫缓
# Windows
.\gradlew.bat assembleRelease
# macOS / Linux
./gradlew assembleRelease

# 鏋勫缓浜х墿锛歛pp/build/outputs/apk/release/app-release.apk
```

### 寮€鍙戜笌璋冭瘯

```bash
# 缂栬瘧 debug 鐗堟湰
./gradlew assembleDebug

# 缂栬瘧骞跺畨瑁呭埌褰撳墠杩炴帴鐨勮澶?
./gradlew installDebug

# 鍗曞厓娴嬭瘯
./gradlew test

# Lint 妫€鏌?
./gradlew lint
```

### 鍦?Android Studio 涓墦寮€

1. 鎵撳紑 Android Studio锛圚edgehog 鎴栨洿鏂帮級
2. `File` 鈫?`Open` 鈫?閫夋嫨 `Quiddity-android` 鐩綍
3. 绛夊緟 Gradle Sync 瀹屾垚
4. 閫夋嫨 `app` Run Configuration锛岀偣鍑?鈻?杩愯

## 閰嶇疆鏂囦欢

### `keystore.properties`锛堜笉鎻愪氦锛?

绛惧悕閰嶇疆锛岀粨鏋勫涓嬶細

```properties
storeFile=D:/Quiddity-Keys/android/release.keystore
storePassword=xxx
keyAlias=quiddity
keyPassword=xxx
```

鍙弬鑰?[`keystore.properties.example`](./keystore.properties.example)銆?

### `gradle.properties`

- `org.gradle.jvmargs`锛欽VM 鍫嗗ぇ灏忥紙榛樿 1024MB锛?
- `org.gradle.java.home`锛欽DK 17 瀹夎璺緞
- `android.useAndroidX=true`
- `android.nonTransitiveRClass=true`

### `gradle/libs.versions.toml`

缁熶竴绠＄悊鎵€鏈変緷璧栫増鏈紙Version Catalog锛夛紝鏂板渚濊禆璇蜂慨鏀规鏂囦欢銆?

## 鏍稿績妯″潡璇存槑

### 1. AI 璋冪敤涓庢祦寮忓搷搴?

- **鍏ュ彛**锛歔`ChatRepository.streamAssistantReply`](app/src/main/kotlin/com/quiddity/app/data/repo/ChatRepository.kt)
- **缃戠粶**锛歔`data/remote/`](app/src/main/kotlin/com/quiddity/app/data/remote/)锛圤kHttp + okhttp-sse锛?
- **鍗忚**锛氬吋瀹?OpenAI Chat Completions API 瑙勮寖
- **娴佸紡**锛氶€氳繃 SSE锛圫erver-Sent Events锛夊疄鏃舵帴鏀跺閲忓唴瀹?
- **澶氭ā鍨嬫敮鎸?*锛氶€氳繃 `ApiCatalogManager` 缁熶竴绠＄悊 42 涓唴缃ā鍨嬩笌鑷畾涔夋潯鐩?

### 2. 浼氳瘽鍘嬬缉锛堣蹇嗗簱锛?

- **杩涢樁绾э紙ADVANCED锛?*锛氶粯璁?20 杞Е鍙戜竴娆?
- **瀹屾暣绾э紙FULL锛?*锛氶粯璁?40 杞Е鍙戜竴娆?
- **鍩虹绾э紙BASIC锛?*锛氶粯璁?6 杞Е鍙戜竴娆?
- **瑙﹀彂鏉′欢**锛氱敱 [`CompressionStateMachine`](app/src/main/kotlin/com/quiddity/app/domain/CompressionStateMachine.kt) 鍒ゅ畾锛堝惎鐢ㄨ蹇嗗簱涓旇窛涓婃鍘嬬缉杈惧埌闃堝€硷級锛屾祦缁撴潫鍚庡湪 `ChatViewModel` 娴佸紡绠＄嚎涓Е鍙?

### 3. 涓婁笅鏂囪鍓?

[`ChatContextTrimmer`](app/src/main/kotlin/com/quiddity/app/domain/ChatContextTrimmer.kt) 瀹炵幇浜?*鎸?杞?锛堜互 USER 娑堟伅涓洪敋鐐癸級瑁佸壀**鐨勭畻娉曪紙`takeLastRounds` / `takeFromRound`锛夛紝姝ｇ‘澶勭悊"缁х画璇?涓?寤惰繜鍙戦€?瀵艰嚧鐨勫崟杞娑堟伅鎯呭喌锛涘伐鍏疯疆鍥炰紶鎴柇鐢?[`ChatToolRoundTrimmer`](app/src/main/kotlin/com/quiddity/app/data/repo/ChatToolRoundTrimmer.kt) 鎵挎媴銆?

### 4. 妯″瀷鍒嗛厤鏂规

[`ApiCatalogManager`](app/src/main/kotlin/com/quiddity/app/domain/ApiCatalogManager.kt) 瀹氫箟浜嗕笁妗ｆā鍨嬪垎绾э細

| 妗ｄ綅 | 榛樿涓婁笅鏂?| 鍘嬬缉棰戠巼 | 鍏稿瀷妯″瀷 |
|---|---|---|---|
| 鍩虹绾э紙BASIC锛?| 6 杞?| 姣?6 杞?| kimi-k2.7-code-highspeed銆乻park-x銆乬lm-4-flash 绛?|
| 杩涢樁绾э紙ADVANCED锛?| 20 杞?| 姣?20 杞?| glm-5.1銆乲imi-k2.6銆乻park-x2銆丮iniMax-M2.5 绛?|
| 瀹屾暣绾э紙FULL锛?| 40 杞?| 姣?40 杞?| deepseek-v4-pro銆乲imi-k3銆乬lm-5.3銆乭y3銆乪rnie-5.1 绛?|

鍥剧墖璇嗗浘涓嶅崰鐢ㄦā鍨嬫。浣嶏細鍙戦€佸浘鐗囨椂褰撳墠瀵硅瘽妯″瀷鑷甫瑙嗚鍒欑洿鎺ヨ瘑鍥撅紝鍚﹀垯璧扮嫭绔嬨€岃瑙?OCR銆嶅悕鍐屽厹搴曪紙qwen-vl / GLM-4V / 璞嗗寘瑙嗚 / DeepSeek-OCR 绛夛級銆?

### 5. 灏忓簲鐢ㄦ鏋?

涓婚〉涓嬫媺杩涘叆"灏忓簲鐢ㄤ腑蹇?锛堟敹钘?鍏ㄩ儴锛夛紝灏忓簲鐢ㄩ€氳繃妯″潡鍖?`MiniApp` 鎺ュ彛鎺ュ叆锛屼腑蹇冮〉銆佽矾鐢便€佹敹钘忎笌閭€璇锋皵娉¤嚜鍔ㄧ敓鏁堛€?

**鏂板涓€涓皬搴旂敤锛堜竴琛屽懡浠わ級锛?*

```
.\new-miniapp.ps1 -Id dice -Name "楠板瓙" -Description "闅忔満鎺烽瀛愬皬娓告垙"
```

鑴氭墜鏋朵細鐢熸垚鍙紪璇戝崰浣嶅疄鐜板苟鑷姩娉ㄥ唽鍒?`MiniAppRegistry`锛岄殢鍚庢妸鍗犱綅椤垫浛鎹㈡垚鐪熷疄涓氬姟鍗冲彲锛涘鏉傚皬搴旂敤鍙傝€冩鐩樺皬搴旂敤鍒嗗眰锛坄domain/` 绾€昏緫 + `ViewModel` 鐘舵€佹満 + `MiniAppInviteManager` 澶嶇敤閭€璇锋祦绋嬶級銆傝瑙?[`new-miniapp.ps1`](new-miniapp.ps1)銆?

### 6. Agent 妯″紡

Agent 浼氳瘽鏀寔宸ュ叿璋冪敤闂幆锛歚AgentToolRegistry` 娉ㄥ唽 56 涓伐鍏凤紙搴旂敤/鏂囦欢/绯荤粺/浜や簰鍥涚被锛夛紝`AgentExecutors` 鎻愪緵璇诲彇绫绘墽琛屽櫒锛圫hizuku 鍐欏叆绫婚渶鎺堟潈锛夛紝`AgentWorkflowController` 璐熻矗澶氳疆浠诲姟缂栨帓涓庨噸璇曪紱`AgentChatScreen` 鎻愪緵鐙珛鑱婂ぉ鐣岄潰锛堝伐鍏风棔杩圭┛鎻掓鏂囥€佽鑹插簱閫夋嫨銆佸嵄闄╂搷浣滅‘璁や笌鎾ゅ洖锛夈€?

### 7. 鏃犻殰纰嶈灞忎笌涓诲姩娑堟伅

`active/` 妯″潡鎵胯浇锛歚ScreenReaderService`锛堟棤闅滅璇诲睆锛屼緵 Agent 鎰熺煡灞忓箷涓庢ā鎷熸搷浣滐級銆乣ActiveMessageService` / `TimeLibraryRepository`锛堟寜鏃堕棿搴撳畾鏃朵富鍔ㄥ彂娑堟伅锛夈€乣OperationNotifyController` 涓?`NotificationBridge`锛堣鍔ㄩ€氱煡寮圭獥涓庨€氱煡鍥炲锛夈€?

## 鏁版嵁瀛樺偍

鎵€鏈夋暟鎹潎瀛樺偍鍦?Android 搴旂敤鐨勭鏈夌洰褰曚腑锛?*涓嶄笂浼犱换浣曠敤鎴锋暟鎹?*銆?

```
/data/data/com.quiddity.app/
鈹溾攢鈹€ files/quiddity-data/
鈹?  鈹溾攢鈹€ conversations.json      # 浼氳瘽鍒楄〃
鈹?  鈹溾攢鈹€ messages_<浼氳瘽id>.json  # 鍚勪細璇濇秷鎭?
鈹?  鈹斺攢鈹€ settings.json           # 搴旂敤璁剧疆锛堟槑鏂囷級
鈹溾攢鈹€ datastore/
鈹?  鈹斺攢鈹€ settings.preferences_pb # DataStore Preferences
鈹斺攢鈹€ ...
```

## 瀹夊叏璇存槑

- API Key 浣跨敤 **AES256-GCM** 鍔犲瘑淇濆瓨
- 鍔犲瘑瀵嗛挜鐢?Android Keystore 绯荤粺绠＄悊
- 鎵€鏈夌綉缁滈€氫俊浣跨敤 HTTPS
- 涓嶉泦鎴愪换浣曟暟鎹垎鏋?SDK
- 涓嶆敹闆嗕换浣曠敤鎴疯涓烘暟鎹?

璇﹁闅愮澹版槑锛堝畼缃?`/privacy` 椤甸潰锛夈€?

## 璺嚎鍥?

- [x] 1.0.0锛氭牳蹇冨璇濄€佸妯″瀷銆佸帇缂┿€佽蹇?
- [x] 1.0.1锛氱増鏈彿閫掑锛坴ersionCode 1 鈫?2锛夛紝閲嶆柊绛惧悕鍙戝竷
- [x] 1.0.2锛氱増鏈彿閫掑锛坴ersionCode 2 鈫?3锛夛紝閲嶆柊绛惧悕鍙戝竷
- [x] 1.0.3锛氱増鏈彿閫掑锛坴ersionCode 3 鈫?4锛夛紝閲嶅啓 UpdateChecker锛圖ownloadManager + FileProvider锛夛紝淇搴旂敤鍐呮洿鏂颁笅杞?
- [x] 1.1.0锛氳仈缃戞悳绱紙RAG锛夛紝鐗堟湰鍙烽€掑锛坴ersionCode 4 鈫?5锛夛紝AI 鍙疄鏃惰仈缃戞绱?+ 鏉ユ簮鍒楄〃 + 鎵嬪姩/鑷姩妯″紡 + 鎼滅储鑼冨洿鎺у埗 + 缂撳瓨鍘婚噸
- [x] 1.1.1锛氫慨澶嶉儴鍒嗘墜鏈烘娴嬫洿鏂板け璐?/ 鍚姩涓嬭浇澶辫触锛岀増鏈彿閫掑锛坴ersionCode 5 鈫?6锛夛紝UpdateChecker 澶氭簮 fallback + installApk 鏀圭敤 FileProvider + downloadApk 璺緞淇
- [x] 1.2.0锛氫富鍔ㄦ秷鎭紙鏃堕棿搴擄級锛孉I 姣忓ぉ瀹氭椂涓诲姩鍙戞秷鎭紝鐗堟湰鍙烽€掑锛坴ersionCode 7 鈫?8锛?
- [x] 1.3.0锛氭暟鎹鍑?schema v2 鎺ュ彛棰勭暀锛堣鑹插簱 / 缇よ亰鎺ュ彛 / 璁板繂璋冪敤寮忓瓧娈典笌鎻愮ず璇嶏級锛岀増鏈彿閫掑锛坴ersionCode 8 鈫?9锛?
- [x] 1.4.0锛氬皬搴旂敤妗嗘灦涓婄嚎锛堥瀛?/ 闂磋皪娓告垙 / 鎷嗗脊 / 妫嬬洏锛夛紝鐗堟湰鍙烽€掑锛坴ersionCode 11 鈫?12锛?
- [x] 1.5.0锛氫細璇濆閫?/ 瀵煎嚭闀垮浘 / 鍙戦€佸欢杩?/ 缇よ亰鐐瑰悕鍥炲涓庤蹇嗗帇缂╋紝鐗堟湰鍙烽€掑锛坴ersionCode 12 鈫?13锛?
- [x] 1.5.x锛氳仈缃戞悳绱㈢粏鍖栦笌绋冲畾鎬т慨澶嶏紙versionCode 13 鈫?14锛?
- [x] 1.6.0锛欰gent 妯″紡锛堝伐鍏疯皟鐢?/ 鏃犻殰纰嶈灞?/ 涓诲姩娑堟伅 / 56 涓伐鍏凤級锛岀増鏈彿閫掑锛坴ersionCode 14 鈫?16锛屽惈淇鐗堬級
- [x] 1.6.1锛氫慨澶?Shizuku 宸叉巿鏉冧粛鏃犳硶寮€鍚伐鍏峰垎绫汇€佹柊澧?DeepSeek 澶氭ā鎬佹ā鍨?deepseek-v4-flash-vision-exp锛岀増鏈彿閫掑锛坴ersionCode 16 鈫?17锛?
- [x] 1.6.2锛氫慨澶?DeepSeek 鎬濊€冩ā寮忎笅缇よ亰/Agent/鑱旂綉鎼滅储鎼哄甫宸ュ叿鏃舵姤 400銆屾€濊€冨唴瀹归渶瑕佸洖浼犮€嶏紙鎬濊€冨師鏂囪惤搴?+ 鍘嗗彶鍘熸牱鍥炰紶锛夛紝鐗堟湰鍙烽€掑锛坴ersionCode 17 鈫?18锛?
- [ ] 1.7.0锛氭彃浠剁郴缁?
- [ ] 2.0.0锛氱渚фā鍨嬶紙llama.cpp / MediaPipe锛?

## 鐩稿叧浠撳簱

| 浠撳簱 | 鎻忚堪 |
|---|---|
| [Quiddity-website](https://github.com/jiuan-9/Quiddity-website) | 瀹樻柟缃戠珯锛圧eact + Vite锛?|
| [Quiddity-Chat-Windows](https://github.com/jiuan-9/Quiddity-Chat-Windows) | Quiddity-Chat 妗岄潰绔畨瑁呭寘锛圗lectron锛屾簮鐮佹殏鏈叕寮€锛?|
| [Quiddity-Agent](https://github.com/jiuan-9/Quiddity-Agent) | Quiddity-Agent 妗岄潰绔紙Electron锛屾簮鐮佹殏鏈叕寮€锛?|

## 璐＄尞

娆㈣繋鎻愪氦 Issue 涓?Pull Request銆傛彁浜や唬鐮佸墠璇烽槄璇?[CONTRIBUTING.md](./CONTRIBUTING.md)銆?

## 璁稿彲璇?

鏈」鐩熀浜?**MIT 鍗忚**寮€婧愶紝璇﹁ [LICENSE](./LICENSE)銆?

## 鑷磋阿

- Jetpack Compose 鍥㈤槦
- OkHttp 鍥㈤槦
- 鎵€鏈変负寮€婧?AI 鐢熸€佽础鐚殑寮€鍙戣€?

---

鐭ユ墍涓嶅敖锛屽線澶嶄笉姝?鈥?Know no bounds, repeat no end.
