package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VertexServiceAccountCredentials(
    val projectId: String,
    val email: String,
    val privateKey: String,
) {
    companion object {
        fun fromJson(content: String): VertexServiceAccountCredentials {
            val json = Json.parseToJsonElement(content).jsonObject
            fun required(name: String): String = json[name]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: error("服务账号 JSON 缺少 $name")
            require(required("type") == "service_account") { "请选择 Google Cloud 服务账号 JSON，不是 OAuth 客户端 JSON" }
            val key = required("private_key")
            require("-----BEGIN PRIVATE KEY-----" in key && "-----END PRIVATE KEY-----" in key) {
                "服务账号 JSON 中的私钥格式不正确"
            }
            return VertexServiceAccountCredentials(required("project_id"), required("client_email"), key)
        }
    }
}
