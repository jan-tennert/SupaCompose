package io.github.jan.supacompose.postgrest.query

import io.github.jan.supacompose.supabaseJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

data class PostgrestResult(val body: JsonElement, val statusCode: Int) {

    /**
     * Decodes [body] as [T] using [json]
     */
    inline fun <reified T> decodeAs(json: Json = supabaseJson): T = json.decodeFromJsonElement(body)

    /**
     * Decodes [body] as [T] using [json]. If there's an error it will return null
     */
    inline fun <reified T> decodeAsOrNull(json: Json = supabaseJson): T? = try {
        decodeAs<T>(json)
    } catch (e: Exception) {
        null
    }

}
