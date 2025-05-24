package com.mrc.mistarbot.service

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Test service for creating cards when OpenAI is unavailable (demo mode)
 */
class TestCardService {

    private val cardTemplates = mapOf(
        // Fantasy themes
        "dragon" to Triple("Ancient Dragon", 8..10, CardRarity.LEGENDARY),
        "warrior" to Triple("Battle Warrior", 7..9, CardRarity.RARE),
        "knight" to Triple("Noble Knight", 6..8, CardRarity.UNCOMMON),
        "wizard" to Triple("Mystic Wizard", 5..7, CardRarity.RARE),
        "mage" to Triple("Arcane Mage", 5..8, CardRarity.RARE),
        "phoenix" to Triple("Fire Phoenix", 8..9, CardRarity.LEGENDARY),
        "wolf" to Triple("Shadow Wolf", 6..7, CardRarity.UNCOMMON),
        "cat" to Triple("Spirit Cat", 4..6, CardRarity.COMMON),

        // Nature themes  
        "forest" to Triple("Forest Guardian", 5..7, CardRarity.UNCOMMON),
        "tree" to Triple("Ancient Tree", 3..5, CardRarity.COMMON),
        "flower" to Triple("Bloom Spirit", 2..4, CardRarity.COMMON),
        "mountain" to Triple("Stone Titan", 7..9, CardRarity.RARE),
        "ocean" to Triple("Sea Leviathan", 6..8, CardRarity.RARE),
        "storm" to Triple("Storm Elemental", 7..8, CardRarity.RARE),

        // Tech/Modern themes
        "robot" to Triple("Cyber Guardian", 6..8, CardRarity.UNCOMMON),
        "car" to Triple("Speed Machine", 5..7, CardRarity.COMMON),
        "city" to Triple("Urban Titan", 5..6, CardRarity.COMMON),
        "cyber" to Triple("Digital Entity", 6..7, CardRarity.UNCOMMON)
    )

    private val fallbackNames = listOf(
        "Mystic Entity", "Ancient Guardian", "Noble Warrior", "Shadow Spirit",
        "Crystal Bearer", "Storm Rider", "Fire Walker", "Ice Guardian",
        "Wind Dancer", "Earth Shaker", "Light Bringer", "Dark Hunter"
    )

    fun createSmartCard(filename: String, imageUrl: String, userId: String): Card {
        logger.info { "Creating smart demo card from filename: $filename" }

        val cleanFilename = filename.lowercase()
            .replace(Regex("[0-9_-]"), " ")  // Remove numbers and separators
            .replace(Regex("\\.(png|jpg|jpeg|gif|webp)"), "") // Remove extensions

        // Try to match filename to themes
        val matchedTemplate = cardTemplates.entries.find { (keyword, _) ->
            cleanFilename.contains(keyword)
        }

        val (baseName, attackRange, baseRarity) = matchedTemplate?.value
            ?: Triple(fallbackNames.random(), 4..7, CardRarity.COMMON)

        // Add some filename-based variation
        val nameVariation = when {
            cleanFilename.contains("fire") -> "Fire $baseName"
            cleanFilename.contains("ice") -> "Frost $baseName"
            cleanFilename.contains("shadow") -> "Shadow $baseName"
            cleanFilename.contains("light") -> "Radiant $baseName"
            cleanFilename.contains("dark") -> "Dark $baseName"
            cleanFilename.contains("gold") -> "Golden $baseName"
            cleanFilename.contains("ancient") -> "Ancient $baseName"
            else -> baseName
        }

        val attack = attackRange.random()
        val defense = when {
            cleanFilename.contains("armor") || cleanFilename.contains("shield") -> {
                // High defense items
                ((attack + 1).coerceAtMost(10)).let { minDef ->
                    (minDef..10).random()
                }
            }

            cleanFilename.contains("glass") || cleanFilename.contains("crystal") -> {
                // Fragile items - low defense
                (1..3).random()
            }

            else -> {
                // Normal variation around attack value
                val minDef = (attack - 2).coerceAtLeast(1)
                val maxDef = (attack + 2).coerceAtMost(10)
                (minDef..maxDef).random()
            }
        }

        // Filename-based rarity boost
        val finalRarity = when {
            cleanFilename.contains("legendary") || cleanFilename.contains("epic") -> CardRarity.LEGENDARY
            cleanFilename.contains("rare") || cleanFilename.contains("unique") -> CardRarity.RARE
            cleanFilename.contains("special") || cleanFilename.contains("master") -> CardRarity.UNCOMMON
            Random.nextFloat() < 0.1 -> CardRarity.values().random() // 10% chance for random rarity boost
            else -> baseRarity
        }

        // Generate description based on card name and theme
        val description = generateDescription(nameVariation, finalRarity)

        logger.info { "Generated smart card: $nameVariation (ATK:$attack, DEF:$defense, ${finalRarity})" }

        return Card(
            name = nameVariation.take(25), // Ensure max length
            imageUrl = imageUrl,
            attack = attack,
            defense = defense,
            rarity = finalRarity,
            description = description,
            ownerId = userId
        )
    }

    private fun generateDescription(name: String, rarity: CardRarity): String {
        val themeDescriptions = mapOf(
            "dragon" to listOf("Breathes ancient fire", "Guardian of forgotten realms", "Scales harder than steel"),
            "warrior" to listOf("Battle-tested and fearless", "Sworn protector of the weak", "Master of combat arts"),
            "knight" to listOf("Honor guides every strike", "Defender of the innocent", "Chivalry incarnate"),
            "wizard" to listOf(
                "Wielder of arcane mysteries",
                "Master of elemental forces",
                "Scholar of forbidden arts"
            ),
            "mage" to listOf("Channels pure magical energy", "Student of ancient wisdom", "Keeper of mystical secrets"),
            "phoenix" to listOf(
                "Reborn from eternal flames",
                "Symbol of hope and renewal",
                "Master of fire and rebirth"
            ),
            "spirit" to listOf("Ethereal and otherworldly", "Guardian of lost souls", "Walker between realms"),
            "guardian" to listOf("Eternal protector", "Vigilant and unwavering", "Shield against darkness"),
            "shadow" to listOf("Moves unseen in darkness", "Master of stealth", "Born from nightmares"),
            "light" to listOf("Beacon of hope", "Banisher of shadows", "Radiant and pure"),
            "crystal" to listOf("Focuses magical energy", "Unbreakable and pure", "Harbors ancient power"),
            "storm" to listOf("Commands wind and lightning", "Fury of the tempest", "Harbinger of change")
        )

        val rarityPrefixes = when (rarity) {
            CardRarity.COMMON -> listOf("", "Simple yet effective", "Humble but reliable")
            CardRarity.UNCOMMON -> listOf("Uncommonly skilled", "Rare talent", "Notable ability")
            CardRarity.RARE -> listOf("Exceptionally powerful", "Legendary skill", "Extraordinary might")
            CardRarity.LEGENDARY -> listOf("Ultimate power", "Mythical strength", "Divine authority")
        }

        // Find matching theme or use default
        val matchedTheme = themeDescriptions.entries.find { (keyword, _) ->
            name.lowercase().contains(keyword)
        }

        val descriptions = matchedTheme?.value ?: listOf(
            "A mysterious entity", "Harbors unknown power", "Shrouded in mystery",
            "Possesses hidden strength", "An enigmatic force", "Holds ancient secrets"
        )

        val prefix = rarityPrefixes.random()
        val description = descriptions.random()

        val fullDescription = if (prefix.isNotEmpty()) "$prefix. $description." else "$description."

        return fullDescription.take(80) // Max 80 characters
    }
}