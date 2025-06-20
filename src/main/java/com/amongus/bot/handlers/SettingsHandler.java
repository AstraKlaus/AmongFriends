package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.managers.LobbyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles settings-related button callbacks in the Among Us bot.
 */
public class SettingsHandler {
    private static final Logger logger = LoggerFactory.getLogger(SettingsHandler.class);
    
    private final AmongUsBot bot;
    private final LobbyManager lobbyManager;
    
    // Centralized mapping for setting names to avoid code duplication
    private static final Map<String, String> SETTING_KEY_MAP = new HashMap<>();
    static {
        SETTING_KEY_MAP.put("impostor_count", LobbySettings.IMPOSTOR_COUNT);
        SETTING_KEY_MAP.put("emergency_meetings", LobbySettings.EMERGENCY_MEETINGS);
        SETTING_KEY_MAP.put("discussion_time", LobbySettings.DISCUSSION_TIME);
        SETTING_KEY_MAP.put("voting_time", LobbySettings.VOTING_TIME);
        SETTING_KEY_MAP.put("tasks_per_player", LobbySettings.TASKS_PER_PLAYER);
        SETTING_KEY_MAP.put("kill_cooldown", LobbySettings.KILL_COOLDOWN);
    }
    
    public SettingsHandler(AmongUsBot bot, LobbyManager lobbyManager) {
        this.bot = bot;
        this.lobbyManager = lobbyManager;
        logger.info("SettingsHandler initialized");
    }
    
    /**
     * Maps setting names to internal keys, handling various name formats.
     * 
     * @param setting The setting name from callback
     * @return The internal setting key
     */
    private String mapSettingToKey(String setting) {
        // First try exact match
        if (SETTING_KEY_MAP.containsKey(setting)) {
            return SETTING_KEY_MAP.get(setting);
        }
        
        // Handle partial matches for backward compatibility
        if (setting.contains("impostor")) return LobbySettings.IMPOSTOR_COUNT;
        if (setting.contains("emergency")) return LobbySettings.EMERGENCY_MEETINGS;
        if (setting.contains("discussion")) return LobbySettings.DISCUSSION_TIME;
        if (setting.contains("voting")) return LobbySettings.VOTING_TIME;
        if (setting.contains("tasks")) return LobbySettings.TASKS_PER_PLAYER;
        if (setting.contains("kill")) return LobbySettings.KILL_COOLDOWN;
        
        // Fallback to original setting if no mapping found
        return setting;
    }
    
    /**
     * Gets default maximum values for settings that don't have them configured.
     * 
     * @param settingKey The setting key
     * @return Default maximum value
     */
    private int getDefaultMaxValue(String settingKey) {
        switch (settingKey) {
            case LobbySettings.IMPOSTOR_COUNT: return 4;
            case LobbySettings.EMERGENCY_MEETINGS: return 5;
            case LobbySettings.DISCUSSION_TIME: return 180;
            case LobbySettings.VOTING_TIME: return 180;
            case LobbySettings.TASKS_PER_PLAYER: return 10;
            case LobbySettings.KILL_COOLDOWN: return 360;
            default: return Integer.MAX_VALUE;
        }
    }
    
    /**
     * Gets default minimum values for settings that don't have them configured.
     * 
     * @param settingKey The setting key
     * @return Default minimum value
     */
    private int getDefaultMinValue(String settingKey) {
        switch (settingKey) {
            case LobbySettings.IMPOSTOR_COUNT: return 1;
            case LobbySettings.EMERGENCY_MEETINGS: return 0;
            case LobbySettings.DISCUSSION_TIME: return 30;
            case LobbySettings.VOTING_TIME: return 30;
            case LobbySettings.TASKS_PER_PLAYER: return 1;
            case LobbySettings.KILL_COOLDOWN: return 10;
            default: return 0;
        }
    }
    
