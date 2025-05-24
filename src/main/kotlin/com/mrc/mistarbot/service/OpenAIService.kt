package com.mrc.mistarbot.service

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class OpenAIService(private val apiKey: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun analyzeImage(imageUrl: String, userId: String): Card {
        logger.info { "Starting image analysis for user: $userId" }
        logger.info { "Image URL: $imageUrl" }
        logger.info { "Sending request to OpenAI..." }

        try {
            val response = client.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append("Authorization", "Bearer $apiKey")
                    append("Content-Type", "application/json")
                }
                setBody(buildJsonObject {
                    put("model", "gpt-4o-mini")
                    put(
                        "messages", JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "role" to JsonPrimitive("user"),
                                        "content" to JsonArray(
                                            listOf(
                                                JsonObject(
                                                    mapOf(
                                                        "type" to JsonPrimitive("text"),
                                                        "text" to JsonPrimitive(
                                                            """
                                            Analyze this image and create a trading card from it. Return ONLY a valid JSON object with these exact fields:
                                            {
                                                "name": "A creative name for the card (max 30 chars)",
                                                "attack": 5,
                                                "defense": 4,
                                                "rarity": "RARE"
                                            }
                                            
                                            Rules:
                                            - attack: number from 1-10
                                            - defense: number from 1-10  
                                            - rarity: exactly one of: COMMON, UNCOMMON, RARE, LEGENDARY
                                            - Base values on the visual content and perceived power of the image
                                            - Return ONLY the JSON, no other text
                                        """.trimIndent()
                                                        )
                                                    )
                                                ),
                                                JsonObject(
                                                    mapOf(
                                                        "type" to JsonPrimitive("image_url"),
                                                        "image_url" to JsonObject(
                                                            mapOf(
                                                                "url" to JsonPrimitive(imageUrl)
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    put("max_tokens", 300)
                })
            }

            logger.info { "Received response from OpenAI, status: ${response.status}" }

            // Log the raw response for debugging
            val responseText = response.bodyAsText()
            logger.info { "OpenAI response: $responseText" }

            if (!response.status.isSuccess()) {
                // Parse error response
                val errorResponse = Json.decodeFromString<JsonObject>(responseText)
                val errorMessage = errorResponse["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "Unknown error occurred"

                when (response.status.value) {
                    429 -> throw OpenAIException("OpenAI API quota exceeded. Please check your billing details.")
                    401 -> throw OpenAIException("Invalid OpenAI API key.")
                    else -> throw OpenAIException("OpenAI API error: $errorMessage")
                }
            }

            // Parse successful response
            val jsonResponse = Json.decodeFromString<JsonObject>(responseText)
            val choices = jsonResponse["choices"]?.jsonArray
                ?: throw OpenAIException("Invalid response format from OpenAI")

            if (choices.isEmpty()) {
                throw OpenAIException("No response generated from OpenAI")
            }

            val content = choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: throw OpenAIException("No content in OpenAI response")

            logger.info { "OpenAI content: $content" }

            val cardData = try {
                // Clean the content to extract JSON
                val cleanContent = content.trim().let {
                    if (it.startsWith("```json")) {
                        it.removePrefix("```json").removeSuffix("```").trim()
                    } else if (it.startsWith("```")) {
                        it.removePrefix("```").removeSuffix("```").trim()
                    } else {
                        it
                    }
                }

                Json.parseToJsonElement(cleanContent).jsonObject
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse card data as JSON: $content" }
                throw RuntimeException("Invalid JSON from OpenAI: ${e.message}", e)
            }

            logger.info { "Parsed card data: $cardData" }

            return try {
                Card(
                    name = cardData["name"]?.jsonPrimitive?.content ?: "Unknown Card",
                    imageUrl = imageUrl,
                    attack = cardData["attack"]?.jsonPrimitive?.int ?: 5,
                    defense = cardData["defense"]?.jsonPrimitive?.int ?: 5,
                    rarity = CardRarity.valueOf(cardData["rarity"]?.jsonPrimitive?.content ?: "COMMON"),
                    ownerId = userId
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to create Card object from data: $cardData" }
                throw RuntimeException("Failed to create card: ${e.message}", e)
            }
        } catch (e: Exception) {
            when (e) {
                is OpenAIException -> throw e
                is HttpRequestTimeoutException -> throw OpenAIException("OpenAI request timed out")
                else -> throw OpenAIException("Failed to communicate with OpenAI: ${e.message}")
            }
        }
    }
}

class OpenAIException(message: String) : Exception(message)