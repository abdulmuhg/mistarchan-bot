package com.mrc.mistarbot.service

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Fallback service for creating cards when OpenAI is unavailable
 */
class TestCardService {
    
    private val cardNames = listOf(
        "Mystic Warrior", "Shadow Knight", "Fire Dragon", "Ice Phoenix", "Thunder Beast",
        "Crystal Guardian", "Storm Elemental", "Earth Golem", "Wind Spirit", "Dark Mage",
        "Light Paladin", "Forest Ranger", "Ocean Lord", "Mountain King", "Sky Dancer"
    )
    
    fun createTestCard(imageUrl: String, userId: String): Card {
        logger.info { "Creating test card (OpenAI fallback mode)" }
        
        val name = cardNames.random()
        val attack = Random.nextInt(1, 11)
        val defense = Random.nextInt(1, 11)
        val rarity = when (Random.nextInt(100)) {
            in 0..49 -> CardRarity.COMMON      // 50%
            in 50..74 -> CardRarity.UNCOMMON   // 25%
            in 75..89 -> CardRarity.RARE       // 15%
            else -> CardRarity.LEGENDARY       // 10%
        }
        
        return Card(
            name = name,
            imageUrl = imageUrl,
            attack = attack,
            defense = defense,
            rarity = rarity,
            ownerId = userId
        )
    }
}