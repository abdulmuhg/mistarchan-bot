package com.mrc.mistarbot.game

import com.mrc.mistarbot.model.Card
import kotlinx.serialization.Serializable

enum class BattlePosition {
    ATTACK,
    DEFENSE
}

@Serializable
data class BattleMove(
    val card: Card,
    val position: BattlePosition
)

@Serializable
data class RoundResult(
    val winner: String?, // Discord user ID of the winner, null if tie
    val playerAMove: BattleMove,
    val playerBMove: BattleMove,
    val description: String
)

class Battle(
    val playerAId: String,
    val playerBId: String,
    val playerACards: List<Card>,
    val playerBCards: List<Card>
) {
    private val rounds = mutableListOf<RoundResult>()
    private val usedCards = mutableSetOf<Int>() // Track cards that have been used
    
    fun submitMove(playerId: String, cardId: Int, position: BattlePosition): Boolean {
        if (cardId in usedCards) return false
        
        // Verify card ownership and availability
        val playerCards = if (playerId == playerAId) playerACards else playerBCards
        val card = playerCards.find { it.id == cardId } ?: return false
        
        usedCards.add(cardId)
        return true
    }
    
    fun resolveRound(playerAMove: BattleMove, playerBMove: BattleMove): RoundResult {
        val result = when {
            // Attack vs Attack
            playerAMove.position == BattlePosition.ATTACK && 
            playerBMove.position == BattlePosition.ATTACK -> {
                when {
                    playerAMove.card.attack > playerBMove.card.attack -> playerAId
                    playerBMove.card.attack > playerAMove.card.attack -> playerBId
                    else -> null
                }
            }
            
            // Attack vs Defense
            playerAMove.position == BattlePosition.ATTACK && 
            playerBMove.position == BattlePosition.DEFENSE -> {
                if (playerAMove.card.attack > playerBMove.card.defense) playerAId else playerBId
            }
            
            // Defense vs Attack
            playerAMove.position == BattlePosition.DEFENSE && 
            playerBMove.position == BattlePosition.ATTACK -> {
                if (playerBMove.card.attack > playerAMove.card.defense) playerBId else playerAId
            }
            
            // Defense vs Defense
            else -> null // Tie
        }
        
        val description = buildString {
            append("${playerAMove.card.name} (${playerAMove.position}) vs ")
            append("${playerBMove.card.name} (${playerBMove.position})\n")
            when (result) {
                playerAId -> append("Player A wins the round!")
                playerBId -> append("Player B wins the round!")
                else -> append("It's a tie!")
            }
        }
        
        return RoundResult(result, playerAMove, playerBMove, description).also {
            rounds.add(it)
        }
    }
    
    fun getWinner(): String? {
        val playerAWins = rounds.count { it.winner == playerAId }
        val playerBWins = rounds.count { it.winner == playerBId }
        
        return when {
            playerAWins >= 2 -> playerAId
            playerBWins >= 2 -> playerBId
            else -> null
        }
    }
    
    fun isComplete(): Boolean = rounds.size >= 3 || getWinner() != null
} 