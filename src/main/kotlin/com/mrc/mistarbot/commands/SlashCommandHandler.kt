package com.mrc.mistarbot.commands

import com.mrc.mistarbot.database.Database
import com.mrc.mistarbot.game.Battle
import com.mrc.mistarbot.game.BattlePosition
import com.mrc.mistarbot.game.BattleUpdateResult
import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.service.CardImageGenerator
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.channel.createMessage
import dev.kord.rest.builder.message.addFile
import kotlinx.coroutines.*
import kotlin.random.Random
import mu.KotlinLogging
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

private val logger = KotlinLogging.logger {}

// Mock User classes defined here to avoid import issues
data class MockUser(
    val id: String,
    val name: String,
    val personality: BattlePersonality,
    val cards: List<Card>
)

enum class BattlePersonality {
    AGGRESSIVE,     // Prefers attack moves
    DEFENSIVE,      // Prefers defense moves
    BALANCED,       // Mixed strategy
    SMART,          // Analyzes opponent and adapts
    CHAOTIC         // Completely random
}

class SlashCommandHandler(
    private val activeGames: MutableMap<String, Battle>
) {

    // Store mock battles separately to handle AI automation
    private val activeMockBattles = mutableMapOf<String, MockUser>()
    private val mockUserService = MockUserService()

    suspend fun handleCardsCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val action = interaction.command.strings["action"]

            when (action) {
                "list" -> {
                    val cards = Database.getCardsByOwner(interaction.user.id.toString())
                    if (cards.isEmpty()) {
                        response.respond {
                            content = """
                                📦 **No cards yet!** 
                                
                                🎨 **Get started:** Upload some images to create your first cards!
                                💡 **Tip:** You need at least 3 cards to battle
                            """.trimIndent()
                        }
                    } else {
                        val cardList = buildString {
                            appendLine("🎴 **Your Card Collection (${cards.size} total):**")
                            appendLine("```")
                            cards.forEachIndexed { index, card ->
                                val rarity = when (card.rarity.name) {
                                    "COMMON" -> "⚪"
                                    "UNCOMMON" -> "🟢"
                                    "RARE" -> "🔵"
                                    "EPIC" -> "🟣"
                                    "LEGENDARY" -> "🟡"
                                    else -> "⚪"
                                }
                                appendLine("[ID: ${card.id}] ${card.name}")
                                appendLine("         ATK: ${card.attack} | DEF: ${card.defense} | $rarity ${card.rarity}")
                                if (index < cards.size - 1) appendLine()
                            }
                            appendLine("```")
                            appendLine("💡 **Quick Actions:**")
                            appendLine("• `/card ${cards.first().id}` - View details")
                            appendLine("• `/practice` - Practice vs AI")
                            appendLine("• `/challenge @user` - Battle players ${if (cards.size >= 3) "✅" else "❌"}")
                        }
                        response.respond {
                            content = cardList
                        }
                    }
                }
                else -> {
                    response.respond {
                        content = "❌ Unknown action. Use `list` to view your cards."
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handleCardsCommand: ${e.message}" }
            response.respond {
                content = "❌ Error: ${e.message}"
            }
        }
    }

    suspend fun handleCardCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val cardId = interaction.command.integers["id"]?.toInt()
            if (cardId == null) {
                response.respond {
                    content = "❌ Invalid card ID. Use `/cards list` to see your card IDs!"
                }
                return
            }

            val cards = Database.getCardsByOwner(interaction.user.id.toString())
            val card = cards.find { it.id == cardId }

            if (card == null) {
                response.respond {
                    content = """
                        ❌ **Card #$cardId not found**
                        
                        💡 **Your cards:** ${if (cards.isEmpty()) "None yet!" else cards.take(3).joinToString { "#${it.id}" }}
                        Use `/cards list` to see all cards.
                    """.trimIndent()
                }
                return
            }

            val rarityEmoji = when (card.rarity.name) {
                "COMMON" -> "⚪"
                "UNCOMMON" -> "🟢"
                "RARE" -> "🔵"
                "EPIC" -> "🟣"
                "LEGENDARY" -> "🟡"
                else -> "⚪"
            }

            response.respond {
                content = """
                    🎴 **Card Details - #${card.id}**
                    
                    **📛 ${card.name}**
                    ⚔️ **Attack:** ${card.attack}/10
                    🛡️ **Defense:** ${card.defense}/10
                    $rarityEmoji **Rarity:** ${card.rarity}
                    📅 **Created:** <t:${card.createdAt / 1000}:R>
                    ${if (!card.description.isNullOrBlank()) "\n💭 *${card.description}*" else ""}
                    
                    ⚔️ Ready for battle with `/practice` or `/challenge`
                """.trimIndent()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handleCardCommand: ${e.message}" }
            response.respond { content = "❌ Error: ${e.message}" }
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

        val battleId = "${interaction.user.id}-ai-${System.currentTimeMillis()}"
        val channelId = interaction.channelId.toString()
        val battle = Battle(
            battleId = battleId,
            channelId = channelId,
            playerAId = interaction.user.id.toString(),
            playerBId = targetUser.id.toString(),
            playerACards = playerACards.shuffled().take(3),
            playerBCards = playerBCards.shuffled().take(3),
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

    suspend fun handlePracticeCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val difficultyStr = interaction.command.strings["difficulty"] ?: "medium"
            val personality = when (difficultyStr.lowercase()) {
                "easy" -> BattlePersonality.CHAOTIC
                "medium" -> BattlePersonality.BALANCED
                "hard" -> BattlePersonality.SMART
                "aggressive" -> BattlePersonality.AGGRESSIVE
                "defensive" -> BattlePersonality.DEFENSIVE
                else -> BattlePersonality.BALANCED
            }

            val channelId = interaction.channelId.toString()

            if (activeGames.containsKey(channelId)) {
                response.respond {
                    content = "⚔️ **Battle in progress!** Use `/battle` to check status."
                }
                return
            }

            val playerCards = Database.getCardsByOwner(interaction.user.id.toString())

            if (playerCards.size < 3) {
                response.respond {
                    content = """
                        ❌ **Need more cards!**
                        
                        You have ${playerCards.size}/3 cards needed for battle.
                        Upload images to create more cards!
                    """.trimIndent()
                }
                return
            }

            // Create AI opponent
            val mockUser = mockUserService.createMockUser(
                "${interaction.user.id}_${System.currentTimeMillis()}",
                personality
            )

            val battleId = "${interaction.user.id}-ai-${System.currentTimeMillis()}"
            val battle = Battle(
                battleId = battleId,
                playerAId = interaction.user.id.toString(),
                playerBId = mockUser.id,
                playerACards = playerCards.shuffled().take(3),
                playerBCards = mockUser.cards.shuffled().take(3),
                channelId = channelId
            )

            activeGames[channelId] = battle
            activeMockBattles[channelId] = mockUser

            val playerCardList = battle.playerACards.joinToString("\n") {
                "• **[${it.id}]** ${it.name} `⚔️${it.attack} 🛡️${it.defense}`"
            }
            val aiCardList = battle.playerBCards.joinToString("\n") {
                "• ${it.name} `⚔️${it.attack} 🛡️${it.defense}`"
            }

            val aiName = mockUserService.getMockUserDisplayName(mockUser)
            val taunt = mockUserService.getTrashTalkMessage(mockUser, "battle_start")

            response.respond {
                content = """
                    🤖 **PRACTICE BATTLE** vs $aiName
                    
                    > *"$taunt"*
                    
                    ┌─ **📋 Your Battle Cards** ─┐
                    $playerCardList
                    └─────────────────────────────┘
                    
                    ┌─ **🤖 AI Battle Cards** ─┐
                    $aiCardList
                    └─────────────────────────────┘
                    
                    **⚔️ Round 1 Starting!**
                    Choose your move: `/play <card_id> <attack/defense>`
                    
                    *Example:* `/play ${battle.playerACards.first().id} attack`
                """.trimIndent()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handlePracticeCommand: ${e.message}" }
            response.respond { content = "❌ Error: ${e.message}" }
        }
    }

    suspend fun handlePlayCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val channelId = interaction.channelId.toString()
            val battle = activeGames[channelId]

            if (battle == null) {
                response.respond {
                    content = """
                        ❌ **No active battle!**
                        
                        Start one: `/practice` or `/challenge @user`
                    """.trimIndent()
                }
                return
            }

            val cardId = interaction.command.integers["card_id"]?.toInt()
            val positionStr = interaction.command.strings["position"]

            if (cardId == null || positionStr == null) {
                response.respond {
                    content = "❌ Missing info! Use: `/play <card_id> <attack/defense>`"
                }
                return
            }

            val playerId = interaction.user.id.toString()
            val isMockBattle = activeMockBattles.containsKey(channelId)

            if (playerId != battle.playerAId && playerId != battle.playerBId) {
                response.respond {
                    content = "❌ You're not in this battle!"
                }
                return
            }

            val position = when (positionStr.lowercase()) {
                "attack" -> BattlePosition.ATTACK
                "defense" -> BattlePosition.DEFENSE
                else -> {
                    response.respond {
                        content = "❌ Invalid position! Use `attack` or `defense`"
                    }
                    return
                }
            }

            // Get the card name for secret confirmation
            val playerCards = battle.getAvailableCards(playerId)
            val selectedCard = playerCards.find { it.id == cardId }
            val cardName = selectedCard?.name ?: "Card #$cardId"

            val result = battle.submitMove(playerId, cardId, position)

            when (result) {
                is BattleUpdateResult.Error -> {
                    val availableCards = battle.getAvailableCards(playerId)
                    response.respond {
                        content = """
                            ❌ **${result.message}**
                            
                            **🎴 Available cards:**
                            ${availableCards.joinToString("\n") { "• **[${it.id}]** ${it.name}" }}
                        """.trimIndent()
                    }
                }

                is BattleUpdateResult.MoveAccepted -> {
                    // SECURE: Only show move to the player who submitted it
                    response.respond {
                        content = """
                            ✅ **Move submitted secretly!**
                            
                            🤫 You chose: **${cardName}** in **$position** position
                            ${if (isMockBattle) "🤖 AI is thinking..." else "⏳ Waiting for opponent..."}
                            
                            📊 **Battle Status:**
                            • **Round:** ${battle.getCurrentRound()}/3
                            • **Score:** You ${if (playerId == battle.playerAId) battle.getPlayerAScore() else battle.getPlayerBScore()} - ${if (playerId == battle.playerAId) battle.getPlayerBScore() else battle.getPlayerAScore()} Opponent
                        """.trimIndent()
                    }

                    if (isMockBattle) {
                        handleMockUserMove(battle, channelId, interaction, response)
                    }
                }

                is BattleUpdateResult.RoundComplete -> {
                    val mockUser = activeMockBattles[channelId]
                    val playerScore = if (playerId == battle.playerAId) battle.getPlayerAScore() else battle.getPlayerBScore()
                    val opponentScore = if (playerId == battle.playerAId) battle.getPlayerBScore() else battle.getPlayerAScore()
                    val isPlayerWin = result.roundResult.winner == playerId

                    response.respond {
                        content = """
                            🎯 **ROUND ${result.roundResult.roundNumber} RESULTS**
                            
                            ${formatRoundResultWithImage(result.roundResult, battle, playerId, mockUser, interaction)}
                            
                            **📊 Score Update:**
                            • **You:** $playerScore ${if (isPlayerWin) "📈 +1" else ""}
                            • **${mockUser?.let { mockUserService.getMockUserDisplayName(it) } ?: "Opponent"}:** $opponentScore ${if (!isPlayerWin && result.roundResult.winner != null) "📈 +1" else ""}
                            
                            ${if (battle.getRoundsLeft() > 0)
                            "**▶️ Round ${result.nextRound} Starting!**\nChoose your next move!"
                        else ""}
                        """.trimIndent()
                    }
                }

                is BattleUpdateResult.BattleComplete -> {
                    handleBattleComplete(result, battle, channelId, interaction, response)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handlePlayCommand: ${e.message}" }
            response.respond { content = "❌ Error: ${e.message}" }
        }
    }

    private fun formatRoundResult(
        roundResult: com.mrc.mistarbot.game.RoundResult,
        battle: Battle,
        playerId: String,
        mockUser: MockUser?
    ): String {
        val playerMove = if (roundResult.playerAMove.playerId == playerId) roundResult.playerAMove else roundResult.playerBMove
        val opponentMove = if (roundResult.playerAMove.playerId == playerId) roundResult.playerBMove else roundResult.playerAMove

        val opponentName = mockUser?.let { mockUserService.getMockUserDisplayName(it) } ?: "Opponent"
        val isPlayerWin = roundResult.winner == playerId
        val isTie = roundResult.winner == null

        return buildString {
            appendLine("┌─ **Move Reveals** ─┐")
            appendLine("• **You:** ${playerMove.card.name} (${playerMove.position}) `⚔️${playerMove.card.attack} 🛡️${playerMove.card.defense}`")
            appendLine("• **$opponentName:** ${opponentMove.card.name} (${opponentMove.position}) `⚔️${opponentMove.card.attack} 🛡️${opponentMove.card.defense}`")
            appendLine("└────────────────────┘")
            appendLine()

            // Combat resolution
            when {
                playerMove.position == BattlePosition.ATTACK && opponentMove.position == BattlePosition.ATTACK -> {
                    appendLine("⚔️ **Attack vs Attack:** ${playerMove.card.attack} vs ${opponentMove.card.attack}")
                }
                playerMove.position == BattlePosition.ATTACK && opponentMove.position == BattlePosition.DEFENSE -> {
                    appendLine("⚔️🛡️ **Attack vs Defense:** ${playerMove.card.attack} vs ${opponentMove.card.defense}")
                }
                playerMove.position == BattlePosition.DEFENSE && opponentMove.position == BattlePosition.ATTACK -> {
                    appendLine("🛡️⚔️ **Defense vs Attack:** ${playerMove.card.defense} vs ${opponentMove.card.attack}")
                }
                else -> {
                    appendLine("🛡️🛡️ **Defense vs Defense:** Always a tie")
                }
            }

            // Result
            when {
                isPlayerWin -> appendLine("🏆 **You win this round!**")
                isTie -> appendLine("🤝 **Round tied - no points!**")
                else -> appendLine("💔 **You lose this round!**")
            }

            // Add AI taunt if it's a mock battle
            mockUser?.let {
                val taunt = mockUserService.getTrashTalkMessage(it,
                    when {
                        isPlayerWin -> "round_loss"
                        isTie -> "round_tie"
                        else -> "round_win"
                    }
                )
                appendLine()
                appendLine("> *\"$taunt\"*")
            }
        }
    }

    private suspend fun formatRoundResultWithImage(
        roundResult: com.mrc.mistarbot.game.RoundResult,
        battle: Battle,
        playerId: String,
        mockUser: MockUser?,
        interaction: GuildChatInputCommandInteraction
    ): String {
        val playerMove = if (roundResult.playerAMove.playerId == playerId) roundResult.playerAMove else roundResult.playerBMove
        val opponentMove = if (roundResult.playerAMove.playerId == playerId) roundResult.playerBMove else roundResult.playerAMove

        val isPlayerWin = roundResult.winner == playerId
        val isTie = roundResult.winner == null
        val opponentName = mockUser?.let { mockUserService.getMockUserDisplayName(it) } ?: "Opponent"

        // Generate battle scene image ONLY ONCE with proper names
        try {
            val cardGenerator = CardImageGenerator()
            val battleSceneBytes = cardGenerator.generateBattleScene(
                playerCard = playerMove.card,
                playerPosition = playerMove.position,
                opponentCard = opponentMove.card,
                opponentPosition = opponentMove.position,
                roundNumber = roundResult.roundNumber,
                winner = when {
                    isPlayerWin -> "player"
                    isTie -> null
                    else -> "opponent"
                },
                isRevealed = true,
                playerName = "You",
                opponentName = opponentName,
                arenaStyle = null // null = random arena each battle
            )

            // Save and send battle scene
            val tempFile = kotlin.io.path.createTempFile("battle_r${roundResult.roundNumber}", ".png")
            tempFile.writeBytes(battleSceneBytes)

            interaction.channel.createMessage {
                content = "⚔️ **BATTLE SCENE - Round ${roundResult.roundNumber}**"
                addFile(tempFile)
            }

            tempFile.deleteIfExists()

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate battle scene image" }
        }

        return buildString {
            appendLine("🎯 **Round ${roundResult.roundNumber} Results**")

            // Combat analysis
            when {
                playerMove.position == BattlePosition.ATTACK && opponentMove.position == BattlePosition.ATTACK -> {
                    appendLine("⚔️ **Attack vs Attack:** ${playerMove.card.attack} vs ${opponentMove.card.attack}")
                }
                playerMove.position == BattlePosition.ATTACK && opponentMove.position == BattlePosition.DEFENSE -> {
                    appendLine("⚔️🛡️ **Attack vs Defense:** ${playerMove.card.attack} vs ${opponentMove.card.defense}")
                }
                playerMove.position == BattlePosition.DEFENSE && opponentMove.position == BattlePosition.ATTACK -> {
                    appendLine("🛡️⚔️ **Defense vs Attack:** ${playerMove.card.defense} vs ${opponentMove.card.attack}")
                }
                else -> {
                    appendLine("🛡️🛡️ **Defense vs Defense:** Always a tie")
                }
            }

            // Result
            when {
                isPlayerWin -> appendLine("🏆 **You win this round!**")
                isTie -> appendLine("🤝 **Round tied - no points!**")
                else -> appendLine("💔 **You lose this round!**")
            }

            // AI taunt
            mockUser?.let {
                val taunt = mockUserService.getTrashTalkMessage(it,
                    when {
                        isPlayerWin -> "round_loss"
                        isTie -> "round_tie"
                        else -> "round_win"
                    }
                )
                appendLine()
                appendLine("> *\"$taunt\"*")
            }
        }
    }


    private suspend fun handleMockUserMove(
        battle: Battle,
        channelId: String,
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val mockUser = activeMockBattles[channelId]
        if (mockUser == null) {
            logger.error { "Mock user not found for channel $channelId" }
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                logger.info { "🤖 AI is thinking..." }
                delay(Random.nextLong(2000, 4000))

                val availableCards = battle.getAvailableCards(mockUser.id)
                if (availableCards.isEmpty()) {
                    logger.error { "No available cards for AI ${mockUser.id}" }
                    interaction.channel.createMessage {
                        content = "❌ AI error: No available cards!"
                    }
                    return@launch
                }

                val currentScore = Pair(battle.getPlayerBScore(), battle.getPlayerAScore())
                val (chosenCard, chosenPosition) = mockUserService.chooseBattleMove(
                    mockUser, availableCards, battle.getCurrentRound(), currentScore
                )

                logger.info { "🤖 AI chose: ${chosenCard.name} in $chosenPosition position" }

                val result = battle.submitMove(mockUser.id, chosenCard.id, chosenPosition)
                logger.info { "🤖 AI move result: ${result::class.simpleName}" }

                when (result) {
                    is BattleUpdateResult.RoundComplete -> {
                        logger.info { "🎯 Round ${result.roundResult.roundNumber} complete!" }

                        val playerScore = battle.getPlayerAScore()
                        val aiScore = battle.getPlayerBScore()
                        val isPlayerWin = result.roundResult.winner == interaction.user.id.toString()
                        val aiName = mockUserService.getMockUserDisplayName(mockUser)

                        // Use the SAME image generation method - NO MORE DUPLICATES!
                        val roundAnalysis = formatRoundResultWithImage(
                            result.roundResult,
                            battle,
                            interaction.user.id.toString(),
                            mockUser,
                            interaction
                        )

                        interaction.channel.createMessage {
                            content = """
                            $roundAnalysis
                            
                            **📊 Score Update:**
                            • **You:** $playerScore ${if (isPlayerWin) "📈 +1" else ""}
                            • **$aiName:** $aiScore ${if (!isPlayerWin && result.roundResult.winner != null) "📈 +1" else ""}
                            
                            ${if (battle.getRoundsLeft() > 0)
                                "**▶️ Round ${result.nextRound} Starting!**\nChoose your next move with `/play <card_id> <attack/defense>`"
                            else ""}
                        """.trimIndent()
                        }
                    }

                    is BattleUpdateResult.BattleComplete -> {
                        logger.info { "🏆 Battle complete!" }
                        handleMockBattleCompleteWithImage(result, mockUser, battle, channelId, interaction)
                    }

                    is BattleUpdateResult.Error -> {
                        logger.error { "AI move error: ${result.message}" }
                        interaction.channel.createMessage {
                            content = "❌ AI move failed: ${result.message}"
                        }
                    }

                    else -> {
                        logger.warn { "Unexpected AI move result: $result" }
                        interaction.channel.createMessage {
                            content = "🤖 **${mockUserService.getMockUserDisplayName(mockUser)}** has made their move!"
                        }
                    }
                }

            } catch (e: Exception) {
                logger.error(e) { "💥 Critical error in AI move: ${e.message}" }

                try {
                    interaction.channel.createMessage {
                        content = """
                        ❌ **AI Error!**
                        
                        The AI opponent encountered an error: ${e.message}
                        
                        You can continue by using `/battle` to check status or start a new practice battle.
                    """.trimIndent()
                    }
                } catch (msgError: Exception) {
                    logger.error(msgError) { "Failed to send error message to user" }
                }
            }
        }
    }

    private suspend fun handleMockBattleCompleteWithImage(
        result: BattleUpdateResult.BattleComplete,
        mockUser: MockUser,
        battle: Battle,
        channelId: String,
        interaction: GuildChatInputCommandInteraction
    ) {
        val finalResult = result.battleResult
        val playerWon = finalResult.winner == interaction.user.id.toString()
        val aiName = mockUserService.getMockUserDisplayName(mockUser)

        // Generate final battle scene with winner highlighted
        try {
            val playerMove = if (result.finalRound.playerAMove.playerId == interaction.user.id.toString())
                result.finalRound.playerAMove else result.finalRound.playerBMove
            val opponentMove = if (result.finalRound.playerAMove.playerId == interaction.user.id.toString())
                result.finalRound.playerBMove else result.finalRound.playerAMove

            val cardGenerator = CardImageGenerator()
            val finalBattleSceneBytes = cardGenerator.generateBattleScene(
                playerCard = playerMove.card,
                playerPosition = playerMove.position,
                opponentCard = opponentMove.card,
                opponentPosition = opponentMove.position,
                roundNumber = result.finalRound.roundNumber,
                winner = when {
                    playerWon -> "player"
                    finalResult.winner == null -> null
                    else -> "opponent"
                },
                isRevealed = true,
                playerName = "You",
                opponentName = aiName
            )

            // Save and send final battle scene
            val tempFile = kotlin.io.path.createTempFile("final_battle", ".png")
            tempFile.writeBytes(finalBattleSceneBytes)

            interaction.channel.createMessage {
                content = "🏆 **FINAL BATTLE SCENE**"
                addFile(tempFile)
            }

            tempFile.deleteIfExists()

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate final battle scene image" }
        }

        val finalTaunt = mockUserService.getTrashTalkMessage(
            mockUser,
            if (playerWon) "battle_loss" else "battle_victory"
        )

        interaction.channel.createMessage {
            content = """
            🏆 **PRACTICE BATTLE COMPLETE!**
            
            **🎊 FINAL RESULTS:**
            **Winner: ${if (playerWon) "You" else aiName}** ${if (playerWon) "🥇" else "🤖"}
            
            **📊 Final Score:** You ${finalResult.playerAScore} - ${finalResult.playerBScore} $aiName
            
            **📋 Battle Summary:**
            • **Rounds Played:** ${finalResult.rounds.size}/3
            • **Your Wins:** ${finalResult.playerAScore}
            • **AI Wins:** ${finalResult.playerBScore}
            • **Reason:** ${finalResult.battleEndReason}
            
            > *"$finalTaunt"*
            
            ${if (playerWon) "🎉 **Victory!** Great strategy!" else "🎯 **Good practice!** Try different tactics next time!"}
            
            Ready for another? `/practice` or `/challenge @user`
        """.trimIndent()
        }

        // Cleanup
        activeGames.remove(channelId)
        activeMockBattles.remove(channelId)
    }

    private suspend fun handleMockBattleComplete(
        result: BattleUpdateResult.BattleComplete,
        mockUser: MockUser,
        battle: Battle,
        channelId: String,
        interaction: GuildChatInputCommandInteraction
    ) {
        val finalResult = result.battleResult
        val playerWon = finalResult.winner == interaction.user.id.toString()
        val aiName = mockUserService.getMockUserDisplayName(mockUser)

        val finalTaunt = mockUserService.getTrashTalkMessage(
            mockUser,
            if (playerWon) "battle_loss" else "battle_victory"
        )

        interaction.channel.createMessage {
            content = """
                🏆 **PRACTICE BATTLE COMPLETE!**
                
                ${formatRoundResultWithImage(result.finalRound, battle, interaction.user.id.toString(), mockUser, interaction)}
                
                **🎊 FINAL RESULTS:**
                **Winner: ${if (playerWon) "You" else aiName}** ${if (playerWon) "🥇" else "🤖"}
                
                **📊 Final Score:** You ${finalResult.playerAScore} - ${finalResult.playerBScore} $aiName
                
                > *"$finalTaunt"*
                
                ${if (playerWon) "🎉 **Victory!** Great strategy!" else "🎯 **Good practice!** Try different tactics next time!"}
                
                Ready for another? `/practice` or `/challenge @user`
            """.trimIndent()
        }

        // Cleanup
        activeGames.remove(channelId)
        activeMockBattles.remove(channelId)
    }

    private fun formatRoundResult(
        finalRound: com.mrc.mistarbot.game.RoundResult,
        mockUser: MockUser,
        battle: Battle,
        playerId: String
    ): String {
        return finalRound.description.replace("Player A", "You").replace("Player B", mockUserService.getMockUserDisplayName(mockUser))
    }

    private suspend fun handleBattleComplete(
        result: BattleUpdateResult.BattleComplete,
        battle: Battle,
        channelId: String,
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val finalResult = result.battleResult
        val winner = finalResult.winner

        response.respond {
            content = """
                🏆 **BATTLE COMPLETE!**
                
                ${result.finalRound.description}
                
                **Winner: ${if (winner == battle.playerAId) "Player A" else if (winner == battle.playerBId) "Player B" else "Tie"}** ${if (winner != null) "🥇" else "🤝"}
                
                **Final Score:** ${finalResult.playerAScore} - ${finalResult.playerBScore}
                
                Great battle! Ready for more? `/practice` or `/challenge @user`
            """.trimIndent()
        }

        activeGames.remove(channelId)
    }

    suspend fun handleBattleStatusCommand(
        interaction: GuildChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val channelId = interaction.channelId.toString()
            val battle = activeGames[channelId]

            if (battle == null) {
                response.respond {
                    content = """
                        📊 **No active battle**
                        
                        Start one: `/practice` or `/challenge @user`
                    """.trimIndent()
                }
                return
            }

            val playerId = interaction.user.id.toString()
            val playerCards = battle.getAvailableCards(playerId)
            val isMockBattle = activeMockBattles.containsKey(channelId)
            val mockUser = activeMockBattles[channelId]

            response.respond {
                content = """
                    📊 **Battle Status**
                    
                    **⚔️ Current Round:** ${battle.getCurrentRound()}/3
                    **📈 Score:** You ${if (playerId == battle.playerAId) battle.getPlayerAScore() else battle.getPlayerBScore()} - ${if (playerId == battle.playerAId) battle.getPlayerBScore() else battle.getPlayerAScore()} ${if (isMockBattle) mockUser?.let { mockUserService.getMockUserDisplayName(it) } ?: "AI" else "Opponent"}
                    
                    **🎴 Your Remaining Cards:**
                    ${playerCards.joinToString("\n") { "• **[${it.id}]** ${it.name} `⚔️${it.attack} 🛡️${it.defense}`" }}
                    
                    **📝 Move Status:**
                    • **You:** ${if (battle.hasPendingMove(playerId)) "✅ Move submitted" else "⏳ Waiting for your move"}
                    • **${if (isMockBattle) "AI" else "Opponent"}:** ${if (battle.hasPendingMove(if (playerId == battle.playerAId) battle.playerBId else battle.playerAId)) "✅ Move submitted" else "⏳ Thinking..."}
                """.trimIndent()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handleBattleStatusCommand: ${e.message}" }
            response.respond { content = "❌ Error: ${e.message}" }
        }
    }

    suspend fun handleHelpCommand(
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            response.respond {
                content = """
                    🎴 **MistarChan Bot - AI Trading Card Game**
                    
                    **📋 Commands:**
                    `/cards list` - View your collection
                    `/card <id>` - View card details  
                    `/practice [difficulty]` - 🤖 Battle AI opponents
                    `/challenge @user` - Battle other players
                    `/play <id> <attack/defense>` - Make battle moves
                    `/battle` - Check battle status
                    `/help` - Show this menu
                    
                    **🎨 Creating Cards:**
                    Upload images to create unique cards with AI-generated stats!
                    
                    **🤖 Practice Difficulties:**
                    • `easy` - 🎲 Random AI
                    • `medium` - ⚖️ Balanced AI  
                    • `hard` - 🧠 Smart AI
                    • `aggressive` - 🔥 Attack-focused
                    • `defensive` - 🛡️ Defense-focused
                    
                    **⚔️ Battle Rules:**
                    • Best of 3 rounds wins
                    • Secret move selection
                    • Attack vs Attack → Higher ATK wins
                    • Attack vs Defense → ATK must beat DEF  
                    • Defense vs Attack → DEF must stop ATK
                    • Defense vs Defense → Always ties
                    
                    **🚀 Quick Start:**
                    1. Upload 3+ images for cards
                    2. Try `/practice` to learn
                    3. Battle friends with `/challenge`
                """.trimIndent()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in handleHelpCommand: ${e.message}" }
            response.respond { content = "❌ Error: ${e.message}" }
        }
    }

    private fun getBattleCards(battle: Battle, playerId: String): List<Card> {
        return if (playerId == battle.playerAId) battle.playerACards else battle.playerBCards
    }
}

class MockUserService {
    companion object {
        val MOCK_USERS = listOf(
            "rookie_trainer" to BattlePersonality.BALANCED,
            "attack_master" to BattlePersonality.AGGRESSIVE,
            "defense_guru" to BattlePersonality.DEFENSIVE,
            "strategy_sage" to BattlePersonality.SMART,
            "chaos_lord" to BattlePersonality.CHAOTIC
        )
    }

    fun createMockUser(userId: String, personality: BattlePersonality): MockUser {
        val mockCards = generateMockCards(userId, personality)
        val name = MOCK_USERS.find { it.second == personality }?.first ?: "mock_user"
        return MockUser("mock_$userId", name, personality, mockCards)
    }

    private fun generateMockCards(userId: String, personality: BattlePersonality): List<Card> {
        val cards = mutableListOf<Card>()
        repeat(Random.nextInt(5, 8)) { index ->
            val card = when (personality) {
                BattlePersonality.AGGRESSIVE -> generateCard(userId, index, 6..10, 1..5, "Flame Warrior,Thunder Strike,Blade Master".split(","))
                BattlePersonality.DEFENSIVE -> generateCard(userId, index, 1..5, 6..10, "Iron Shield,Stone Guardian,Fortress Wall".split(","))
                BattlePersonality.BALANCED -> generateCard(userId, index, 4..7, 4..7, "Mystic Knight,Elemental Spirit,Balance Walker".split(","))
                BattlePersonality.SMART -> generateCard(userId, index, 3..9, 3..9, "Strategy Master,Chess Grandmaster,Tactical Genius".split(","))
                BattlePersonality.CHAOTIC -> generateCard(userId, index, 1..10, 1..10, "Chaos Bringer,Random Force,Wild Card".split(","))
            }
            cards.add(card)
        }
        return cards
    }

    private fun generateCard(userId: String, index: Int, atkRange: IntRange, defRange: IntRange, names: List<String>): Card {
        return Card(
            id = index + 1000,
            name = names.random(),
            imageUrl = "https://example.com/mock_$index.jpg",
            attack = atkRange.random(),
            defense = defRange.random(),
            rarity = randomRarity(),
            ownerId = userId,
            description = "AI generated card for battle practice."
        )
    }

    private fun randomRarity(): com.mrc.mistarbot.model.CardRarity = when (Random.nextInt(100)) {
        in 0..49 -> com.mrc.mistarbot.model.CardRarity.COMMON
        in 50..74 -> com.mrc.mistarbot.model.CardRarity.UNCOMMON
        in 75..89 -> com.mrc.mistarbot.model.CardRarity.RARE
        in 90..96 -> com.mrc.mistarbot.model.CardRarity.EPIC
        else -> com.mrc.mistarbot.model.CardRarity.LEGENDARY
    }

    fun chooseBattleMove(mockUser: MockUser, availableCards: List<Card>, roundNumber: Int, currentScore: Pair<Int, Int>): Pair<Card, BattlePosition> {
        val card = when (mockUser.personality) {
            BattlePersonality.AGGRESSIVE -> availableCards.maxByOrNull { it.attack } ?: availableCards.random()
            BattlePersonality.DEFENSIVE -> availableCards.maxByOrNull { it.defense } ?: availableCards.random()
            BattlePersonality.BALANCED -> availableCards.maxByOrNull { it.attack + it.defense } ?: availableCards.random()
            BattlePersonality.SMART -> {
                val (mockScore, opponentScore) = currentScore
                when {
                    mockScore < opponentScore -> availableCards.maxByOrNull { it.attack }
                    mockScore > opponentScore -> availableCards.maxByOrNull { it.defense }
                    else -> availableCards.maxByOrNull { it.attack + it.defense }
                } ?: availableCards.random()
            }
            BattlePersonality.CHAOTIC -> availableCards.random()
        }

        val position = when (mockUser.personality) {
            BattlePersonality.AGGRESSIVE -> if (Random.nextFloat() < 0.8f) BattlePosition.ATTACK else BattlePosition.DEFENSE
            BattlePersonality.DEFENSIVE -> if (Random.nextFloat() < 0.8f) BattlePosition.DEFENSE else BattlePosition.ATTACK
            BattlePersonality.BALANCED -> if (card.attack > card.defense) BattlePosition.ATTACK else BattlePosition.DEFENSE
            BattlePersonality.SMART -> {
                val (mockScore, opponentScore) = currentScore
                when {
                    roundNumber == 3 && mockScore == opponentScore -> BattlePosition.ATTACK
                    mockScore < opponentScore -> if (Random.nextFloat() < 0.7f) BattlePosition.ATTACK else BattlePosition.DEFENSE
                    else -> if (card.attack >= card.defense) BattlePosition.ATTACK else BattlePosition.DEFENSE
                }
            }
            BattlePersonality.CHAOTIC -> if (Random.nextBoolean()) BattlePosition.ATTACK else BattlePosition.DEFENSE
        }

        return Pair(card, position)
    }

    fun getMockUserDisplayName(mockUser: MockUser): String {
        val emoji = when (mockUser.personality) {
            BattlePersonality.AGGRESSIVE -> "🔥"
            BattlePersonality.DEFENSIVE -> "🛡️"
            BattlePersonality.BALANCED -> "⚖️"
            BattlePersonality.SMART -> "🧠"
            BattlePersonality.CHAOTIC -> "🎲"
        }
        return "$emoji ${mockUser.name.split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }}"
    }

    fun getTrashTalkMessage(mockUser: MockUser, context: String): String {
        val messages = when (mockUser.personality) {
            BattlePersonality.AGGRESSIVE -> listOf("🔥 Time to heat things up!", "⚔️ Prepare for destruction!", "💥 This ends now!")
            BattlePersonality.DEFENSIVE -> listOf("🛡️ Unbreakable defense!", "🏰 None shall pass!", "⚖️ Patience wins wars.")
            BattlePersonality.BALANCED -> listOf("⚖️ Harmony prevails.", "🧘 Balance in all things.", "🎯 Calculated moves.")
            BattlePersonality.SMART -> listOf("🧠 Victory calculated.", "🔍 Moves predicted.", "📊 Odds favor me.")
            BattlePersonality.CHAOTIC -> listOf("🎲 Chaos unleashed!", "🌀 Expect madness!", "🃏 Wild card time!")
        }
        return messages.random()
    }
}