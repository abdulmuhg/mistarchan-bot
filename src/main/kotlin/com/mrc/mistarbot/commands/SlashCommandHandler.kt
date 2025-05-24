package com.mrc.mistarbot.commands

import com.mrc.mistarbot.database.Database
import com.mrc.mistarbot.game.Battle
import com.mrc.mistarbot.game.BattlePosition
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SlashCommandHandler(
    private val activeGames: MutableMap<String, Battle>
) {

    suspend fun handleCardsCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val action = interaction.command.strings["action"]

        when (action) {
            "list" -> {
                val cards = Database.getCardsByOwner(interaction.user.id.toString())
                if (cards.isEmpty()) {
                    response.respond {
                        content = "You don't have any cards yet! Upload some images to create cards."
                    }
                } else {
                    val cardList = buildString {
                        appendLine("**Your Cards (${cards.size} total):**")
                        appendLine("```")
                        cards.forEachIndexed { index, card ->
                            val rarity = when (card.rarity.name) {
                                "COMMON" -> "⚪"
                                "UNCOMMON" -> "🟢"
                                "RARE" -> "🔵"
                                "LEGENDARY" -> "🟡"
                                else -> "⚪"
                            }
                            // IMPROVED: Make ID more obvious
                            appendLine("[ID: ${card.id}] ${card.name}")
                            appendLine("         ATK: ${card.attack} | DEF: ${card.defense} | $rarity ${card.rarity}")
                            if (index < cards.size - 1) appendLine()
                        }
                        appendLine("```")
                        appendLine("💡 **How to view details:** Use `/card <ID>` (example: `/card ${cards.first().id}`)")
                        appendLine("⚔️ **Ready to battle?** Use `/challenge @user` when you have 3+ cards!")
                    }
                    response.respond {
                        content = cardList
                    }
                }
            }

            else -> {
                response.respond {
                    content = "Unknown action. Use 'list' to view your cards."
                }
            }
        }
    }

    suspend fun handleCardCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val cardId = interaction.command.integers["id"]?.toInt()
        if (cardId == null) {
            response.respond {
                content = "❌ Invalid card ID provided. Use `/cards list` to see your card IDs!"
            }
            return
        }

        val cards = Database.getCardsByOwner(interaction.user.id.toString())
        val card = cards.find { it.id == cardId }

        if (card == null) {
            response.respond {
                content = """
                    ❌ Card #$cardId not found or you don't own it.
                    
                    💡 **Your available card IDs:**
                    ${if (cards.isEmpty())
                    "• No cards yet! Upload images to create cards."
                else
                    cards.joinToString("\n") { "• ID ${it.id}: ${it.name}" }
                }
                """.trimIndent()
            }
            return
        }

        val rarityEmoji = when (card.rarity.name) {
            "COMMON" -> "⚪"
            "UNCOMMON" -> "🟢"
            "RARE" -> "🔵"
            "LEGENDARY" -> "🟡"
            else -> "⚪"
        }

        response.respond {
            content = """
                **🎴 Card Details - ID #${card.id}**
                
                **📛 Name:** ${card.name}
                ⚔️ **Attack:** ${card.attack}/10
                🛡️ **Defense:** ${card.defense}/10
                $rarityEmoji **Rarity:** ${card.rarity}
                🖼️ **Image:** ${card.imageUrl}
                📅 **Created:** <t:${card.createdAt / 1000}:R>
                ${if (!card.description.isNullOrBlank()) "\n💭 **Description:** ${card.description}" else ""}
                
                💡 **Usage:** This card can be used in battles with `/challenge @user`
            """.trimIndent()
        }
    }

    suspend fun handleChallengeCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val targetUser = interaction.command.users["opponent"]
        if (targetUser == null) {
            response.respond {
                content = "Invalid user specified."
            }
            return
        }

        if (targetUser.id == interaction.user.id) {
            response.respond {
                content = "You can't challenge yourself! Find another player to battle."
            }
            return
        }

        if (targetUser.isBot) {
            response.respond {
                content = "You can't challenge a bot! Challenge a real player instead."
            }
            return
        }

        // Check if there's already an active battle in this channel
        if (activeGames.containsKey(interaction.channelId.toString())) {
            response.respond {
                content = "There's already an active battle in this channel! Wait for it to finish."
            }
            return
        }

        val playerACards = Database.getCardsByOwner(interaction.user.id.toString())
        val playerBCards = Database.getCardsByOwner(targetUser.id.toString())

        if (playerACards.size < 3) {
            response.respond {
                content = "You need at least 3 cards to battle! Create more cards by uploading images."
            }
            return
        }

        if (playerBCards.size < 3) {
            response.respond {
                content =
                    "${targetUser.mention} needs at least 3 cards to battle! They should upload some images first."
            }
            return
        }

        val battle = Battle(
            playerAId = interaction.user.id.toString(),
            playerBId = targetUser.id.toString(),
            playerACards = playerACards.shuffled().take(3),
            playerBCards = playerBCards.shuffled().take(3)
        )

        activeGames[interaction.channelId.toString()] = battle

        val playerACardList =
            battle.playerACards.joinToString("\n") { "• [ID: ${it.id}] ${it.name} (ATK: ${it.attack}, DEF: ${it.defense})" }
        val playerBCardList =
            battle.playerBCards.joinToString("\n") { "• [ID: ${it.id}] ${it.name} (ATK: ${it.attack}, DEF: ${it.defense})" }

        response.respond {
            content = """
                ⚔️ **Battle Started!** ⚔️
                ${interaction.user.mention} vs ${targetUser.mention}
                
                **${interaction.user.username}'s Battle Cards:**
                $playerACardList
                
                **${targetUser.username}'s Battle Cards:**  
                $playerBCardList
                
                **🎯 How to Play:**
                Use `/play <card_id> <attack/defense>` to make your moves!
                
                **📋 Battle Rules:**
                • Best of 3 rounds wins the battle
                • Attack vs Attack: Higher attack wins
                • Attack vs Defense: Attack wins if Attack > Defense  
                • Defense vs Attack: Defense wins if Defense ≥ Attack
                • Defense vs Defense: Tie (no points)
                
                **${interaction.user.username}** goes first! Choose a card ID from your battle cards above.
            """.trimIndent()
        }
    }

    suspend fun handlePlayCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val battle = activeGames[interaction.channelId.toString()]
        if (battle == null) {
            response.respond {
                content = "No active battle in this channel! Use `/challenge @user` to start a battle."
            }
            return
        }

        val cardId = interaction.command.integers["card_id"]?.toInt()
        val positionStr = interaction.command.strings["position"]

        if (cardId == null || positionStr == null) {
            response.respond {
                content = "Invalid parameters provided."
            }
            return
        }

        // Check if user is part of this battle
        if (interaction.user.id.toString() != battle.playerAId &&
            interaction.user.id.toString() != battle.playerBId
        ) {
            response.respond {
                content = "You're not part of this battle!"
            }
            return
        }

        val position = when (positionStr.lowercase()) {
            "attack" -> BattlePosition.ATTACK
            "defense" -> BattlePosition.DEFENSE
            else -> {
                response.respond {
                    content = "Invalid position! Use 'attack' or 'defense'."
                }
                return
            }
        }

        val success = battle.submitMove(interaction.user.id.toString(), cardId, position)
        if (!success) {
            response.respond {
                content = """
                    ❌ **Invalid move!** Make sure:
                    • You own the card (Card ID: $cardId)
                    • The card hasn't been used already  
                    • You're using one of your battle cards
                    
                    **Your available battle card IDs:**
                    ${
                    getBattleCards(
                        battle,
                        interaction.user.id.toString()
                    ).joinToString("\n") { "• [ID: ${it.id}] ${it.name}" }
                }
                """.trimIndent()
            }
            return
        }

        response.respond {
            content =
                "✅ **Move submitted!** You played card #$cardId in **$positionStr** position.\nWaiting for your opponent..."
        }

        // TODO: Add battle resolution logic when both players have submitted moves
        // This would check if both players have submitted moves for the current round
        // and then resolve the round, announce the winner, and check if battle is complete
    }

    suspend fun handleHelpCommand(
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        response.respond {
            content = """
                🎴 **MistarChan Bot - AI Trading Card Game**
                
                **📋 Commands:**
                `/cards list` - View your card collection (shows card IDs)
                `/card <id>` - View details of a specific card (use ID from list)
                `/challenge @user` - Challenge another user to battle
                `/play <card_id> <attack/defense>` - Make a move in battle
                `/status` - Show bot status and your stats
                `/clear` - Clear your card collection
                `/help` - Show this help message
                
                **🎨 How to Create Cards:**
                Simply upload an image to any channel I can see! I'll analyze it with AI and create a unique trading card with:
                • **Name** based on image content
                • **Attack** power (1-10) 
                • **Defense** power (1-10)
                • **Rarity** (Common ⚪ | Uncommon 🟢 | Rare 🔵 | Legendary 🟡)
                • **Description** with mystical flavor text
                
                **🔍 Finding Card IDs:**
                1. Use `/cards list` to see all your cards
                2. Look for `[ID: 123]` next to each card name
                3. Use `/card 123` to see full details
                
                **⚔️ Battle System:**
                • Challenge players with `/challenge @user`
                • Each battle uses 3 random cards from your collection
                • Choose **Attack** or **Defense** position for each card
                • Win 2 out of 3 rounds to win the battle!
                
                **🎯 Combat Rules:**
                • **Attack vs Attack:** Higher attack wins
                • **Attack vs Defense:** Attack wins if Attack > Defense
                • **Defense vs Attack:** Defense wins if Defense ≥ Attack  
                • **Defense vs Defense:** Tie (no points awarded)
                
                **🚀 Getting Started:**
                1. Upload an image to create your first card
                2. Use `/cards list` to see your cards and their IDs
                3. Create at least 3 cards to battle
                4. Challenge someone with `/challenge @user`
                5. Have fun! 🎉
            """.trimIndent()
        }
    }

    private fun getBattleCards(battle: Battle, playerId: String): List<com.mrc.mistarbot.model.Card> {
        return if (playerId == battle.playerAId) battle.playerACards else battle.playerBCards
    }
}