package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.states.GameActiveState;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

/**
 * Handler for regular text messages and photos.
 */
public class MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);
    
    private final AmongUsBot bot;
    private final LobbyManager lobbyManager;
    
    public MessageHandler(AmongUsBot bot, LobbyManager lobbyManager) {
        this.bot = bot;
        this.lobbyManager = lobbyManager;
    }
    
    public void handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        
        // Find if the user is in an active game
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            // User is not in a game, send default message
            handleDefaultMessage(chatId);
            return;
        }
        
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            // This should not happen but handle it just in case
            logger.warn("Player {} is registered in lobby {} but not found in the lobby", userId, lobby.getLobbyCode());
            handleDefaultMessage(chatId);
            return;
        }
        
        // Check if the game is in active state
        if (lobby.getGameState() instanceof GameActiveState) {
            GameActiveState gameState = (GameActiveState) lobby.getGameState();
            
            // Check for photo messages for sabotage fixes or task completion
            if (message.hasPhoto()) {
                if (gameState.isPlayerFixingLights(userId) || 
                    gameState.isPlayerAtReactorLocation(userId) || 
                    player.isAwaitingPhotoConfirmation() ||
                    player.isAwaitingFakeTaskPhotoConfirmation()) {
                    
                    handlePhotoConfirmation(message, lobby, player);
                    return;
                }
            }
        }
        
        // Check if player is waiting for photo confirmation for tasks or fake tasks
        if ((player.isAwaitingPhotoConfirmation() || player.isAwaitingFakeTaskPhotoConfirmation()) && message.hasPhoto()) {
            handlePhotoConfirmation(message, lobby, player);
            return;
        }
        
        // Handle regular text messages
        if (message.hasText()) {
            String text = message.getText();
            logger.info("Received message from user {}: {}", userId, text);
            
            // If we got here, it's a regular message with no special handling
            handleDefaultMessage(chatId);
        } else if (message.hasPhoto()) {
            // Unrelated photo
            bot.sendTextMessage(chatId, "Я получил ваше фото, но нет действия, ожидающего фото подтверждения.");
        }
    }
    
    /**
     * Handles photo messages for task completion or sabotage fixing.
     * 
     * @param message The incoming message
     * @param lobby The player's game lobby
     * @param player The player who sent the photo
     */
    private void handlePhotoConfirmation(Message message, GameLobby lobby, Player player) {
        Long chatId = message.getChatId();
        Long userId = player.getUserId();
        
        // Check if the message contains a photo
        if (!message.hasPhoto()) {
            bot.sendTextMessage(chatId, "Пожалуйста, отправьте фото для подтверждения вашего действия.");
            return;
        }
        
        // Get photo information
        List<PhotoSize> photos = message.getPhoto();
        logger.info("Received photo from player {} for confirmation in lobby {}", userId, lobby.getLobbyCode());
        
        // Получаем ID фото (берем самый крупный размер)
        String photoFileId = photos.get(photos.size() - 1).getFileId();
        logger.debug("Photo file ID: {}", photoFileId);
        
        // Check if the game is in active state
        if (!(lobby.getGameState() instanceof GameActiveState)) {
            logger.warn("Received photo confirmation from player {} outside of active game state", userId);
            bot.sendTextMessage(chatId, "Подтверждение фото возможно только во время активной фазы игры.");
            return;
        }
        
        GameActiveState gameState = (GameActiveState) lobby.getGameState();
        
        // Check if this is a sabotage fix confirmation
        if (gameState.isPlayerFixingLights(userId)) {
            logger.info("Processing photo for lights fix from player {} in lobby {}", userId, lobby.getLobbyCode());
            // Добавляем событие починки света
            GameEvent fixEvent = lobby.addGameEvent(userId, "FIX_LIGHTS", "Починка света");
            if (fixEvent != null) {
                // Прикрепляем фото к событию
                fixEvent.setPhotoFileId(photoFileId);
                logger.debug("Added photo to FIX_LIGHTS event for player {}", userId);
            }
            
            // Handle lights fix confirmation
            gameState.confirmLightsFix(bot, lobby, player);
            return;
        } else if (gameState.isPlayerAtReactorLocation(userId)) {
            logger.info("Processing photo for reactor location confirmation from player {} in lobby {}", userId, lobby.getLobbyCode());
            // Добавляем событие починки реактора
            GameEvent fixEvent = lobby.addGameEvent(userId, "FIX_REACTOR", "Починка реактора");
            if (fixEvent != null) {
                // Прикрепляем фото к событию
                fixEvent.setPhotoFileId(photoFileId);
                logger.debug("Added photo to FIX_REACTOR event for player {}", userId);
            }
            
            // Handle reactor fix confirmation
            gameState.confirmReactorFix(bot, lobby, player);
            return;
        }
        
        // Check if this is a fake task confirmation from an impostor
        if (player.isImpostor() && player.isAwaitingFakeTaskPhotoConfirmation()) {
            Integer fakeTaskIndex = player.getAwaitingPhotoForFakeTask();
            logger.info("Processing photo for fake task from impostor {} for fake task {} in lobby {}", 
                    userId, fakeTaskIndex, lobby.getLobbyCode());
            
            // Mark the task as completed
            boolean completed = gameState.completeFakeTask(userId, fakeTaskIndex);
            
            // Clear the awaiting status
            player.setAwaitingPhotoForFakeTask(null);
            
            if (completed) {
                // Get the fake task name
                String fakeTaskName = gameState.getFakeTaskName(userId, fakeTaskIndex);
                int completedCount = gameState.getCompletedFakeTaskCount(userId);
                int totalCount = gameState.getTotalFakeTaskCount(userId);
                
                // Добавляем событие выполнения фейкового задания
                GameEvent fakeTaskEvent = lobby.addGameEvent(userId, "FAKE_TASK", fakeTaskName);
                if (fakeTaskEvent != null) {
                    // Прикрепляем фото к событию
                    fakeTaskEvent.setPhotoFileId(photoFileId);
                    logger.debug("Added photo to FAKE_TASK event for player {}", userId);
                }
                
                // Send confirmation message to impostor (using same format as for crewmates)
                bot.sendTextMessage(chatId, 
                    "✅ Проверка фото принята! Задание \"" + fakeTaskName + "\" выполнено!\n" +
                    "Прогресс: " + completedCount + "/" + totalCount + " заданий");
            } else {
                // This should not happen normally, but handle it just in case
                bot.sendTextMessage(chatId, "Это задание уже выполнено или недействительно.");
                logger.warn("Impostor {} attempted to complete invalid fake task {} with photo", 
                        userId, fakeTaskIndex);
            }
            
            // Update player's action keyboard
            gameState.updatePlayerActionKeyboard(bot, lobby, player);
            
            logger.info("Impostor {} completed fake task {} with photo confirmation", userId, fakeTaskIndex);
            return;
        }
        
        // If not a sabotage fix or fake task, check if it's a real task confirmation
        Integer taskIndex = player.getAwaitingPhotoForTaskIndex();
        if (taskIndex == null) {
            logger.warn("Player {} sent a photo but is not awaiting any confirmation", userId);
            bot.sendTextMessage(chatId, "Нет действия, ожидающего подтверждения фотографией.");
            return;
        }
        
        logger.info("Processing photo for task completion from player {} for task {} in lobby {}", 
                userId, taskIndex, lobby.getLobbyCode());
        
        // Complete the task
        boolean completed = player.completeTask(taskIndex);
        
        if (completed) {
            // Clear the awaiting status
            player.setAwaitingPhotoForTaskIndex(null);
            
            // Update the player
            String taskName = player.getTasks().get(taskIndex).getName();
            
            // Добавляем событие выполнения задания
            GameEvent taskEvent = lobby.addGameEvent(userId, "TASK", taskName);
            if (taskEvent != null) {
                // Прикрепляем фото к событию
                taskEvent.setPhotoFileId(photoFileId);
                logger.debug("Added photo to TASK event for player {}", userId);
            }
            
            bot.sendTextMessage(chatId, 
                "✅ Проверка фото принята! Задание \"" + taskName + "\" выполнено!\n" +
                "Прогресс: " + player.getCompletedTaskCount() + "/" + player.getTotalTaskCount() + " заданий");
            
            // Update player's action keyboard
            gameState.updatePlayerActionKeyboard(bot, lobby, player);
            
            logger.info("Player {} completed task {} with photo confirmation", userId, taskIndex);
            
            // Check win conditions
            gameState.checkAndUpdateWinConditions(bot, lobby);
        } else {
            // This should not happen normally, but handle it just in case
            player.setAwaitingPhotoForTaskIndex(null);
            bot.sendTextMessage(chatId, "Это задание уже выполнено или недействительно.");
            logger.warn("Player {} attempted to complete already completed task {} with photo", 
                    userId, taskIndex);
        }
    }
    
    /**
     * Handles default messages when no special handling is needed.
     * 
     * @param chatId The chat ID to send the response to
     */
    private void handleDefaultMessage(Long chatId) {
        bot.sendTextMessage(chatId, "Для взаимодействия с ботом используйте команды, начинающиеся с /. Введите /help, чтобы увидеть доступные команды.");
    }
}
// COMPLETED: MessageHandler class 