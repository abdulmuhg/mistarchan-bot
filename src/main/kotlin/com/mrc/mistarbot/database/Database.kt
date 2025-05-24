package com.mrc.mistarbot.database

import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
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
            SchemaUtils.drop(Cards)
            SchemaUtils.create(Cards)
            logger.info { "Database initialized with updated schema" }
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
}