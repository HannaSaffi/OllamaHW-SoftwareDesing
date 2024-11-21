package com.example.ollama

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

sealed class OllamaResult {
    data class Success(val completeResponse: String, val filePath: String) : OllamaResult()
    data class Error(val message: String) : OllamaResult()
}

class OllamaClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60.seconds.inWholeMilliseconds
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds
            socketTimeoutMillis = 60.seconds.inWholeMilliseconds
        }
        engine { requestTimeout = 60.seconds.inWholeMilliseconds }
    }

    private fun createOutputFile(prompt: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return File("ollama_response_${timestamp}.txt").apply {
            writeText("Prompt: $prompt\n\nResponse:\n")
        }
    }

    suspend fun generateResponse(model: String, prompt: String): OllamaResult {
        val outputFile = createOutputFile(prompt)
        val request = OllamaRequest(model, prompt)

        return try {
            val responseText = client.post("http://localhost:11434/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.bodyAsText()

            val completeResponse = processResponse(responseText, outputFile)
            if (completeResponse.isBlank()) OllamaResult.Error("No valid response content received")
            else OllamaResult.Success(completeResponse, outputFile.absolutePath)

        } catch (e: Exception) {
            outputFile.appendText("\nError occurred: ${e.message}\n")
            OllamaResult.Error("Request failed: ${e.message}")
        }
    }

    private fun processResponse(responseText: String, outputFile: File): String {
        val stringBuilder = StringBuilder()
        responseText.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                try {
                    val jsonObject = Json.parseToJsonElement(line) as? JsonObject
                    jsonObject?.get("response")?.jsonPrimitive?.content?.let { responseContent ->
                        stringBuilder.append(responseContent)
                        outputFile.appendText(responseContent)
                    }
                } catch (e: Exception) {
                    println("Warning: Skipping malformed JSON line: $line")
                }
            }
        }
        outputFile.appendText("\n\nGenerated at: ${LocalDateTime.now()}\n")
        return stringBuilder.toString()
    }

    fun close() {
        client.close()
    }
}
