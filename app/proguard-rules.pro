# Quiddity ProGuard / R8 规则

# ============================================================================
# 1. Kotlinx Serialization
# ============================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.quiddity.app.**$$serializer { *; }
-keepclassmembers class com.quiddity.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.quiddity.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留所有 @Serializable 标注的类
-keep @kotlinx.serialization.Serializable class com.quiddity.app.** { *; }

# ============================================================================
# 2. OkHttp / OkHttp-SSE
# ============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp 平台类（JDK 8+ Android 平台）
-keep class okhttp3.internal.platform.Platform { *; }
-keep class okhttp3.internal.platform.android.AndroidLog { *; }
-keep class okhttp3.internal.platform.android.AndroidSocketAdapter { *; }
-keep class okhttp3.internal.platform.Jdk8Platform { *; }
-keep class okhttp3.internal.platform.Jdk9Platform { *; }
-keep class okhttp3.internal.platform.ConscryptPlatform { *; }

# EventSource（SSE）
-keep class okhttp3.sse.** { *; }
-keep interface okhttp3.sse.** { *; }

# ============================================================================
# 3. Coroutines
# ============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================================
# 4. DataStore
# ============================================================================
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }

# ============================================================================
# 5. Coil
# ============================================================================
-keep class coil.Coil { *; }
-dontwarn coil.**

# ============================================================================
# 6. Compose（通常 R8 自动处理，但保留关键反射入口）
# ============================================================================
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ============================================================================
# 7. 应用数据模型（确保反射访问安全）
# ============================================================================
-keep class com.quiddity.app.data.model.** { *; }
-keep class com.quiddity.app.data.remote.** { *; }

# ============================================================================
# 8. 反射工具类
# ============================================================================
# 深层重构 v3 安全加固：
# 不再 keep CryptoUtils 整个类——这会阻止 R8 内联 SecretKeyDerivation 的派生过程，
# 让密钥拼接逻辑在反编译中可读。只 keep 公开的 DecryptFailure 密封类（异常反射可能用到）。
-keep class com.quiddity.app.util.CryptoUtils$DecryptFailure { *; }
-keep class com.quiddity.app.util.CryptoUtils$DecryptFailure$* { *; }
# 允许 R8 重命名/内联密钥派生器（提升反向门槛）
-dontwarn com.quiddity.app.util.SecretKeyDerivation
# 允许 R8 移除未使用的本地变量
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'q'

# ============================================================================
# 9. 通用规则
# ============================================================================
# 保留泛型签名（kotlinx.serialization 需要）
-keepattributes Signature
-keepattributes EnclosingMethod
# 保留源文件名与行号（崩溃栈）
-keepattributes SourceFile,LineNumberTable