    /**
     * Handles a settings-related callback query.
     * 
     * @param callbackQuery The callback query from Telegram
     */
    public void handleCallback(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        // Extract the parts of the callback data
        String[] parts = callbackData.split("_");
        
        if (parts.length < 2) {
            logger.warn("Malformed settings callback data: {}", callbackData);
            acknowledgeCallbackQuery(callbackQuery.getId(), "Invalid command");
            return;
        }
        
        // Check if the user is in a lobby
        GameLobby lobby = lobbyManager.getLobbyForPlayer(userId);
        if (lobby == null) {
            logger.warn("User {} attempted to change settings but is not in a lobby", userId);
            acknowledgeCallbackQuery(callbackQuery.getId(), "You are not in a lobby");
            return;
        }
        
        // Check if the user is the host
        if (!lobby.isHost(userId)) {
            logger.warn("User {} attempted to change settings but is not the host", userId);
            acknowledgeCallbackQuery(callbackQuery.getId(), "Only the host can change settings");
            return;
        }
        
        String action = parts[1];
        
        switch (action) {
            case "open":
                handleOpenSettings(lobby, chatId, messageId);
                break;
            case "update":
                if (parts.length >= 4) {
                    String setting = parts[2];
                    int value = Integer.parseInt(parts[3]);
                    handleUpdateSetting(lobby, setting, value, chatId, messageId);
                }
                break;
            case "increase":
                if (parts.length >= 3) {
                    String setting = parts[2];
                    handleIncreaseSetting(lobby, setting, chatId, messageId);
                }
                break;
            case "decrease":
                if (parts.length >= 3) {
                    String setting = parts[2];
                    handleDecreaseSetting(lobby, setting, chatId, messageId);
                }
                break;
            case "reset":
                handleResetSettings(lobby, chatId, messageId);
                break;
            case "close":
                handleCloseSettings(chatId, messageId);
                break;
            case "noop":
                // Do nothing, this is for disabled buttons
                logger.debug("Received noop callback from user {}", userId);
                break;
            case "info":
                if (parts.length >= 3) {
                    String setting = parts[2];
                    handleSettingInfo(lobby, setting, callbackQuery.getId());
                }
                break;
            default:
                logger.warn("Unknown settings action: {}", action);
                acknowledgeCallbackQuery(callbackQuery.getId(), "Unknown command");
                return;
        }
        
        acknowledgeCallbackQuery(callbackQuery.getId(), null);
    }
    
