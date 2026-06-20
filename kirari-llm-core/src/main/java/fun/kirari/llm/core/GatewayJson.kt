package `fun`.kirari.llm.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun openAiMessage(role: String, text: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", text)
}

internal fun extractOpenAiContent(content: JsonElement?): String {
    return when (content) {
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        is JsonArray -> content.joinToString(separator = "") { item ->
            item.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }

        is JsonObject -> content["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        else -> ""
    }
}
