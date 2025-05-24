package com.mrc.mistarbot.service

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
    }

    suspend fun generateCardImage(card: Card, originalImageUrl: String): ByteArray = withContext(Dispatchers.IO) {
        logger.info { "🎨 Generating visual card for: ${card.name}" }

        try {
            // Create card canvas
            val cardImage = BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g2d = cardImage.createGraphics()

            // Enable high-quality rendering
            setupRenderingHints(g2d)

            // Draw card components
            drawBackground(g2d, card.rarity)
            drawCardFrame(g2d, card.rarity)
            drawUserImage(g2d, originalImageUrl)
            drawCardTitle(g2d, card.name, card.rarity)
            drawStatsSection(g2d, card)
            drawRarityBadge(g2d, card.rarity)

            g2d.dispose()

            // Convert to bytes
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

    private fun setupRenderingHints(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    private fun drawBackground(g2d: Graphics2D, rarity: CardRarity) {
        // Gradient background based on rarity
        val (topColor, bottomColor) = when (rarity) {
            CardRarity.COMMON -> Color(240, 240, 245) to Color(200, 200, 210)
            CardRarity.UNCOMMON -> Color(230, 255, 230) to Color(180, 230, 180)
            CardRarity.RARE -> Color(230, 230, 255) to Color(180, 180, 230)
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
            CardRarity.LEGENDARY -> Color(220, 180, 60)
        }

        // Outer border
        g2d.color = frameColor
        g2d.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2d.drawRoundRect(5, 5, CARD_WIDTH - 10, CARD_HEIGHT - 10, 20, 20)

        // Inner highlight
        g2d.color = frameColor.brighter()
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(15, 15, CARD_WIDTH - 30, CARD_HEIGHT - 30, 15, 15)
    }

    private fun drawUserImage(g2d: Graphics2D, imageUrl: String) {
        try {
            logger.debug { "Loading image from: $imageUrl" }
            val originalImage = ImageIO.read(URL(imageUrl))

            // Scale image to fit the designated area
            val scaledImage = originalImage.getScaledInstance(
                IMAGE_AREA_WIDTH,
                IMAGE_AREA_HEIGHT,
                Image.SCALE_SMOOTH
            )

            // Create clipping area for rounded corners
            val originalClip = g2d.clip
            val roundRect = RoundRectangle2D.Float(
                IMAGE_X.toFloat(),
                IMAGE_Y.toFloat(),
                IMAGE_AREA_WIDTH.toFloat(),
                IMAGE_AREA_HEIGHT.toFloat(),
                15f, 15f
            )
            g2d.clip = roundRect

            // Draw the image
            g2d.drawImage(scaledImage, IMAGE_X, IMAGE_Y, null)
            g2d.clip = originalClip

            // Add border around image
            g2d.color = Color.BLACK
            g2d.stroke = BasicStroke(3f)
            g2d.drawRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)

        } catch (e: Exception) {
            logger.warn(e) { "Failed to load user image, using placeholder" }
            drawImagePlaceholder(g2d)
        }
    }

    private fun drawImagePlaceholder(g2d: Graphics2D) {
        // Dark placeholder background
        g2d.color = Color(60, 60, 60)
        g2d.fillRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)

        // Placeholder text
        g2d.color = Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 24)
        val text = "IMAGE"
        val fm = g2d.fontMetrics
        val textX = IMAGE_X + (IMAGE_AREA_WIDTH - fm.stringWidth(text)) / 2
        val textY = IMAGE_Y + (IMAGE_AREA_HEIGHT + fm.height) / 2
        g2d.drawString(text, textX, textY)

        // Border
        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(3f)
        g2d.drawRoundRect(IMAGE_X, IMAGE_Y, IMAGE_AREA_WIDTH, IMAGE_AREA_HEIGHT, 15, 15)
    }

    private fun drawCardTitle(g2d: Graphics2D, name: String, rarity: CardRarity) {
        val titleY = 50

        // Title background
        val titleColor = when (rarity) {
            CardRarity.COMMON -> Color(200, 200, 200)
            CardRarity.UNCOMMON -> Color(180, 220, 180)
            CardRarity.RARE -> Color(180, 180, 220)
            CardRarity.LEGENDARY -> Color(230, 200, 120)
        }

        g2d.color = titleColor
        g2d.fillRoundRect(20, titleY - 30, CARD_WIDTH - 40, 40, 10, 10)

        // Title border
        g2d.color = titleColor.darker()
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(20, titleY - 30, CARD_WIDTH - 40, 40, 10, 10)

        // Title text
        g2d.color = Color.BLACK
        g2d.font = Font("Arial", Font.BOLD, 18)

        var cardName = name
        val fm = g2d.fontMetrics

        // Truncate if too long
        while (fm.stringWidth(cardName) > CARD_WIDTH - 60 && cardName.length > 1) {
            cardName = cardName.dropLast(1)
        }

        val nameX = (CARD_WIDTH - fm.stringWidth(cardName)) / 2
        g2d.drawString(cardName, nameX, titleY - 5)
    }

    private fun drawStatsSection(g2d: Graphics2D, card: Card) {
        val statsY = CARD_HEIGHT - 120

        // Stats background
        g2d.color = Color(240, 240, 240, 230)
        g2d.fillRoundRect(20, statsY, CARD_WIDTH - 40, 80, 15, 15)

        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(20, statsY, CARD_WIDTH - 40, 80, 15, 15)

        // Attack stat (left)
        drawStatBox(g2d, "Attack", card.attack.toString(), 40, statsY + 20, Color.LIGHT_GRAY)

        // Defense stat (right)
        drawStatBox(g2d, "Defense", card.defense.toString(), CARD_WIDTH - 120, statsY + 20, Color.LIGHT_GRAY)

        // Divider line instead of "VS"
        g2d.color = Color.DARK_GRAY
        g2d.stroke = BasicStroke(3f)
        val centerX = CARD_WIDTH / 2
        g2d.drawLine(centerX, statsY + 25, centerX, statsY + 55)

        // Optional: Add small decorative elements at top and bottom of divider
        g2d.fillOval(centerX - 3, statsY + 22, 6, 6)
        g2d.fillOval(centerX - 3, statsY + 52, 6, 6)
    }

    private fun drawStatBox(g2d: Graphics2D, label: String, value: String, x: Int, y: Int, backgroundColor: Color) {
        val boxWidth = 80
        val boxHeight = 40

        // Stat background - use light gray
        g2d.color = Color(220, 220, 220) // Light gray background
        g2d.fillRoundRect(x, y, boxWidth, boxHeight, 8, 8)

        // Stat border - darker gray for contrast
        g2d.color = Color(120, 120, 120)
        g2d.stroke = BasicStroke(2f)
        g2d.drawRoundRect(x, y, boxWidth, boxHeight, 8, 8)

        // Stat text
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

    private fun drawRarityBadge(g2d: Graphics2D, rarity: CardRarity) {
        val badgeY = 385
        val rarityText = rarity.name

        val badgeColor = when (rarity) {
            CardRarity.COMMON -> Color.GRAY
            CardRarity.UNCOMMON -> Color.GREEN
            CardRarity.RARE -> Color.BLUE
            CardRarity.LEGENDARY -> Color.ORANGE
        }

        g2d.font = Font("Arial", Font.BOLD, 14)
        val fm = g2d.fontMetrics
        val textWidth = fm.stringWidth(rarityText)
        val badgeWidth = textWidth + 30
        val badgeX = (CARD_WIDTH - badgeWidth) / 2

        // Badge background
        g2d.color = badgeColor
        g2d.fillRoundRect(badgeX, badgeY - 20, badgeWidth, 25, 12, 12)

        // Badge border
        g2d.color = badgeColor.darker()
        g2d.stroke = BasicStroke(1f)
        g2d.drawRoundRect(badgeX, badgeY - 20, badgeWidth, 25, 12, 12)

        // Badge text
        g2d.color = Color.WHITE
        val textX = badgeX + (badgeWidth - textWidth) / 2
        g2d.drawString(rarityText, textX, badgeY - 5)
    }
}