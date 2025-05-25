package com.mrc.mistarbot.game

import com.mrc.mistarbot.model.Card
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

enum class BattlePosition {
    ATTACK,
    DEFENSE
}

enum class BattleState {
    WAITING_FOR_PLAYERS,    // Battle created, waiting for moves
    ROUND_IN_PROGRESS,      // Players submitting moves
    ROUND_COMPLETE,         // Round resolved, waiting for next
    BATTLE_COMPLETE         // Battle finished
}

@Serializable
data class BattleMove(
    val playerId: String,
    val card: Card,
    val position: BattlePosition,
    val roundNumber: Int
)

@Serializable
data class RoundResult(
    val roundNumber: Int,
    val winner: String?, // Discord user ID of the winner, null if tie
    val playerAMove: BattleMove,
    val playerBMove: BattleMove,
    val description: String,
    val pointsAwarded: Int // 1 for win, 0 for tie
)

@Serializable
data class BattleResult(
    val winner: String?, // Overall battle winner
    val playerAScore: Int,
    val playerBScore: Int,
    val rounds: List<RoundResult>,
    val battleEndReason: String
)

class Battle(
    val battleId: String,
    val playerAId: String,
    val playerBId: String,
    val playerACards: List<Card>,
    val playerBCards: List<Card>,
    val channelId: String
) {
    private val rounds = mutableListOf<RoundResult>()
    private val usedCards = mutableSetOf<Int>() // Track cards that have been used
    private val pendingMoves = mutableMapOf<String, BattleMove>() // Current round moves
    private var currentRound = 1
    var state = BattleState.WAITING_FOR_PLAYERS
        private set

    init {
        logger.info { "🥊 New battle created: $battleId between $playerAId and $playerBId" }
    }

    /**
     * Submit a move for the current round
     * Returns BattleUpdateResult indicating what happened
     */
    fun submitMove(playerId: String, cardId: Int, position: BattlePosition): BattleUpdateResult {
        logger.info { "🎯 Move submitted by $playerId: card $cardId in $position position" }

        // Validation checks
        if (state == BattleState.BATTLE_COMPLETE) {
            return BattleUpdateResult.Error("Battle is already complete!")
        }

        if (cardId in usedCards) {
            return BattleUpdateResult.Error("Card $cardId has already been used in this battle!")
        }

        // Check if player already submitted move for this round
        if (pendingMoves.containsKey(playerId)) {
            return BattleUpdateResult.Error("You already submitted a move for this round!")
        }

        // Verify card ownership and availability
        val playerCards = if (playerId == playerAId) playerACards else playerBCards
        val card = playerCards.find { it.id == cardId }
            ?: return BattleUpdateResult.Error("Card $cardId not found in your battle deck!")

        // Submit the move
        val move = BattleMove(playerId, card, position, currentRound)
        pendingMoves[playerId] = move
        usedCards.add(cardId)

        logger.info { "✅ Move accepted from $playerId" }

        // Check if both players have submitted moves
        if (pendingMoves.size == 2) {
            return resolvePendingRound()
        }

        // Waiting for other player
        state = BattleState.ROUND_IN_PROGRESS
        return BattleUpdateResult.MoveAccepted("Move submitted! Waiting for opponent...")
    }

    /**
     * Resolve the current round when both players have submitted moves
     */
    private fun resolvePendingRound(): BattleUpdateResult {
        logger.info { "🔥 Resolving round $currentRound" }

        val playerAMove = pendingMoves[playerAId]!!
        val playerBMove = pendingMoves[playerBId]!!

        val roundResult = resolveRound(playerAMove, playerBMove)
        rounds.add(roundResult)

        // Clear pending moves for next round
        pendingMoves.clear()

        // Check if battle is complete
        val battleResult = checkBattleComplete()
        if (battleResult != null) {
            state = BattleState.BATTLE_COMPLETE
            logger.info { "🏆 Battle complete! Winner: ${battleResult.winner}" }
            return BattleUpdateResult.BattleComplete(roundResult, battleResult)
        }

        // Prepare for next round
        currentRound++
        state = BattleState.WAITING_FOR_PLAYERS
        logger.info { "▶️ Moving to round $currentRound" }

        return BattleUpdateResult.RoundComplete(roundResult, currentRound)
    }

    /**
     * Resolve a single round between two moves
     */
    private fun resolveRound(playerAMove: BattleMove, playerBMove: BattleMove): RoundResult {
        val winner = when {
            // Attack vs Attack - higher attack wins
            playerAMove.position == BattlePosition.ATTACK &&
                    playerBMove.position == BattlePosition.ATTACK -> {
                when {
                    playerAMove.card.attack > playerBMove.card.attack -> playerAId
                    playerBMove.card.attack > playerAMove.card.attack -> playerBId
                    else -> null // Tie
                }
            }

            // Attack vs Defense - attack wins if attack > defense
            playerAMove.position == BattlePosition.ATTACK &&
                    playerBMove.position == BattlePosition.DEFENSE -> {
                if (playerAMove.card.attack > playerBMove.card.defense) playerAId else playerBId
            }

            // Defense vs Attack - defense wins if defense >= attack
            playerAMove.position == BattlePosition.DEFENSE &&
                    playerBMove.position == BattlePosition.ATTACK -> {
                if (playerBMove.card.attack > playerAMove.card.defense) playerBId else playerAId
            }

            // Defense vs Defense - always a tie
            else -> null
        }

        val pointsAwarded = if (winner != null) 1 else 0

        val description = buildRoundDescription(playerAMove, playerBMove, winner)

        return RoundResult(
            roundNumber = currentRound,
            winner = winner,
            playerAMove = playerAMove,
            playerBMove = playerBMove,
            description = description,
            pointsAwarded = pointsAwarded
        )
    }

    /**
     * Build descriptive text for round result
     */
    private fun buildRoundDescription(
        playerAMove: BattleMove,
        playerBMove: BattleMove,
        winner: String?
    ): String {
        val playerACard = playerAMove.card
        val playerBCard = playerBMove.card

        return buildString {
            appendLine("**Round $currentRound Results:**")
            appendLine("🔸 **${playerACard.name}** (${playerAMove.position}) - ATK: ${playerACard.attack}, DEF: ${playerACard.defense}")
            appendLine("🔹 **${playerBCard.name}** (${playerBMove.position}) - ATK: ${playerBCard.attack}, DEF: ${playerBCard.defense}")
            appendLine()

            when {
                winner == playerAId -> appendLine("🏆 **Player A wins this round!**")
                winner == playerBId -> appendLine("🏆 **Player B wins this round!**")
                else -> appendLine("🤝 **Round ends in a tie!**")
            }

            // Add combat explanation
            when {
                playerAMove.position == BattlePosition.ATTACK && playerBMove.position == BattlePosition.ATTACK -> {
                    appendLine("⚔️ *Attack vs Attack: ${playerACard.attack} vs ${playerBCard.attack}*")
                }

                playerAMove.position == BattlePosition.ATTACK && playerBMove.position == BattlePosition.DEFENSE -> {
                    appendLine("⚔️🛡️ *Attack vs Defense: ${playerACard.attack} vs ${playerBCard.defense}*")
                }

                playerAMove.position == BattlePosition.DEFENSE && playerBMove.position == BattlePosition.ATTACK -> {
                    appendLine("🛡️⚔️ *Defense vs Attack: ${playerACard.defense} vs ${playerBCard.attack}*")
                }

                else -> {
                    appendLine("🛡️🛡️ *Defense vs Defense: Always a tie*")
                }
            }
        }
    }

    /**
     * Check if battle should end and return result
     */
    private fun checkBattleComplete(): BattleResult? {
        val playerAWins = rounds.count { it.winner == playerAId }
        val playerBWins = rounds.count { it.winner == playerBId }

        // Best of 3 - first to 2 wins
        val winner = when {
            playerAWins >= 2 -> playerAId
            playerBWins >= 2 -> playerBId
            rounds.size >= 3 -> {
                // All 3 rounds played, determine winner by score
                when {
                    playerAWins > playerBWins -> playerAId
                    playerBWins > playerAWins -> playerBId
                    else -> null // Actual tie
                }
            }

            else -> null // Battle continues
        }

        if (winner != null || rounds.size >= 3) {
            val endReason = when {
                winner != null && (playerAWins >= 2 || playerBWins >= 2) -> "First to 2 round wins"
                winner != null -> "Higher score after 3 rounds"
                else -> "Tie game after 3 rounds"
            }

            return BattleResult(
                winner = winner,
                playerAScore = playerAWins,
                playerBScore = playerBWins,
                rounds = rounds.toList(),
                battleEndReason = endReason
            )
        }

        return null
    }

    // Getters for current battle state
    fun getCurrentRound(): Int = currentRound
    fun getPlayerAScore(): Int = rounds.count { it.winner == playerAId }
    fun getPlayerBScore(): Int = rounds.count { it.winner == playerBId }
    fun getRoundsPlayed(): Int = rounds.size
    fun getRoundsLeft(): Int = 3 - rounds.size
    fun hasPendingMove(playerId: String): Boolean = pendingMoves.containsKey(playerId)
    fun getAvailableCards(playerId: String): List<Card> {
        val playerCards = if (playerId == playerAId) playerACards else playerBCards
        return playerCards.filter { it.id !in usedCards }
    }

    /**
     * Get current battle status for display
     */
    fun getBattleStatus(): String = buildString {
        appendLine("⚔️ **Battle Status** ⚔️")
        appendLine("📊 **Score:** Player A: ${getPlayerAScore()} | Player B: ${getPlayerBScore()}")
        appendLine("🎯 **Round:** $currentRound/3")

        when (state) {
            BattleState.WAITING_FOR_PLAYERS -> {
                appendLine("⏳ **Waiting for moves from both players**")
            }

            BattleState.ROUND_IN_PROGRESS -> {
                val waitingFor = if (hasPendingMove(playerAId)) "Player B" else "Player A"
                appendLine("⏳ **Waiting for move from $waitingFor**")
            }

            BattleState.ROUND_COMPLETE -> {
                appendLine("✅ **Round complete, preparing next round**")
            }

            BattleState.BATTLE_COMPLETE -> {
                appendLine("🏁 **Battle Complete!**")
            }
        }
    }
}

/**
 * Result of a battle operation (move submission, round resolution, etc.)
 */
sealed class BattleUpdateResult {
    data class MoveAccepted(val message: String) : BattleUpdateResult()
    data class RoundComplete(val roundResult: RoundResult, val nextRound: Int) : BattleUpdateResult()
    data class BattleComplete(val finalRound: RoundResult, val battleResult: BattleResult) : BattleUpdateResult()
    data class Error(val message: String) : BattleUpdateResult()
}