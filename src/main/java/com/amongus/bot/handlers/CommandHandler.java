package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.states.LobbyState;
import com.amongus.bot.models.Player;
import com.amongus.bot.handlers.SettingsHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for bot commands that start with /.
 */
public class CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    
    private final AmongUsBot bot;
    private final LobbyManager lobbyManager;
    private final SettingsHandler settingsHandler;
    
    public CommandHandler(AmongUsBot bot, LobbyManager lobbyManager) {
        this.bot = bot;
        this.lobbyManager = lobbyManager;
        this.settingsHandler = new SettingsHandler(bot, lobbyManager);
        logger.debug("CommandHandler initialized");
    }
    
    public void handle(Update update) {
        Message message = update.getMessage();
        String text = message.getText();
        Long chatId = message.getChatId();
        User user = message.getFrom();
        Long userId = user.getId();
        String userName = user.getUserName() != null ? user.getUserName() : user.getFirstName();
        
        // Extract command and arguments
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";
        
        logger.info("Handling command '{}' from user {} ({}), args: [{}]", command, userName, userId, args);
        
        try {
            switch (command) {
                case "/start":
                    handleStartCommand(chatId);
                    break;
                case "/help":
                    handleHelpCommand(chatId);
                    break;
                case "/newgame":
                    handleNewGameCommand(chatId, userId, userName);
                    break;
                case "/join":
                    handleJoinCommand(chatId, userId, userName, args);
                    break;
                case "/leave":
                    handleLeaveCommand(chatId, userId);
                    break;
                case "/startgame":
                    handleStartGameCommand(chatId, userId);
                    break;
                case "/status":
                    handleStatusCommand(chatId, userId);
                    break;
                case "/players":
                    handlePlayersCommand(chatId, userId);
                    break;
                case "/settings":
                    handleSettingsCommand(chatId, user, bot);
                    break;
                case "/set":
                    handleSetCommand(chatId, user, args, bot);
                    break;
                case "/reset":
                    handleResetSettingsCommand(chatId, userId);
                    break;
                default:
                    handleUnknownCommand(chatId);
            }
        } catch (Exception e) {
            logger.error("Error handling command {}: {}", command, e.getMessage(), e);
            bot.sendTextMessage(chatId, "Произошла ошибка при выполнении команды. Попробуйте снова или используйте /help.");
        }
    }
    
    private void handleStartCommand(Long chatId) {
        logger.debug("Handling /start command in chat: {}", chatId);
        
        String welcomeMessage = "*Добро пожаловать в Among Us Telegram Bot!*\n\n" +
                "Нажмите кнопки ниже для создания или подключения к игре.\n\n" +
                "*Правила игры:*\n" +
                "• Передвигайтесь спокойно и неспеша\n" +
                "• Избегайте передвижения парами или группами\n" +
                "• Всегда закрывайте за собой двери\n" +
                "• Обсуждение игры разрешено только во время голосования\n" +
                "• Не подглядывайте в телефоны других игроков и не просите их нажимать кнопки\n" +
                "• Выполняйте задания добросовестно, получая удовольствие от процесса\n\n" +
                "Используйте кнопки вместо команд для удобного управления.";
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(welcomeMessage);
        message.enableMarkdown(true);
        message.setReplyMarkup(createWelcomeKeyboard());
        
        bot.executeMethod(message);
        logger.debug("Sent welcome message to chat: {}", chatId);
    }
    
    /**
     * Creates a keyboard for the welcome message with basic commands.
     * 
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createWelcomeKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row - Create and Join
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton createGameButton = new InlineKeyboardButton();
        createGameButton.setText("🎮 Создать игру");
        createGameButton.setCallbackData("command_newgame");
        row1.add(createGameButton);
        
        InlineKeyboardButton joinGameButton = new InlineKeyboardButton();
        joinGameButton.setText("🔑 Присоединиться к игре");
        joinGameButton.setCallbackData("command_promptjoin");
        row1.add(joinGameButton);
        
        keyboard.add(row1);
        
        // Second row - Help
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Помощь");
        helpButton.setCallbackData("command_help");
        row2.add(helpButton);
        
        keyboard.add(row2);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleHelpCommand(Long chatId) {
        logger.debug("Handling /help command in chat: {}", chatId);
        
        String helpMessage = "Доступные команды:\n\n" +
                "/start - Показать приветственное сообщение\n" +
                "/help - Показать это справочное сообщение\n" +
                "/newgame - Создать новое игровое лобби\n" +
                "/join <код> - Присоединиться к существующей игре по коду лобби\n" +
                "/leave - Покинуть текущую игру\n" +
                "/startgame - Начать игру (только для хоста лобби)\n" +
                "/status - Проверить текущий статус игры\n" +
                "/players - Список игроков в текущем лобби\n" +
                "/endgame - Завершить текущую игру (только для хоста лобби)\n" +
                "/settings - Настроить параметры игры с помощью интерактивных кнопок\n" +
                "/reset - Сбросить все настройки игры на значения по умолчанию";
        
        bot.sendTextMessage(chatId, helpMessage);
        logger.debug("Sent help message to chat: {}", chatId);
    }
    
    private void handleNewGameCommand(Long chatId, Long userId, String userName) {
        logger.debug("Handling /newgame command from user {} in chat: {}", userId, chatId);
        
        // Check if user is already in a game
        GameLobby existingLobby = lobbyManager.getLobbyForPlayer(userId);
        if (existingLobby != null) {
            logger.info("User {} is already in lobby {}, cannot create new game", userId, existingLobby.getLobbyCode());
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Вы уже находитесь в лобби (" + existingLobby.getLobbyCode() + 
                    "). Используйте кнопку ниже, чтобы покинуть текущую игру.");
            message.setReplyMarkup(createExistingLobbyKeyboard(existingLobby.getLobbyCode()));
            bot.executeMethod(message);
            return;
        }
        
        // Create a new game lobby
        GameLobby lobby = lobbyManager.createLobby(userId, userName);
        logger.info("Created new game lobby {} for user {}", lobby.getLobbyCode(), userId);
        
        // Set initial game state
        lobby.setGameState(new LobbyState());
        logger.debug("Set initial LobbyState for lobby {}", lobby.getLobbyCode());
        
        // Store chat ID for the player
        Player player = lobby.getPlayer(userId);
        if (player != null) {
            player.setChatId(chatId);
            logger.debug("Set chatId {} for player {}", chatId, userId);
        }
        
        // Send confirmation message with buttons
        String message = "*Создано новое лобби игры!*\n\n" +
                "*Код лобби:* `" + lobby.getLobbyCode() + "`\n\n" +
                "Поделитесь этим кодом с друзьями, чтобы они могли присоединиться.\n" +
                "Используйте кнопки ниже для управления игрой.";
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyMarkup(createLobbyHostKeyboard(lobby.getLobbyCode()));
        
        bot.executeMethod(sendMessage);
        logger.info("Successfully created new game lobby {} for user {}", lobby.getLobbyCode(), userId);
    }
    
    /**
     * Creates a keyboard for a lobby host with game control buttons.
     * 
     * @param lobbyCode The lobby code
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createLobbyHostKeyboard(String lobbyCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row - Start game and settings
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton startGameButton = new InlineKeyboardButton();
        startGameButton.setText("▶️ Начать игру");
        startGameButton.setCallbackData("command_startgame");
        row1.add(startGameButton);
        
        InlineKeyboardButton settingsButton = new InlineKeyboardButton();
        settingsButton.setText("⚙️ Настройки");
        settingsButton.setCallbackData("command_settings");
        row1.add(settingsButton);
        
        keyboard.add(row1);
        
        // Second row - View players and leave
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton playersButton = new InlineKeyboardButton();
        playersButton.setText("👥 Игроки");
        playersButton.setCallbackData("command_players");
        row2.add(playersButton);
        
        InlineKeyboardButton leaveButton = new InlineKeyboardButton();
        leaveButton.setText("🚪 Покинуть лобби");
        leaveButton.setCallbackData("command_leave");
        row2.add(leaveButton);
        
        keyboard.add(row2);
        
        // Optional third row - copy lobby code button
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        
        InlineKeyboardButton copyCodeButton = new InlineKeyboardButton();
        copyCodeButton.setText("📋 Копировать код");
        copyCodeButton.setCallbackData("command_copycode_" + lobbyCode);
        row3.add(copyCodeButton);
        
        keyboard.add(row3);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        logger.info("Created lobby host keyboard with buttons: Start Game (command_startgame), " +
                  "Settings (command_settings), Players (command_players), " +
                  "Leave (command_leave), Copy Code (command_copycode_" + lobbyCode + ")");
        
        return keyboardMarkup;
    }
    
    /**
     * Creates a keyboard for when a user is already in a lobby.
     * 
     * @param lobbyCode The lobby code
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createExistingLobbyKeyboard(String lobbyCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row - Lobby actions
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton returnButton = new InlineKeyboardButton();
        returnButton.setText("↩️ Вернуться в лобби");
        returnButton.setCallbackData("command_status");
        row1.add(returnButton);
        
        InlineKeyboardButton leaveButton = new InlineKeyboardButton();
        leaveButton.setText("🚪 Покинуть лобби");
        leaveButton.setCallbackData("command_leave");
        row1.add(leaveButton);
        
        keyboard.add(row1);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    /**
     * Creates a keyboard for a player in lobby, with host privileges if applicable.
     * 
     * @param lobbyCode The lobby code
     * @param isHost Whether the player is the host
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createLobbyKeyboardForPlayer(String lobbyCode, boolean isHost) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        if (isHost) {
            // First row - Start game and settings (only for host)
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            
            InlineKeyboardButton startGameButton = new InlineKeyboardButton();
            startGameButton.setText("▶️ Начать игру");
            startGameButton.setCallbackData("command_startgame");
            row1.add(startGameButton);
            
            InlineKeyboardButton settingsButton = new InlineKeyboardButton();
            settingsButton.setText("⚙️ Настройки");
            settingsButton.setCallbackData("command_settings");
            row1.add(settingsButton);
            
            keyboard.add(row1);
        } else {
            // First row - Settings for non-hosts (view only)
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            
            InlineKeyboardButton settingsButton = new InlineKeyboardButton();
            settingsButton.setText("⚙️ Настройки");
            settingsButton.setCallbackData("command_settings");
            row1.add(settingsButton);
            
            keyboard.add(row1);
        }
        
        // Second row - View players and leave (for all players)
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton playersButton = new InlineKeyboardButton();
        playersButton.setText("👥 Игроки");
        playersButton.setCallbackData("command_players");
        row2.add(playersButton);
        
        InlineKeyboardButton leaveButton = new InlineKeyboardButton();
        leaveButton.setText("🚪 Покинуть лобби");
        leaveButton.setCallbackData("command_leave");
        row2.add(leaveButton);
        
        keyboard.add(row2);
        
        // Third row - copy lobby code button (for all players)
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        
        InlineKeyboardButton copyCodeButton = new InlineKeyboardButton();
        copyCodeButton.setText("📋 Копировать код");
        copyCodeButton.setCallbackData("command_copycode_" + lobbyCode);
        row3.add(copyCodeButton);
        
        keyboard.add(row3);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    private void handleJoinCommand(Long chatId, Long userId, String userName, String lobbyCode) {
        logger.debug("Handling /join command from user {} in chat: {} with code: {}", userId, chatId, lobbyCode);
        
        // Check if lobby code was provided
        if (lobbyCode == null || lobbyCode.trim().isEmpty()) {
            logger.warn("User {} did not provide a lobby code", userId);
            
            // Create a keyboard to prompt for the code with a force reply
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Пожалуйста, укажите код лобби. Например: /join ABCDEF");
            
            // Add buttons for common actions
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton newGameButton = new InlineKeyboardButton();
            newGameButton.setText("🎮 Создать новую игру");
            newGameButton.setCallbackData("command_newgame");
            row.add(newGameButton);
            
            InlineKeyboardButton helpButton = new InlineKeyboardButton();
            helpButton.setText("❓ Помощь");
            helpButton.setCallbackData("command_help");
            row.add(helpButton);
            
            keyboard.add(row);
            
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);
            
            bot.executeMethod(message);
            return;
        }
        
        // Normalize the lobby code (convert to uppercase and trim)
        lobbyCode = lobbyCode.trim().toUpperCase();
        
        // Check if user is already in a game
        GameLobby existingLobby = lobbyManager.getLobbyForPlayer(userId);
        if (existingLobby != null) {
            // If trying to join the same lobby, just inform the user
            if (existingLobby.getLobbyCode().equals(lobbyCode)) {
                logger.info("User {} is already in lobby {}", userId, lobbyCode);
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Вы уже находитесь в этом лобби.");
                message.setReplyMarkup(createLobbyKeyboardForPlayer(lobbyCode, existingLobby.isHost(userId)));
                
                bot.executeMethod(message);
                return;
            }
            
            // Otherwise, ask them to leave first
            logger.info("User {} is already in lobby {}, cannot join {}", userId, existingLobby.getLobbyCode(), lobbyCode);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Вы уже находитесь в лобби (" + existingLobby.getLobbyCode() + 
                    "). Сначала покиньте текущую игру с помощью кнопки ниже.");
            message.setReplyMarkup(createExistingLobbyKeyboard(existingLobby.getLobbyCode()));
            
            bot.executeMethod(message);
            return;
        }
        
        // Try to join the lobby
        boolean joinSuccess = lobbyManager.addPlayerToLobby(lobbyCode, userId, userName);
        if (!joinSuccess) {
            logger.warn("Failed to join lobby {} for user {}", lobbyCode, userId);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Не удалось присоединиться к лобби с кодом: " + lobbyCode + 
                    "\nПроверьте код и попробуйте снова, или создайте новую игру.");
            
            // Add buttons to create new game or try again
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton newGameButton = new InlineKeyboardButton();
            newGameButton.setText("🎮 Создать новую игру");
            newGameButton.setCallbackData("command_newgame");
            row.add(newGameButton);
            
            InlineKeyboardButton tryAgainButton = new InlineKeyboardButton();
            tryAgainButton.setText("🔄 Попробовать снова");
            tryAgainButton.setCallbackData("command_promptjoin");
            row.add(tryAgainButton);
            
            keyboard.add(row);
            
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);
            
            bot.executeMethod(message);
            return;
        }
        
        // Get the lobby reference after joining
        GameLobby lobby = lobbyManager.getLobby(lobbyCode);
        
        // Set chat ID for the player
        Player player = lobby.getPlayer(userId);
        if (player != null) {
            player.setChatId(chatId);
            logger.debug("Set chatId {} for player {}", chatId, userId);
        }
        
        // Send confirmation message with buttons
        String message = "*Вы присоединились к лобби: " + lobbyCode + "*\n\n" +
                "В этом лобби сейчас " + lobby.getPlayerCount() + " игроков.\n" +
                "Ожидайте начала игры или используйте кнопки ниже.";
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.enableMarkdown(true);
        
        // Create full keyboard with all options for joined player
        boolean isHost = lobby.isHost(userId);
        sendMessage.setReplyMarkup(createLobbyKeyboardForPlayer(lobbyCode, isHost));
        
        bot.executeMethod(sendMessage);
        logger.info("User {} successfully joined lobby {}", userId, lobbyCode);
        
        // Notify the host
        Long hostId = lobby.getHostId();
        if (hostId != null && !hostId.equals(userId)) {
            Player host = lobby.getPlayer(hostId);
            if (host != null && host.getChatId() != null) {
                bot.sendTextMessage(host.getChatId(), userName + " присоединился к вашему лобби.");
                logger.debug("Notified host {} about user {} joining", hostId, userId);
            }
        }
        
        // If the lobby has a game state, notify it about the player joining
        if (lobby.getGameState() != null && lobby.getGameState() instanceof LobbyState) {
            lobby.getGameState().onEnter(bot, lobby);
            logger.debug("Triggered onEnter for LobbyState after user {} joined lobby {}", userId, lobbyCode);
        }
    }
    
    private void handleLeaveCommand(Long chatId, Long userId) {
        logger.debug("Handling /leave command from user {} in chat: {}", userId, chatId);
        
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            logger.info("User {} is not in any lobby", userId);
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби.");
            return;
        }
        
        String lobbyCode = lobby.getLobbyCode();
        boolean wasHost = lobby.isHost(userId);
        String userName = lobby.getPlayer(userId).getUserName();
        
        // Try to leave the lobby
        boolean success = lobbyManager.removePlayerFromLobby(userId);
        if (!success) {
            logger.info("User {} is not in any lobby", userId);
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби.");
            return;
        }
        
        // Send confirmation message
        bot.sendTextMessage(chatId, "Вы покинули лобби: " + lobbyCode);
        logger.info("User {} successfully left lobby {}", userId, lobbyCode);
        
        // Lobby may have been removed if it's empty now
        lobby = lobbyManager.getLobby(lobbyCode);
        if (lobby != null) {
            // If there's a new host, notify them
            if (wasHost && !lobby.isEmpty()) {
                Long newHostId = lobby.getHostId();
                if (newHostId != null) {
                    Player newHost = lobby.getPlayer(newHostId);
                    if (newHost != null && newHost.getChatId() != null) {
                        bot.sendTextMessage(newHost.getChatId(), 
                                "Предыдущий хост покинул игру. Теперь вы хост этого лобби. " +
                                "Используйте /startgame, чтобы начать игру, когда все будут готовы.");
                        logger.debug("Notified new host {} after previous host left", newHostId);
                    }
                }
            }
            
            // Notify remaining players
            for (Player player : lobby.getPlayerList()) {
                if (player.getChatId() != null && !player.getUserId().equals(userId)) {
                    bot.sendTextMessage(player.getChatId(), userName + " покинул лобби.");
                    logger.debug("Notified player {} about user {} leaving", player.getUserId(), userId);
                }
            }
            
            // If the lobby has a game state, update it
            if (lobby.getGameState() != null && lobby.getGameState() instanceof LobbyState) {
                lobby.getGameState().onEnter(bot, lobby);
                logger.debug("Triggered onEnter for LobbyState after user {} left lobby {}", userId, lobbyCode);
            }
        }
    }
    
    private void handleStartGameCommand(Long chatId, Long userId) {
        logger.debug("Handling /startgame command from user {} in chat: {}", userId, chatId);
        
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            logger.info("User {} is not in any lobby", userId);
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби. Используйте /newgame, чтобы создать новое.");
            return;
        }
        
        // Check if user is the host
        if (!lobby.isHost(userId)) {
            logger.info("User {} is not the host of lobby {}", userId, lobby.getLobbyCode());
            bot.sendTextMessage(chatId, "Только хост может начать игру.");
            return;
        }
        
        // Check if there are enough players
        if (!lobby.hasEnoughPlayers()) {
            logger.info("Not enough players in lobby {} to start the game", lobby.getLobbyCode());
            bot.sendTextMessage(chatId, "Недостаточно игроков для начала игры. Нужно минимум " + 
                    "4 игрока (сейчас в лобби: " + lobby.getPlayerCount() + ").");
            return;
        }
        
        // If game state is already LobbyState, it will handle the start game request
        if (lobby.getGameState() != null && lobby.getGameState().canPerformAction(lobby, userId, "start_game")) {
            logger.info("Starting game in lobby {}", lobby.getLobbyCode());
            
            // Let the current game state handle the start game request
            lobby.getGameState().handleUpdate(bot, lobby, null);
            
            bot.sendTextMessage(chatId, "Игра начинается!");
            logger.info("Game successfully started in lobby {}", lobby.getLobbyCode());
        } else {
            logger.warn("Cannot start game in lobby {} - invalid state", lobby.getLobbyCode());
            bot.sendTextMessage(chatId, "Невозможно начать игру в текущем состоянии.");
        }
    }
    
    private void handleStatusCommand(Long chatId, Long userId) {
        logger.debug("Handling /status command from user {} in chat: {}", userId, chatId);
        
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            logger.info("User {} is not in any lobby", userId);
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби.");
            return;
        }
        
        // Get game status
        String status = "Лобби: " + lobby.getLobbyCode() + "\n" +
                "Игроков: " + lobby.getPlayerCount() + "\n" +
                "Хост: " + lobby.getPlayer(lobby.getHostId()).getUserName() + "\n" +
                "Состояние игры: " + (lobby.getGameState() != null ? lobby.getGameState().getStateName() : "Нет");
        
        bot.sendTextMessage(chatId, status);
        logger.debug("Sent status information for lobby {} to user {}", lobby.getLobbyCode(), userId);
    }
    
    private void handlePlayersCommand(Long chatId, Long userId) {
        logger.debug("Handling /players command from user {} in chat: {}", userId, chatId);
        
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            logger.info("User {} is not in any lobby", userId);
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби.");
            return;
        }
        
        // Get player list
        List<Player> players = lobby.getPlayerList();
        StringBuilder playerList = new StringBuilder();
        playerList.append("Игроки в лобби ").append(lobby.getLobbyCode()).append(":\n\n");
        
        for (Player player : players) {
            String hostMark = lobby.isHost(player.getUserId()) ? " (Хост)" : "";
            playerList.append("- ").append(player.getUserName()).append(hostMark).append("\n");
        }
        
        bot.sendTextMessage(chatId, playerList.toString());
        logger.debug("Sent player list for lobby {} to user {}", lobby.getLobbyCode(), userId);
    }
    
    private void handleUnknownCommand(Long chatId) {
        logger.debug("Handling unknown command in chat: {}", chatId);
        bot.sendTextMessage(chatId, "Неизвестная команда. Используйте /help, чтобы увидеть доступные команды.");
    }
    
    private void handleSettingsCommand(Long chatId, User user, AmongUsBot bot) {
        if (user == null) {
            bot.sendTextMessage(chatId, "Ошибка: Информация о пользователе недоступна.");
            return;
        }
        
        Long userId = user.getId();
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби. Сначала создайте или присоединитесь к лобби.");
            return;
        }
        
        // Use the SettingsHandler to display an interactive settings menu
        settingsHandler.handleOpenSettings(lobby, chatId, null);
    }
    
    private void handleSetCommand(Long chatId, User user, String args, AmongUsBot bot) {
        if (user == null) {
            bot.sendTextMessage(chatId, "Ошибка: Информация о пользователе недоступна.");
            return;
        }
        
        Long userId = user.getId();
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь в игровом лобби. Сначала создайте или присоединитесь к лобби.");
            return;
        }
        
        // Recommend using the new settings interface
        bot.sendTextMessage(chatId, "🆕 Мы улучшили настройки игры! Пожалуйста, используйте /settings для доступа к новому интерактивному интерфейсу с кнопками для более удобной настройки.");
        
        // Open the settings menu
        settingsHandler.handleOpenSettings(lobby, chatId, null);
    }
    
    private void handleResetSettingsCommand(Long chatId, Long userId) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь в лобби.");
            return;
        }

        if (!userId.equals(lobby.getHostId())) {
            bot.sendTextMessage(chatId, "Только хост может сбросить настройки игры.");
            return;
        }

        if (lobby.getGameState() != null && !(lobby.getGameState() instanceof com.amongus.bot.game.states.LobbyState)) {
            bot.sendTextMessage(chatId, "Невозможно изменить настройки во время игры.");
            return;
        }

        lobby.resetSettings();
        bot.sendTextMessage(chatId, "Все настройки были сброшены на значения по умолчанию:\n" + lobby.getSettings().getSettingsSummary());
    }
}
// COMPLETED: CommandHandler class 