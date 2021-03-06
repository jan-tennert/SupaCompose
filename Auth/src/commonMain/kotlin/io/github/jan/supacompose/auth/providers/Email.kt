package io.github.jan.supacompose.auth.providers

import io.github.jan.supacompose.supabaseJson
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

object Email : DefaultAuthProvider<Email.Config, Email.Result> {

    @Serializable
    data class Config(var email: String = "", var password: String = "")
    @Serializable
    data class Result(
        val id: String,
        val email: String,
        @SerialName("confirmation_sent_at") val confirmationSentAt: Instant,
        @SerialName("created_at") val createdAt: Instant,
        @SerialName("updated_at") val updatedAt: Instant,
    )

    override fun decodeResult(json: JsonObject): Result = supabaseJson.decodeFromJsonElement(json)

    override fun encodeCredentials(credentials: Config.() -> Unit): String = supabaseJson.encodeToString(Config().apply(credentials))

}