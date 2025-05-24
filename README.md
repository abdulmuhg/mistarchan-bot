# MistarChan Bot

A Discord bot that turns images into collectible trading cards using AI, then lets players battle with their cards!

## Features

- **Image-to-Card Generation**: Upload images to create unique trading cards with AI-generated stats
- **Card Collection**: Build and manage your personal card collection
- **Battle System**: Challenge other players to card battles with strategic gameplay
- **Rarity System**: Cards are assigned different rarity levels affecting their value
- **Slash Commands**: Modern Discord slash command interface with auto-completion

## Setup

1. Clone this repository
2. Create a Discord bot and get your bot token from the [Discord Developer Portal](https://discord.com/developers/applications)
3. Get an OpenAI API key from [OpenAI](https://platform.openai.com/)
4. Set up environment variables:
   ```bash
   export DISCORD_TOKEN="your_discord_bot_token"
   export OPENAI_API_KEY="your_openai_api_key"
   ```
5. Build the project:
   ```bash
   ./gradlew build
   ```
6. Run the bot:
   ```bash
   ./gradlew run
   ```

## Slash Commands

The bot uses Discord's modern slash command system for a better user experience:

### Card Management
- `/cards list` - View your entire card collection with stats
- `/card <id>` - View detailed information about a specific card

### Battle System
- `/challenge @user` - Challenge another user to a battle
- `/play <card_id> <attack/defense>` - Make a move in an active battle

### Information
- `/help` - Display all available commands and game rules

## Card Generation

Simply upload an image to any channel the bot has access to. The bot will:
1. Analyze the image using OpenAI's Vision API
2. Generate appropriate stats (attack, defense, rarity) based on image content
3. Create and save the card to your collection automatically

### Card Attributes
- **Name**: Creative, thematic title based on image content (max 40 chars)
- **Attack**: Offensive power (1-10 scale)
- **Defense**: Defensive power (1-10 scale)
- **Rarity**: Common ⚪, Uncommon 🟢, Rare 🔵, Legendary 🟡
- **Image**: Your original uploaded image as card artwork

## Battle System

Battles are strategic turn-based games with the following rules:

### Setup
1. Both players need at least 3 cards to battle
2. Each player gets 3 random cards from their collection
3. Players take turns playing cards in either Attack or Defense position

### Combat Rules
| Your Position | Opponent Position | You Win If | Points |
|---------------|-------------------|------------|---------|
| Attack | Attack | Your Attack > Their Attack | 1 |
| Attack | Defense | Your Attack > Their Defense | 1 |
| Defense | Attack | Your Defense ≥ Their Attack | 1 |
| Defense | Defense | No winner (tie) | 0 |

### Victory Conditions
- **Best of 3 rounds** wins the battle
- First player to win 2 rounds is declared the winner
- Strategic positioning and card selection are key to victory

## Example Gameplay

1. **Create Cards**: Upload images to build your collection
   ```
   [User uploads image of a dragon]
   ✨ New Card Created! ✨
   Dragon Guardian
   ⚔️ Attack: 8
   🛡️ Defense: 6
   ⭐ Rarity: RARE
   ```

2. **Challenge Players**: Use slash commands to battle
   ```
   /challenge @friend
   ⚔️ Battle Started! ⚔️
   @you vs @friend
   Each player has been assigned 3 random cards...
   ```

3. **Strategic Combat**: Choose your moves wisely
   ```
   /play 12 attack
   ✅ Move submitted! You played Dragon Guardian in ATTACK position.
   Waiting for your opponent...
   ```

## Development

The project uses:
- **Kotlin 1.9.22** - Modern, expressive programming language
- **Kord Discord Library** - Coroutine-based Discord API wrapper
- **SQLite + Exposed** - Local database for card storage
- **OpenAI Vision API** - AI-powered image analysis
- **Ktor Client** - HTTP client for API calls

### Project Structure
```
src/main/kotlin/com/mrc/mistarbot/
├── Main.kt                 # Bot initialization and event handling
├── commands/
│   └── SlashCommandHandler.kt  # Slash command logic
├── model/
│   ├── Card.kt            # Card data model
│   └── Battle.kt          # Battle game logic
├── service/
│   └── OpenAIService.kt   # AI image analysis
└── database/
    └── Database.kt        # Data persistence
```

## Anti-Abuse Features

- **Daily Limits**: Prevent card generation spam
- **Quality Standards**: Ensure meaningful image content
- **Cooldown Periods**: Spacing between card creation
- **Duplicate Prevention**: Block identical or similar images

## Future Enhancements

- **Card Trading**: Exchange cards between players
- **Tournaments**: Organized competitive events
- **Advanced Abilities**: Special card powers and effects
- **Deck Building**: Custom deck construction
- **Leaderboards**: Server-wide rankings and statistics

## License

MIT License - feel free to use and modify as needed!

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.