    /**
     * Opens the settings menu.
     * 
     * @param lobby The game lobby
     * @param chatId The chat ID
     * @param messageId The message ID to edit or null to send a new message
     */
    public void handleOpenSettings(GameLobby lobby, Long chatId, Integer messageId) {
        String text = formatSettingsMessage(lobby);
        InlineKeyboardMarkup keyboard = createSettingsKeyboard(lobby);
        
        try {
            if (messageId != null) {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId);
                editMessage.setMessageId(messageId);
                editMessage.setText(text);
                editMessage.setReplyMarkup(keyboard);
                editMessage.enableMarkdown(true);
                bot.execute(editMessage);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText(text);
                message.setReplyMarkup(keyboard);
                message.enableMarkdown(true);
                bot.execute(message);
            }
            logger.info("Displayed settings menu for lobby {}", lobby.getLobbyCode());
        } catch (TelegramApiException e) {
            logger.error("Error sending settings menu", e);
        }
    }
    
    /**
     * Updates a specific setting.
     * 
     * @param lobby The game lobby
     * @param setting The setting to update
     * @param value The new value
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     */
    private void handleUpdateSetting(GameLobby lobby, String setting, int value, Long chatId, Integer messageId) {
        if (lobby.updateSetting(setting, value)) {
            logger.info("Updated setting {} to {} for lobby {}", setting, value, lobby.getLobbyCode());
            handleOpenSettings(lobby, chatId, messageId);
        }
    }
    
    /**
     * Increases a setting by 1 or 5 for time-based settings.
     * 
     * @param lobby The game lobby
     * @param setting The setting to increase
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     */
    private void handleIncreaseSetting(GameLobby lobby, String setting, Long chatId, Integer messageId) {
        LobbySettings settings = lobby.getSettings();
        String settingKey = mapSettingToKey(setting);
        
        logger.info("Mapped setting '{}' to key '{}'", setting, settingKey);
        
        Integer currentValue = settings.getSetting(settingKey);
        Integer maxValue = settings.getMaxValue(settingKey);
        
        // Check that values are not null and we can increase
        if (currentValue == null) {
            logger.error("Current value for setting {} is null", settingKey);
            return;
        }
        
        if (maxValue == null) {
            maxValue = getDefaultMaxValue(settingKey);
            logger.warn("Missing max value for setting {}, using default: {}", settingKey, maxValue);
        }
        
        // Определяем шаг увеличения в зависимости от типа настройки
        int step = 1;
        if (settingKey.equals(LobbySettings.DISCUSSION_TIME) || 
            settingKey.equals(LobbySettings.VOTING_TIME) || 
            settingKey.equals(LobbySettings.KILL_COOLDOWN)) {
            step = 5;
        }
        
        // Проверяем, что новое значение не превысит максимум
        int newValue = currentValue + step;
        if (newValue > maxValue) {
            newValue = maxValue;
        }
        
        if (currentValue < maxValue) {
            logger.info("Increasing setting {} from {} to {}", settingKey, currentValue, newValue);
            if (lobby.updateSetting(settingKey, newValue)) {
                handleOpenSettings(lobby, chatId, messageId);
            } else {
                logger.warn("Failed to update setting {}", settingKey);
            }
        } else {
            logger.warn("Cannot increase setting {} beyond maximum value {}", settingKey, maxValue);
        }
    }
    
    /**
     * Decreases a setting by 1 or 5 for time-based settings.
     * 
     * @param lobby The game lobby
     * @param setting The setting to decrease
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     */
    private void handleDecreaseSetting(GameLobby lobby, String setting, Long chatId, Integer messageId) {
        LobbySettings settings = lobby.getSettings();
        String settingKey = mapSettingToKey(setting);
        
        logger.info("Mapped setting '{}' to key '{}'", setting, settingKey);
        
        Integer currentValue = settings.getSetting(settingKey);
        Integer minValue = settings.getMinValue(settingKey);
        
        // Check that values are not null and we can decrease
        if (currentValue == null) {
            logger.error("Current value for setting {} is null", settingKey);
            return;
        }
        
        if (minValue == null) {
            minValue = getDefaultMinValue(settingKey);
            logger.warn("Missing min value for setting {}, using default: {}", settingKey, minValue);
        }
        
        // Определяем шаг уменьшения в зависимости от типа настройки
        int step = 1;
        if (settingKey.equals(LobbySettings.DISCUSSION_TIME) || 
            settingKey.equals(LobbySettings.VOTING_TIME) || 
            settingKey.equals(LobbySettings.KILL_COOLDOWN)) {
            step = 5;
        }
        
        // Проверяем, что новое значение не будет меньше минимума
        int newValue = currentValue - step;
        if (newValue < minValue) {
            newValue = minValue;
        }
        
        if (currentValue > minValue) {
            logger.info("Decreasing setting {} from {} to {}", settingKey, currentValue, newValue);
            if (lobby.updateSetting(settingKey, newValue)) {
                handleOpenSettings(lobby, chatId, messageId);
            } else {
                logger.warn("Failed to update setting {}", settingKey);
            }
        } else {
            logger.warn("Cannot decrease setting {} below minimum value {}", settingKey, minValue);
        }
    }
    
    /**
     * Resets all settings to defaults.
     * 
     * @param lobby The game lobby
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     */
    private void handleResetSettings(GameLobby lobby, Long chatId, Integer messageId) {
        lobby.resetSettings();
        handleOpenSettings(lobby, chatId, messageId);
    }
    
    /**
     * Closes the settings menu.
     * 
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     */
    private void handleCloseSettings(Long chatId, Integer messageId) {
        try {
            // Вместо отправки сообщения о сохранении, сразу имитируем нажатие на кнопку "command_status"
            bot.executeCallbackQuery("command_status", chatId);
            
            logger.info("Closed settings menu and redirected to lobby menu");
        } catch (Exception e) {
            logger.error("Error closing settings menu", e);
        }
    }
    
    /**
     * Displays information about a setting.
     * 
     * @param lobby The game lobby
     * @param setting The setting key
     * @param callbackId The callback query ID
     */
    private void handleSettingInfo(GameLobby lobby, String setting, String callbackId) {
        LobbySettings settings = lobby.getSettings();
        String settingKey = mapSettingToKey(setting);
        
        logger.info("Info for setting '{}' mapped to key '{}'", setting, settingKey);
        
        Integer value = settings.getSetting(settingKey);
        Integer min = settings.getMinValue(settingKey);
        Integer max = settings.getMaxValue(settingKey);
        
        // Use defaults if null values
        if (min == null) min = getDefaultMinValue(settingKey);
        if (max == null) max = getDefaultMaxValue(settingKey);
        
        if (value == null) {
            acknowledgeCallbackQuery(callbackId, "Информация о настройке недоступна");
            return;
        }
        
        // Provide helpful info based on the setting
        String message;
        switch(settingKey) {
            case LobbySettings.IMPOSTOR_COUNT:
                message = "Количество Предателей в игре (игроки с возможностью убийства)\nДиапазон: " + min + " - " + max;
                break;
            case LobbySettings.TASKS_PER_PLAYER:
                message = "Количество заданий, назначаемых каждому члену экипажа\nДиапазон: " + min + " - " + max;
                break;
            case LobbySettings.EMERGENCY_MEETINGS:
                message = "Количество экстренных собраний, которые может созвать каждый игрок\nДиапазон: " + min + " - " + max;
                break;
            case LobbySettings.DISCUSSION_TIME:
                message = "Время в секундах для обсуждения перед началом голосования\nДиапазон: " + min + " сек - " + max + " сек";
                break;
            case LobbySettings.VOTING_TIME:
                message = "Время в секундах для голосования игроков\nДиапазон: " + min + " сек - " + max + " сек";
                break;
            case LobbySettings.KILL_COOLDOWN:
                message = "Время в секундах между убийствами Предателя\nДиапазон: " + min + " сек - " + max + " сек";
                break;
            default:
                message = "Значение настройки может быть между " + min + " и " + max;
        }
        
        acknowledgeCallbackQuery(callbackId, message);
    }
    
    /**
     * Formats the settings message.
     * 
     * @param lobby The game lobby
     * @return The formatted message
     */
    private String formatSettingsMessage(GameLobby lobby) {
        LobbySettings settings = lobby.getSettings();
        StringBuilder sb = new StringBuilder();
        
        sb.append("*🎮 Настройки лобби ")
          .append(lobby.getLobbyCode())
          .append("*\n\n");
        
        sb.append("*🔧 Конфигурация игры:*\n")
          .append("• Предатели: ")
          .append(settings.getImpostorCount())
          .append("\n")
          .append("• Заданий на игрока: ")
          .append(settings.getTasksPerPlayer())
          .append("\n")
          .append("• Экстренных собраний: ")
          .append(settings.getEmergencyMeetings())
          .append("\n")
          .append("• Перезарядка убийства: ")
          .append(settings.getKillCooldown())
          .append(" сек\n\n");
        
        sb.append("*⏱ Настройки времени:*\n")
          .append("• Время обсуждения: ")
          .append(settings.getDiscussionTime())
          .append(" сек\n")
          .append("• Время голосования: ")
          .append(settings.getVotingTime())
          .append(" сек\n\n");
        
        sb.append("_Используйте кнопки ниже для изменения настроек_");
        
        return sb.toString();
    }
    
    /**
     * Creates an inline keyboard with settings controls.
     * 
     * @param lobby The game lobby
     * @return An inline keyboard markup
     */
    private InlineKeyboardMarkup createSettingsKeyboard(GameLobby lobby) {
        LobbySettings settings = lobby.getSettings();
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Game settings
        keyboard.add(createSettingRow("Предатели", LobbySettings.IMPOSTOR_COUNT, 
                settings.getImpostorCount(), 
                settings.getMinValue(LobbySettings.IMPOSTOR_COUNT), 
                settings.getMaxValue(LobbySettings.IMPOSTOR_COUNT)));
        
        keyboard.add(createSettingRow("Заданий на игрока", LobbySettings.TASKS_PER_PLAYER, 
                settings.getTasksPerPlayer(), 
                settings.getMinValue(LobbySettings.TASKS_PER_PLAYER), 
                settings.getMaxValue(LobbySettings.TASKS_PER_PLAYER)));
        
        keyboard.add(createSettingRow("Экстренных собраний", LobbySettings.EMERGENCY_MEETINGS, 
                settings.getEmergencyMeetings(), 
                settings.getMinValue(LobbySettings.EMERGENCY_MEETINGS), 
                settings.getMaxValue(LobbySettings.EMERGENCY_MEETINGS)));
        
        keyboard.add(createSettingRow("Перезарядка убийства", LobbySettings.KILL_COOLDOWN, 
                settings.getKillCooldown(), 
                settings.getMinValue(LobbySettings.KILL_COOLDOWN), 
                settings.getMaxValue(LobbySettings.KILL_COOLDOWN)));
        
        // Time settings
        keyboard.add(createSettingRow("Время обсуждения", LobbySettings.DISCUSSION_TIME, 
                settings.getDiscussionTime(), 
                settings.getMinValue(LobbySettings.DISCUSSION_TIME), 
                settings.getMaxValue(LobbySettings.DISCUSSION_TIME)));
        
        keyboard.add(createSettingRow("Время голосования", LobbySettings.VOTING_TIME, 
                settings.getVotingTime(), 
                settings.getMinValue(LobbySettings.VOTING_TIME), 
                settings.getMaxValue(LobbySettings.VOTING_TIME)));
        
        // Action buttons
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        
        InlineKeyboardButton resetButton = new InlineKeyboardButton();
        resetButton.setText("↩️ Сбросить настройки");
        resetButton.setCallbackData("settings_reset");
        actionRow.add(resetButton);
        
        InlineKeyboardButton closeButton = new InlineKeyboardButton();
        closeButton.setText("✅ Сохранить и закрыть");
        closeButton.setCallbackData("settings_close");
        actionRow.add(closeButton);
        
        keyboard.add(actionRow);
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        
        return keyboardMarkup;
    }
    
    /**
     * Creates a row of buttons for a single setting.
     * 
     * @param label The setting label
     * @param setting The setting key
     * @param value The current value
     * @param min The minimum value
     * @param max The maximum value
     * @return A list of buttons for this setting
     */
    private List<InlineKeyboardButton> createSettingRow(String label, String setting, int value, int min, int max) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        // Decrease button
        InlineKeyboardButton decreaseButton = new InlineKeyboardButton();
        if (value > min) {
            decreaseButton.setText("➖");
            decreaseButton.setCallbackData("settings_decrease_" + setting);
        } else {
            decreaseButton.setText("⬛"); // Disabled button
            decreaseButton.setCallbackData("settings_noop");
        }
        row.add(decreaseButton);
        
        // Current value button
        InlineKeyboardButton valueButton = new InlineKeyboardButton();
        
        // Add units for time-based settings
        String displayValue = value + (setting.endsWith("time") ? " сек" : "");
        valueButton.setText(label + ": " + displayValue);
        valueButton.setCallbackData("settings_info_" + setting);
        row.add(valueButton);
        
        // Increase button
        InlineKeyboardButton increaseButton = new InlineKeyboardButton();
        if (value < max) {
            increaseButton.setText("➕");
            increaseButton.setCallbackData("settings_increase_" + setting);
        } else {
            increaseButton.setText("⬛"); // Disabled button
            increaseButton.setCallbackData("settings_noop");
        }
        row.add(increaseButton);
        
        return row;
    }
    
    /**
     * Acknowledges a callback query.
     * 
     * @param callbackId The callback query ID
     * @param text The text to show (or null for no text)
     */
    private void acknowledgeCallbackQuery(String callbackId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        if (text != null) {
            answer.setText(text);
            answer.setShowAlert(true);
        }
        
        try {
            bot.execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Error answering callback query", e);
        }
    }
} 