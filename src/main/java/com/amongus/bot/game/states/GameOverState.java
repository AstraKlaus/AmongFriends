package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Game state for when the game is over and a winner has been determined.
 */
public class GameOverState implements GameState {
    private static final Logger logger = LoggerFactory.getLogger(GameOverState.class);
    
    private static final String STATE_NAME = "GAME_OVER";
    
    private final String winner;
    private final String winReason;
    
    /**
     * Creates a new game over state with the specified winner and win reason.
     * 
     * @param winner The winner of the game ("Crewmates" or "Impostors")
     * @param winReason The reason for the win
     */
    public GameOverState(String winner, String winReason) {
        this.winner = winner;
        this.winReason = winReason;
    }
    
    @Override
    public String getStateName() {
        return STATE_NAME;
    }
    
    @Override
    public void onEnter(AmongUsBot bot, GameLobby lobby) {
        logger.info("Entered game over state for game {}, winner: {}", lobby.getLobbyCode(), winner);
        
        // Отправляем краткие результаты игры
        sendGameSummary(bot, lobby);
        
        // Отправляем подробный отчет о событиях в игре
        sendGameReport(bot, lobby);
    }
    
    /**
     * Отправляет краткие результаты игры всем игрокам.
     */
    private void sendGameSummary(AmongUsBot bot, GameLobby lobby) {
        StringBuilder message = new StringBuilder();
        message.append("🏁 **ИГРА ОКОНЧЕНА!**\n\n");
        
        // Результат игры
        String winnerEmoji = winner.equalsIgnoreCase("Crewmates") ? "👨‍🚀" : "🔪";
        message.append(winnerEmoji).append(" **").append(winner).append(" ПОБЕДИЛИ!**\n");
        message.append("📋 Причина: ").append(winReason).append("\n\n");
        
        // Роли игроков
        message.append("👥 **РОЛИ ИГРОКОВ:**\n");
        for (Player player : lobby.getPlayerList()) {
            String roleEmoji = player.isImpostor() ? "🔪" : "👨‍🚀";
            String statusEmoji = player.isAlive() ? "✅" : "💀";
            String roleName = player.isImpostor() ? "Предатель" : "Член экипажа";
            
            message.append(statusEmoji).append(" ").append(player.getUserName())
                   .append(" - ").append(roleEmoji).append(" ").append(roleName);
            
            // Для членов экипажа показываем прогресс заданий
            if (!player.isImpostor()) {
                int completed = player.getCompletedTaskCount();
                int total = player.getTotalTaskCount();
                message.append(" (").append(completed).append("/").append(total).append(" заданий)");
            }
            message.append("\n");
        }
        
        // Отправляем сводку всем игрокам
        for (Player player : lobby.getPlayerList()) {
            try {
                Long chatId = player.getChatId() != null ? player.getChatId() : player.getUserId();
                bot.sendTextMessage(chatId, message.toString());
            } catch (Exception e) {
                logger.error("Failed to send game summary to player {}: {}", player.getUserId(), e.getMessage());
            }
        }
    }
    
