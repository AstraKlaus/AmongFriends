# Among Us Telegram Bot

Телеграм-бот для игры в "Among Us" через текстовый интерфейс.

## Требования

- Java 17 или выше
- Maven
- Telegram Bot API Token

## Настройка

1. Убедитесь, что установлена Java 17:
   ```
   java -version
   ```

2. Укажите правильные настройки переменных среды:
   ```
   JAVA_HOME=путь_к_вашей_JDK17
   PATH=%JAVA_HOME%\bin;%PATH%
   ```

3. В файле `src/main/java/com/amongus/bot/core/AmongUsBot.java` замените:
   - `BOT_USERNAME` на имя вашего бота
   - `BOT_TOKEN` на токен вашего бота, полученный от @BotFather

## Сборка

```
mvn clean package
```

## Запуск

### Windows
```
run.bat
```

или

```
java -jar target/among-us-telegram-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Логирование

Логи выводятся в консоль. Уровни логирования можно настроить в файле:
`src/main/resources/simplelogger.properties`

## Команды бота

- `/start` - Показать приветственное сообщение
- `/help` - Показать справку по командам
- `/newgame` - Создать новую игровую лобби
- `/join <код>` - Присоединиться к существующему лобби по коду
- `/leave` - Покинуть текущую игру
- `/startgame` - Начать игру (только для хоста лобби)
- `/status` - Проверить текущий статус игры
- `/players` - Получить список игроков в текущем лобби
- `/endgame` - Завершить текущую игру (только для хоста лобби)

## Структура проекта

- `src/main/java/com/amongus/bot/core/` - Основные классы бота
- `src/main/java/com/amongus/bot/game/` - Игровая логика
- `src/main/java/com/amongus/bot/handlers/` - Обработчики команд и сообщений
- `src/main/java/com/amongus/bot/models/` - Модели данных

## Features

- Create and join game lobbies
- Play as a Crewmate or Impostor
- Complete tasks as a Crewmate
- Sabotage and kill as an Impostor
- Hold discussions and vote to eject suspected Impostors
- Full game lifecycle management (lobby, setup, active game, discussion, game over)

## Prerequisites

- Java 11 or higher
- Maven
- A Telegram Bot Token (obtained from [@BotFather](https://t.me/BotFather))

## Setup

1. Clone this repository:
   ```
   git clone https://github.com/yourusername/among-us-telegram-bot.git
   cd among-us-telegram-bot
   ```

2. Register a new bot with [@BotFather](https://t.me/BotFather) on Telegram and get your bot token.

3. Edit the `src/main/java/com/amongus/bot/core/AmongUsBot.java` file and update the following properties with your bot information:
   ```java
   private final String BOT_USERNAME = "your_bot_username"; // Replace with your bot's username
   private final String BOT_TOKEN = "your_bot_token"; // Replace with your bot token
   ```

4. Build the project:
   ```
   mvn clean package
   ```

5. Run the bot:
   ```
   java -jar target/among-us-telegram-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

## How to Play

1. Start a conversation with the bot by searching for your bot's username in Telegram.

2. Send the `/start` command to get the welcome message and main options.

3. Create a new game by clicking the "Create New Game" button or using the `/newgame` command.

4. Share the lobby code with your friends so they can join your game.

5. Once enough players have joined (minimum 4), the host can start the game.

6. The game will assign roles (Crewmate or Impostor) to each player.

7. Crewmates need to complete all tasks or identify and eject all Impostors.

8. Impostors need to kill enough Crewmates to equal their numbers.

9. Players can call emergency meetings or report dead bodies to initiate discussions.

10. During discussions, players can vote to eject a suspected Impostor.

## Game Commands

- `/start` - Start the bot and see available commands
- `/help` - Show help information
- `/newgame` - Create a new game lobby
- `/join <code>` - Join a game with the specified lobby code
- `/leave` - Leave the current game
- `/startgame` - Start the game (host only)
- `/players` - List players in the current lobby
- `/endgame` - End the current game (host only)

## Architecture

The bot is built using the following structure:

- `com.amongus.bot.core` - Core bot functionality
- `com.amongus.bot.game.lobby` - Lobby management
- `com.amongus.bot.game.states` - Game state management
- `com.amongus.bot.game.roles` - Player roles
- `com.amongus.bot.game.tasks` - Task system
- `com.amongus.bot.game.voting` - Voting system
- `com.amongus.bot.handlers` - Message and callback handlers
- `com.amongus.bot.models` - Data models
- `com.amongus.bot.utils` - Utility classes
- `com.amongus.bot.commands` - Bot commands

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Inspired by the game "Among Us" by InnerSloth
- Built using the [TelegramBots](https://github.com/rubenlagus/TelegramBots) library 