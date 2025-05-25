package com.mrc.mistarbot.service

import com.mrc.mistarbot.game.BattlePosition
import com.mrc.mistarbot.model.Card
import com.mrc.mistarbot.model.CardRarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

class CardImageGenerator {

    companion object {
        const val CARD_WIDTH = 400
        const val CARD_HEIGHT = 600
        const val IMAGE_AREA_HEIGHT = 280
        const val IMAGE_AREA_WIDTH = 340
        const val IMAGE_X = 30
        const val IMAGE_Y = 80

        // Battle scene constants
        const val BATTLE_SCENE_WIDTH = 1000
        const val BATTLE_SCENE_HEIGHT = 700
        const val BATTLE_CARD_WIDTH = 300
        const val BATTLE_CARD_HEIGHT = 450
    }

    enum class ArenaStyle(val filename: String, val displayName: String) {
        MYSTICAL("/battle_arena_mystical.png", "Mystical Colosseum"),
        NATURAL("/battle_arena_natural.png", "Sunny Meadow"),
        CYBERPUNK("/battle_arena_cyberpunk.png", "Neon Tech Arena"),
        DESERT("/battle_arena_desert.png", "Ancient Desert"),
        ICE("/battle_arena_ice.png", "Crystal Tundra")
    }

    private val arenaCache = mutableMapOf<ArenaStyle, BufferedImage>()

    private fun loadBattleBackground(style: ArenaStyle): BufferedImage {
        return arenaCache.getOrPut(style) {
            try {
                val backgroundStream = javaClass.getResourceAsStream(style.filename)
                if (backgroundStream != null) {
                    val image = ImageIO.read(backgroundStream)
                    logger.info { "✅ Loaded arena: ${style.displayName}" }
                    image
                } else {
                    logger.warn { "⚠️ Arena not found: ${style.displayName}, using default" }
                    createDefaultBackground()
                }
            } catch (e: Exception) {
                logger.error(e) { "❌ Failed to load arena: ${style.displayName}" }
                createDefaultBackground()
            }
        }
    }

