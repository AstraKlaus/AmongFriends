package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Game state for when players are in the lobby waiting for the game to start.
 */
public class LobbyState implements GameState {
    private static final Logger logger = LoggerFactory.getLogger(LobbyState.class);
    
    private static final String STATE_NAME = "LOBBY";
    
    @Override
    public String getStateName() {
        return STATE_NAME;
    }
    
    @Override
    public void onEnter(AmongUsBot bot, GameLobby lobby) {
        logger.debug("LobbyState.onEnter: Entering lobby state for game {}", lobby.getLobbyCode());
        
        // Send a welcome message to all players
        String message = buildLobbyStatusMessage(lobby);
        
        // Send to all players in the lobby
        for (Player player : lobby.getPlayerList()) {
            // Skip players without chatId
            if (player.getChatId() == null) {
                logger.warn("Player {} has no chatId, skipping message", player.getUserId());
                continue;
            }
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(player.getChatId());
            sendMessage.setText(message);
            sendMessage.setParseMode("Markdown");
            
            // Show different keyboards based on whether player is host
            boolean isHost = lobby.isHost(player.getUserId());
            sendMessage.setReplyMarkup(createLobbyKeyboard(lobby, isHost));
            
            bot.executeMethod(sendMessage);
            logger.debug("Sent lobby status to player {} ({})", player.getUserName(), player.getUserId());
        }
        
        logger.info("Entered lobby state for game {}", lobby.getLobbyCode());
    }
    
