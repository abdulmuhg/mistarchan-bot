package com.mrc.mistarbot.database

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database as ExposedDatabase

private val logger = KotlinLogging.logger {}

object Cards : IntIdTable() {
    val name = varchar("name", 255)
    val imageUrl = varchar("image_url", 1024)
    val attack = integer("attack")
    val defense = integer("defense")
    val rarity = enumeration("rarity", CardRarity::class)
    val ownerId = varchar("owner_id", 64)
    val description = varchar("description", 500).nullable()
    val createdAt = long("created_at")
}

object Database {

    fun init() {
        val dbFile = "cards.db"

        ExposedDatabase.connect(
            "jdbc:sqlite:$dbFile",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.createMissingTablesAndColumns(Cards)

            // Get existing card count
            val existingCards = Cards.selectAll().count()
            logger.info { "✅ Database initialized - Found $existingCards existing cards" }
        }
    }

    fun saveCard(card: Card): Card = transaction {
        val id = Cards.insert {
            it[name] = card.name
            it[imageUrl] = card.imageUrl
            it[attack] = card.attack
            it[defense] = card.defense
            it[rarity] = card.rarity
            it[ownerId] = card.ownerId
            it[description] = card.description
            it[createdAt] = card.createdAt
        } get Cards.id

        card.copy(id = id.value)
    }

    fun resetDatabase(): Int = transaction {
        val deletedCount = Cards.selectAll().count()
        SchemaUtils.drop(Cards)
        SchemaUtils.create(Cards)
        logger.warn { "🗑️ Database reset! Deleted $deletedCount cards" }
        deletedCount.toInt()
    }

    fun getCardsByOwner(ownerId: String): List<Card> = transaction {
        Cards.select { Cards.ownerId eq ownerId }
            .map { row ->
                Card(
                    id = row[Cards.id].value,
                    name = row[Cards.name],
                    imageUrl = row[Cards.imageUrl],
                    attack = row[Cards.attack],
                    defense = row[Cards.defense],
                    rarity = row[Cards.rarity],
                    ownerId = row[Cards.ownerId],
                    description = row[Cards.description],
                    createdAt = row[Cards.createdAt]
                )
            }
    }

    // NEW: Clear all cards from database (admin only)
    fun clearAllCards(): Int = transaction {
        val deletedCount = Cards.deleteAll()
        logger.info { "🗑️ Cleared $deletedCount cards from database" }
        deletedCount
    }

    // NEW: Clear cards for specific user
    fun clearUserCards(ownerId: String): Int = transaction {
        val deletedCount = Cards.deleteWhere { Cards.ownerId eq ownerId }
        logger.info { "🗑️ Cleared $deletedCount cards for user: $ownerId" }
        deletedCount
    }

    // NEW: Get total card count for statistics
    fun getTotalCardCount(): Long = transaction {
        Cards.selectAll().count()
    }

    // NEW: Get card count by rarity (for statistics)
    fun getCardCountByRarity(): Map<CardRarity, Long> = transaction {
        CardRarity.entries.associateWith { rarity ->
            Cards.select { Cards.rarity eq rarity }.count()
        }
    }

    // NEW: Get top card owners (for leaderboards)
    fun getTopCardOwners(limit: Int = 10): List<Pair<String, Long>> = transaction {
        Cards.slice(Cards.ownerId, Cards.ownerId.count())
            .selectAll()
            .groupBy(Cards.ownerId)
            .orderBy(Cards.ownerId.count(), SortOrder.DESC)
            .limit(limit)
            .map { row ->
                row[Cards.ownerId] to row[Cards.ownerId.count()]
            }
    }
}