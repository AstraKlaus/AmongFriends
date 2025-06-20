package com.amongus.bot.game;

/**
 * Game constants to replace hardcoded values throughout the codebase
 */
public final class GameConstants {
    
    // Private constructor to prevent instantiation
    private GameConstants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
    
    // Game timing constants (in seconds)
    public static final int DEFAULT_DISCUSSION_TIME = 30;
    public static final int DEFAULT_VOTING_TIME = 60;
    public static final int REACTOR_MELTDOWN_TIME = 30;
    public static final int LIGHTS_SABOTAGE_DURATION = 45;
    
    // Player limits
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 20;
    public static final int DEFAULT_EMERGENCY_MEETINGS_PER_PLAYER = 1;
    
    // Task completion
    public static final int TOTAL_TASKS_PER_PLAYER = 3;
    public static final double WIN_TASK_COMPLETION_THRESHOLD = 100.0;
    
    // Impostor settings
    public static final int DEFAULT_IMPOSTORS_COUNT = 1;
    public static final int MAX_IMPOSTORS_FOR_SMALL_GAME = 1; // 4-6 players
    public static final int MAX_IMPOSTORS_FOR_MEDIUM_GAME = 2; // 7-9 players
    public static final int MAX_IMPOSTORS_FOR_LARGE_GAME = 3; // 10+ players
    
    // Sabotage cooldowns (in seconds)
    public static final int SABOTAGE_COOLDOWN = 30;
    public static final int KILL_COOLDOWN = 30;
    
    // Message delays (in milliseconds)
    public static final int MESSAGE_DELETION_DELAY = 5000;
    public static final int NOTIFICATION_DELAY = 1000;
    
    // Game settings keys
    public static final String SETTING_DISCUSSION_TIME = "discussionTime";
    public static final String SETTING_VOTING_TIME = "votingTime";
    public static final String SETTING_EMERGENCY_MEETINGS = "emergencyMeetings";
    public static final String SETTING_KILL_COOLDOWN = "killCooldown";
    public static final String SETTING_SABOTAGE_COOLDOWN = "sabotageCooldown";
    public static final String SETTING_TOTAL_TASKS = "totalTasks";
    public static final String SETTING_IMPOSTORS_COUNT = "impostorsCount";
    
    // Button callback prefixes
    public static final String JOIN_GAME_PREFIX = "join_game_";
    public static final String LEAVE_GAME_PREFIX = "leave_game_";
    public static final String START_GAME_PREFIX = "start_game_";
    public static final String SETTINGS_PREFIX = "settings_";
    public static final String VOTE_PREFIX = "vote_";
    public static final String TASK_PREFIX = "task_";
    public static final String SABOTAGE_PREFIX = "sabotage_";
    
    // Error messages
    public static final String ERROR_LOBBY_NOT_FOUND = "Лобби не найдено";
    public static final String ERROR_PLAYER_NOT_FOUND = "Игрок не найден";
    public static final String ERROR_GAME_NOT_ACTIVE = "Игра не активна";
    public static final String ERROR_INVALID_ACTION = "Недопустимое действие";
    public static final String ERROR_INSUFFICIENT_PLAYERS = "Недостаточно игроков";
    
    // Success messages
    public static final String SUCCESS_GAME_STARTED = "Игра началась!";
    public static final String SUCCESS_TASK_COMPLETED = "Задание выполнено!";
    public static final String SUCCESS_VOTE_REGISTERED = "Голос засчитан!";
    
    // Lobby settings defaults
    public static final boolean DEFAULT_ANONYMOUS_VOTING = false;
    public static final boolean DEFAULT_SKIP_VOTING_ENABLED = true;
    public static final int DEFAULT_SKIP_VOTES_REQUIRED = 3;
    
    // Thread pool settings
    public static final int CORE_POOL_SIZE = 5;
    public static final int MAXIMUM_POOL_SIZE = 20;
    public static final long KEEP_ALIVE_TIME = 60L; // seconds
    
    // Performance optimization
    public static final int BULK_OPERATION_THRESHOLD = 5; // When to use bulk operations vs individual
    public static final int CACHE_SIZE = 100; // For any caching mechanisms
} 