    /**
     * Формирует и отправляет подробный отчет о событиях в игре.
     * 
     * @param bot Бот для отправки сообщений
     * @param lobby Игровое лобби
     */
    private void sendGameReport(AmongUsBot bot, GameLobby lobby) {
        logger.info("Preparing game report for lobby {}", lobby.getLobbyCode());
        
        List<GameEvent> events = lobby.getSortedGameEvents();
        logger.info("Found {} events for lobby {}", events.size(), lobby.getLobbyCode());
        
        // Создаем системное событие окончания игры, если список пуст
        if (events.isEmpty()) {
            logger.warn("No game events found for lobby {}, adding system game over event", lobby.getLobbyCode());
            Long hostId = lobby.getHostId();
            GameEvent systemEvent = lobby.addGameEvent(hostId, "GAME_OVER", "Игра завершена: " + winner + " победили");
            if (systemEvent != null) {
                events = lobby.getSortedGameEvents();
            } else {
                Player host = lobby.getPlayer(hostId);
                if (host != null) {
                    GameEvent tempEvent = new GameEvent(hostId, host.getUserName(), "GAME_OVER", "Игра завершена: " + winner + " победили");
                    events = new ArrayList<>();
                    events.add(tempEvent);
                }
            }
        }

        // Строим структурированный отчет
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("📊 **ПОДРОБНЫЙ ОТЧЕТ О ИГРЕ**\n\n");
        
        // Разделяем события по категориям
        List<GameEvent> killEvents = new ArrayList<>();
        List<GameEvent> taskEvents = new ArrayList<>();
        List<GameEvent> meetingEvents = new ArrayList<>();
        List<GameEvent> sabotageEvents = new ArrayList<>();
        List<GameEvent> voteEvents = new ArrayList<>();
        List<GameEvent> otherEvents = new ArrayList<>();
        
        for (GameEvent event : events) {
            String action = event.getAction().toUpperCase();
            switch (action) {
                case "KILL":
                    killEvents.add(event);
                    break;
                case "TASK":
                case "FAKE_TASK":
                    taskEvents.add(event);
                    break;
                case "REPORT":
                case "MEETING":
                    meetingEvents.add(event);
                    break;
                case "SABOTAGE":
                case "FIX_LIGHTS":
                case "FIX_REACTOR":
                    sabotageEvents.add(event);
                    break;
                case "VOTE":
                case "VOTE_RESULT":
                    voteEvents.add(event);
                    break;
                default:
                    otherEvents.add(event);
            }
        }
        
        // Хронология событий
        reportBuilder.append("⏰ **ХРОНОЛОГИЯ СОБЫТИЙ:**\n");
        if (!events.isEmpty()) {
            boolean inVotingPhase = false;
            
            for (int i = 0; i < events.size(); i++) {
                GameEvent event = events.get(i);
                String action = event.getAction().toUpperCase();
                
                // Проверяем начало голосования (обнаружение тела или экстренное собрание)
                if ((action.equals("REPORT") || action.equals("MEETING")) && !inVotingPhase) {
                    reportBuilder.append("\n🔍 **--- НАЧАЛО ГОЛОСОВАНИЯ ---**\n");
                    inVotingPhase = true;
                }
                
                reportBuilder.append(String.format("%d. %s\n", i + 1, event.getFormattedDescription()));
                
                // Проверяем конец голосования (результат голосования или исключение)
                if ((action.equals("VOTE_RESULT") || action.equals("EJECTED")) && inVotingPhase) {
                    reportBuilder.append("🏁 **--- КОНЕЦ ГОЛОСОВАНИЯ ---**\n\n");
                    inVotingPhase = false;
                }
            }
        } else {
            reportBuilder.append("• Нет зарегистрированных событий\n");
        }
        
        // Статистика по категориям
        reportBuilder.append("\n📈 **СТАТИСТИКА СОБЫТИЙ:**\n");
        
        if (!killEvents.isEmpty()) {
            reportBuilder.append("🔪 **Убийства (").append(killEvents.size()).append("):**\n");
            for (GameEvent event : killEvents) {
                reportBuilder.append("  • ").append(event.getFormattedDescription()).append("\n");
            }
            reportBuilder.append("\n");
        }
        
        if (!meetingEvents.isEmpty()) {
            reportBuilder.append("🚨 **Собрания и находки тел (").append(meetingEvents.size()).append("):**\n");
            for (GameEvent event : meetingEvents) {
                reportBuilder.append("  • ").append(event.getFormattedDescription()).append("\n");
            }
            reportBuilder.append("\n");
        }
        
        if (!voteEvents.isEmpty()) {
            reportBuilder.append("🗳️ **Голосования (").append(voteEvents.size()).append("):**\n");
            for (GameEvent event : voteEvents) {
                reportBuilder.append("  • ").append(event.getFormattedDescription()).append("\n");
            }
            reportBuilder.append("\n");
        }
        
        if (!sabotageEvents.isEmpty()) {
            reportBuilder.append("⚡ **Саботаж и починки (").append(sabotageEvents.size()).append("):**\n");
            for (GameEvent event : sabotageEvents) {
                reportBuilder.append("  • ").append(event.getFormattedDescription()).append("\n");
            }
            reportBuilder.append("\n");
        }
        
        if (!taskEvents.isEmpty()) {
            reportBuilder.append("📋 **Задания (").append(taskEvents.size()).append("):**\n");
            for (GameEvent event : taskEvents) {
                reportBuilder.append("  • ").append(event.getFormattedDescription()).append("\n");
            }
            reportBuilder.append("\n");
        }
        
        // Индивидуальная статистика игроков
        reportBuilder.append("👤 **ИНДИВИДУАЛЬНАЯ СТАТИСТИКА:**\n");
        
        // Подсчет действий по игрокам
        Map<Long, Map<String, Integer>> playerStats = new HashMap<>();
        
        // Инициализация счетчиков
        for (Player player : lobby.getPlayerList()) {
            playerStats.put(player.getUserId(), new HashMap<>());
        }
        
        // Подсчет действий
        for (GameEvent event : events) {
            Long userId = event.getUserId();
            String action = event.getAction();
            
            Map<String, Integer> stats = playerStats.get(userId);
            if (stats != null) {
                stats.put(action, stats.getOrDefault(action, 0) + 1);
            }
        }
        
        // Выводим статистику каждого игрока
        for (Player player : lobby.getPlayerList()) {
            Long userId = player.getUserId();
            Map<String, Integer> stats = playerStats.get(userId);
            
            String roleEmoji = player.isImpostor() ? "🔪" : "👨‍🚀";
            String roleName = player.isImpostor() ? "Предатель" : "Член экипажа";
            String statusEmoji = player.isAlive() ? "✅" : "💀";
            
            reportBuilder.append("\n").append(statusEmoji).append(" **").append(player.getUserName())
                         .append("** (").append(roleEmoji).append(" ").append(roleName).append("):\n");
            
            // Выводим выполненные задания для членов экипажа
            if (!player.isImpostor()) {
                int completedTasks = player.getCompletedTaskCount();
                int totalTasks = player.getTotalTaskCount();
                int percentage = player.getTaskCompletionPercentage();
                reportBuilder.append("  📋 Задания: ").append(completedTasks).append("/").append(totalTasks)
                             .append(" (").append(percentage).append("%)\n");
            }
            
            // Выводим статистику действий
            if (stats != null && !stats.isEmpty()) {
                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    String actionName = getActionDisplayName(entry.getKey());
                    // Пропускаем действия, для которых getActionDisplayName возвращает null
                    if (actionName != null) {
                        reportBuilder.append("  • ").append(actionName).append(": ").append(entry.getValue()).append("\n");
                    }
                }
            } else {
                reportBuilder.append("  • Нет зарегистрированных действий\n");
            }
        }
        
