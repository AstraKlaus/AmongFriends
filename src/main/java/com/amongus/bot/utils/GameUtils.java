package com.amongus.bot.utils;

import java.util.Random;

/**
 * Utility class with helper methods for the Among Us game.
 */
public class GameUtils {
    
    private static final Random random = new Random();
    
    /**
     * Generates a random hex color code.
     * 
     * @return A string in the format "#RRGGBB"
     */
    public static String getRandomColor() {
        int color = random.nextInt(0xFFFFFF + 1);
        return String.format("#%06x", color);
    }
    
    /**
     * Returns a human-friendly time string from seconds.
     * 
     * @param seconds The time in seconds
     * @return A formatted time string (e.g., "2 minutes 30 seconds")
     */
    public static String formatTimeString(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        
        if (remainingSeconds == 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " " +
                   remainingSeconds + " second" + (remainingSeconds == 1 ? "" : "s");
        }
    }
    
    /**
     * Safely truncates a string to the specified length, adding an ellipsis if needed.
     * 
     * @param str The input string
     * @param maxLength The maximum length
     * @return The truncated string
     */
    public static String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * Escapes special Markdown characters for Telegram's MarkdownV2 format.
     * 
     * @param text The text to escape
     * @return The escaped text
     */
    public static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
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
     * Validates a Telegram username.
     * 
     * @param username The username to validate
     * @return True if the username is valid, false otherwise
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        return username.matches("^[a-zA-Z0-9_]{5,32}$");
    }
    
    /**
     * Returns an emoji representation of a numeric value from 0-100.
     * 
     * @param value The value (0-100)
     * @return A string of emojis representing the value
     */
    public static String getProgressEmoji(int value) {
        int clampedValue = Math.max(0, Math.min(100, value));
        int filledBars = clampedValue / 10;
        
        StringBuilder progress = new StringBuilder();
        
        for (int i = 0; i < 10; i++) {
            if (i < filledBars) {
                progress.append("🟩"); // Filled
            } else {
                progress.append("⬜"); // Empty
            }
        }
        
        return progress.toString();
    }
}
// COMPLETED: GameUtils class 