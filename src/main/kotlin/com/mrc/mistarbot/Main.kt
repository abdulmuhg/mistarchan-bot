package com.mrc.mistarbot

import com.mrc.mistarbot.commands.SlashCommandHandler
import com.mrc.mistarbot.database.Database
import com.mrc.mistarbot.game.Battle
import com.mrc.mistarbot.service.CardImageGenerator
import com.mrc.mistarbot.service.OpenAIService
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.input
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.addFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

private val logger = KotlinLogging.logger {}
private val activeGames = mutableMapOf<String, Battle>() // Key: channelId

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    logger.info { "Starting bot initialization..." }

    val token = System.getenv("DISCORD_TOKEN")
        ?: throw IllegalArgumentException("DISCORD_TOKEN environment variable must be set")
    logger.info { "Discord token loaded" }

    val openAiKey = System.getenv("OPENAI_API_KEY")
        ?: throw IllegalArgumentException("OPENAI_API_KEY environment variable must be set")
    logger.info { "OpenAI key loaded" }

    // Optional: Set your test guild ID for faster command registration
    val testGuildId = System.getenv("TEST_GUILD_ID") // Add this to your environment variables
    val adminUserId = System.getenv("ADMIN_USER_ID") // Your Discord user ID for shutdown commands

    Database.init()
    val openAI = OpenAIService(openAiKey)
    val commandHandler = SlashCommandHandler(activeGames)

    logger.info { "Initializing Kord..." }
    val kord = Kord(token)

    // Create a scope for the bot operations
    val botScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Set up graceful shutdown handling
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "🛑 Shutdown signal received (Ctrl+C)" }
        runBlocking {
            try {
                logger.info { "🔄 Shutting down bot gracefully..." }

                // Cancel ongoing operations
                botScope.cancel("Bot shutdown")

                // Close Discord connection with timeout
                withTimeout(5000) {
                    kord.shutdown()
                }

                logger.info { "✅ Bot disconnected successfully" }
            } catch (e: Exception) {
                logger.error(e) { "❌ Error during shutdown" }
            }
        }
    })

    // Register slash commands
    logger.info { "Registering slash commands..." }
    registerSlashCommands(kord, testGuildId)

    logger.info { "Setting up event handlers..." }

    // Handle slash command interactions
    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val response = interaction.deferPublicResponse()

        try {
            when (interaction.invokedCommandName) {
                "cards" -> commandHandler.handleCardsCommand(interaction, response)
                "card" -> commandHandler.handleCardCommand(interaction, response)
                "challenge" -> commandHandler.handleChallengeCommand(interaction, response)
                "play" -> commandHandler.handlePlayCommand(interaction, response)
                "help" -> commandHandler.handleHelpCommand(response)
                else -> {
                    response.respond {
                        content = "Unknown command: ${interaction.invokedCommandName}"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling slash command: ${interaction.invokedCommandName}" }
            response.respond {
                content = "An error occurred while processing your command. Please try again later."
            }
        }
    }

    // Handle image uploads and debug commands
    kord.on<MessageCreateEvent> {
        logger.info { "📨 MSG: content='${message.content}' author=${message.author?.username} attachments=${message.attachments.size}" }
        if (message.author?.isBot == true) return@on

        // Debug commands
        when (message.content) {
            "!test-openai" -> {
                message.channel.createMessage("OpenAI Key: ${if (openAiKey.isNotEmpty()) "✅ Loaded" else "❌ Missing"}")
            }

            "!debug" -> {
                message.channel.createMessage(
                    """
                    🔍 **Debug Info:**
                    Bot Status: ✅ Online
                    OpenAI Key: ${if (openAiKey.isNotEmpty()) "✅ Set" else "❌ Missing"}
                    Database: ✅ Connected
                    Slash Commands: ✅ Registered
                    Message Content: '${message.content}'
                    
                    Try uploading an image to create a card!
                """.trimIndent()
                )
            }

            "!ping" -> {
                val startTime = System.currentTimeMillis()
                val msg = message.channel.createMessage("🏓 Pinging...")
                val endTime = System.currentTimeMillis()
                msg.edit {
                    content = "🏓 Pong! Bot latency: ${endTime - startTime}ms"
                }
            }

            "!shutdown" -> {
                if (adminUserId != null && message.author?.id.toString() == adminUserId) {
                    message.channel.createMessage("🔄 Shutting down bot... Goodbye! 👋")
                    botScope.launch {
                        delay(2000)
                        kord.shutdown()
                    }
                }
            }
        }

        // Handle image uploads
        if (message.attachments.isNotEmpty()) {
            handleImageUpload(message, openAI)
        }
    }

    logger.info { "🚀 Bot is starting up..." }
    try {
        logger.info { "🔌 Connecting to Discord..." }
        kord.login {
            @OptIn(PrivilegedIntent::class)
            intents += Intent.MessageContent
            intents += Intent.GuildMessages
            intents += Intent.Guilds
            intents += Intent.GuildMembers
            intents += Intent.DirectMessages

            logger.info { "✨ Intents configured: ${intents.values}" }
        }
        logger.info { "✅ Bot connected successfully! Press Ctrl+C to stop." }
    } catch (e: Exception) {
        logger.error(e) { "❌ Failed to start bot" }
        throw e
    }
}

private suspend fun registerSlashCommands(kord: Kord, testGuildId: String?) {
    try {
        val commands = if (testGuildId != null) {
            logger.info { "Registering guild-specific commands for faster testing in guild: $testGuildId" }
            // Guild commands appear instantly
            kord.createGuildApplicationCommands(Snowflake(testGuildId)) {
                createCommands()
            }.toList()
        } else {
            logger.info { "Registering global commands (may take up to 1 hour to appear)" }
            // Global commands take up to 1 hour
            kord.createGlobalApplicationCommands {
                createCommands()
            }.toList()
        }

        logger.info { "Successfully registered ${commands.size} slash commands: ${commands.map { it.name }}" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to register slash commands!" }
        throw e
    }
}

private fun dev.kord.rest.builder.interaction.MultiApplicationCommandBuilder.createCommands() {
    // /cards command
    input("cards", "Manage your card collection") {
        string("action", "What to do with your cards") {
            choice("list", "list")
            required = true
        }
    }

    // /card command
    input("card", "View details of a specific card") {
        integer("id", "The ID of the card to view") {
            required = true
        }
    }

    // /challenge command
    input("challenge", "Challenge another user to a battle") {
        user("opponent", "The user you want to challenge") {
            required = true
        }
    }

    // /play command
    input("play", "Make a move in an active battle") {
        integer("card_id", "The ID of the card to play") {
            required = true
        }
        string("position", "Attack or Defense position") {
            choice("attack", "attack")
            choice("defense", "defense")
            required = true
        }
    }

    // /help command
    input("help", "Show bot commands and usage information")
}

// Replace your handleImageUpload function with this enhanced version

private suspend fun handleImageUpload(message: Message, openAI: OpenAIService) {
    logger.info { "🖼️ IMAGE UPLOAD from user: ${message.author?.id}" }

    // Log every attachment in detail
    message.attachments.forEachIndexed { i, att ->
        logger.info { "📎 Attachment $i: '${att.filename}' size=${att.size} type=${att.contentType}" }
        logger.info { "🔍 Image check: ${att.hasImageExtension()}" }
    }

    val imageAttachment = message.attachments.firstOrNull { it.hasImageExtension() }

    if (imageAttachment == null) {
        logger.warn { "❌ NO VALID IMAGE FOUND" }
        message.channel.createMessage("❌ No valid image found! Upload PNG, JPG, JPEG, GIF, or WEBP")
        return
    }

    logger.info { "✅ PROCESSING: ${imageAttachment.filename}" }
    val loadingMsg = message.channel.createMessage("🔄 Creating your card...")

    try {
        // Step 1: Generate card stats using AI
        loadingMsg.edit { content = "🤖 AI analyzing image..." }
        logger.info { "🤖 CALLING OPENAI..." }
        val card = openAI.analyzeImage(imageAttachment.url, message.author!!.id.toString())

        logger.info { "✨ CARD CREATED: ${card.name}" }
        val savedCard = Database.saveCard(card)

        // Step 2: Check if visual card generation is enabled
        val visualMode = System.getenv("VISUAL_CARDS")?.toBoolean() ?: true // Default to true for visual cards

        if (visualMode) {
            loadingMsg.edit { content = "🎨 Generating visual card..." }

            try {
                // Generate visual card
                val cardGenerator = CardImageGenerator()
                val cardImageBytes = cardGenerator.generateCardImage(savedCard, imageAttachment.url)

                logger.info { "🎨 Visual card generated (${cardImageBytes.size} bytes)" }

                // OPTION 1: Using temporary file with Path (most compatible)
                val tempFile = kotlin.io.path.createTempFile("card_${savedCard.id}", ".png")
                tempFile.writeBytes(cardImageBytes)

                // Send the final result
                loadingMsg.edit {
                    content = """
            ✨ **Visual Card Created!** ✨
            **${savedCard.name}**
            ⚔️ ATK: ${savedCard.attack} | 🛡️ DEF: ${savedCard.defense}
            ⭐ ${savedCard.rarity}
            
            🎨 **FREE Visual Card Generated!**
            
            Use `/cards list` to see your collection!
        """.trimIndent()
                }

                // Send visual card using Path
                message.channel.createMessage {
                    content = "🖼️ **Your Visual Trading Card:**"
                    addFile(tempFile)
                }

                // Clean up
                tempFile.deleteIfExists()

            } catch (e: Exception) {
                logger.error(e) { "Failed to generate visual card: ${e.message}" }

                // Fallback to text-only if visual generation fails
                loadingMsg.edit {
                    content = """
            ✨ **Card Created!** ✨
            **${savedCard.name}**
            ⚔️ ATK: ${savedCard.attack} | 🛡️ DEF: ${savedCard.defense}
            ⭐ ${savedCard.rarity}
            
            ⚠️ Visual generation failed, but your card stats are saved!
            
            Use `/cards list` to see your collection!
        """.trimIndent()
                }
            }
        } else {
            // Standard text card without visual generation
            loadingMsg.edit {
                content = """
                    ✨ **Card Created!** ✨
                    **${savedCard.name}**
                    ⚔️ ATK: ${savedCard.attack} | 🛡️ DEF: ${savedCard.defense}
                    ⭐ ${savedCard.rarity}
                    
                    Use `/cards list` to see your collection!
                """.trimIndent()
            }
        }

        logger.info { "🎉 SUCCESS! Card ID: ${savedCard.id}" }

    } catch (e: Exception) {
        logger.error(e) { "💥 ERROR creating card: ${e.message}" }
        loadingMsg.edit {
            content = "❌ Error: ${e.message ?: "Failed to create card"}"
        }
    }
}

private fun Attachment.hasImageExtension(): Boolean {
    val result = filename.endsWith(".png", ignoreCase = true) ||
            filename.endsWith(".jpg", ignoreCase = true) ||
            filename.endsWith(".jpeg", ignoreCase = true) ||
            filename.endsWith(".gif", ignoreCase = true) ||
            filename.endsWith(".webp", ignoreCase = true)

    logger.debug { "Checking image extension for '$filename': $result" }
    return result
}