    @Override
    public void onExit(AmongUsBot bot, GameLobby lobby) {
        logger.debug("LobbyState.onExit: Exiting lobby state for game {}", lobby.getLobbyCode());
        
        // Notify all players that the game is starting
        String message = "🎮 Игра начинается! Назначаем роли...";
        
        for (Player player : lobby.getPlayerList()) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), message);
                logger.debug("Sent game starting notification to player {} ({})", 
                        player.getUserName(), player.getUserId());
            }
        }
        
        logger.info("Exited lobby state for game {}", lobby.getLobbyCode());
    }
    
    @Override
    public GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update) {
        // If update is null, it might be a direct call to start the game
        if (update == null) {
            logger.debug("LobbyState.handleUpdate: Received null update, starting game for lobby {}", 
                    lobby.getLobbyCode());
            return new SetupState();
        }
        
        // Handle callback queries (button clicks)
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long userId = update.getCallbackQuery().getFrom().getId();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            
            logger.debug("LobbyState.handleUpdate: Received callback query {} from user {} in chat {}", 
                    callbackData, userId, chatId);
            
            if (callbackData.equals("start_game") && lobby.isHost(userId)) {
                logger.info("Host {} started the game in lobby {}", userId, lobby.getLobbyCode());
                return new SetupState();
            } else if (callbackData.equals("start_game_not_enough_players") && lobby.isHost(userId)) {
                // Inform the host about minimum player requirement
                String message = "❌ Недостаточно игроков для начала игры!\n\n" +
                        "🎯 Нужно минимум 4 игрока\n" +
                        "👥 Сейчас в лобби: " + lobby.getPlayerCount() + "\n\n" +
                        "📋 Поделитесь кодом лобби с друзьями: `" + lobby.getLobbyCode() + "`";
                bot.sendTextMessage(chatId, message);
                logger.info("Host {} tried to start game with insufficient players in lobby {}", userId, lobby.getLobbyCode());
                return null;
            } else if (callbackData.equals("settings")) {
                // Handle settings button - open real settings menu
                if (lobby.isHost(userId)) {
                    try {
                        // Use bot's SettingsHandler instance
                        bot.getSettingsHandler().handleOpenSettings(lobby, chatId, null);
                        logger.info("Opened settings menu for host {} in lobby {}", userId, lobby.getLobbyCode());
                    } catch (Exception e) {
                        logger.error("Error opening settings menu from lobby state", e);
                        bot.sendTextMessage(chatId, "Ошибка при открытии настроек. Попробуйте использовать команду /settings.");
                    }
                } else {
                    bot.sendTextMessage(chatId, "Только хост может изменять настройки игры.");
                }
            } else if (callbackData.startsWith("settings_")) {
                // Handle settings-related callbacks by delegating to SettingsHandler
                if (lobby.isHost(userId)) {
                    try {
                        // Create a fake CallbackQuery for the SettingsHandler
                        CallbackQuery callbackQuery = update.getCallbackQuery();
                        bot.getSettingsHandler().handleCallback(callbackQuery);
                        logger.debug("Delegated settings callback {} to SettingsHandler for host {} in lobby {}", 
                                callbackData, userId, lobby.getLobbyCode());
                    } catch (Exception e) {
                        logger.error("Error handling settings callback from lobby state", e);
                        bot.sendTextMessage(chatId, "Ошибка при обработке настроек.");
                    }
                } else {
                    logger.warn("Non-host user {} attempted to use settings callback {} in lobby {}", 
                            userId, callbackData, lobby.getLobbyCode());
                    bot.sendTextMessage(chatId, "Только хост может изменять настройки игры.");
                }
            } else if (callbackData.equals("view_players")) {
                // Handle view players button
                String playersMessage = buildPlayersListMessage(lobby);
                bot.sendTextMessage(chatId, playersMessage);
            }
        }
        
        return null; // Stay in lobby state
    }
    
    @Override
    public boolean canPerformAction(GameLobby lobby, Long userId, String action) {
        if (action.equals("start_game")) {
            boolean canStart = lobby.isHost(userId) && lobby.hasEnoughPlayers();
            logger.debug("LobbyState.canPerformAction: User {} can {} start game in lobby {} (isHost: {}, hasEnoughPlayers: {})", 
                    userId, canStart ? "" : "NOT ", lobby.getLobbyCode(), lobby.isHost(userId), lobby.hasEnoughPlayers());
            return canStart;
        }
        
        return false;
    }
    
    /**
     * Builds a status message showing information about the lobby.
     * 
     * @param lobby The game lobby
     * @return A formatted message with lobby information
     */
    private String buildLobbyStatusMessage(GameLobby lobby) {
        logger.debug("Building lobby status message for lobby {}", lobby.getLobbyCode());
        
        StringBuilder message = new StringBuilder();
        message.append("*Лобби Among Us*\n\n");
        message.append("*Код лобби:* `/join ").append(lobby.getLobbyCode()).append("`\n\n");
        
        message.append("*Игроки (").append(lobby.getPlayerCount()).append("):*\n");
        
        for (Player player : lobby.getPlayerList()) {
            String hostMark = lobby.isHost(player.getUserId()) ? " 👑" : "";
            // Escape markdown characters in usernames
            String escapedUserName = escapeMarkdown(player.getUserName());
            message.append("- ").append(escapedUserName).append(hostMark).append("\n");
        }
        
        message.append("\nПоделитесь кодом лобби с друзьями, чтобы они присоединились!\n");
        message.append("Нужно минимум 4 игрока для начала игры.");
        
        return message.toString();
    }
    
    /**
     * Creates an inline keyboard for players in the lobby.
     * 
     * @param lobby The game lobby
     * @param isHost Whether the player is the host
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createLobbyKeyboard(GameLobby lobby, boolean isHost) {
        logger.debug("Creating lobby keyboard for lobby {}, isHost: {}", lobby.getLobbyCode(), isHost);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row: Start game button (only for host) or Settings button (only for host)
        List<InlineKeyboardButton> firstRow = new ArrayList<>();
        if (isHost) {
            InlineKeyboardButton startGameButton = new InlineKeyboardButton();
            if (lobby.hasEnoughPlayers()) {
                startGameButton.setText("🚀 Начать игру");
                startGameButton.setCallbackData("start_game");
            } else {
                startGameButton.setText("🚀 Начать игру (" + lobby.getPlayerCount() + "/4)");
                startGameButton.setCallbackData("start_game_not_enough_players");
            }
            firstRow.add(startGameButton);
            
            InlineKeyboardButton settingsButton = new InlineKeyboardButton();
            settingsButton.setText("⚙️ Настройки");
            settingsButton.setCallbackData("settings");
            firstRow.add(settingsButton);
            keyboard.add(firstRow);
        }
        
        // Second row: View players and Copy lobby code
        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        InlineKeyboardButton viewPlayersButton = new InlineKeyboardButton();
        viewPlayersButton.setText("👥 Игроки (" + lobby.getPlayerCount() + ")");
        viewPlayersButton.setCallbackData("view_players");
        secondRow.add(viewPlayersButton);
        
        InlineKeyboardButton copyCodeButton = new InlineKeyboardButton();
        copyCodeButton.setText("📋 Копировать код");
        copyCodeButton.setCallbackData("command_copycode_" + lobby.getLobbyCode());
        secondRow.add(copyCodeButton);
        keyboard.add(secondRow);
        
        // Third row: Leave lobby
        List<InlineKeyboardButton> thirdRow = new ArrayList<>();
        InlineKeyboardButton leaveButton = new InlineKeyboardButton();
        leaveButton.setText("🚪 Покинуть лобби");
        leaveButton.setCallbackData("command_leave");
        thirdRow.add(leaveButton);
        keyboard.add(thirdRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Builds a message with the list of players in the lobby.
     * 
     * @param lobby The game lobby
     * @return A formatted message with players list
     */
    private String buildPlayersListMessage(GameLobby lobby) {
        StringBuilder message = new StringBuilder();
        message.append("👥 *Игроки в лобби:*\n\n");
        
        for (Player player : lobby.getPlayerList()) {
            String hostMark = lobby.isHost(player.getUserId()) ? " 👑" : "";
            // Escape markdown characters in usernames
            String escapedUserName = escapeMarkdown(player.getUserName());
            message.append("• ").append(escapedUserName).append(hostMark).append("\n");
        }
        
        message.append("\n*Всего игроков:* ").append(lobby.getPlayerCount());
        
        return message.toString();
    }
    
    /**
     * Escapes markdown special characters in text.
     * 
     * @param text The text to escape
     * @return The escaped text
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        // Don't escape common emoji characters
        return text.replace("*", "\\*")
                  .replace("_", "\\_")
                  .replace("`", "\\`")
                  .replace("[", "\\[")
                  .replace("]", "\\]")
                  .replace("(", "\\(")
                  .replace(")", "\\)")
                  .replace("~", "\\~")
                  .replace(">", "\\>")
                  .replace("#", "\\#")
                  .replace("+", "\\+")
                  .replace("-", "\\-")
                  .replace("=", "\\=")
                  .replace("|", "\\|")
                  .replace("{", "\\{")
                  .replace("}", "\\}")
                  .replace(".", "\\.")
                  .replace("!", "\\!");
    }

    /**
     * Creates an inline keyboard for the host with game control buttons.
     * 
     * @param lobby The game lobby
     * @return An inline keyboard markup
     * @deprecated Use createLobbyKeyboard instead
     */
    @Deprecated
    private InlineKeyboardMarkup createHostKeyboard(GameLobby lobby) {
        logger.debug("Creating host keyboard for lobby {}", lobby.getLobbyCode());
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        // Start game button - enabled only if there are enough players
        InlineKeyboardButton startGameButton = new InlineKeyboardButton();
        startGameButton.setText("🚀 Начать игру");
        startGameButton.setCallbackData("start_game");
        
        row.add(startGameButton);
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
}
// COMPLETED: LobbyState class 