package com.mrc.mistarbot.model

import kotlinx.serialization.Serializable

enum class CardRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

@Serializable
data class Card(
    val id: Int = 0,
    val name: String,
    val imageUrl: String,
    val attack: Int,
    val defense: Int,
    val rarity: CardRarity,
    val ownerId: String, // Discord user ID
    val description: String? = null, // NEW: Card description (max 2 lines)
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(attack in 1..10) { "Attack must be between 1 and 10" }
        require(defense in 1..10) { "Defense must be between 1 and 10" }
    }
}