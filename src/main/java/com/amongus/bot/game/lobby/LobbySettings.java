package com.amongus.bot.game.lobby;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents configurable settings for a game lobby.
 */
public class LobbySettings {
    private static final Logger logger = LoggerFactory.getLogger(LobbySettings.class);
    
    // Default values
    public static final int DEFAULT_IMPOSTOR_COUNT = 1;
    public static final int DEFAULT_EMERGENCY_MEETINGS = 1;
    public static final int DEFAULT_DISCUSSION_TIME = 90; // seconds
    public static final int DEFAULT_VOTING_TIME = 60; // seconds
    public static final int DEFAULT_TASKS_PER_PLAYER = 3;
    public static final int DEFAULT_KILL_COOLDOWN = 45; // seconds
    
    // Setting keys
    public static final String IMPOSTOR_COUNT = "impostor_count";
    public static final String EMERGENCY_MEETINGS = "emergency_meetings";
    public static final String DISCUSSION_TIME = "discussion_time";
    public static final String VOTING_TIME = "voting_time";
    public static final String TASKS_PER_PLAYER = "tasks_per_player";
    public static final String KILL_COOLDOWN = "kill_cooldown";
    
    // Mapping from user-friendly names to internal keys
    private static final Map<String, String> SETTING_NAME_MAP = new HashMap<>();
    static {
        SETTING_NAME_MAP.put("impostors", IMPOSTOR_COUNT);
        SETTING_NAME_MAP.put("impostor_count", IMPOSTOR_COUNT);
        SETTING_NAME_MAP.put("impostorcount", IMPOSTOR_COUNT);
        
        SETTING_NAME_MAP.put("meetings", EMERGENCY_MEETINGS);
        SETTING_NAME_MAP.put("emergency_meetings", EMERGENCY_MEETINGS);
        SETTING_NAME_MAP.put("emergencymeetings", EMERGENCY_MEETINGS);
        
        SETTING_NAME_MAP.put("discussion", DISCUSSION_TIME);
        SETTING_NAME_MAP.put("discussion_time", DISCUSSION_TIME);
        SETTING_NAME_MAP.put("discussiontime", DISCUSSION_TIME);
        
        SETTING_NAME_MAP.put("voting", VOTING_TIME);
        SETTING_NAME_MAP.put("voting_time", VOTING_TIME);
        SETTING_NAME_MAP.put("votingtime", VOTING_TIME);
        
        SETTING_NAME_MAP.put("tasks", TASKS_PER_PLAYER);
        SETTING_NAME_MAP.put("tasks_per_player", TASKS_PER_PLAYER);
        SETTING_NAME_MAP.put("tasksperplayer", TASKS_PER_PLAYER);
        
        SETTING_NAME_MAP.put("cooldown", KILL_COOLDOWN);
        SETTING_NAME_MAP.put("kill_cooldown", KILL_COOLDOWN);
        SETTING_NAME_MAP.put("killcooldown", KILL_COOLDOWN);
    }
    
    // Setting constraints
    private static final Map<String, Integer> MIN_VALUES = new HashMap<>();
    private static final Map<String, Integer> MAX_VALUES = new HashMap<>();
    
    static {
        MIN_VALUES.put(IMPOSTOR_COUNT, 1);
        MIN_VALUES.put(EMERGENCY_MEETINGS, 0);
        MIN_VALUES.put(DISCUSSION_TIME, 30);
        MIN_VALUES.put(VOTING_TIME, 30);
        MIN_VALUES.put(TASKS_PER_PLAYER, 1);
        MIN_VALUES.put(KILL_COOLDOWN, 10);
        
        MAX_VALUES.put(IMPOSTOR_COUNT, 3);
        MAX_VALUES.put(EMERGENCY_MEETINGS, 5);
        MAX_VALUES.put(DISCUSSION_TIME, 180);
        MAX_VALUES.put(VOTING_TIME, 180);
        MAX_VALUES.put(TASKS_PER_PLAYER, 10);
        MAX_VALUES.put(KILL_COOLDOWN, 120);
    }
    
    private final Map<String, Integer> settings;
    
    public LobbySettings() {
        settings = new HashMap<>();
        resetToDefaults();
    }
    
