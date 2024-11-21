package com.example

import com.example.ollama.OllamaClient
import com.example.ollama.OllamaResult
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = OllamaClient()

    try {
        println("Sending request to Ollama...")
        val result = client.generateResponse(
            model = "llama3.2",
            prompt = "What places do you recommend me visiting in Paraguay?" // My home country :)
        )

        when (result) {
            is OllamaResult.Success -> {
                println("\nResponse saved to file: ${result.filePath}")
                println("\nResponse from model:")
                println(result.completeResponse)
            }
            is OllamaResult.Error -> {
                println("\nError occurred: ${result.message}")
            }
        }
    } catch (e: Exception) {
        println("\nUnexpected error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}