    // Updated battle scene method
    suspend fun generateBattleScene(
        playerCard: Card,
        playerPosition: BattlePosition,
        opponentCard: Card,
        opponentPosition: BattlePosition,
        roundNumber: Int,
        winner: String? = null,
        isRevealed: Boolean = true,
        playerName: String = "You",
        opponentName: String = "Opponent",
        arenaStyle: ArenaStyle? = null // null = random
    ): ByteArray = withContext(Dispatchers.IO) {

        // Random arena selection if not specified
        val selectedArena = arenaStyle ?: ArenaStyle.entries.toTypedArray().random()

        logger.info { "⚔️ Battle in ${selectedArena.displayName}: ${playerCard.name} vs ${opponentCard.name}" }

        try {
            val battleImage = BufferedImage(BATTLE_SCENE_WIDTH, BATTLE_SCENE_HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g2d = battleImage.createGraphics()
            setupRenderingHints(g2d)

            // Draw selected arena background
            val background = loadBattleBackground(selectedArena)
            g2d.drawImage(background, 0, 0, BATTLE_SCENE_WIDTH, BATTLE_SCENE_HEIGHT, null)

            setupRenderingHints(g2d)

            // Draw card components
            // Card positions
            val leftCardX = 50
            val rightCardX = BATTLE_SCENE_WIDTH - BATTLE_CARD_WIDTH - 50
            val cardY = (BATTLE_SCENE_HEIGHT - BATTLE_CARD_HEIGHT) / 2

            // Draw player card (left)
            drawBattleCard(
                g2d, playerCard, leftCardX, cardY, playerPosition,
                isRevealed = true,
                isWinner = winner == "player",
                isLoser = winner != null && winner != "player"
            )

            // Draw opponent card (right)
            drawBattleCard(
                g2d, opponentCard, rightCardX, cardY, opponentPosition,
                isRevealed = isRevealed,
                isWinner = winner == "opponent",
                isLoser = winner != null && winner != "opponent"
            )

            g2d.dispose()

            val outputStream = ByteArrayOutputStream()
            ImageIO.write(battleImage, "PNG", outputStream)
            val result = outputStream.toByteArray()

            logger.info { "✅ Battle scene generated in ${selectedArena.displayName} (${result.size} bytes)" }
            result

        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to generate battle scene" }
            throw e
        }
    }

    // Existing generateCardImage method...
    suspend fun generateCardImage(card: Card, originalImageUrl: String): ByteArray = withContext(Dispatchers.IO) {
        logger.info { "🎨 Generating visual card for: ${card.name}" }

        try {
            val cardImage = BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g2d = cardImage.createGraphics()

            setupRenderingHints(g2d)
            drawBackground(g2d, card.rarity)
            drawCardFrame(g2d, card.rarity)
            drawUserImage(g2d, originalImageUrl)
            drawCardTitle(g2d, card.name, card.rarity)
            drawStatsSection(g2d, card)
            drawCardDescription(g2d, card.description)

            g2d.dispose()

            val outputStream = ByteArrayOutputStream()
            ImageIO.write(cardImage, "PNG", outputStream)
            val result = outputStream.toByteArray()

            logger.info { "✅ Card image generated (${result.size} bytes)" }
            result

        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to generate card image" }
            throw e
        }
    }


    private fun drawBattleArena(g2d: Graphics2D) {
        // Arena floor
        g2d.color = Color(40, 40, 60)
        g2d.fillOval(100, BATTLE_SCENE_HEIGHT - 200, BATTLE_SCENE_WIDTH - 200, 150)

        // Arena border
        g2d.color = Color(80, 80, 100)
        g2d.stroke = BasicStroke(4f)
        g2d.drawOval(100, BATTLE_SCENE_HEIGHT - 200, BATTLE_SCENE_WIDTH - 200, 150)

        // Center line
        g2d.color = Color(100, 100, 120)
        g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(10f, 10f), 0f)
        g2d.drawLine(BATTLE_SCENE_WIDTH / 2, 100, BATTLE_SCENE_WIDTH / 2, BATTLE_SCENE_HEIGHT - 100)
    }

    private fun drawBattleCard(
        g2d: Graphics2D,
        card: Card,
        x: Int,
        y: Int,
        position: BattlePosition,
        isRevealed: Boolean,
        isWinner: Boolean,
        isLoser: Boolean = false
    ) {
        // Card shadow
        g2d.color = Color(0, 0, 0, 60)
        g2d.fillRoundRect(x + 5, y + 5, BATTLE_CARD_WIDTH, BATTLE_CARD_HEIGHT, 20, 20)

        if (!isRevealed) {
            // Draw card back
            drawCardBack(g2d, x, y)
            return
        }

        // Winner glow effect
        if (isWinner) {
            g2d.color = Color(255, 215, 0, 100) // Gold glow
            g2d.fillRoundRect(x - 10, y - 10, BATTLE_CARD_WIDTH + 20, BATTLE_CARD_HEIGHT + 20, 30, 30)
        }

        // Scale down the regular card drawing
        val scale = 0.75f
        val scaledWidth = (CARD_WIDTH * scale).toInt()
        val scaledHeight = (CARD_HEIGHT * scale).toInt()
        val offsetX = (BATTLE_CARD_WIDTH - scaledWidth) / 2
        val offsetY = (BATTLE_CARD_HEIGHT - scaledHeight) / 2

        // Create a mini version of the card
        val miniCard = BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB)
        val cardG2d = miniCard.createGraphics()
        setupRenderingHints(cardG2d)

        // Scale graphics
        cardG2d.scale(scale.toDouble(), scale.toDouble())

        // Draw card components
        drawBackground(cardG2d, card.rarity)
        drawCardFrame(cardG2d, card.rarity)
        drawUserImage(cardG2d, card.imageUrl)
        drawCardTitle(cardG2d, card.name, card.rarity)
        drawStatsSection(cardG2d, card)

