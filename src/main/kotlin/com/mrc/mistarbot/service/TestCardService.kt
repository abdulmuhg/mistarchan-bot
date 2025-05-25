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

    // Updated to match OpenAI distribution
    companion object {
        private val RARITY_DISTRIBUTION = mapOf(
            CardRarity.COMMON to 50,      // 50%
            CardRarity.UNCOMMON to 25,    // 25%
            CardRarity.RARE to 15,        // 15%
            CardRarity.EPIC to 8,         // 8%
            CardRarity.LEGENDARY to 2     // 2%
        )
    }

    private val cardTemplates = mapOf(
        // Legendary themes (naturally rare) - Ultimate tier
        "dragon" to Triple("Ancient Dragon", 8..10, CardRarity.LEGENDARY),
        "phoenix" to Triple("Fire Phoenix", 8..9, CardRarity.LEGENDARY),
        "titan" to Triple("Stone Titan", 7..9, CardRarity.LEGENDARY),
        "leviathan" to Triple("Sea Leviathan", 6..8, CardRarity.LEGENDARY),
        "god" to Triple("Divine Entity", 9..10, CardRarity.LEGENDARY),
        "ultimate" to Triple("Ultimate Being", 8..10, CardRarity.LEGENDARY),

        // Epic themes - Exceptional tier
        "meme" to Triple("Meme Lord", 7..9, CardRarity.EPIC),
        "funny" to Triple("Comedy Genius", 6..8, CardRarity.EPIC),
        "epic" to Triple("Epic Moment", 7..9, CardRarity.EPIC),
        "legendary" to Triple("Legendary Being", 7..9, CardRarity.EPIC),
        "master" to Triple("Grand Master", 6..8, CardRarity.EPIC),
        "champion" to Triple("Arena Champion", 7..9, CardRarity.EPIC),

        // Rare themes - High value
        "warrior" to Triple("Battle Warrior", 6..8, CardRarity.RARE),
        "wizard" to Triple("Mystic Wizard", 5..7, CardRarity.RARE),
        "mage" to Triple("Arcane Mage", 5..8, CardRarity.RARE),
        "storm" to Triple("Storm Elemental", 7..8, CardRarity.RARE),
        "group" to Triple("Squad Goals", 6..8, CardRarity.RARE),
        "party" to Triple("Party Legend", 6..8, CardRarity.RARE),
        "reaction" to Triple("Reaction Master", 5..7, CardRarity.RARE),
        "fail" to Triple("Epic Fail", 6..7, CardRarity.RARE),
        "derp" to Triple("Derp Champion", 5..8, CardRarity.RARE),

        // Uncommon themes - Social content
        "knight" to Triple("Noble Knight", 5..7, CardRarity.UNCOMMON),
        "wolf" to Triple("Shadow Wolf", 6..7, CardRarity.UNCOMMON),
        "forest" to Triple("Forest Guardian", 5..7, CardRarity.UNCOMMON),
        "robot" to Triple("Cyber Guardian", 6..8, CardRarity.UNCOMMON),
        "friends" to Triple("Friend Squad", 5..7, CardRarity.UNCOMMON),
        "selfie" to Triple("Selfie Star", 4..6, CardRarity.UNCOMMON),
        "smile" to Triple("Smile Keeper", 4..6, CardRarity.UNCOMMON),
        "face" to Triple("Expression Master", 5..7, CardRarity.UNCOMMON),

        // Common themes
        "cat" to Triple("Spirit Cat", 3..6, CardRarity.COMMON),
        "tree" to Triple("Ancient Tree", 3..5, CardRarity.COMMON),
        "flower" to Triple("Bloom Spirit", 2..4, CardRarity.COMMON),
        "car" to Triple("Speed Machine", 4..6, CardRarity.COMMON),
        "city" to Triple("Urban Guardian", 4..6, CardRarity.COMMON),
        "house" to Triple("Home Spirit", 2..4, CardRarity.COMMON),
        "food" to Triple("Nourishing Essence", 3..5, CardRarity.COMMON),
        "book" to Triple("Knowledge Keeper", 3..6, CardRarity.COMMON)
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

        val (baseName, attackRange, themeRarity) = matchedTemplate?.value
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
            cleanFilename.contains("epic") || cleanFilename.contains("legendary") -> "Epic $baseName"
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

        // Apply natural rarity distribution with filename influence
        val finalRarity = applyNaturalRarityDistribution(themeRarity, cleanFilename)

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

    /**
     * Applies natural rarity distribution while considering theme and filename hints
     */
    private fun applyNaturalRarityDistribution(themeRarity: CardRarity, filename: String): CardRarity {
        val random = Random.nextInt(100) + 1 // 1-100

        // Base distribution - Better EPIC balance
        val baseRarity = when {
            random <= 50 -> CardRarity.COMMON      // 50%
            random <= 75 -> CardRarity.UNCOMMON    // 25% (51-75)
            random <= 90 -> CardRarity.RARE        // 15% (76-90)
            random <= 98 -> CardRarity.EPIC        // 8%  (91-98)
            else -> CardRarity.LEGENDARY           // 2%  (99-100)
        }

        // Theme and filename influence - balanced approach
        val finalRarity = when {
            // MEME AND FUNNY CONTENT - Good value
            filename.contains("meme") || filename.contains("funny") || filename.contains("lol") ||
                    filename.contains("wtf") || filename.contains("derp") || filename.contains("fail") -> {
                when {
                    baseRarity >= CardRarity.EPIC || Random.nextFloat() < 0.2 -> {
                        if (Random.nextFloat() < 0.15) CardRarity.LEGENDARY
                        else if (Random.nextFloat() < 0.4) CardRarity.EPIC
                        else CardRarity.RARE
                    }

                    baseRarity >= CardRarity.RARE || Random.nextFloat() < 0.4 -> CardRarity.RARE
                    else -> CardRarity.UNCOMMON
                }
            }

            // GROUP AND SOCIAL CONTENT - Community value
            filename.contains("group") || filename.contains("squad") || filename.contains("friends") ||
                    filename.contains("party") || filename.contains("selfie") || filename.contains("us") -> {
                when {
                    baseRarity >= CardRarity.EPIC || Random.nextFloat() < 0.15 -> {
                        if (Random.nextFloat() < 0.3) CardRarity.EPIC else CardRarity.RARE
                    }

                    baseRarity >= CardRarity.RARE || Random.nextFloat() < 0.35 -> CardRarity.RARE
                    else -> CardRarity.UNCOMMON
                }
            }

            // REACTION/EXPRESSION CONTENT - Meme potential
            filename.contains("reaction") || filename.contains("face") || filename.contains("expression") ||
                    filename.contains("smile") || filename.contains("laugh") || filename.contains("cry") -> {
                when {
                    baseRarity >= CardRarity.EPIC || Random.nextFloat() < 0.12 -> {
                        if (Random.nextFloat() < 0.25) CardRarity.EPIC else CardRarity.RARE
                    }

                    baseRarity >= CardRarity.RARE || Random.nextFloat() < 0.3 -> CardRarity.RARE
                    else -> CardRarity.UNCOMMON
                }
            }

            // Strong legendary indicators
            filename.contains("legendary") || filename.contains("ultimate") || filename.contains("divine") -> {
                when {
                    baseRarity >= CardRarity.LEGENDARY || Random.nextFloat() < 0.1 -> CardRarity.LEGENDARY
                    baseRarity >= CardRarity.EPIC || Random.nextFloat() < 0.2 -> CardRarity.EPIC
                    else -> CardRarity.RARE
                }
            }

            // Epic indicators
            filename.contains("epic") || filename.contains("master") || filename.contains("champion") -> {
                when {
                    baseRarity >= CardRarity.EPIC || Random.nextFloat() < 0.2 -> {
                        if (Random.nextFloat() < 0.15) CardRarity.LEGENDARY else CardRarity.EPIC
                    }

                    baseRarity >= CardRarity.RARE || Random.nextFloat() < 0.4 -> CardRarity.RARE
                    else -> CardRarity.UNCOMMON
                }
            }

            // Strong rare indicators
            filename.contains("rare") || filename.contains("special") || filename.contains("unique") -> {
                when {
                    baseRarity >= CardRarity.RARE || Random.nextFloat() < 0.3 -> {
                        if (Random.nextFloat() < 0.1) CardRarity.EPIC else CardRarity.RARE
                    }

                    else -> CardRarity.UNCOMMON
                }
            }

            // Theme influence: if theme suggests higher rarity, small chance to upgrade
            themeRarity == CardRarity.LEGENDARY && baseRarity == CardRarity.RARE
                    && Random.nextFloat() < 0.2 -> CardRarity.LEGENDARY

            themeRarity == CardRarity.RARE && baseRarity == CardRarity.UNCOMMON
                    && Random.nextFloat() < 0.3 -> CardRarity.RARE

            themeRarity == CardRarity.UNCOMMON && baseRarity == CardRarity.COMMON
                    && Random.nextFloat() < 0.25 -> CardRarity.UNCOMMON

            else -> baseRarity
        }

        return finalRarity
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
            "storm" to listOf("Commands wind and lightning", "Fury of the tempest", "Harbinger of change"),
            "cat" to listOf("Silent hunter", "Graceful and agile", "Watches from shadows"),
            "tree" to listOf("Ancient wisdom keeper", "Rooted in earth's power", "Stands through all seasons"),
            "flower" to listOf("Delicate beauty", "Nature's gentle touch", "Blooms with life energy"),

            // NEW: Meme and social descriptions
            "meme" to listOf("Peak comedy achieved", "Viral sensation unleashed", "Internet legend born"),
            "funny" to listOf("Spreads pure joy", "Master of comedic timing", "Laughter incarnate"),
            "group" to listOf("Squad goals activated", "Friendship power unlocked", "Unity in chaos"),
            "party" to listOf("Life of every gathering", "Celebration mastermind", "Joy spreader supreme"),
            "selfie" to listOf("Captures perfect moments", "Social media royalty", "Confidence radiates"),
            "friends" to listOf("Bond stronger than steel", "Memories maker", "Loyalty personified"),
            "reaction" to listOf("Perfect expression capture", "Emotion made visible", "Response game strong"),
            "fail" to listOf("Learns from every mistake", "Embraces chaos gracefully", "Humor in disaster"),
            "face" to listOf("Expression tells stories", "Emotion master", "Visual storyteller"),
            "smile" to listOf("Brightens darkest days", "Happiness spreader", "Joy bringer")
        )

        val rarityPrefixes = when (rarity) {
            CardRarity.COMMON -> listOf("", "Simple yet effective", "Humble but reliable")
            CardRarity.UNCOMMON -> listOf("Uncommonly skilled", "Notable ability", "Rising talent")
            CardRarity.RARE -> listOf("Exceptionally powerful", "Remarkable prowess", "Extraordinary might")
            CardRarity.EPIC -> listOf("Epic mastery", "Exceptional greatness", "Legendary potential")
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