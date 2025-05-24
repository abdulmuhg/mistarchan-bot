package com.mrc.mistarbot.service

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class OpenAIService(private val apiKey: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Natural rarity distribution (matches typical TCG distributions)
    companion object {
        private val RARITY_DISTRIBUTION = mapOf(
            CardRarity.COMMON to 60,      // 60%
            CardRarity.UNCOMMON to 25,    // 25%
            CardRarity.RARE to 10,        // 10%
            CardRarity.LEGENDARY to 5     // 5%
        )
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
                                                "name": "A creative name for the card (max 25 chars)",
                                                "attack": 7,
                                                "defense": 3,
                                                "rarity": "COMMON",
                                                "description": "Short mystical flavor text (max 60 characters for 2-line display)"
                                            }
                                            
                                            RARITY GUIDELINES (Very Important):
                                            Choose rarity based on SOCIAL VALUE, humor, meme potential, and uniqueness - not just artistic quality!
                                            
                                            • COMMON (60% of cards): Simple, everyday objects, basic solo photos, regular items, plain backgrounds
                                              Examples: single coffee cup, basic selfie, plain landscape, simple object photo
                                            
                                            • UNCOMMON (25% of cards): Interesting content with social or humorous elements, group activities, expressive moments
                                              Examples: group selfies, funny facial expressions, interesting poses, social gatherings, cool outfits
                                            
                                            • RARE (10% of cards): HIGH MEME POTENTIAL, very funny moments, epic group photos, dramatic reactions, special occasions
                                              Examples: hilarious facial expressions, perfect reaction faces, epic group shots, funny situations, dramatic poses, special events, amazing memes
                                            
                                            • LEGENDARY (5% of cards): ULTIMATE MEME MATERIAL, absolutely hilarious, legendary group moments, perfect reaction images, iconic community content
                                              Examples: legendary funny faces, epic fail moments, perfect meme templates, iconic group photos, absolutely hilarious situations, unforgettable reactions
                                            
                                            STAT GUIDELINES (Use Full 1-10 Range!):
                                            Be bold with stats! Use the ENTIRE 1-10 range for variety and excitement:
                                            
                                            • ATTACK (1-10): Base on dynamic/aggressive/energetic elements
                                              - 1-3: Gentle, peaceful, static objects (flowers, sleeping cats, calm scenes)
                                              - 4-6: Moderate energy (people walking, cars, normal activities)  
                                              - 7-8: High energy (sports, action, dramatic poses, powerful animals)
                                              - 9-10: EXTREME power (explosions, intense action, apex predators, warriors)
                                            
                                            • DEFENSE (1-10): Base on protective/sturdy/resilient elements  
                                              - 1-3: Fragile, delicate (glass, bubbles, thin materials, vulnerability)
                                              - 4-6: Normal durability (people, everyday objects, standard materials)
                                              - 7-8: Very sturdy (armor, shields, strong buildings, protective poses)
                                              - 9-10: UNBREAKABLE (fortresses, mountains, ultimate protection)
                                            
                                            STAT VARIETY EXAMPLES:
                                            • Glass sculpture: ATK 2, DEF 1 (beautiful but fragile)
                                            • Fierce tiger: ATK 9, DEF 6 (pure aggression)  
                                            • Castle wall: ATK 3, DEF 10 (defensive powerhouse)
                                            • Lightning bolt: ATK 10, DEF 2 (ultimate attack, no defense)
                                            • Sleeping baby: ATK 1, DEF 8 (peaceful but protected)
                                            • Racing car: ATK 8, DEF 4 (speed over safety)
                                            
                                            RARITY & STATS RELATIONSHIP:
                                            • COMMON: Usually 2-7 range, can have extremes if image supports it
                                            • UNCOMMON: Usually 3-8 range, more likely to have good stats
                                            • RARE: Usually 4-9 range, strong potential for high stats  
                                            • LEGENDARY: Can use FULL 1-10 range, including perfect 10s!
                                            
                                            CREATE VARIETY! Avoid always using 4-6. Be bold with 1-3 and 8-10!
                                            
                                            Be honest about rarity - don't inflate it! Most images should be COMMON or UNCOMMON.
                                            Only use LEGENDARY for truly spectacular, once-in-a-lifetime images.
                                            
                                            Return ONLY the JSON, no other text.
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

            val responseText = response.bodyAsText()
            logger.info { "OpenAI response: $responseText" }

            if (!response.status.isSuccess()) {
                val errorResponse = Json.decodeFromString<JsonObject>(responseText)
                val errorMessage = errorResponse["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "Unknown error occurred"

                when (response.status.value) {
                    429 -> throw OpenAIException("OpenAI API quota exceeded. Please check your billing details.")
                    401 -> throw OpenAIException("Invalid OpenAI API key.")
                    else -> throw OpenAIException("OpenAI API error: $errorMessage")
                }
            }

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

            // Get AI's suggested rarity
            val aiRarity = try {
                CardRarity.valueOf(cardData["rarity"]?.jsonPrimitive?.content ?: "COMMON")
            } catch (e: Exception) {
                CardRarity.COMMON
            }

            // Apply natural distribution with some AI influence
            val finalRarity = applyNaturalRarityDistribution(aiRarity)

            logger.info { "AI suggested: $aiRarity, Final rarity: $finalRarity" }

            return try {
                Card(
                    name = cardData["name"]?.jsonPrimitive?.content ?: "Unknown Card",
                    imageUrl = imageUrl,
                    attack = cardData["attack"]?.jsonPrimitive?.int ?: 5,
                    defense = cardData["defense"]?.jsonPrimitive?.int ?: 5,
                    rarity = finalRarity,
                    description = cardData["description"]?.jsonPrimitive?.content
                        ?: "A mysterious entity of unknown origin.",
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

    /**
     * Applies natural rarity distribution while considering AI's suggestion
     * This ensures proper rarity percentages while still allowing AI influence
     */
    private fun applyNaturalRarityDistribution(aiSuggestedRarity: CardRarity): CardRarity {
        val random = Random.nextInt(100) + 1 // 1-100

        // Weighted distribution based on natural TCG rarity
        val finalRarity = when {
            random <= 60 -> CardRarity.COMMON      // 60%
            random <= 85 -> CardRarity.UNCOMMON    // 25% (61-85)
            random <= 95 -> CardRarity.RARE        // 10% (86-95)
            else -> CardRarity.LEGENDARY           // 5%  (96-100)
        }

        // AI influence: if AI suggests higher rarity and we're close, sometimes upgrade
        val upgradedRarity = when {
            // If AI suggests LEGENDARY and we rolled RARE, 30% chance to upgrade
            aiSuggestedRarity == CardRarity.LEGENDARY && finalRarity == CardRarity.RARE
                    && Random.nextFloat() < 0.3 -> CardRarity.LEGENDARY

            // If AI suggests RARE and we rolled UNCOMMON, 20% chance to upgrade
            aiSuggestedRarity == CardRarity.RARE && finalRarity == CardRarity.UNCOMMON
                    && Random.nextFloat() < 0.2 -> CardRarity.RARE

            // If AI suggests UNCOMMON and we rolled COMMON, 15% chance to upgrade
            aiSuggestedRarity == CardRarity.UNCOMMON && finalRarity == CardRarity.COMMON
                    && Random.nextFloat() < 0.15 -> CardRarity.UNCOMMON

            else -> finalRarity
        }

        return upgradedRarity
    }
}

class OpenAIException(message: String) : Exception(message)