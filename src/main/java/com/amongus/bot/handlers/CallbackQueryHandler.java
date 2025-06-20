package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.states.DiscussionState;
import com.amongus.bot.game.states.GameActiveState;
import com.amongus.bot.game.states.GameState;
import com.amongus.bot.game.states.LobbyState;
import com.amongus.bot.game.states.SetupState;
import com.amongus.bot.models.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for callback queries (inline keyboard button presses).
 */
public class CallbackQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(CallbackQueryHandler.class);
    
    private final AmongUsBot bot;
    private final LobbyManager lobbyManager;
    
    public CallbackQueryHandler(AmongUsBot bot, LobbyManager lobbyManager) {
        this.bot = bot;
        this.lobbyManager = lobbyManager;
    }
    
    public void handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        String callbackId = callbackQuery.getId();
        String userName = callbackQuery.getFrom().getUserName() != null 
            ? callbackQuery.getFrom().getUserName() 
            : callbackQuery.getFrom().getFirstName();
        
        logger.info("Processing callback query from user {}: {}", userId, callbackData);
        
        // Parse the callback data format
        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        
        logger.debug("Parsed callback action: {}", action);
        
        // Сначала проверяем, является ли это командным callback
        if (action.equals("command")) {
            logger.debug("Handling command callback: {}", parts.length > 1 ? parts[1] : "");
            handleCommandCallbacks(callbackQuery, parts.length > 1 ? parts[1] : "");
            acknowledgeCallbackQuery(callbackId);
            return;
        }
        
        // Затем, если это не команда, делегируем обработку текущему состоянию игры
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby != null && lobby.getGameState() != null) {
            logger.debug("Delegating callback to game state handler for state: {}", 
                    lobby.getGameState().getClass().getSimpleName());
            
            // Let the game state handle this callback
            GameState nextState = lobby.getGameState().handleUpdate(bot, lobby, update);
            
            // Process state transition if needed
            if (nextState != null) {
                GameState currentState = lobby.getGameState();
                logger.info("Game state transition from {} to {} for lobby {}", 
                        currentState.getClass().getSimpleName(),
                        nextState.getClass().getSimpleName(),
                        lobby.getLobbyCode());
                
                currentState.onExit(bot, lobby);
                lobby.setGameState(nextState);
                nextState.onEnter(bot, lobby);
            }
            
            acknowledgeCallbackQuery(callbackId);
            return;
        }
        
        // Handle other callback types
        logger.debug("Handling action callback: {}", action);
        switch (action) {
            case "start_game":
                handleStartGameAction(chatId, userId);
                break;
            case "role":
                handleRoleAction(chatId, userId, parts);
                break;
            case "vote":
                handleVoteAction(chatId, userId, parts);
                break;
            case "task":
                handleTaskAction(chatId, userId, parts);
                break;
            case "emergency":
                handleEmergencyAction(chatId, userId);
                break;
            case "report":
                handleReportAction(chatId, userId);
                break;
            case "kill":
                handleKillAction(chatId, userId, parts);
                break;
            case "new_game":
                handleNewGameAction(chatId, userId, userName);
                break;
            default:
                // Неизвестные callback-запросы не должны приводить к отправке ненужных сообщений
                // Особенно во время голосования, так как это может приводить к повторной отправке меню
                logger.warn("Unknown callback action: {} from user {}", action, userId);
                
                // Просто подтверждаем обработку запроса без отправки сообщения
                // Раньше здесь было bot.sendTextMessage, что могло вызывать проблемы
        }
        
        // Always acknowledge the callback query to stop the loading animation
        acknowledgeCallbackQuery(callbackId);
    }
    
    private void handleStartGameAction(Long chatId, Long userId) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не состоите ни в одном лобби.");
            logger.warn("User {} tried to start game but is not in any lobby", userId);
            return;
        }
        
        if (!lobby.isHost(userId)) {
            bot.sendTextMessage(chatId, "Только хост может начать игру.");
            logger.warn("Non-host user {} tried to start game in lobby {}", userId, lobby.getLobbyCode());
            return;
        }
        
        if (!lobby.hasEnoughPlayers()) {
            String message = "Невозможно начать игру. Нужно минимум " + 
                    "4 игрока (сейчас в лобби: " + lobby.getPlayerCount() + ").";
            bot.sendTextMessage(chatId, message);
            logger.info("Not enough players to start game in lobby {}", lobby.getLobbyCode());
            return;
        }
        
        logger.info("Host {} started the game in lobby {}", userId, lobby.getLobbyCode());
        
        // Transition to setup state
        if (lobby.getGameState() instanceof LobbyState) {
            LobbyState lobbyState = (LobbyState) lobby.getGameState();
            lobbyState.onExit(bot, lobby);
        }
        
        SetupState setupState = new SetupState();
        lobby.setGameState(setupState);
        setupState.onEnter(bot, lobby);
    }
    
    /**
     * Handles role-related actions, such as viewing one's role or special role abilities.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     * @param parts The callback data parts
     */
    private void handleRoleAction(Long chatId, Long userId, String[] parts) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to perform role action but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to perform role action but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        // Different role actions
        if (parts.length > 1) {
            String roleAction = parts[1];
            
            if (roleAction.equals("view")) {
                // View role
                if (player.getRole() != null) {
                    // Get role name and description
                    String roleName = player.getRole().isImpostor() ? "Предатель" : "Член экипажа";
                    String roleDescription = player.getRole().isImpostor() 
                            ? "Устраните членов экипажа, не будучи обнаруженным."
                            : "Выполните все задания и найдите предателей.";
                    
                    bot.sendTextMessage(chatId, 
                            "Ваша роль: " + roleName + "\n" + roleDescription);
                    logger.debug("User {} viewed their role in game {}", userId, lobby.getLobbyCode());
                } else {
                    bot.sendTextMessage(chatId, "Вам еще не назначена роль.");
                    logger.warn("User {} tried to view role but has no role in game {}", 
                            userId, lobby.getLobbyCode());
                }
            } else {
                bot.sendTextMessage(chatId, "Неизвестное действие с ролью.");
                logger.warn("User {} tried to perform unknown role action {} in game {}", 
                        userId, roleAction, lobby.getLobbyCode());
            }
        }
    }
    
    /**
     * Handles voting actions during discussion/meeting phases.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     * @param parts The callback data parts
     */
    private void handleVoteAction(Long chatId, Long userId, String[] parts) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to vote but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to vote but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        // Проверка, что мы находимся в фазе дискуссии
        if (!(lobby.getGameState() instanceof DiscussionState)) {
            bot.sendTextMessage(chatId, "Голосование возможно только во время обсуждений.");
            logger.warn("User {} tried to vote outside of discussion phase in game {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        // Пытаемся предотвратить обработку голосования здесь - это должно происходить в DiscussionState
        logger.info("Redirecting vote action to DiscussionState for user {} in game {}", 
                userId, lobby.getLobbyCode());
        
        // Просто отправляем сообщение, что голосование должно выполняться через основное меню
        bot.sendTextMessage(chatId, "Используйте кнопки в основном меню голосования.");
    }
    
    /**
     * Handles task-related actions, such as starting or completing tasks.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     * @param parts The callback data parts
     */
    private void handleTaskAction(Long chatId, Long userId, String[] parts) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to perform task action but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to perform task action but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (!(lobby.getGameState() instanceof GameActiveState)) {
            bot.sendTextMessage(chatId, "Задания можно выполнять только во время активной фазы игры.");
            logger.warn("User {} tried to perform task action outside of active game in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        GameActiveState gameState = (GameActiveState) lobby.getGameState();
        
        if (player.isImpostor()) {
            // Impostors might be trying to fake tasks
            bot.sendTextMessage(chatId, "Будучи Предателем, вы только притворяетесь, что выполняете задания.");
            logger.debug("Impostor {} attempted to perform a task in game {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (parts.length > 1) {
            try {
                int taskIndex = Integer.parseInt(parts[1]);
                
                if (taskIndex < 0 || taskIndex >= player.getTasks().size()) {
                    bot.sendTextMessage(chatId, "Недопустимый индекс задания.");
                    logger.warn("User {} attempted to complete invalid task index {} in game {}", 
                            userId, taskIndex, lobby.getLobbyCode());
                    return;
                }
                
                if (player.getTasks().get(taskIndex).isCompleted()) {
                    bot.sendTextMessage(chatId, "Это задание уже выполнено.");
                    logger.debug("User {} attempted to complete already completed task {} in game {}", 
                            userId, taskIndex, lobby.getLobbyCode());
                    return;
                }
                
                // This is now handled by the GameActiveState
                // Let the current game state handle this action
                if (gameState.canPerformAction(lobby, userId, "task:" + taskIndex)) {
                    player.setAwaitingPhotoForTaskIndex(taskIndex);
                    
                    String taskName = player.getTasks().get(taskIndex).getName();
                    bot.sendTextMessage(chatId, 
                            "Пожалуйста, отправьте фото, подтверждающее выполнение задания: \"" + taskName + "\"");
                    
                    logger.info("User {} requested to complete task {} in game {}, awaiting photo confirmation", 
                            userId, taskIndex, lobby.getLobbyCode());
                } else {
                    bot.sendTextMessage(chatId, "В данный момент вы не можете выполнить это действие.");
                    logger.warn("User {} attempted to perform task {} but not allowed in game {}", 
                            userId, taskIndex, lobby.getLobbyCode());
                }
                
            } catch (NumberFormatException e) {
                bot.sendTextMessage(chatId, "Недопустимый формат индекса задания.");
                logger.warn("User {} sent invalid task index format in game {}: {}", 
                        userId, lobby.getLobbyCode(), e.getMessage());
            }
        }
    }
    
    /**
     * Handles emergency meeting action.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     */
    private void handleEmergencyAction(Long chatId, Long userId) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to call emergency meeting but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to call emergency meeting but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (!(lobby.getGameState() instanceof GameActiveState)) {
            bot.sendTextMessage(chatId, "Экстренные собрания можно созывать только во время активной фазы игры.");
            logger.warn("User {} tried to call emergency meeting outside of active game in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        // Forward to the game state to handle the action
        GameActiveState gameState = (GameActiveState) lobby.getGameState();
        
        if (gameState.canPerformAction(lobby, userId, "emergency_meeting")) {
            // Let the game state handle this
            logger.info("User {} called emergency meeting in game {}", userId, lobby.getLobbyCode());
            
            // This is now handled in the GameActiveState
            // Use redirected Update to handle this in the correct state
            // createEmergencyUpdate(userId, lobby)
        } else {
            bot.sendTextMessage(chatId, "В данный момент вы не можете созвать экстренное собрание.");
            logger.warn("User {} attempted to call emergency meeting but not allowed in game {}", 
                    userId, lobby.getLobbyCode());
        }
    }
    
    /**
     * Handles report body action.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     */
    private void handleReportAction(Long chatId, Long userId) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to report body but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to report body but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (!(lobby.getGameState() instanceof GameActiveState)) {
            bot.sendTextMessage(chatId, "Сообщать о телах можно только во время активной фазы игры.");
            logger.warn("User {} tried to report body outside of active game in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        // Forward to the game state to handle the action
        GameActiveState gameState = (GameActiveState) lobby.getGameState();
        
        if (gameState.canPerformAction(lobby, userId, "report_body")) {
            // Let the game state handle this
            logger.info("User {} reported body in game {}", userId, lobby.getLobbyCode());
            
            // This is now handled in the GameActiveState
            // Use redirected Update to handle this in the correct state
            // createReportUpdate(userId, lobby)
        } else {
            bot.sendTextMessage(chatId, "В данный момент вы не можете сообщить о теле.");
            logger.warn("User {} attempted to report body but not allowed in game {}", 
                    userId, lobby.getLobbyCode());
        }
    }
    
    /**
     * Handles kill action from an impostor.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     * @param parts The callback data parts
     */
    private void handleKillAction(Long chatId, Long userId, String[] parts) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не участвуете ни в одной игре.");
            logger.warn("User {} tried to kill but is not in any game", userId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            bot.sendTextMessage(chatId, "Вы не являетесь игроком в этой игре.");
            logger.warn("User {} tried to kill but is not a player in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (!(lobby.getGameState() instanceof GameActiveState)) {
            bot.sendTextMessage(chatId, "Убийства можно совершать только во время активной фазы игры.");
            logger.warn("User {} tried to kill outside of active game in lobby {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        if (!player.isImpostor()) {
            bot.sendTextMessage(chatId, "Только предатели могут убивать других игроков.");
            logger.warn("Non-impostor {} tried to kill in game {}", userId, lobby.getLobbyCode());
            return;
        }
        
        if (parts.length < 2) {
            bot.sendTextMessage(chatId, "Неверный формат команды убийства.");
            logger.warn("User {} sent invalid kill command format in game {}", 
                    userId, lobby.getLobbyCode());
            return;
        }
        
        long targetId;
        try {
            targetId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            bot.sendTextMessage(chatId, "Неверный формат ID цели.");
            logger.warn("User {} sent invalid target ID format in game {}: {}", 
                    userId, lobby.getLobbyCode(), e.getMessage());
            return;
        }
        
        Player target = lobby.getPlayer(targetId);
        if (target == null) {
            bot.sendTextMessage(chatId, "Целевой игрок не найден в этой игре.");
            logger.warn("User {} tried to kill non-existent player {} in game {}", 
                    userId, targetId, lobby.getLobbyCode());
            return;
        }
        
        if (!target.isAlive()) {
            bot.sendTextMessage(chatId, "Целевой игрок уже мертв.");
            logger.warn("User {} tried to kill already dead player {} in game {}", 
                    userId, targetId, lobby.getLobbyCode());
            return;
        }
        
        // Forward to the game state to handle the action
        GameActiveState gameState = (GameActiveState) lobby.getGameState();
        
        if (gameState.canPerformAction(lobby, userId, "kill:" + targetId)) {
            // Let the game state handle this
            logger.info("Impostor {} killed player {} in game {}", 
                    userId, targetId, lobby.getLobbyCode());
            
            // This is now handled in the GameActiveState
            // Use redirected Update to handle this in the correct state
            // createKillUpdate(userId, targetId, lobby)
        } else {
            bot.sendTextMessage(chatId, "В данный момент вы не можете совершить убийство.");
            logger.warn("Impostor {} attempted to kill but not allowed in game {}", 
                    userId, lobby.getLobbyCode());
        }
    }
    
    /**
     * Handles requests to start a new game after game over.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     */
    private void handleNewGameAction(Long chatId, Long userId, String userName) {
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        
        if (lobby == null) {
            bot.sendTextMessage(chatId, "You are not in any lobby.");
            logger.warn("User {} tried to start new game but is not in any lobby", userId);
            return;
        }
        
        if (!lobby.isHost(userId)) {
            bot.sendTextMessage(chatId, "Only the host can start a new game.");
            logger.warn("Non-host user {} tried to start new game in lobby {}", userId, lobby.getLobbyCode());
            return;
        }
        
        // Reset all players
        for (Player player : lobby.getPlayerList()) {
            // Use the new reset method to properly reset player state
            player.reset();
            logger.debug("Reset player {} state for new game in lobby {}", 
                    player.getUserId(), lobby.getLobbyCode());
        }
        
        // Notify all players
        for (Player player : lobby.getPlayerList()) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Starting a new game with the same players...");
            }
        }
        
        // Transition to lobby state
        lobby.setGameState(new LobbyState());
        lobby.getGameState().onEnter(bot, lobby);
        
        logger.info("Host {} started a new game in lobby {}", userId, lobby.getLobbyCode());
    }
    
    private void acknowledgeCallbackQuery(String callbackId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        try {
            bot.execute(answer);
            logger.debug("Acknowledged callback query: {}", callbackId);
        } catch (TelegramApiException e) {
            logger.error("Failed to acknowledge callback query: {}", e.getMessage());
        }
    }

    /**
     * Handles callbacks that execute commands.
     * 
     * @param callbackQuery The callback query
     * @param commandData The command data (after the "command_" prefix)
     */
    private void handleCommandCallbacks(CallbackQuery callbackQuery, String commandData) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        String userName = callbackQuery.getFrom().getUserName() != null 
            ? callbackQuery.getFrom().getUserName() 
            : callbackQuery.getFrom().getFirstName();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        logger.info("Handling command callback: {} from user {} ({})", commandData, userName, userId);
        
        // Split the command data to get any parameters
        String[] commandParts = commandData.split("_", 2);
        String command = commandParts[0];
        String params = commandParts.length > 1 ? commandParts[1] : "";
        
        logger.debug("Command: {}, params: {}", command, params);
        
        switch (command) {
            case "newgame":
                // Создаем новую игру
                logger.info("Creating new game via button for user {}", userId);
                
                // Проверяем, не находится ли пользователь уже в игре
                GameLobby existingLobby = lobbyManager.getLobbyForPlayer(userId);
                if (existingLobby != null) {
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Вы уже находитесь в лобби (" + existingLobby.getLobbyCode() + 
                            "). Сначала покиньте текущую игру.");
                    message.setReplyMarkup(createExistingLobbyKeyboard(existingLobby.getLobbyCode()));
                    
                    try {
                        bot.execute(message);
                    } catch (TelegramApiException e) {
                        logger.error("Error sending already in lobby message", e);
                        bot.sendTextMessage(chatId, "Вы уже находитесь в лобби. Используйте /leave, чтобы покинуть его.");
                    }
                    return;
                }
                
                // Создаем лобби
                GameLobby newLobby = lobbyManager.createLobby(userId, userName);
                
                // Устанавливаем начальное состояние игры
                newLobby.setGameState(new LobbyState());
                logger.debug("Set initial LobbyState for lobby {}", newLobby.getLobbyCode());
                
                // Сохраняем chatId для игрока
                Player player = newLobby.getPlayer(userId);
                if (player != null) {
                    player.setChatId(chatId);
                    logger.debug("Set chatId {} for player {}", chatId, userId);
                }
                
                // Отправляем сообщение с подтверждением и кнопками
                String message = "*Создано новое лобби игры!*\n\n" +
                        "*Код лобби:* `/join " + newLobby.getLobbyCode() + "`\n\n" +
                        "Поделитесь этим кодом с друзьями, чтобы они могли присоединиться.\n" +
                        "Используйте кнопки ниже для управления игрой.";
                
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(message);
                sendMessage.enableMarkdown(true);
                sendMessage.setReplyMarkup(createLobbyHostKeyboard(newLobby.getLobbyCode()));
                
                try {
                    bot.execute(sendMessage);
                    logger.info("Successfully created new game lobby {} for user {}", 
                            newLobby.getLobbyCode(), userId);
                } catch (TelegramApiException e) {
                    logger.error("Error sending new game confirmation", e);
                    bot.sendTextMessage(chatId, "Ошибка при создании игры. Попробуйте снова.");
                }
                break;
            
            case "promptjoin":
                // Prompt the user to enter a lobby code
                handlePromptJoinAction(chatId, userId);
                break;
            
            case "settings":
                // Open settings menu
                GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
                if (lobby != null) {
                    try {
                        // Create a new SettingsHandler instance and call handleOpenSettings
                        SettingsHandler settingsHandler = new SettingsHandler(bot, lobbyManager);
                        settingsHandler.handleOpenSettings(lobby, chatId, null);
                        logger.info("Opened settings menu for user {} in lobby {}", userId, lobby.getLobbyCode());
                    } catch (Exception e) {
                        logger.error("Error opening settings menu", e);
                        bot.sendTextMessage(chatId, "Ошибка при открытии настроек. Попробуйте использовать команду /settings.");
                    }
                } else {
                    logger.warn("User {} tried to open settings but is not in any lobby", userId);
                    bot.sendTextMessage(chatId, "Вы не находитесь в лобби. Сначала создайте или присоединитесь к игре.");
                }
                break;
            
            case "help":
                // Show help message
                sendHelpMessage(chatId);
                break;
            
            case "startgame":
                // Start the game (simulate /startgame command)
                handleStartGameAction(chatId, userId);
                break;
            
            case "players":
                // Show player list (simulate /players command)
                sendPlayersList(chatId, userId);
                break;
            
            case "leave":
                // Leave the game (simulate /leave command)
                handleLeaveAction(chatId, userId);
                break;
            
            case "status":
                // Show game status (simulate /status command)
                sendGameStatus(chatId, userId);
                break;
            
            case "copycode":
                // Copy lobby code to clipboard
                GameLobby lobbyForCode = lobbyManager.getLobbyForPlayer(userId);
                if (lobbyForCode != null) {
                    String lobbyCode = params.isEmpty() ? lobbyForCode.getLobbyCode() : params;
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(callbackQuery.getId());
                    answer.setText("/join " + lobbyCode);
                    answer.setShowAlert(true);
                    
                    try {
                        bot.execute(answer);
                        logger.info("Displayed lobby code {} to user {}", lobbyCode, userId);
                    } catch (TelegramApiException e) {
                        logger.error("Error showing lobby code", e);
                    }
                } else {
                    logger.warn("User {} tried to copy lobby code but is not in any lobby", userId);
                    bot.sendTextMessage(chatId, "Вы не находитесь в лобби.");
                }
                break;
            
            default:
                logger.warn("Unknown command callback: {}", command);
                bot.sendTextMessage(chatId, "Неизвестная команда. Используйте /help для просмотра доступных команд.");
        }
    }

    private void handlePromptJoinAction(Long chatId, Long userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Пожалуйста, введите код лобби:");
        
        // Create a force reply keyboard
        ForceReplyKeyboard forceReply = new ForceReplyKeyboard();
        forceReply.setForceReply(true);
        forceReply.setSelective(true);
        message.setReplyMarkup(forceReply);
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending force reply for join prompt", e);
            bot.sendTextMessage(chatId, "Произошла ошибка. Пожалуйста, используйте команду /join <код>.");
        }
    }

    private void handleLeaveAction(Long chatId, Long userId) {
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь ни в одном лобби.");
            return;
        }
        
        String lobbyCode = lobby.getLobbyCode();
        boolean wasHost = lobby.isHost(userId);
        String userName = lobby.getPlayer(userId).getUserName();
        
        // Leave the lobby
        boolean success = lobbyManager.removePlayerFromLobby(userId);
        if (!success) {
            bot.sendTextMessage(chatId, "Не удалось покинуть лобби.");
            return;
        }
        
        // Send confirmation
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Вы покинули лобби: " + lobbyCode + "\n\nНажмите кнопку ниже, чтобы создать новую игру.");
        
        // Add button to create new game
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton newGameButton = new InlineKeyboardButton();
        newGameButton.setText("🎮 Создать новую игру");
        newGameButton.setCallbackData("command_newgame");
        row.add(newGameButton);
        
        keyboard.add(row);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending leave confirmation", e);
            bot.sendTextMessage(chatId, "Вы покинули лобби: " + lobbyCode);
        }
        
        // Notify remaining players
        lobby = lobbyManager.getLobby(lobbyCode);
        if (lobby != null) {
            // Handle host reassignment
            if (wasHost && !lobby.isEmpty()) {
                Long newHostId = lobby.getHostId();
                if (newHostId != null) {
                    Player newHost = lobby.getPlayer(newHostId);
                    if (newHost != null && newHost.getChatId() != null) {
                        bot.sendTextMessage(newHost.getChatId(), 
                                "Предыдущий хост покинул игру. Теперь вы хост этого лобби.");
                    }
                }
            }
            
            // Notify all remaining players
            for (Player player : lobby.getPlayerList()) {
                if (player.getChatId() != null && !player.getUserId().equals(userId)) {
                    bot.sendTextMessage(player.getChatId(), userName + " покинул лобби.");
                }
            }
        }
    }

    private void sendPlayersList(Long chatId, Long userId) {
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь ни в одном лобби.");
            return;
        }
        
        // Build player list
        List<Player> players = lobby.getPlayerList();
        StringBuilder playerList = new StringBuilder();
        playerList.append("*Игроки в лобби ").append(lobby.getLobbyCode()).append(":*\n\n");
        
        for (Player player : players) {
            String hostMark = lobby.isHost(player.getUserId()) ? " 👑" : "";
            playerList.append("• ").append(player.getUserName()).append(hostMark).append("\n");
        }
        
        // Add lobby management buttons
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(playerList.toString());
        message.enableMarkdown(true);
        
        // Create keyboard
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 Назад");
        backButton.setCallbackData("command_status");
        row.add(backButton);
        
        if (lobby.isHost(userId)) {
            InlineKeyboardButton startButton = new InlineKeyboardButton();
            startButton.setText("▶️ Начать игру");
            startButton.setCallbackData("command_startgame");
            row.add(startButton);
        }
        
        keyboard.add(row);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending players list", e);
            bot.sendTextMessage(chatId, playerList.toString());
        }
    }

    private void sendGameStatus(Long chatId, Long userId) {
        // Check if user is in a game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            bot.sendTextMessage(chatId, "Вы не находитесь ни в одном лобби.");
            return;
        }
        
        // Build status message
        String status = "*Информация о лобби:*\n" +
                "Код: `/join " + lobby.getLobbyCode() + "`\n" +
                "Игроков: " + lobby.getPlayerCount() + "\n" +
                "Хост: " + lobby.getPlayer(lobby.getHostId()).getUserName() + "\n" +
                "Состояние: " + (lobby.getGameState() != null ? lobby.getGameState().getStateName() : "Не начата");
        
        // Add lobby management buttons
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(status);
        message.enableMarkdown(true);
        
        // Create keyboard based on whether user is host
        InlineKeyboardMarkup keyboard;
        if (lobby.isHost(userId)) {
            keyboard = createLobbyHostKeyboard(lobby.getLobbyCode());
        } else {
            keyboard = createLobbyPlayerKeyboard(lobby.getLobbyCode());
        }
        
        message.setReplyMarkup(keyboard);
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending game status", e);
            bot.sendTextMessage(chatId, status);
        }
    }

    private InlineKeyboardMarkup createLobbyPlayerKeyboard(String lobbyCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // First row - players and leave
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton playersButton = new InlineKeyboardButton();
        playersButton.setText("👥 Игроки");
        playersButton.setCallbackData("command_players");
        row1.add(playersButton);
        
        InlineKeyboardButton leaveButton = new InlineKeyboardButton();
        leaveButton.setText("🚪 Покинуть лобби");
        leaveButton.setCallbackData("command_leave");
        row1.add(leaveButton);
        
        keyboard.add(row1);
        
        // Second row - copy code
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton copyCodeButton = new InlineKeyboardButton();
        copyCodeButton.setText("📋 Копировать код");
        copyCodeButton.setCallbackData("command_copycode_" + lobbyCode);
        row2.add(copyCodeButton);
        
        keyboard.add(row2);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }

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
        
        logger.info("Created lobby host keyboard with buttons: Start Game, Settings, Players, Leave, Copy Code");
        return keyboardMarkup;
    }

    private void sendHelpMessage(Long chatId) {
        String helpMessage = "*Доступные команды:*\n\n" +
                "• /start - Показать приветственное сообщение\n" +
                "• /help - Показать эту справку\n" +
                "• /newgame - Создать новое лобби\n" +
                "• /join <код> - Присоединиться к лобби по коду\n" +
                "• /leave - Покинуть текущую игру\n" +
                "• /startgame - Начать игру (только для хоста)\n" +
                "• /status - Проверить текущий статус игры\n" +
                "• /players - Список игроков в лобби\n" +
                "• /settings - Настройки игры с интерактивными кнопками\n" +
                "• /reset - Сбросить все настройки игры\n\n" +
                "*Подсказка:* Используйте кнопки вместо команд для более удобного взаимодействия.";
        
        // Add buttons for common actions
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(helpMessage);
        message.enableMarkdown(true);
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton newGameButton = new InlineKeyboardButton();
        newGameButton.setText("🎮 Создать игру");
        newGameButton.setCallbackData("command_newgame");
        row.add(newGameButton);
        
        InlineKeyboardButton joinGameButton = new InlineKeyboardButton();
        joinGameButton.setText("🔑 Присоединиться");
        joinGameButton.setCallbackData("command_promptjoin");
        row.add(joinGameButton);
        
        keyboard.add(row);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending help message", e);
            bot.sendTextMessage(chatId, helpMessage);
        }
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
}
// COMPLETED: CallbackQueryHandler class 