        cardG2d.dispose()

        // Apply grayscale effect for losing card
        val finalCard = if (isLoser) {
            applyGrayscaleEffect(miniCard)
        } else {
            miniCard
        }

        // Draw the mini card on battle scene
        g2d.drawImage(finalCard, x + offsetX, y + offsetY, null)

        // Position indicator
        drawPositionIndicator(g2d, x, y, position, isWinner, isLoser)
    }

    private fun applyGrayscaleEffect(originalImage: BufferedImage): BufferedImage {
        val grayscaleImage = BufferedImage(
            originalImage.width,
            originalImage.height,
            BufferedImage.TYPE_INT_RGB
        )

        val g2d = grayscaleImage.createGraphics()
        setupRenderingHints(g2d)

        // Apply grayscale filter
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
        g2d.drawImage(originalImage, 0, 0, null)

        // Add dark overlay for defeated effect
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f)
        g2d.color = Color.GRAY
        g2d.fillRect(0, 0, originalImage.width, originalImage.height)

        g2d.dispose()
        return grayscaleImage
    }

    private fun drawCardBack(g2d: Graphics2D, x: Int, y: Int) {
        // Card back with question mark
        g2d.color = Color(60, 60, 80)
        g2d.fillRoundRect(x, y, BATTLE_CARD_WIDTH, BATTLE_CARD_HEIGHT, 20, 20)

        g2d.color = Color(100, 100, 120)
        g2d.stroke = BasicStroke(4f)
        g2d.drawRoundRect(x, y, BATTLE_CARD_WIDTH, BATTLE_CARD_HEIGHT, 20, 20)

        // Question mark
        g2d.color = Color(200, 200, 220)
        g2d.font = Font("Arial", Font.BOLD, 80)
        val fm = g2d.fontMetrics
        val questionX = x + (BATTLE_CARD_WIDTH - fm.stringWidth("?")) / 2
        val questionY = y + (BATTLE_CARD_HEIGHT + fm.height) / 2
        g2d.drawString("?", questionX, questionY)
    }

    private fun drawPositionIndicator(
        g2d: Graphics2D,
        x: Int,
        y: Int,
        position: BattlePosition,
        isWinner: Boolean,
        isLoser: Boolean = false
    ) {
        val indicatorY = y + BATTLE_CARD_HEIGHT + 10
        val indicatorColor = when {
            isWinner -> Color.YELLOW
            isLoser -> Color.GRAY
            else -> Color.WHITE
        }

        g2d.color = indicatorColor
        g2d.font = Font("Arial", Font.BOLD, 24) // Larger font for emojis

        // We remove emoji for now. not working
        val emoji = when (position) {
            BattlePosition.ATTACK -> ""
            BattlePosition.DEFENSE -> ""
        }

        val fm = g2d.fontMetrics
        val textX = x + (BATTLE_CARD_WIDTH - fm.stringWidth(emoji)) / 2
        g2d.drawString(emoji, textX, indicatorY + 30)

        // Add position text below emoji
        g2d.font = Font("Arial", Font.BOLD, 12)
        val positionText = position.name
        val posFm = g2d.fontMetrics
        val posX = x + (BATTLE_CARD_WIDTH - posFm.stringWidth(positionText)) / 2
        g2d.drawString(positionText, posX, indicatorY + 50)
    }

    private fun drawBattleCenter(
        g2d: Graphics2D,
        roundNumber: Int,
        winner: String?,
        playerPosition: BattlePosition,
        opponentPosition: BattlePosition,
        playerName: String,
        opponentName: String
    ) {
        val centerX = BATTLE_SCENE_WIDTH / 2
        val centerY = BATTLE_SCENE_HEIGHT / 2

        // VS Text background
        g2d.color = Color(0, 0, 0, 150)
        g2d.fillOval(centerX - 80, centerY - 80, 160, 160)

        // VS border
        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(4f)
        g2d.drawOval(centerX - 80, centerY - 80, 160, 160)

        // VS Text
        g2d.color = Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 48)
        val vsText = "VS"
        val fm = g2d.fontMetrics
        val vsX = centerX - fm.stringWidth(vsText) / 2
        val vsY = centerY + fm.height / 3
        g2d.drawString(vsText, vsX, vsY)

        // Round number
        g2d.font = Font("Arial", Font.BOLD, 20)
        val roundText = "Round $roundNumber"
        val roundFm = g2d.fontMetrics
        val roundX = centerX - roundFm.stringWidth(roundText) / 2
        g2d.drawString(roundText, roundX, centerY - 100)

        // Combat type indicator - REMOVED redundant VS text

        // Winner indicator with actual names
        if (winner != null) {
            g2d.color = Color.YELLOW
            g2d.font = Font("Arial", Font.BOLD, 16)
            val winText = when (winner) {
                "player" -> "$playerName WINS!"
                "opponent" -> "$opponentName WINS!"
                else -> "TIE!"
            }
            val winFm = g2d.fontMetrics
            val winX = centerX - winFm.stringWidth(winText) / 2
            g2d.drawString(winText, winX, centerY + 100)
        }
    }

    // Removed positionEmoji function - no longer needed

    // Existing helper methods remain the same...
    private fun setupRenderingHints(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    private fun drawBackground(g2d: Graphics2D, rarity: CardRarity) {
        val (topColor, bottomColor) = when (rarity) {
            CardRarity.COMMON -> Color(240, 240, 245) to Color(200, 200, 210)
            CardRarity.UNCOMMON -> Color(230, 255, 230) to Color(180, 230, 180)
            CardRarity.RARE -> Color(220, 240, 255) to Color(160, 200, 255)
            CardRarity.EPIC -> Color(255, 230, 255) to Color(220, 180, 255)
            CardRarity.LEGENDARY -> Color(255, 250, 200) to Color(230, 200, 120)
        }

        val gradient = GradientPaint(0f, 0f, topColor, 0f, CARD_HEIGHT.toFloat(), bottomColor)
        g2d.paint = gradient
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT)
    }

    private fun drawCardFrame(g2d: Graphics2D, rarity: CardRarity) {
        val frameColor = when (rarity) {
            CardRarity.COMMON -> Color(120, 120, 120)
            CardRarity.UNCOMMON -> Color(60, 160, 60)
            CardRarity.RARE -> Color(60, 60, 200)
            CardRarity.EPIC -> Color(180, 60, 180)
            CardRarity.LEGENDARY -> Color(220, 180, 60)
        }

        g2d.color = frameColor
        g2d.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2d.drawRoundRect(5, 5, CARD_WIDTH - 10, CARD_HEIGHT - 10, 20, 20)

        g2d.color = frameColor.brighter()
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(15, 15, CARD_WIDTH - 30, CARD_HEIGHT - 30, 15, 15)
    }

    private fun drawUserImage(g2d: Graphics2D, imageUrl: String) {
        try {
            val originalImage = ImageIO.read(URL(imageUrl))
            val scaledImage = originalImage.getScaledInstance(IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, Image.SCALE_SMOOTH)

            val originalClip = g2d.clip
            val roundRect = RoundRectangle2D.Float(
                IMAGE_X.toFloat(), IMAGE_Y.toFloat(),
                IMAGE_AREA_WIDTH.toFloat(), IMAGE_AREA_HEIGHT.toFloat(),
                15f, 15f
            )
            g2d.clip = roundRect
            g2d.drawImage(scaledImage, IMAGE_X, IMAGE_Y, null)
            g2d.clip = originalClip

            g2d.color = Color.BLACK
            g2d.stroke = BasicStroke(3f)
            g2d.drawRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)

        } catch (e: Exception) {
            drawImagePlaceholder(g2d)
        }
    }

    private fun drawImagePlaceholder(g2d: Graphics2D) {
        g2d.color = Color(60, 60, 60)
        g2d.fillRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)

        g2d.color = Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 24)
        val text = "IMAGE"
        val fm = g2d.fontMetrics
        val textX = IMAGE_X + (IMAGE_AREA_WIDTH - fm.stringWidth(text)) / 2
        val textY = IMAGE_Y + (IMAGE_AREA_HEIGHT + fm.height) / 2
        g2d.drawString(text, textX, textY)

        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(3f)
        g2d.drawRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)
    }

    private fun drawCardTitle(g2d: Graphics2D, name: String, rarity: CardRarity) {
        val titleY = 50

        val titleColor = when (rarity) {
            CardRarity.COMMON -> Color(200, 200, 200)
            CardRarity.UNCOMMON -> Color(180, 220, 180)
            CardRarity.RARE -> Color(180, 200, 240)
            CardRarity.EPIC -> Color(220, 180, 240)
            CardRarity.LEGENDARY -> Color(230, 200, 120)
        }

        g2d.color = titleColor
        g2d.fillRoundRect(20, titleY - 30, CARD_WIDTH - 40, 40, 10, 10)

        g2d.color = titleColor.darker()
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(20, titleY - 30, CARD_WIDTH - 40, 40, 10, 10)

        g2d.color = Color.BLACK
        g2d.font = Font("Arial", Font.BOLD, 18)

        var cardName = name
        val fm = g2d.fontMetrics

        while (fm.stringWidth(cardName) > CARD_WIDTH - 60 && cardName.length > 1) {
            cardName = cardName.dropLast(1)
        }

        val nameX = (CARD_WIDTH - fm.stringWidth(cardName)) / 2
        g2d.drawString(cardName, nameX, titleY - 5)
    }

    private fun drawStatsSection(g2d: Graphics2D, card: Card) {
        val statsY = CARD_HEIGHT - 120

        g2d.color = Color(240, 240, 240, 230)
        g2d.fillRoundRect(20, statsY, CARD_WIDTH - 40, 80, 15, 15)

        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(20, statsY, CARD_WIDTH - 40, 80, 15, 15)

        drawStatBox(g2d, "Attack", card.attack.toString(), 40, statsY + 20, Color.LIGHT_GRAY)
        drawStatBox(g2d, "Defense", card.defense.toString(), CARD_WIDTH - 120, statsY + 20, Color.LIGHT_GRAY)
        drawRarityBadgeInCenter(g2d, card.rarity, statsY + 30)
    }

    private fun drawRarityBadgeInCenter(g2d: Graphics2D, rarity: CardRarity, centerY: Int) {
        val rarityText = rarity.name
        val badgeColor = when (rarity) {
            CardRarity.COMMON -> Color.GRAY
            CardRarity.UNCOMMON -> Color.GREEN
            CardRarity.RARE -> Color.BLUE
            CardRarity.EPIC -> Color.MAGENTA
            CardRarity.LEGENDARY -> Color.ORANGE
        }

        g2d.font = Font("Arial", Font.BOLD, 11)
        val fm = g2d.fontMetrics
        val textWidth = fm.stringWidth(rarityText)
        val badgeWidth = textWidth + 16
        val badgeHeight = 20
        val badgeX = (CARD_WIDTH - badgeWidth) / 2
        val badgeY = centerY - badgeHeight / 2

        val lightColor = badgeColor.brighter()
        val darkColor = badgeColor.darker()
        val gradient = GradientPaint(
            badgeX.toFloat(), badgeY.toFloat(), lightColor,
            badgeX.toFloat(), (badgeY + badgeHeight).toFloat(), darkColor
        )
        g2d.paint = gradient
        g2d.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 10, 10)

        g2d.color = badgeColor.darker().darker()
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 10, 10)

        g2d.color = lightColor.brighter()
        g2d.stroke = BasicStroke(1f)
        g2d.drawRoundRect(badgeX + 1, badgeY + 1, badgeWidth - 2, badgeHeight - 2, 8, 8)

        g2d.color = Color.BLACK
        val textX = badgeX + (badgeWidth - textWidth) / 2
        val textY = badgeY + (badgeHeight + fm.ascent) / 2 - 1

        g2d.drawString(rarityText, textX + 1, textY + 1)

        g2d.color = Color.WHITE
        g2d.drawString(rarityText, textX, textY)

        g2d.paint = Color.BLACK
    }

    private fun drawStatBox(g2d: Graphics2D, label: String, value: String, x: Int, y: Int, backgroundColor: Color) {
        val boxWidth = 80
        val boxHeight = 40

        g2d.color = Color(220, 220, 220)
        g2d.fillRoundRect(x, y, boxWidth, boxHeight, 8, 8)

        g2d.color = Color(120, 120, 120)
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(x, y, boxWidth, boxHeight, 8, 8)

        g2d.color = Color.BLACK
        g2d.font = Font("Arial", Font.BOLD, 12)

        val labelFm = g2d.fontMetrics
        val labelX = x + (boxWidth - labelFm.stringWidth(label)) / 2
        g2d.drawString(label, labelX, y + 15)

        g2d.font = Font("Arial", Font.BOLD, 18)
        val valueFm = g2d.fontMetrics
        val valueX = x + (boxWidth - valueFm.stringWidth(value)) / 2
        g2d.drawString(value, valueX, y + 32)
    }

    private fun drawCardDescription(g2d: Graphics2D, description: String?) {
        if (description.isNullOrBlank()) return

        val descY = 390
        g2d.color = Color.BLACK
        g2d.font = Font("Arial", Font.ITALIC, 12)
        val fm = g2d.fontMetrics

        val words = description.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (fm.stringWidth(testLine) <= CARD_WIDTH - 60) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = word
                } else {
                    currentLine = word.take(20) + "..."
                    lines.add(currentLine)
                    currentLine = ""
                }

                if (lines.size >= 2) break
            }
        }

        if (currentLine.isNotEmpty() && lines.size < 2) {
            lines.add(currentLine)
        }

        lines.forEachIndexed { index, line ->
            val lineY = descY + (index * (fm.height + 2))
            val lineX = (CARD_WIDTH - fm.stringWidth(line)) / 2
            g2d.drawString(line, lineX, lineY)
        }
    }

    private var cachedBackground: BufferedImage? = null

    private fun loadBattleBackground(): BufferedImage {
        if (cachedBackground == null) {
            try {
                // Load from resources folder
                val backgroundStream = javaClass.getResourceAsStream("/battle_arena_natural.png")
                if (backgroundStream != null) {
                    cachedBackground = ImageIO.read(backgroundStream)
                    logger.info { "✅ Battle arena background loaded successfully" }
                } else {
                    logger.warn { "⚠️ Battle arena background not found, using default" }
                    // Create a simple gradient background as fallback
                    cachedBackground = createDefaultBackground()
                }
            } catch (e: Exception) {
                logger.error(e) { "❌ Failed to load battle arena background" }
                cachedBackground = createDefaultBackground()
            }
        }
        return cachedBackground!!
    }

    private fun createDefaultBackground(): BufferedImage {
        val bg = BufferedImage(BATTLE_SCENE_WIDTH, BATTLE_SCENE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g2d = bg.createGraphics()
        setupRenderingHints(g2d)

        // Default gradient background
        val bgGradient = GradientPaint(
            0f, 0f, Color(20, 20, 40),
            0f, BATTLE_SCENE_HEIGHT.toFloat(), Color(60, 60, 80)
        )
        g2d.paint = bgGradient
        g2d.fillRect(0, 0, BATTLE_SCENE_WIDTH, BATTLE_SCENE_HEIGHT)

        g2d.dispose()
        return bg
    }
}