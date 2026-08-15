package com.quiddity.app.domain.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun globalActionName(action: String?): String = when (action) {
            "back" -> "返回"
            "home" -> "首页"
            "recents" -> "最近任务"
            "notifications" -> "通知栏"
            "quick_settings" -> "快捷设置"
            "lock" -> "锁屏"
            else -> action.orEmpty()
        }

internal fun paramsObject(
            properties: Map<String, JsonObject>,
            required: List<String>
        ): JsonObject = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(properties),
                "required" to JsonArray(required.map { JsonPrimitive(it) })
            )
        )

internal fun stringParam(description: String): JsonObject = JsonObject(
            mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive(description)
            )
        )

internal fun intParam(description: String): JsonObject = JsonObject(
            mapOf(
                "type" to JsonPrimitive("integer"),
                "description" to JsonPrimitive(description)
            )
        )

internal fun boolParam(description: String): JsonObject = JsonObject(
            mapOf(
                "type" to JsonPrimitive("boolean"),
                "description" to JsonPrimitive(description)
            )
        )

internal fun arrayParam(description: String): JsonObject = JsonObject(
            mapOf(
                "type" to JsonPrimitive("array"),
                "items" to JsonObject(
                    mapOf("type" to JsonPrimitive("string"))
                ),
                "description" to JsonPrimitive(description)
            )
        )

internal fun argString(args: JsonObject, key: String): String? =
            (args[key] as? JsonPrimitive)?.content

internal fun argInt(args: JsonObject, key: String): Int? =
            (args[key] as? JsonPrimitive)?.content?.toIntOrNull()

internal fun argArray(args: JsonObject, key: String): List<String>? {
            val element = args[key] as? JsonArray ?: return null
            return element.mapNotNull { (it as? JsonPrimitive)?.content }
        }
