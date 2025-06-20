package com.amongus.bot.commands;

import com.amongus.bot.core.AmongUsBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to start interaction with the bot.
 */
public class StartCommand extends BotCommand {
    private static final Logger logger = LoggerFactory.getLogger(StartCommand.class);
    
    private final AmongUsBot bot;
    
    public StartCommand(AmongUsBot bot) {
        super("start", "Start the bot and see available commands");
        this.bot = bot;
    }
    
    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
        logger.info("Received /start command from user {} in chat {}", user.getId(), chat.getId());
        
        // Create welcome message
        String message = "*Welcome to Among Us Telegram Bot!*\n\n" +
                "This bot allows you to play the popular game \"Among Us\" " +
                "directly in Telegram with your friends.\n\n" +
                "Use the buttons below to start a new game or join an existing one:";
        
        // Create keyboard with buttons
        InlineKeyboardMarkup markup = createStartKeyboard();
        
        // Send the message
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat.getId());
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");
        sendMessage.setReplyMarkup(markup);
        
        bot.executeMethod(sendMessage);
    }
    
    /**
     * Creates a keyboard with buttons for the start command.
     * 
     * @return The inline keyboard markup
     */
    private InlineKeyboardMarkup createStartKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // New Game button
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton newGameButton = new InlineKeyboardButton();
        newGameButton.setText("🎮 Create New Game");
        newGameButton.setCallbackData("newgame");
        row1.add(newGameButton);
        keyboard.add(row1);
        
        // Join Game button
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton joinGameButton = new InlineKeyboardButton();
        joinGameButton.setText("🔍 Join Game");
        joinGameButton.setCallbackData("join");
        row2.add(joinGameButton);
        keyboard.add(row2);
        
        // Help button
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ How to Play");
        helpButton.setCallbackData("help");
        row3.add(helpButton);
        keyboard.add(row3);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}
// COMPLETED: StartCommand class 