        // Отправляем отчет всем игрокам
        String report = reportBuilder.toString();
        logger.info("Sending game report to {} players in lobby {}", lobby.getPlayerCount(), lobby.getLobbyCode());
        
        for (Player player : lobby.getPlayerList()) {
            try {
                Long chatId = player.getChatId() != null ? player.getChatId() : player.getUserId();
                bot.sendTextMessage(chatId, report);
                logger.info("Sent game report to player {} ({})", player.getUserName(), chatId);
                
                // Отправляем фотографии событий, если они есть
                if (!events.isEmpty()) {
                    sendEventPhotos(bot, lobby, player, events);
                }
                
                // Добавляем кнопку новой игры только для хоста после отчета
                if (lobby.isHost(player.getUserId())) {
                    InlineKeyboardMarkup newGameKeyboard = createNewGameKeyboard();
                    SendMessage newGameMessage = new SendMessage();
                    newGameMessage.setChatId(chatId);
                    newGameMessage.setText("🎮 **Готовы к новой игре?**");
                    newGameMessage.setReplyMarkup(newGameKeyboard);
                    bot.executeMethod(newGameMessage);
                }
                
            } catch (Exception e) {
                logger.error("Failed to send game report to player {}: {}", player.getUserId(), e.getMessage(), e);
                
                // Пробуем отправить простое сообщение
                try {
                    Long chatId = player.getChatId() != null ? player.getChatId() : player.getUserId();
                    bot.sendTextMessage(chatId, "Игра окончена! " + winner + " победили!\n\nПодробный отчет не может быть отправлен из-за технической ошибки.");
                } catch (Exception ex) {
                    logger.error("Failed even simple message to player {}: {}", player.getUserId(), ex.getMessage());
                }
            }
        }
    }
    
    /**
     * Преобразует код действия в читаемое название.
     * Возвращает null для действий, которые не нужно отображать в индивидуальной статистике.
     */
    private String getActionDisplayName(String action) {
        switch (action.toUpperCase()) {
            case "KILL":
                return null; // Не отображаем убийства в индивидуальной статистике
            case "TASK":
                return "📋 Выполнено заданий";
            case "REPORT":
                return "🚨 Сообщений о теле";
            case "MEETING":
                return "📢 Созвано собраний";
            case "VOTE":
                return "🗳️ Проголосовано";
            case "EJECTED":
                return "🚪 Исключён голосованием";
            case "SABOTAGE":
                return "⚡ Саботажей";
            case "FIX_LIGHTS":
                return "💡 Починок света";
            case "FIX_REACTOR":
                return "⚛️ Починок реактора";
            case "SCAN":
                return "🔍 Сканирований";
            case "FAKE_TASK":
                return "🎭 Имитаций заданий";
            case "GAME_OVER":
                return "🏁 Окончание игры";
            case "VOTE_RESULT":
                return "📊 Результатов голосования";
            default:
                return action;
        }
    }
    
    /**
     * Отправляет фотографии событий игроку.
     * 
     * @param bot Бот для отправки сообщений
     * @param lobby Игровое лобби
     * @param player Игрок, которому отправляются фото
     * @param events Список событий
     */
    private void sendEventPhotos(AmongUsBot bot, GameLobby lobby, Player player, List<GameEvent> events) {
        List<GameEvent> eventsWithPhotos = new ArrayList<>();
        
        // Фильтруем события с фотографиями
        for (GameEvent event : events) {
            if (event != null && event.hasPhoto()) {
                eventsWithPhotos.add(event);
            }
        }
        
        if (eventsWithPhotos.isEmpty()) {
            logger.debug("No events with photos found for player {} in lobby {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        // Отправляем сообщение о наличии фотографий
        Long chatId = player.getChatId();
        if (chatId == null) {
            chatId = player.getUserId();
        }
        
        try {
            bot.sendTextMessage(chatId, "Фотоотчет игры:");
            logger.info("Sending {} photos to player {} in lobby {}", 
                    eventsWithPhotos.size(), player.getUserName(), lobby.getLobbyCode());
            
            // Отправляем каждую фотографию с подписью
            for (GameEvent event : eventsWithPhotos) {
                try {
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId);
                    sendPhoto.setPhoto(new InputFile(event.getPhotoFileId()));
                    sendPhoto.setCaption(event.getFormattedDescription());
                    
                    bot.execute(sendPhoto);
                    
                    // Делаем небольшую задержку, чтобы не превысить лимиты Telegram API
                    Thread.sleep(100);
                } catch (Exception e) {
                    logger.error("Failed to send photo for event {}: {}", 
                            event.getFormattedDescription(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send photo report to player {}: {}", 
                    player.getUserId(), e.getMessage());
        }
    }
    
    @Override
    public void onExit(AmongUsBot bot, GameLobby lobby) {
        logger.info("Exited game over state for game {}", lobby.getLobbyCode());
        
        // Очищаем историю событий при выходе из состояния завершения игры
        lobby.clearGameEvents();
    }
    
    @Override
    public GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long userId = update.getCallbackQuery().getFrom().getId();
            
            if (callbackData.equals("new_game") && lobby.isHost(userId)) {
                // Create a new game with the same players
                logger.info("Starting a new game from game {}", lobby.getLobbyCode());
                
                // Reset all players
                for (Player player : lobby.getPlayerList()) {
                    player.reset();
                    logger.debug("Reset player {} state for new game in lobby {}", 
                            player.getUserId(), lobby.getLobbyCode());
                    
                    if (player.getChatId() != null) {
                        bot.sendTextMessage(player.getChatId(), 
                                "🎮 Новая игра создана! Возвращаемся в лобби...");
                    }
                }
                
                // Return to the lobby state
                return new LobbyState();
            }
        }
        
        return null; // Stay in the current state
    }
    
    @Override
    public boolean canPerformAction(GameLobby lobby, Long userId, String action) {
        if (action.equals("new_game")) {
            return lobby.isHost(userId);
        }
        return false;
    }
    
    /**
     * Creates a keyboard with a button to start a new game.
     * 
     * @return The inline keyboard markup
     */
    private InlineKeyboardMarkup createNewGameKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton newGameButton = new InlineKeyboardButton();
        newGameButton.setText("Начать новую игру");
        newGameButton.setCallbackData("new_game");
        row.add(newGameButton);
        keyboard.add(row);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}
// COMPLETED: GameOverState class 