    /**
     * Resets all settings to their default values.
     */
    public void resetToDefaults() {
        settings.put(IMPOSTOR_COUNT, DEFAULT_IMPOSTOR_COUNT);
        settings.put(EMERGENCY_MEETINGS, DEFAULT_EMERGENCY_MEETINGS);
        settings.put(DISCUSSION_TIME, DEFAULT_DISCUSSION_TIME);
        settings.put(VOTING_TIME, DEFAULT_VOTING_TIME);
        settings.put(TASKS_PER_PLAYER, DEFAULT_TASKS_PER_PLAYER);
        settings.put(KILL_COOLDOWN, DEFAULT_KILL_COOLDOWN);
        
        logger.info("Reset lobby settings to defaults");
    }
    
    /**
     * Updates a specific setting if the value is valid.
     * 
     * @param setting The setting to update (user-friendly name or internal key)
     * @param value The new value
     * @return true if the setting was updated, false if the value was invalid
     */
    public boolean updateSetting(String setting, int value) {
        // Map the user-friendly setting name to the internal key if needed
        String settingKey = SETTING_NAME_MAP.getOrDefault(setting.toLowerCase(), setting.toLowerCase());
        
        if (!settings.containsKey(settingKey)) {
            logger.warn("Attempted to update unknown setting: {} (mapped to: {})", setting, settingKey);
            return false;
        }
        
        Integer minValue = MIN_VALUES.get(settingKey);
        Integer maxValue = MAX_VALUES.get(settingKey);
        
        if (value < minValue || value > maxValue) {
            logger.warn("Invalid value {} for setting {}, must be between {} and {}", 
                    value, settingKey, minValue, maxValue);
            return false;
        }
        
        settings.put(settingKey, value);
        logger.info("Updated setting {} to {}", settingKey, value);
        return true;
    }
    
    /**
     * Gets the current value of a setting.
     * 
     * @param setting The setting to retrieve
     * @return The current value, or null if the setting doesn't exist
     */
    public Integer getSetting(String setting) {
        return settings.get(setting);
    }
    
    /**
     * Gets the minimum value for a setting.
     * 
     * @param setting The setting
     * @return The minimum value, or null if the setting doesn't exist
     */
    public Integer getMinValue(String setting) {
        return MIN_VALUES.get(setting);
    }
    
    /**
     * Gets the maximum value for a setting.
     * 
     * @param setting The setting
     * @return The maximum value, or null if the setting doesn't exist
     */
    public Integer getMaxValue(String setting) {
        return MAX_VALUES.get(setting);
    }
    
    /**
     * Gets all current settings.
     * 
     * @return A map of all settings and their values
     */
    public Map<String, Integer> getAllSettings() {
        return new HashMap<>(settings);
    }
    
    public int getImpostorCount() {
        return settings.get(IMPOSTOR_COUNT);
    }
    
    public int getEmergencyMeetings() {
        return settings.get(EMERGENCY_MEETINGS);
    }
    
    public int getDiscussionTime() {
        return settings.get(DISCUSSION_TIME);
    }
    
    public int getVotingTime() {
        return settings.get(VOTING_TIME);
    }
    
    public int getTasksPerPlayer() {
        return settings.get(TASKS_PER_PLAYER);
    }
    
    public int getKillCooldown() {
        return settings.get(KILL_COOLDOWN);
    }
    
    /**
     * Adjusts impostor count based on the number of players.
     * 
     * @param playerCount The number of players in the lobby
     */
    public void adjustImpostorCount(int playerCount) {
        int maxImpostors;
        
        if (playerCount < 5) {
            maxImpostors = 1;
        } else if (playerCount <= 10) {
            maxImpostors = 2;
        } else {
            maxImpostors = 3;
        }
        
        int currentImpostors = getImpostorCount();
        if (currentImpostors > maxImpostors) {
            logger.info("Adjusting impostor count from {} to {} based on player count {}", 
                    currentImpostors, maxImpostors, playerCount);
            updateSetting(IMPOSTOR_COUNT, maxImpostors);
        }
    }
    
    @Override
    public String toString() {
        return "Impostors: " + getImpostorCount() + 
               "\nEmergency Meetings: " + getEmergencyMeetings() +
               "\nDiscussion Time: " + getDiscussionTime() + "s" +
               "\nVoting Time: " + getVotingTime() + "s" +
               "\nTasks Per Player: " + getTasksPerPlayer() +
               "\nKill Cooldown: " + getKillCooldown() + "s";
    }
    
    /**
     * Gets a formatted summary of all settings.
     * 
     * @return A formatted string with all settings
     */
    public String getSettingsSummary() {
        return toString();
    }
} 