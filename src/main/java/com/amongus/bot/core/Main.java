package com.amongus.bot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Main entry point for the Among Us Telegram bot application.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Among Us Telegram Bot...");
        logger.info("Java version: {}", System.getProperty("java.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        
        try {
            logger.info("Initializing Telegram Bots API...");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            
            logger.info("Creating bot instance...");
            AmongUsBot bot = new AmongUsBot();
            
            logger.info("Registering bot with Telegram API...");
            botsApi.registerBot(bot);
            
            logger.info("Bot successfully registered and started!");
            logger.info("Bot username: {}", bot.getBotUsername());
            logger.info("=============================================");
            logger.info("Bot is ready to receive messages");
            logger.info("=============================================");
        } catch (TelegramApiException e) {
            logger.error("Failed to start bot: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
// COMPLETED: Main class 