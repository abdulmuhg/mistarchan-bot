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

    // Increased EPIC chances - better balance
    companion object {
        private val RARITY_DISTRIBUTION = mapOf(
            CardRarity.COMMON to 50,      // 50%
            CardRarity.UNCOMMON to 25,    // 25%
            CardRarity.RARE to 15,        // 15%
            CardRarity.EPIC to 8,         // 8% - Increased from 4%
            CardRarity.LEGENDARY to 2     // 2% - Slightly increased
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
                                                "attack": 9,
                                                "defense": 2,
                                                "rarity": "UNCOMMON",
                                                "description": "Mystical flavor text. Legend says he can outrun time itself."
                                            }
                                            
                                            RARITY GUIDELINES (Very Important):
                                            Choose rarity based on SOCIAL VALUE, humor, meme potential, and uniqueness - not just artistic quality!
                                            
                                            • COMMON (50% of cards): Simple, everyday objects, basic solo photos, regular items, plain backgrounds
                                              Examples: single coffee cup, basic selfie, plain landscape, simple object photo
                                            
                                            • UNCOMMON (25% of cards): Interesting content with social or humorous elements, group activities, expressive moments
                                              Examples: group selfies, funny facial expressions, interesting poses, social gatherings, cool outfits
                                            
                                            • RARE (15% of cards): HIGH MEME POTENTIAL, very funny moments, good group photos, dramatic reactions, special occasions
                                              Examples: hilarious facial expressions, good reaction faces, nice group shots, funny situations, dramatic poses, special events
                                            
                                            • EPIC (7% of cards): EXCEPTIONAL CONTENT, amazing meme material, epic group moments, incredible reactions, outstanding community content
                                              Examples: legendary funny faces, epic fail moments, perfect group dynamics, incredible expressions, amazing community memories, viral-worthy content
                                            
                                            • LEGENDARY (3% of cards): ULTIMATE PERFECTION, once-in-a-lifetime moments, absolute peak meme content, iconic masterpieces
                                              Examples: perfect meme templates, historic group photos, absolutely legendary reactions, life-changing moments, ultimate viral content
                                            
                                            STAT GUIDELINES (MUST Use Full 1-10 Range!):
                                            STOP being conservative! Use EXTREME stats for excitement and variety!
                                            DO NOT default to middle values like 4-6. Be BOLD with 1-3 and 8-10!
                                            
                                            • ATTACK (1-10): Base on dynamic/aggressive/energetic elements
                                              - 1-2: Extremely gentle/peaceful (sleeping babies, flowers, meditation, calm water)
                                              - 3-4: Low energy (reading, sitting, passive objects)
                                              - 5-6: Moderate energy (walking, normal activities, regular movement)  
                                              - 7-8: High energy (running, sports, action poses, excited expressions)
                                              - 9-10: MAXIMUM POWER (explosions, roaring animals, intense action, warriors, lightning)
                                            
                                            • DEFENSE (1-10): Base on protective/sturdy/resilient elements  
                                              - 1-2: Extremely fragile (soap bubbles, glass, paper, delicate flowers)
                                              - 3-4: Fragile (thin materials, vulnerable poses, light objects)
                                              - 5-6: Normal durability (people, everyday objects, standard materials)
                                              - 7-8: Very sturdy (thick walls, armor, strong poses, solid buildings)
                                              - 9-10: UNBREAKABLE (mountains, fortresses, tanks, ultimate protection)
                                            
                                            REQUIRED STAT VARIETY EXAMPLES:
                                            • Soap bubble: ATK 1, DEF 1 (delicate perfection)
                                            • Sleeping cat: ATK 1, DEF 6 (peaceful but protected)
                                            • Glass art: ATK 2, DEF 1 (beautiful but fragile)
                                            • Fierce lion: ATK 10, DEF 7 (maximum aggression)  
                                            • Brick wall: ATK 2, DEF 10 (defensive powerhouse)
                                            • Lightning: ATK 10, DEF 1 (pure attack, no defense)
                                            • Racing car: ATK 9, DEF 3 (speed over safety)
                                            • Castle: ATK 3, DEF 10 (built for defense)
                                            
                                            CRITICAL: Create exciting contrasts! High attack + low defense OR low attack + high defense!
                                            Avoid boring middle stats! Most cards should have at least one stat below 4 or above 7!
                                            
                                            RARITY & STATS RELATIONSHIP (STRICTLY ENFORCE):
                                            Each rarity has specific stat ranges - DO NOT exceed these ranges often!
                                            
                                            • COMMON (3-6 range): Most stats should be 3-6, rarely go to 7, avoid 8+
                                              Focus on interesting low-stat combinations (ATK 3 DEF 6, ATK 6 DEF 3)
                                              Can use 1-2 for very fragile things (bubbles, flowers)
                                              
                                            • UNCOMMON (3-7 range): Most stats should be 3-7, occasionally 8 if really justified
                                              Attack 8 should be RARE for UNCOMMON - only for clearly energetic content
                                              Good balance of moderate stats with some interesting contrasts
                                              
                                            • RARE (4-8 range): Comfortable using 4-8, can have 9 if very justified
                                              This is where attack 8-9 becomes more common and appropriate
                                              Strong stats that reflect genuinely impressive content
                                              
                                            • EPIC (5-9 range): Strong stats 5-9, can freely use 8-9, occasionally 10
                                              High stats reflect the exceptional nature of the content
                                              
                                            • LEGENDARY (1-10 range): Can use FULL range including perfect 10s and dramatic 1s
                                              Ultimate freedom but should still make logical sense
                                            
                                            CRITICAL STAT RULES:
                                            • Attack 8+ should be RARE or higher, not UNCOMMON
                                            • Defense 8+ should be RARE or higher, not UNCOMMON  
                                            • UNCOMMON with attack 7 is fine, attack 8 should be exceptional
                                            • Even if image shows energy, consider rarity when assigning high stats
                                            
                                            CREATE VARIETY! Avoid always using 4-6. Be bold with 1-3 and 8-10!
                                            
                                            DESCRIPTION GUIDELINES (Very Important!):
                                            Create a 2-sentence description with exactly this format:
                                            
                                            SENTENCE 1: Short mystical/atmospheric flavor text (20-30 characters)
                                            SENTENCE 2: Funny hyperbolic punchline about the image content (30-40 characters)
                                            
                                            PUNCHLINE EXAMPLES:
                                            • Someone smoking: "This smoke can cover your family assurance."
                                            • Someone on bicycle: "Legend says he can enter the toll."
                                            • Someone cooking: "His recipes can feed entire villages."
                                            • Someone with phone: "Can connect to networks in other dimensions."
                                            • Someone sleeping: "Dreams so deep they create new realities."
                                            • Someone driving: "GPS gets confused by his navigation skills."
                                            • Someone eating: "Appetite so vast it threatens local ecosystems."
                                            • Someone dancing: "Moves so smooth they defy physics laws."
                                            • Cat sitting: "Judges your life choices with cosmic wisdom."
                                            • Dog running: "Speed breaks the sound barrier regularly."
                                            
                                            PUNCHLINE STYLE:
                                            • Use exaggerated claims and hyperbole
                                            • Reference everyday situations in absurd ways
                                            • "Legend says...", "Rumor has it...", "Scientists believe..."
                                            • Make it sound like a ridiculous superpower or achievement
                                            • Keep it short, punchy, and immediately funny
                                            • Base it on what's actually happening in the image
                                            
                                            TOTAL LENGTH: Keep both sentences under 80 characters total for proper display!
                                            
                                            FINAL REMINDERS:
                                            • Be honest about rarity - don't inflate it! Most images should be COMMON or UNCOMMON
                                            • Only use LEGENDARY for truly spectacular, once-in-a-lifetime images
                                            • Attack 8+ should be RARE or higher, not UNCOMMON
                                            • Make the punchline funny and based on what's actually in the image
                                            
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

        // Weighted distribution - Better EPIC balance
        val finalRarity = when {
            random <= 50 -> CardRarity.COMMON      // 50%
            random <= 75 -> CardRarity.UNCOMMON    // 25% (51-75)
            random <= 90 -> CardRarity.RARE        // 15% (76-90)
            random <= 98 -> CardRarity.EPIC        // 8%  (91-98)
            else -> CardRarity.LEGENDARY           // 2%  (99-100)
        }

        // AI influence - moderate upgrades
        val upgradedRarity = when {
            // LEGENDARY upgrades
            aiSuggestedRarity == CardRarity.LEGENDARY && finalRarity == CardRarity.EPIC
                    && Random.nextFloat() < 0.3 -> CardRarity.LEGENDARY

            // EPIC upgrades
            aiSuggestedRarity == CardRarity.EPIC && finalRarity == CardRarity.RARE
                    && Random.nextFloat() < 0.25 -> CardRarity.EPIC

            aiSuggestedRarity == CardRarity.LEGENDARY && finalRarity == CardRarity.RARE
                    && Random.nextFloat() < 0.15 -> CardRarity.EPIC

            // RARE upgrades
            aiSuggestedRarity == CardRarity.RARE && finalRarity == CardRarity.UNCOMMON
                    && Random.nextFloat() < 0.3 -> CardRarity.RARE

            aiSuggestedRarity >= CardRarity.EPIC && finalRarity == CardRarity.UNCOMMON
                    && Random.nextFloat() < 0.2 -> CardRarity.RARE

            // UNCOMMON upgrades
            aiSuggestedRarity >= CardRarity.RARE && finalRarity == CardRarity.COMMON
                    && Random.nextFloat() < 0.25 -> CardRarity.UNCOMMON

            else -> finalRarity
        }

        return upgradedRarity
    }
}

class OpenAIException(message: String) : Exception(message)