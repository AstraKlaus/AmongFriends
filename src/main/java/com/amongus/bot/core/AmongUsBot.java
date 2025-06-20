package com.amongus.bot.core;

import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.handlers.CallbackQueryHandler;
import com.amongus.bot.handlers.CommandHandler;
import com.amongus.bot.handlers.MessageHandler;
import com.amongus.bot.handlers.SettingsHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.io.Serializable;

/**
 * Main bot class that handles Telegram updates.
 */
public class AmongUsBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(AmongUsBot.class);
    
    private static final String BOT_USERNAME = "AmongFriendsBot"; // Replace with your bot's username
    private static final String BOT_TOKEN = "6377918744:AAH3OKrxQgjAHMX2gp8NYrKqhwnfpfKJO9c"; // Replace with your bot's token
    
    private final LobbyManager lobbyManager;
    private final CommandHandler commandHandler;
    private final MessageHandler messageHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final SettingsHandler settingsHandler;
    
    public AmongUsBot() {
        logger.debug("Initializing AmongUsBot components...");
        
        logger.debug("Initializing LobbyManager...");
        this.lobbyManager = new LobbyManager();
        
        logger.debug("Initializing CommandHandler...");
        this.commandHandler = new CommandHandler(this, lobbyManager);
        
        logger.debug("Initializing MessageHandler...");
        this.messageHandler = new MessageHandler(this, lobbyManager);
        
        logger.debug("Initializing CallbackQueryHandler...");
        this.callbackQueryHandler = new CallbackQueryHandler(this, lobbyManager);
        
        logger.debug("Initializing SettingsHandler...");
        this.settingsHandler = new SettingsHandler(this, lobbyManager);
        
        logger.info("AmongUsBot initialized successfully");
    }
    
    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }
    
    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                String text = message.getText();
                Long userId = message.getFrom().getId();
                String userName = message.getFrom().getUserName();
                Long chatId = message.getChatId();
                
                logger.debug("Received message from user @{} (ID: {}, Chat: {}): {}", 
                        userName, userId, chatId, text);
                
                if (text.startsWith("/")) {
                    // Process commands
                    logger.debug("Processing command: {}", text);
                    commandHandler.handle(update);
                } else {
                    // Process regular messages
                    logger.debug("Processing regular message: {}", text);
                    messageHandler.handle(update);
                }
            } else if (update.hasCallbackQuery()) {
                // Handle callback queries (inline keyboard button clicks)
                String callbackData = update.getCallbackQuery().getData();
                Long userId = update.getCallbackQuery().getFrom().getId();
                String userName = update.getCallbackQuery().getFrom().getUserName();
                
                logger.info("Received callback query from user @{} (ID: {}): {}", 
                        userName, userId, callbackData);
                
                // Check if this is a settings-related callback
                if (callbackData != null && callbackData.startsWith("settings_")) {
                    logger.info("Handling settings callback: {}", callbackData);
                    settingsHandler.handleCallback(update.getCallbackQuery());
                } else {
                    logger.info("Handling general callback: {}", callbackData);
                    callbackQueryHandler.handle(update);
                }
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                // Process message with photo (for task confirmations)
                logger.debug("Processing message with photo");
                messageHandler.handle(update);
            } else {
                logger.debug("Received unsupported update type: {}", update);
            }
        } catch (Exception e) {
            logger.error("Error handling update: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Utility method to send messages and handle exceptions.
     */
    public <T extends Serializable, M extends BotApiMethod<T>> T executeMethod(M method) {
        try {
            logger.debug("Executing API method: {}", method.getClass().getSimpleName());
            
            if (method instanceof SendMessage) {
                SendMessage message = (SendMessage) method;
                logger.debug("Sending message to chat {}: {}", message.getChatId(), 
                        message.getText().length() > 50 ? message.getText().substring(0, 50) + "..." : message.getText());
                
                if (message.getReplyMarkup() != null && message.getReplyMarkup() instanceof InlineKeyboardMarkup) {
                    InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) message.getReplyMarkup();
                    if (keyboard.getKeyboard() != null && !keyboard.getKeyboard().isEmpty()) {
                        logger.debug("Message has inline keyboard with {} rows", keyboard.getKeyboard().size());
                    }
                }
            }
            
            T result = execute(method);
            logger.debug("API method executed successfully");
            return result;
        } catch (TelegramApiException e) {
            logger.error("Failed to execute method {}: {}", method.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Gets the SettingsHandler instance.
     * 
     * @return The SettingsHandler instance
     */
    public SettingsHandler getSettingsHandler() {
        return settingsHandler;
    }
    
    /**
     * Convenience method to send a text message.
     */
    public void sendTextMessage(Long chatId, String text) {
        logger.debug("Sending text message to chat {}: {}", chatId, text);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        executeMethod(message);
    }
    
    /**
     * Convenience method to delete a message.
     * 
     * @param chatId The chat ID where the message is located
     * @param messageId The ID of the message to delete
     * @return True if the message was successfully deleted, false otherwise
     */
    public boolean deleteMessage(Long chatId, Integer messageId) {
        logger.debug("Deleting message {} in chat {}", messageId, chatId);
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);
        
        try {
            execute(deleteMessage);
            logger.debug("Message deleted successfully");
            return true;
        } catch (TelegramApiException e) {
            logger.error("Failed to delete message: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convenience method to edit a message's text.
     * 
     * @param chatId The chat ID where the message is located
     * @param messageId The ID of the message to edit
     * @param text The new text for the message
     * @return True if the message was successfully edited, false otherwise
     */
    public boolean editMessageText(Long chatId, Integer messageId, String text) {
        logger.debug("Editing message {} in chat {}", messageId, chatId);
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setText(text);
        
        try {
            execute(editMessageText);
            logger.debug("Message text edited successfully");
            return true;
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message text: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convenience method to edit a message's reply markup.
     * 
     * @param chatId The chat ID where the message is located
     * @param messageId The ID of the message to edit
     * @param markup The new markup for the message, or null to remove markup
     * @return True if the markup was successfully edited, false otherwise
     */
    public boolean editMessageReplyMarkup(Long chatId, Integer messageId, InlineKeyboardMarkup markup) {
        logger.debug("Editing message reply markup {} in chat {}", messageId, chatId);
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(markup);
        
        try {
            execute(editMarkup);
            logger.debug("Message reply markup edited successfully");
            return true;
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message reply markup: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Выполняет обработку callback запроса как будто пользователь нажал на кнопку.
     * Это позволяет программно имитировать нажатие на кнопку с указанным callbackData.
     *
     * @param callbackData Данные callback запроса (то, что обычно передается при нажатии на кнопку)
     * @param chatId ID чата пользователя
     */
    public void executeCallbackQuery(String callbackData, Long chatId) {
        logger.debug("Executing callback query programmatically: {}", callbackData);
        
        if (callbackData.startsWith("settings_")) {
            logger.info("Redirecting to settings handler: {}", callbackData);
            settingsHandler.handleCallback(createCallbackQuery(callbackData, chatId));
        } else {
            logger.info("Redirecting to general callback handler: {}", callbackData);
            Update update = new Update();
            update.setCallbackQuery(createCallbackQuery(callbackData, chatId));
            callbackQueryHandler.handle(update);
        }
    }
    
    /**
     * Создает объект CallbackQuery для программного использования.
     *
     * @param callbackData Данные callback запроса
     * @param chatId ID чата пользователя
     * @return Объект CallbackQuery
     */
    private CallbackQuery createCallbackQuery(String callbackData, Long chatId) {
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData(callbackData);
        callbackQuery.setId(String.valueOf(System.currentTimeMillis())); // Уникальный ID
        
        org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
        user.setId(chatId);
        callbackQuery.setFrom(user);
        
        org.telegram.telegrambots.meta.api.objects.Message message = new org.telegram.telegrambots.meta.api.objects.Message();
        org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
        chat.setId(chatId);
        message.setChat(chat);
        callbackQuery.setMessage(message);
        
        return callbackQuery;
    }
}
// COMPLETED: AmongUsBot class 