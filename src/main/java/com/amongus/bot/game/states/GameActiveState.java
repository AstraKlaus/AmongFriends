package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.tasks.Task;
import com.amongus.bot.game.tasks.SimpleTask;
import com.amongus.bot.game.tasks.TaskDifficulty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

/**
 * Game state for when the game is actively being played.
 */
public class GameActiveState implements GameState {
    private static final Logger logger = LoggerFactory.getLogger(GameActiveState.class);
    
    private static final String STATE_NAME = "ACTIVE";
    private static final long SCAN_COOLDOWN_MS = 30000; // 30 seconds cooldown for scan action
    private static final long REACTOR_MELTDOWN_TIME_MS = 90000; // 1.5 minutes for reactor sabotage
    
    // Sabotage types
    public enum SabotageType {
        NONE,
        LIGHTS,
        REACTOR
    }
    
    // Current active sabotage
    private volatile SabotageType activeSabotage = SabotageType.NONE;
    
    // Player waiting to confirm sabotage fix with photo
    private volatile Long playerFixingLights = null;
    
    // Players at reactor locations
    private volatile Long playerAtReactorLocation1 = null;
    private volatile Long playerAtReactorLocation2 = null;
    private volatile boolean photoReceivedFromLocation1 = false;
    private volatile boolean photoReceivedFromLocation2 = false;
    
    // Reactor meltdown timer management
    private volatile Long reactorSabotageStartTime = null;
    private volatile ScheduledFuture<?> reactorMeltdownTimer = null;
    
    // Shared thread pool for all game instances
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // Map to track the last time each player used the scan feature
    private final Map<Long, Long> lastScanTimeByPlayer = new ConcurrentHashMap<>();
    
    // Map to track the last time each impostor killed
    private final Map<Long, Long> lastKillTimeByImpostor = new ConcurrentHashMap<>();
    
    // Map to store fake tasks for each impostor and their completion status
    private final Map<Long, List<String>> fakeTasksByImpostor = new ConcurrentHashMap<>();
    private final Map<Long, List<Boolean>> fakeTaskCompletionByImpostor = new ConcurrentHashMap<>();
    
    // Map to store sabotage menu message IDs for deletion
    private final Map<Long, Integer> sabotageMenuMessageIds = new ConcurrentHashMap<>();
    
    // Instance-based emergency meeting synchronization
    private final AtomicBoolean emergencyMeetingInProgress = new AtomicBoolean(false);
    
    // Global active games registry for cleanup
    private static final Map<String, GameActiveState> activeGames = new ConcurrentHashMap<>();
    
    @Override
    public String getStateName() {
        return STATE_NAME;
    }
    
    @Override
    public void onEnter(AmongUsBot bot, GameLobby lobby) {
        if (lobby == null) {
            logger.error("Cannot enter GameActiveState: lobby is null");
            return;
        }
        
        String lobbyCode = lobby.getLobbyCode();
        logger.info("Entered active game state for game {}", lobbyCode);
        
        // Register this game instance
        activeGames.put(lobbyCode, this);
        
        // Initialize game state
        initializeGameState(lobby);
        
        // Send game start messages and action keyboards to all players
        sendGameStartMessages(bot, lobby);
    }
    
    private void initializeGameState(GameLobby lobby) {
        if (lobby == null || lobby.getSettings() == null) {
            logger.error("Cannot initialize game state: lobby or settings is null");
            return;
        }

        // Initialize game state - tasks are already assigned by SetupState
        // No need to reassign tasks here
        logger.debug("Game state initialized for lobby {}", lobby.getLobbyCode());
    }
    
    private void sendGameStartMessages(AmongUsBot bot, GameLobby lobby) {
        for (Player player : lobby.getPlayerList()) {
            if (player == null) {
                logger.warn("Encountered null player in lobby {}", lobby.getLobbyCode());
                continue;
            }
            
            if (player.getChatId() == null) {
                logger.warn("Player {} has no chatId, skipping game start message", player.getUserId());
                continue;
            }
            
            logger.debug("Sending game start message to player {} in game {}", player.getUserId(), lobby.getLobbyCode());
            
            // Create message with tasks and action buttons
            StringBuilder gameMessage = new StringBuilder();
            gameMessage.append("🎮 *Игра началась!*\n\n");
            
            if (!player.isImpostor()) {
                // Show current tasks for crewmates
                gameMessage.append("📋 *Ваши текущие задания:*\n");
                List<Task> tasks = player.getTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    for (int i = 0; i < tasks.size(); i++) {
                        Task task = tasks.get(i);
                        String status = task.isCompleted() ? "✅" : "⏳";
                        gameMessage.append(status).append(" ").append(i + 1).append(". ").append(task.getName()).append("\n");
                    }
                } else {
                    gameMessage.append("Задания не назначены\n");
                }
                gameMessage.append("\n");
            } else {
                // Show fake tasks for impostors
                gameMessage.append("📋 *Ваши текущие задания:*\n");
                
                // Initialize fake tasks for this impostor if they don't exist yet
                if (!fakeTasksByImpostor.containsKey(player.getUserId())) {
                    initializeFakeTasksForImpostor(player.getUserId(), lobby.getSettings().getTasksPerPlayer());
                }
                
                List<String> impostor_tasks = fakeTasksByImpostor.get(player.getUserId());
                List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(player.getUserId());
                
                if (impostor_tasks != null && !impostor_tasks.isEmpty()) {
                    for (int i = 0; i < impostor_tasks.size(); i++) {
                        String status = (completionStatus != null && i < completionStatus.size() && completionStatus.get(i)) ? "✅" : "⏳";
                        gameMessage.append(status).append(" ").append(i + 1).append(". ").append(impostor_tasks.get(i)).append("\n");
                    }
                } else {
                    gameMessage.append("Задания не назначены\n");
                }
                gameMessage.append("\n");
            }
            
            gameMessage.append("🔽 *Используйте кнопки ниже для выполнения действий:*");
            
            SendMessage message = new SendMessage();
            message.setChatId(player.getChatId());
            message.setText(gameMessage.toString());
            message.setParseMode("Markdown");
            message.setReplyMarkup(createActionKeyboard(lobby, player));
            
            bot.executeMethod(message);
            logger.debug("Sent game start message with keyboard to player {} ({})", player.getUserName(), player.getUserId());
            
            // Send detailed task information message for both crewmates and impostors
            sendDetailedTaskMessage(bot, player, lobby);
        }
    }
    
    private void sendDetailedTaskMessage(AmongUsBot bot, Player player, GameLobby lobby) {
        StringBuilder taskMessage = new StringBuilder();
        taskMessage.append("*Ваши задания:*\n\n");
        
        if (!player.isImpostor()) {
            // Show detailed tasks for crewmates
            List<Task> tasks = player.getTasks();
            if (tasks != null && !tasks.isEmpty()) {
                for (int i = 0; i < tasks.size(); i++) {
                    Task task = tasks.get(i);
                    taskMessage.append("📋 ").append(i + 1).append(". ").append(task.getName())
                               .append(" (").append(task.getDifficulty().getDisplayName()).append(")\n");
                    taskMessage.append("   ").append(task.getDescription()).append("\n\n");
                }
            } else {
                taskMessage.append("Задания не назначены\n");
            }
        } else {
            // Show fake tasks for impostors with descriptions
            List<String> impostor_tasks = fakeTasksByImpostor.get(player.getUserId());
            if (impostor_tasks != null && !impostor_tasks.isEmpty()) {
                for (int i = 0; i < impostor_tasks.size(); i++) {
                    String taskName = impostor_tasks.get(i);
                    taskMessage.append("📋 ").append(i + 1).append(". ").append(taskName).append("\n");
                    taskMessage.append("   ").append(getTaskDescription(taskName)).append("\n\n");
                }
            } else {
                taskMessage.append("Задания не назначены\n");
            }
        }
        
        // Send the task message (permanent)
        SendMessage sendTaskMessage = new SendMessage();
        sendTaskMessage.setChatId(player.getChatId());
        sendTaskMessage.setText(taskMessage.toString());
        sendTaskMessage.setParseMode("Markdown");
        
        bot.executeMethod(sendTaskMessage);
        logger.debug("Sent detailed task information to player {} ({})", player.getUserName(), player.getUserId());
    }
    
    private String getTaskDescription(String taskName) {
        // Mapping task names to descriptions (based on the ones from SetupState)
        switch (taskName) {
            case "Сочный насос": return "Насоси шарик на насос и вдуй ему полностью. (2 этаж)";
            case "Наклейка": return "Наклей стикер на общую доску. (Дом на новом участке -> Старый дом)";
            case "Разукрашка": return "Расскрась зверька 3-мя разными цветами. (2 этаж)";
            case "Холст": return "Продолжи общий рисунок на холсте. (Зал)";
            case "Фотограф": return "Сделай фотографию с цветочками. (Улица)";
            case "Черепашья меткость": return "Попади 4-мя шариками в область белого круга. (Ворота новый участок)";
            case "Спайдер-мен": return "Найди всех паучков. (2 этаж)";
            case "Пазл": return "Собери пазл, больше нечего сказать. (Стол у костра)";
            default: return "Выполните это задание согласно инструкции.";
        }
    }
    
    @Override
    public void onExit(AmongUsBot bot, GameLobby lobby) {
        if (lobby == null) {
            logger.error("Cannot exit GameActiveState: lobby is null");
            return;
        }
        
        String lobbyCode = lobby.getLobbyCode();
        logger.info("Exited active game state for game {}", lobbyCode);
        
        // Clean up resources
        cleanup(lobbyCode);
    }
    
    /**
     * Cleans up all resources associated with this game
     */
    private void cleanup(String lobbyCode) {
        // Reset emergency meeting flag
        emergencyMeetingInProgress.set(false);
        
        // Clean up reactor meltdown timer if active
        if (reactorMeltdownTimer != null && !reactorMeltdownTimer.isDone()) {
            reactorMeltdownTimer.cancel(true);
            reactorMeltdownTimer = null;
        }
        
        // Clear all maps
        lastScanTimeByPlayer.clear();
        lastKillTimeByImpostor.clear();
        fakeTasksByImpostor.clear();
        fakeTaskCompletionByImpostor.clear();
        sabotageMenuMessageIds.clear();
        
        // Reset sabotage state
        activeSabotage = SabotageType.NONE;
        playerFixingLights = null;
        playerAtReactorLocation1 = null;
        playerAtReactorLocation2 = null;
        photoReceivedFromLocation1 = false;
        photoReceivedFromLocation2 = false;
        reactorSabotageStartTime = null;
        
        // Remove from active games
        activeGames.remove(lobbyCode);
        
        logger.debug("Cleaned up game resources for game {}", lobbyCode);
    }
    
    /**
     * Static method to get game instance for a lobby
     */
    public static GameActiveState getGameForLobby(String lobbyCode) {
        return activeGames.get(lobbyCode);
    }
    
    /**
     * Static cleanup method for emergency situations
     */
    public static void forceCleanupAll() {
        for (GameActiveState game : activeGames.values()) {
            game.cleanup("");
        }
        activeGames.clear();
        logger.info("Force cleaned up all game resources");
    }
    
    @Override
    public GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update) {
        if (lobby == null || !update.hasCallbackQuery()) {
            return null;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        // Validate user and player
        if (userId == null) {
            logger.warn("Received callback with null userId in game {}", lobby.getLobbyCode());
            return null;
        }
        
        // Get the player
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            logger.warn("Received callback from non-player user {} in game {}", 
                    userId, lobby.getLobbyCode());
            return null;
        }
        
        // Validate callback data
        if (callbackData == null || callbackData.trim().isEmpty()) {
            logger.warn("Received empty callback data from player {} in game {}", 
                    userId, lobby.getLobbyCode());
            return null;
        }

        // Track if a state transition already occurred in a handler
        boolean stateChanged = false;
        
        // Handle actions based on the callback data
        if (callbackData.startsWith("task:")) {
            handleTaskAction(bot, lobby, player, callbackData);
        } else if (callbackData.equals("report_body")) {
            showConfirmationDialog(bot, player, "report_body_confirm", "Вы действительно хотите сообщить о найденном теле?");
        } else if (callbackData.equals("report_body_confirm")) {
            return handleReportActionConfirmed(bot, lobby, player);
        } else if (callbackData.equals("emergency_meeting")) {
            showConfirmationDialog(bot, player, "emergency_meeting_confirm", "Вы действительно хотите созвать экстренное собрание?");
        } else if (callbackData.equals("emergency_meeting_confirm")) {
            return handleEmergencyMeetingActionConfirmed(bot, lobby, player);
        } else if (callbackData.startsWith("kill:")) {
            handleKillAction(bot, lobby, player, callbackData);
        } else if (callbackData.startsWith("fake_task:")) {
            handleFakeTaskAction(bot, lobby, player, callbackData);
        } else if (callbackData.equals("sabotage_menu")) {
            handleSabotageMenuAction(bot, lobby, player);
        } else if (callbackData.startsWith("sabotage:")) {
            handleSpecificSabotage(bot, lobby, player, callbackData);
        } else if (callbackData.equals("fix_lights")) {
            handleFixLightsAction(bot, lobby, player);
        } else if (callbackData.equals("reactor_location1")) {
            handleReactorLocation1Action(bot, lobby, player);
        } else if (callbackData.equals("reactor_location2")) {
            handleReactorLocation2Action(bot, lobby, player);
        } else if (callbackData.equals("kill_menu")) {
            // This case is no longer used since we removed kill_menu callback
            updatePlayerActionKeyboard(bot, lobby, player); // Just update the keyboard
        } else if (callbackData.equals("kill_cooldown")) {
            handleKillCooldownAction(bot, lobby, player);
        } else if (callbackData.equals("i_was_killed")) {
            showConfirmationDialog(bot, player, "i_was_killed_confirm", "Вы действительно были убиты?");
        } else if (callbackData.equals("i_was_killed_confirm")) {
            handleIWasKilledActionConfirmed(bot, lobby, player);
            // Check if game state was changed to GameOverState by the handler
            stateChanged = (lobby.getGameState() instanceof GameOverState);
        } else if (callbackData.equals("scan")) {
            handleScanAction(bot, lobby, player);
        } else if (callbackData.equals("check")) {
            handleCheckAction(bot, lobby, player);
        } else if (callbackData.equals("back_to_main")) {
            handleBackToMainAction(bot, lobby, player);
        } else if (callbackData.equals("confirmation_cancel")) {
            handleCancelConfirmation(bot, lobby, player);
        }
        
        // Only check win conditions if no state change occurred in the handler
        if (!stateChanged) {
            GameState nextState = checkWinConditions(bot, lobby);
            if (nextState != null) {
                return nextState;
            }
        }
        
        return null;
    }
    
    @Override
    public boolean canPerformAction(GameLobby lobby, Long userId, String action) {
        Player player = lobby.getPlayer(userId);
        if (player == null || !player.isAlive()) {
            return false;
        }
        
        if (action.startsWith("task:") && !player.isImpostor()) {
            return true;
        } else if (action.equals("report_body")) {
            return true;
        } else if (action.equals("emergency_meeting")) {
            return true;
        } else if (action.startsWith("kill:") && player.isImpostor()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Creates an action keyboard based on the player's role.
     * 
     * @param lobby The game lobby
     * @param player The player
     * @return The inline keyboard markup
     */
    private InlineKeyboardMarkup createActionKeyboard(GameLobby lobby, Player player) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        if (player.isImpostor()) {
            // Impostor actions
            createImpostorKeyboard(lobby, player, keyboard);
        } else {
            // Crewmate actions
            createCrewmateKeyboard(lobby, player, keyboard);
        }
        
        // Common actions for all roles
        createCommonActionButtons(keyboard, lobby, player);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Creates action buttons for Impostors.
     * 
     * @param lobby The game lobby
     * @param player The player
     * @param keyboard The keyboard to add buttons to
     */
    private void createImpostorKeyboard(GameLobby lobby, Player player, List<List<InlineKeyboardButton>> keyboard) {
        // Instead of adding kill buttons directly, we'll add a "Task" section that's actually fake tasks
        // This way the UI for all players looks consistent
        
        // Initialize fake tasks for this impostor if they don't exist yet
        if (!fakeTasksByImpostor.containsKey(player.getUserId())) {
            initializeFakeTasksForImpostor(player.getUserId(), lobby.getSettings().getTasksPerPlayer());
        }
        
        // Add fake task buttons with appropriate status icons
        List<String> impostor_tasks = fakeTasksByImpostor.get(player.getUserId());
        List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(player.getUserId());
        
        for (int i = 0; i < impostor_tasks.size(); i++) {
            List<InlineKeyboardButton> taskRow = new ArrayList<>();
            InlineKeyboardButton taskButton = new InlineKeyboardButton();
            
            boolean completed = completionStatus.get(i);
            String status = completed ? "✅" : "📋";
            
            taskButton.setText(status + " " + impostor_tasks.get(i));
            taskButton.setCallbackData("fake_task:" + i);
            taskRow.add(taskButton);
            keyboard.add(taskRow);
        }
        
        // Sabotage menu (проверка систем для импосторов - идет первой)
        List<InlineKeyboardButton> sabotageMenuRow = new ArrayList<>();
        InlineKeyboardButton sabotageMenuButton = new InlineKeyboardButton();
        
        // Use cooldown system for consistency in UI but now for sabotage
        if (isKillOnCooldown(player.getUserId(), lobby)) {
            int remainingCooldown = getKillCooldownRemaining(player.getUserId(), lobby);
            sabotageMenuButton.setText("📊 Проверить системы (" + remainingCooldown + "с)");
            sabotageMenuButton.setCallbackData("kill_cooldown"); // Keep this callback for consistency
        } else {
            sabotageMenuButton.setText("📊 Проверить системы");  // This now opens sabotage menu instead of kill menu
            sabotageMenuButton.setCallbackData("sabotage_menu"); // Changed from kill_menu to sabotage_menu
        }
        
        sabotageMenuRow.add(sabotageMenuButton);
        keyboard.add(sabotageMenuRow);
        
        // Add scan button for impostor (сканирование идет второй кнопкой для импосторов)
        List<InlineKeyboardButton> scanRow = new ArrayList<>();
        InlineKeyboardButton scanButton = new InlineKeyboardButton();
        
        boolean scanOnCooldown = isScanOnCooldown(player.getUserId());
        String scanText = scanOnCooldown 
            ? "🔍 Сканировать аномалии" 
            : "🔍 Сканировать аномалии";
        
        scanButton.setText(scanText);
        scanButton.setCallbackData("scan");
        scanRow.add(scanButton);
        keyboard.add(scanRow);
    }
    
    /**
     * Creates action buttons for Crewmates.
     * 
     * @param lobby The game lobby
     * @param player The player
     * @param keyboard The keyboard to add buttons to
     */
    private void createCrewmateKeyboard(GameLobby lobby, Player player, List<List<InlineKeyboardButton>> keyboard) {
        // Task buttons
        for (int i = 0; i < player.getTasks().size(); i++) {
            List<InlineKeyboardButton> taskRow = new ArrayList<>();
            InlineKeyboardButton taskButton = new InlineKeyboardButton();
            
            String taskName = player.getTasks().get(i).getName();
            boolean completed = player.getTasks().get(i).isCompleted();
            String status = completed ? "✅" : "📋";
            
            taskButton.setText(status + " " + taskName);
            taskButton.setCallbackData("task:" + i);
            taskRow.add(taskButton);
            keyboard.add(taskRow);
        }
        
        // Add placeholder buttons that match the impostor UI structure
        // These are real task-like actions that crew can perform

        // Проверка систем идет первой кнопкой для мирных
        List<InlineKeyboardButton> checkRow = new ArrayList<>();
        InlineKeyboardButton checkButton = new InlineKeyboardButton();
        checkButton.setText("📊 Проверить системы");
        checkButton.setCallbackData("check");
        checkRow.add(checkButton);
        keyboard.add(checkRow);
        
        // Scan button - show cooldown if applicable (сканирование идет второй кнопкой для мирных)
        List<InlineKeyboardButton> scanRow = new ArrayList<>();
        InlineKeyboardButton scanButton = new InlineKeyboardButton();
        
        boolean scanOnCooldown = isScanOnCooldown(player.getUserId());
        String scanText = scanOnCooldown 
            ? "🔍 Сканировать аномалии" 
            : "🔍 Сканировать аномалии";
        
        scanButton.setText(scanText);
        scanButton.setCallbackData("scan");
        scanRow.add(scanButton);
        keyboard.add(scanRow);
    }
    
    /**
     * Checks if scan action is on cooldown for a player.
     * 
     * @param userId The user ID to check
     * @return True if scan is on cooldown, false otherwise
     */
    private boolean isScanOnCooldown(Long userId) {
        Long lastScanTime = lastScanTimeByPlayer.get(userId);
        if (lastScanTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastScan = currentTime - lastScanTime;
        return timeSinceLastScan < SCAN_COOLDOWN_MS;
    }
    
    /**
     * Gets remaining cooldown time in seconds.
     * 
     * @param userId The user ID to check
     * @return Remaining cooldown in seconds, or 0 if not on cooldown
     */
    private int getRemainingCooldownSeconds(Long userId) {
        Long lastScanTime = lastScanTimeByPlayer.get(userId);
        if (lastScanTime == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastScan = currentTime - lastScanTime;
        
        if (timeSinceLastScan >= SCAN_COOLDOWN_MS) {
            return 0;
        }
        
        return (int)((SCAN_COOLDOWN_MS - timeSinceLastScan) / 1000);
    }
    
    /**
     * Creates common action buttons for all players.
     * 
     * @param keyboard The keyboard to add buttons to
     * @param lobby The game lobby to check for dead players
     * @param player The current player
     */
    private void createCommonActionButtons(List<List<InlineKeyboardButton>> keyboard, GameLobby lobby, Player player) {
        // Only alive players can perform these actions
        if (!player.isAlive()) {
            return; // Dead players don't get any common action buttons
        }
        
        // Report button - only for alive players
        List<InlineKeyboardButton> reportRow = new ArrayList<>();
        InlineKeyboardButton reportButton = new InlineKeyboardButton();
        reportButton.setText("⚠️ Сообщить о теле");
        reportButton.setCallbackData("report_body");
        reportRow.add(reportButton);
        keyboard.add(reportRow);
        
        // Emergency meeting button - only for alive players
        List<InlineKeyboardButton> emergencyRow = new ArrayList<>();
        InlineKeyboardButton emergencyButton = new InlineKeyboardButton();
        emergencyButton.setText("🚨 Экстренное собрание");
        emergencyButton.setCallbackData("emergency_meeting");
        emergencyRow.add(emergencyButton);
        keyboard.add(emergencyRow);
        
        // "I was killed" button - only for alive players
        List<InlineKeyboardButton> killedRow = new ArrayList<>();
        InlineKeyboardButton killedButton = new InlineKeyboardButton();
        killedButton.setText("💀 Меня убили");
        killedButton.setCallbackData("i_was_killed");
        killedRow.add(killedButton);
        keyboard.add(killedRow);
    }
    
    /**
     * Handles a task action from a player.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player performing the action
     * @param callbackData The callback data from the button
     */
    private void handleTaskAction(AmongUsBot bot, GameLobby lobby, Player player, String callbackData) {
        // Check if player is impostor
        if (player.isImpostor()) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Предатели не могут выполнять настоящие задания!");
            }
            return;
        }

        // Check if there's an active sabotage
        if (activeSabotage != SabotageType.NONE) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Нельзя выполнять задания во время саботажа!");
            }
            return;
        }

        try {
            // Parse task index from callback data
            int taskIndex = Integer.parseInt(callbackData.split(":")[1]);
            
            // Check if task exists and is not completed
            if (taskIndex >= 0 && taskIndex < player.getTotalTaskCount() && !player.isTaskCompleted(taskIndex)) {
                // Request photo confirmation for task
                player.setAwaitingPhotoForTaskIndex(taskIndex);
                
                if (player.getChatId() != null) {
                    Task task = player.getTask(taskIndex);
                    bot.sendTextMessage(player.getChatId(), 
                        "📸 Пожалуйста, отправьте фото, подтверждающее выполнение задания: \"" + 
                        task.getName() + "\"\n\n" + task.getDescription());
                }
                
                logger.info("Player {} requested to complete task {} in game {}, awaiting photo confirmation", 
                        player.getUserId(), taskIndex, lobby.getLobbyCode());
            } else {
                if (player.getChatId() != null) {
                    bot.sendTextMessage(player.getChatId(), "Это задание уже выполнено или недоступно.");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling task action for player {} in game {}: {}", 
                    player.getUserId(), lobby.getLobbyCode(), e.getMessage());
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Произошла ошибка при обработке задания.");
            }
        }
    }
    
    /**
     * Shows a confirmation dialog to the player.
     * 
     * @param bot The bot instance
     * @param player The player to show the dialog to
     * @param confirmCallback The callback data for the confirm button
     * @param message The confirmation message to display
     */
    private void showConfirmationDialog(AmongUsBot bot, Player player, String confirmCallback, String message) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot show confirmation dialog", player.getUserId());
            return;
        }
        
        SendMessage confirmMessage = new SendMessage();
        confirmMessage.setChatId(player.getChatId());
        confirmMessage.setText(message);
        
        // Create keyboard with confirm and cancel buttons
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Confirm button
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Да, подтверждаю");
        confirmButton.setCallbackData(confirmCallback);
        confirmRow.add(confirmButton);
        keyboard.add(confirmRow);
        
        // Cancel button
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("confirmation_cancel");
        cancelRow.add(cancelButton);
        keyboard.add(cancelRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        confirmMessage.setReplyMarkup(keyboardMarkup);
        
        bot.executeMethod(confirmMessage);
        logger.debug("Showed confirmation dialog to player {} for action {}", 
                player.getUserId(), confirmCallback);
    }
    
    /**
     * Handles cancel confirmation action.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleCancelConfirmation(AmongUsBot bot, GameLobby lobby, Player player) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot handle cancel confirmation", player.getUserId());
            return;
        }
        
        // Send a message that the action was cancelled
        bot.sendTextMessage(player.getChatId(), "Действие отменено.");
        
        // Update the player's keyboard to return to the main menu
        updatePlayerActionKeyboard(bot, lobby, player);
        
        logger.debug("Player {} cancelled confirmation dialog", player.getUserId());
    }
    
    /**
     * Handles a confirmed report action from a player.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player who reported
     * @return The new game state if a report was successful, or null if not
     */
    private GameState handleReportActionConfirmed(AmongUsBot bot, GameLobby lobby, Player player) {
        // Check if there are any killed players to report (excluding ejected players)
        boolean hasKilledPlayers = lobby.getPlayerList().stream()
                .anyMatch(p -> p.wasKilled()); // Only allow reporting killed players, not ejected ones
                
        if (!hasKilledPlayers) {
            // No killed players to report
            String errorMessage = "Вы не нашли никаких тел. Продолжайте игру.";
            try {
                bot.sendTextMessage(player.getChatId(), errorMessage);
                // Не используем callback query ID, так как он может быть недоступен
                logger.info("Player {} attempted to report a body when there are no killed players in game {}", 
                        player.getUserId(), lobby.getLobbyCode());
            } catch (Exception e) {
                logger.error("Failed to send error message about no dead bodies", e);
            }
            return null;
        }
        
        // There are killed players, proceed with reporting
        String reportMessage = player.getUserName() + " сообщил о теле! Начинаем обсуждение...";
        
        logger.info("Player {} reported a body in game {}", player.getUserId(), lobby.getLobbyCode());
        
        // Notify all players about the report
        for (Player p : lobby.getPlayerList()) {
            try {
                bot.sendTextMessage(p.getChatId(), reportMessage);
            } catch (Exception e) {
                logger.error("Failed to send report notification to player: " + p.getUserId(), e);
            }
        }
        
        // Create and return a new discussion state
        return new DiscussionState(player.getUserName(), player.getUserId());
    }
    
    /**
     * Handles a confirmed emergency meeting action.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     * @return The next game state or null to stay in current state
     */
    private GameState handleEmergencyMeetingActionConfirmed(AmongUsBot bot, GameLobby lobby, Player player) {
        String lobbyCode = lobby.getLobbyCode();
        
        // Thread-safe emergency meeting synchronization
        if (!emergencyMeetingInProgress.compareAndSet(false, true)) {
            // Another player already started an emergency meeting
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), 
                        "Экстренное собрание уже было вызвано другим игроком!");
            }
            logger.info("Player {} attempted to call emergency meeting in game {} but another meeting is already in progress", 
                    player.getUserId(), lobbyCode);
            return null;
        }

        try {
            // Check if there's an active sabotage preventing emergency meetings
            if (activeSabotage != SabotageType.NONE) {
                if (player.getChatId() != null) {
                    String message = activeSabotage == SabotageType.LIGHTS 
                        ? "Невозможно созвать экстренное собрание при отключенном свете!"
                        : "Невозможно созвать экстренное собрание во время аварии реактора! Сначала устраните неполадки.";
                    bot.sendTextMessage(player.getChatId(), message);
                }
                return null;
            }

            // Check if player has reached the limit of emergency meetings
            int maxMeetings = lobby.getSettings().getEmergencyMeetings();
            if (player.hasReachedEmergencyMeetingLimit(maxMeetings)) {
                if (player.getChatId() != null) {
                    bot.sendTextMessage(player.getChatId(), 
                            "Вы использовали все доступные экстренные собрания (лимит: " + maxMeetings + ").");
                }
                logger.info("Player {} attempted to call an emergency meeting in game {} but has reached the limit", 
                        player.getUserId(), lobbyCode);
                return null;
            }

            // Increment the player's emergency meeting count
            player.incrementEmergencyMeetingsUsed();

            logger.info("Player {} called an emergency meeting in game {}", player.getUserId(), lobbyCode);

            // Notify all players
            for (Player p : lobby.getPlayerList()) {
                if (p.getChatId() == null) {
                    logger.warn("Player {} has no chatId, skipping emergency meeting notification", p.getUserId());
                    continue;
                }

                bot.sendTextMessage(p.getChatId(), 
                        player.getUserName() + " созвал экстренное собрание! Начинаем обсуждение...");
            }

            // Create and return a new discussion state
            return new DiscussionState(player.getUserName(), null);

        } finally {
            // Reset the meeting flag when exiting this state
            // Note: This is handled in onExit method when transitioning to DiscussionState
        }
    }
    
    /**
     * Handles a confirmed "I was killed" action.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleIWasKilledActionConfirmed(AmongUsBot bot, GameLobby lobby, Player player) {
        if (!player.isAlive()) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Вы уже мертвы!");
            }
            return;
        }
        
        // Mark the player as dead
        player.kill();
        
        // Регистрируем событие убийства
        lobby.addGameEvent(player.getUserId(), "KILL", "Был убит");
        
        if (player.getChatId() != null) {
            bot.sendTextMessage(player.getChatId(), 
                    "Вы отмечены как убитый. Вы всё ещё можете выполнять задания, но не можете говорить или участвовать в голосованиях.");
        }
        
        logger.info("Player {} self-reported as killed in game {}", 
                player.getUserId(), lobby.getLobbyCode());
        
        // Update the player's keyboard to reflect their dead status
        updatePlayerActionKeyboard(bot, lobby, player);
        
        // Check win conditions
        checkAndUpdateWinConditions(bot, lobby);
    }
    
    /**
     * Handles a kill action from an impostor.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player performing the action
     * @param callbackData The callback data from the button
     */
    private void handleKillAction(AmongUsBot bot, GameLobby lobby, Player killer, String callbackData) {
        try {
            // Check if kill is on cooldown
            if (isKillOnCooldown(killer.getUserId(), lobby)) {
                if (killer.getChatId() != null) {
                    int remainingCooldown = getKillCooldownRemaining(killer.getUserId(), lobby);
                    bot.sendTextMessage(killer.getChatId(), 
                            "Способность убийства перезаряжается. Пожалуйста, подождите " + remainingCooldown + " секунд.");
                }
                logger.debug("Player {} attempted to kill while on cooldown in game {}", 
                        killer.getUserId(), lobby.getLobbyCode());
                return;
            }
            
            // Extract the target user ID from the callback data
            long targetId = Long.parseLong(callbackData.split(":")[1]);
            Player target = lobby.getPlayer(targetId);
            
            if (target != null && target.isAlive() && !target.isImpostor()) {
                // Kill the target
                target.kill();
                
                // Регистрируем событие убийства
                lobby.addGameEvent(killer.getUserId(), "KILL", "Убил " + target.getUserName());
                
                // Reset sabotage fix attempts if the killed player was fixing something
                if (playerFixingLights != null && playerFixingLights.equals(target.getUserId())) {
                    logger.info("Player {} was killed while fixing lights, resetting playerFixingLights", target.getUserId());
                    playerFixingLights = null;
                }
                
                if (playerAtReactorLocation1 != null && playerAtReactorLocation1.equals(target.getUserId())) {
                    logger.info("Player {} was killed while at reactor location 1, resetting playerAtReactorLocation1", target.getUserId());
                    playerAtReactorLocation1 = null;
                }
                
                if (playerAtReactorLocation2 != null && playerAtReactorLocation2.equals(target.getUserId())) {
                    logger.info("Player {} was killed while at reactor location 2, resetting playerAtReactorLocation2", target.getUserId());
                    playerAtReactorLocation2 = null;
                }
                
                // Set kill cooldown
                lastKillTimeByImpostor.put(killer.getUserId(), System.currentTimeMillis());
                
                logger.info("Player {} killed player {} in game {}", 
                        killer.getUserId(), target.getUserId(), lobby.getLobbyCode());
                
                // Notify the impostor
                if (killer.getChatId() != null) {
                    bot.sendTextMessage(killer.getChatId(), 
                            "Вы убили " + target.getUserName() + "!");
                } else {
                    logger.warn("Impostor {} has no chatId, cannot send kill confirmation", killer.getUserId());
                }
                
                // Notify the victim
                if (target.getChatId() != null) {
                    bot.sendTextMessage(target.getChatId(), 
                            "Вас убил предатель! Вы всё ещё можете выполнять задания, но не можете участвовать в обсуждениях.");
                } else {
                    logger.warn("Victim {} has no chatId, cannot send death notification", target.getUserId());
                }
            } else {
                if (killer.getChatId() != null) {
                    bot.sendTextMessage(killer.getChatId(), "Неверная цель или цель уже мертва.");
                }
                logger.debug("Player {} attempted to kill invalid target {} in game {}", 
                        killer.getUserId(), targetId, lobby.getLobbyCode());
            }
        } catch (Exception e) {
            logger.error("Error handling kill action: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles a fake task action from an impostor.
     * This makes it look like they're doing tasks.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     * @param callbackData The callback data
     */
    private void handleFakeTaskAction(AmongUsBot bot, GameLobby lobby, Player player, String callbackData) {
        if (!player.isImpostor()) {
            logger.warn("Non-impostor player {} attempted fake task in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot send fake task message", player.getUserId());
            return;
        }
        
        try {
            // Check if lights are out - for consistency with normal tasks
            if (activeSabotage == SabotageType.LIGHTS) {
                bot.sendTextMessage(player.getChatId(), "Невозможно выполнять задания при отключенном свете! Сначала почините освещение.");
                return;
            }
            
            int fakeTaskIndex = Integer.parseInt(callbackData.split(":")[1]);
            
            // Get the fake tasks for this impostor
            List<String> impostor_tasks = fakeTasksByImpostor.get(player.getUserId());
            List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(player.getUserId());
            
            if (impostor_tasks == null || completionStatus == null) {
                logger.warn("Impostor {} has no fake tasks assigned in game {}", 
                        player.getUserId(), lobby.getLobbyCode());
                return;
            }
            
            // Verify task index is valid
            if (fakeTaskIndex < 0 || fakeTaskIndex >= impostor_tasks.size()) {
                logger.warn("Impostor {} attempted to complete invalid fake task index {} in game {}", 
                        player.getUserId(), fakeTaskIndex, lobby.getLobbyCode());
                return;
            }
            
            // Check if the task is already completed
            if (completionStatus.get(fakeTaskIndex)) {
                bot.sendTextMessage(player.getChatId(), "Это задание уже выполнено.");
                logger.debug("Impostor {} attempted to complete already completed fake task {} in game {}", 
                        player.getUserId(), fakeTaskIndex, lobby.getLobbyCode());
                return;
            }
            
            String fakeTaskName = impostor_tasks.get(fakeTaskIndex);
            String taskDescription = getTaskDescription(fakeTaskName);
            
            // Set the fake task as awaiting photo confirmation
            player.setAwaitingPhotoForFakeTask(fakeTaskIndex);
            
            // Ask for a photo confirmation, just like with real tasks
            bot.sendTextMessage(player.getChatId(), 
                    "📸 Пожалуйста, отправьте фото, подтверждающее выполнение задания: \"" + fakeTaskName + "\"\n\n" + taskDescription);
            
            logger.info("Impostor {} requested to fake-complete task {} in game {}, awaiting photo confirmation", 
                    player.getUserId(), fakeTaskIndex, lobby.getLobbyCode());
        } catch (Exception e) {
            logger.error("Error handling fake task action: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles sabotage menu action.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleSabotageMenuAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (!player.isImpostor()) {
            logger.warn("Non-impostor player {} attempted to open sabotage menu in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot send sabotage menu", player.getUserId());
            return;
        }
        
        // Check if there's already an active sabotage
        if (activeSabotage != SabotageType.NONE) {
            bot.sendTextMessage(player.getChatId(), 
                    "Саботаж уже активен! Дождитесь его устранения или истечения времени.");
            return;
        }
        
        // Instead of sending a menu message, just update the player's keyboard with sabotage options
        sendSabotageMenu(bot, player);
        logger.info("Impostor {} opened sabotage menu in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Sends the sabotage menu to the player.
     * 
     * @param bot The bot instance
     * @param player The player to send the menu to
     */
    private void sendSabotageMenu(AmongUsBot bot, Player player) {
        SendMessage message = new SendMessage();
        message.setChatId(player.getChatId());
        message.setText("Выберите систему для саботажа:");
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Sabotage options
        String[] sabotageTypes = {"Свет", "Реактор"};
        for (String type : sabotageTypes) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(type);
            button.setCallbackData("sabotage:" + (type.equals("Свет") ? "lights" : "reactor"));
            row.add(button);
            keyboard.add(row);
        }
        
        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("back_to_main");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        
        // Send the message and store its ID for later deletion
        Message sentMessage = bot.executeMethod(message);
        if (sentMessage != null) {
            sabotageMenuMessageIds.put(player.getUserId(), sentMessage.getMessageId());
            logger.debug("Stored sabotage menu message ID {} for player {}", 
                    sentMessage.getMessageId(), player.getUserId());
        }
    }
    
    /**
     * Handles specific sabotage selection.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     * @param callbackData The callback data
     */
    private void handleSpecificSabotage(AmongUsBot bot, GameLobby lobby, Player player, String callbackData) {
        if (!player.isImpostor()) {
            logger.warn("Non-impostor player {} attempted to sabotage in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        if (activeSabotage != SabotageType.NONE) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Саботаж уже активен.");
            }
            return;
        }
        
        // Delete the sabotage menu message if we have its ID
        Integer menuMessageId = sabotageMenuMessageIds.get(player.getUserId());
        if (menuMessageId != null && player.getChatId() != null) {
            boolean deleted = bot.deleteMessage(player.getChatId(), menuMessageId);
            if (deleted) {
                sabotageMenuMessageIds.remove(player.getUserId());
                logger.debug("Deleted sabotage menu message {} for player {}", 
                        menuMessageId, player.getUserId());
            }
        }
        
        String sabotageType = callbackData.split(":")[1];
        
        // Directly activate the sabotage without sending additional messages
        // When we activate a sabotage, it will send notifications to all players
        switch (sabotageType) {
            case "lights":
                activateLightsSabotage(bot, lobby, player);
                break;
            case "reactor":
                activateReactorSabotage(bot, lobby, player);
                break;
            default:
                logger.warn("Unknown sabotage type: {}", sabotageType);
                if (player.getChatId() != null) {
                    updatePlayerActionKeyboard(bot, lobby, player); // Return to main menu
                }
                break;
        }
    }
    
    /**
     * Activates lights sabotage.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player who activated the sabotage
     */
    private void activateLightsSabotage(AmongUsBot bot, GameLobby lobby, Player player) {
        activeSabotage = SabotageType.LIGHTS;
        playerFixingLights = null;
        
        // Notify all players
        for (Player p : lobby.getPlayerList()) {
            if (p.getChatId() != null) {
                SendMessage message = new SendMessage();
                message.setChatId(p.getChatId());
                
                // Send the same message to everyone including impostors
                message.setText("⚠️ ЭКСТРЕННАЯ СИТУАЦИЯ: Свет погас! Задания, сканирование и экстренные собрания отключены до починки света.");
                
                // Add a button to fix lights for all players
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> fixRow = new ArrayList<>();
                InlineKeyboardButton fixButton = new InlineKeyboardButton();
                fixButton.setText("Починить свет");
                fixButton.setCallbackData("fix_lights");
                fixRow.add(fixButton);
                keyboard.add(fixRow);
                
                keyboardMarkup.setKeyboard(keyboard);
                message.setReplyMarkup(keyboardMarkup);
                
                bot.executeMethod(message);
            }
        }
        
        logger.info("Player {} activated lights sabotage in lobby {}", player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Activates reactor sabotage.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player who activated the sabotage
     */
    private void activateReactorSabotage(AmongUsBot bot, GameLobby lobby, Player player) {
        activeSabotage = SabotageType.REACTOR;
        reactorSabotageStartTime = System.currentTimeMillis();
        
        // Сбрасываем состояние локаций и флаги фотографий
        playerAtReactorLocation1 = null;
        playerAtReactorLocation2 = null;
        photoReceivedFromLocation1 = false;
        photoReceivedFromLocation2 = false;
        
        logger.info("Player {} activated reactor sabotage in game {}", 
                player.getUserId(), lobby.getLobbyCode());
        
        // Remove the sabotage menu message
        Integer messageId = sabotageMenuMessageIds.remove(player.getUserId());
        if (messageId != null && player.getChatId() != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(player.getChatId());
                deleteMessage.setMessageId(messageId);
                bot.execute(deleteMessage);
            } catch (Exception e) {
                logger.debug("Could not delete sabotage menu message for player {}: {}", 
                        player.getUserId(), e.getMessage());
            }
        }
        
        // Notify all players about the reactor sabotage and provide fix buttons
        for (Player p : lobby.getPlayerList()) {
            if (p.getChatId() == null) {
                logger.warn("Player {} has no chatId, skipping reactor sabotage notification", p.getUserId());
                continue;
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(p.getChatId());
            message.setText("🔴 АВАРИЯ РЕАКТОРА! Устраните неполадки в двух локациях одновременно, иначе предатели победят через 90 секунд!");
            
            // Add buttons to fix reactor at both locations
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> reactorRow1 = new ArrayList<>();
            InlineKeyboardButton reactorLocation1Button = new InlineKeyboardButton();
            reactorLocation1Button.setText("Пункт 1 реактора");
            reactorLocation1Button.setCallbackData("reactor_location1");
            reactorRow1.add(reactorLocation1Button);
            keyboard.add(reactorRow1);
            
            List<InlineKeyboardButton> reactorRow2 = new ArrayList<>();
            InlineKeyboardButton reactorLocation2Button = new InlineKeyboardButton();
            reactorLocation2Button.setText("Пункт 2 реактора");
            reactorLocation2Button.setCallbackData("reactor_location2");
            reactorRow2.add(reactorLocation2Button);
            keyboard.add(reactorRow2);
            
            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);
            
            bot.executeMethod(message);
        }
        
        // Start reactor meltdown timer using ScheduledExecutorService
        reactorMeltdownTimer = scheduler.schedule(() -> {
            try {
                logger.info("Reactor meltdown timer expired in game {}, checking if sabotage is still active", lobby.getLobbyCode());
                
                // Check if the sabotage is still active
                if (activeSabotage == SabotageType.REACTOR) {
                    logger.info("Reactor sabotage not fixed in time, impostors win in game {}", lobby.getLobbyCode());
                    
                    // Notify all players that impostors won due to reactor meltdown
                    for (Player p : lobby.getPlayerList()) {
                        if (p.getChatId() != null) {
                            bot.sendTextMessage(p.getChatId(), 
                                    "💥 Реактор взорвался! Предатели победили!");
                        }
                    }
                    
                    // Transition to game over state
                    GameOverState gameOverState = new GameOverState("Предатели", "Реактор взорвался!");
                    lobby.setGameState(gameOverState);
                    gameOverState.onEnter(bot, lobby);
                }
            } catch (Exception e) {
                logger.error("Error in reactor meltdown timer for game {}", lobby.getLobbyCode(), e);
            }
        }, REACTOR_MELTDOWN_TIME_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Handles scan action for crew members.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleScanAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot send scan results", player.getUserId());
            return;
        }
        
        // Check if lights are out
        if (activeSabotage == SabotageType.LIGHTS && !player.isImpostor()) {
            bot.sendTextMessage(player.getChatId(), "Сканер не работает при отключенном свете!");
            return;
        }
        
        // Check if scan is on cooldown
        if (isScanOnCooldown(player.getUserId())) {
            int remainingSeconds = getRemainingCooldownSeconds(player.getUserId());
            bot.sendTextMessage(player.getChatId(), 
                    "Сканер всё ещё перезаряжается. Подождите " + remainingSeconds + " секунд перед следующим сканированием.");
            logger.debug("Player {} attempted to use scanner while on cooldown in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        // Get some game statistics
        int totalPlayers = lobby.getPlayerCount();
        int alivePlayers = 0;
        int completedTasks = 0;
        int totalTasks = 0;
        
        for (Player p : lobby.getPlayerList()) {
            if (p.isAlive()) {
                alivePlayers++;
            }
            
            if (!p.isImpostor()) {
                completedTasks += p.getCompletedTaskCount();
                totalTasks += p.getTotalTaskCount();
            }
        }
        
        int taskCompletionPercent = totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0;
        
        StringBuilder message = new StringBuilder();
        message.append("*Результаты сканирования:*\n\n");
        message.append("👥 Игроки: ").append(alivePlayers).append(" живых / ").append(totalPlayers).append(" всего\n");
        message.append("📋 Задания: ").append(completedTasks).append(" выполнено / ").append(totalTasks).append(" всего (").append(taskCompletionPercent).append("%)\n");
        message.append("\nАномалий в непосредственной близости не обнаружено.");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(player.getChatId());
        sendMessage.setText(message.toString());
        sendMessage.setParseMode("Markdown");
        
        bot.executeMethod(sendMessage);
        logger.info("Player {} performed scan in game {}", 
                player.getUserId(), lobby.getLobbyCode());
        
        // Update the last scan time
        lastScanTimeByPlayer.put(player.getUserId(), System.currentTimeMillis());
        
        // Update the player's keyboard to reflect the new cooldown status
        updatePlayerActionKeyboard(bot, lobby, player);
    }
    
    /**
     * Handles check action for crew members.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleCheckAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot send check results", player.getUserId());
            return;
        }
        
        // Для предателей перенаправляем на меню саботажа
        if (player.isImpostor()) {
            handleSabotageMenuAction(bot, lobby, player);
            return;
        }
        
        // Для членов экипажа предоставляем полезную информацию о заданиях
        StringBuilder message = new StringBuilder();
        message.append("*Результаты проверки системы:*\n\n");
        message.append("*Прогресс ваших заданий:*\n");
        message.append("✅ Завершено: ").append(player.getCompletedTaskCount()).append(" / ").append(player.getTotalTaskCount()).append(" заданий\n");
        message.append("📊 Прогресс: ").append(player.getTaskCompletionPercentage()).append("%\n\n");
        
        if (player.getCompletedTaskCount() < player.getTotalTaskCount()) {
            message.append("*Оставшиеся задания:*\n");
            List<Task> tasks = player.getTasks();
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                if (!task.isCompleted()) {
                    message.append("- ").append(task.getName()).append("\n");
                }
            }
        } else {
            message.append("Все ваши задания выполнены! Хорошая работа!");
        }
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(player.getChatId());
        sendMessage.setText(message.toString());
        sendMessage.setParseMode("Markdown");
        
        bot.executeMethod(sendMessage);
        logger.info("Player {} performed system check in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Handles fix lights action.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleFixLightsAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (activeSabotage != SabotageType.LIGHTS) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Свет сейчас не отключен.");
            }
            return;
        }
        
        // Allow impostors to fix lights too - removed impostor check
        
        if (playerFixingLights != null) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), 
                        "Другой игрок уже пытается починить свет. Пожалуйста, подождите.");
            }
            return;
        }
        
        playerFixingLights = player.getUserId();
        
        if (player.getChatId() != null) {
            bot.sendTextMessage(player.getChatId(), 
                    "📸 Пожалуйста, отправьте фото, подтверждающее, что вы чините электрощит.");
        }
        
        logger.info("Player {} is attempting to fix lights in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Handles reactor location 1 action.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleReactorLocation1Action(AmongUsBot bot, GameLobby lobby, Player player) {
        if (activeSabotage != SabotageType.REACTOR) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Реактор сейчас не в аварийном состоянии.");
            }
            return;
        }
        
        // Allow impostors to fix reactor too - removed impostor check
        
        if (playerAtReactorLocation1 != null) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), 
                        "Другой игрок уже находится у Пункта 1. Пожалуйста, идите к Пункту 2.");
            }
            return;
        }
        
        // Cannot be at both locations
        if (playerAtReactorLocation2 != null && playerAtReactorLocation2.equals(player.getUserId())) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Вы не можете быть одновременно в двух местах!");
            }
            return;
        }
        
        playerAtReactorLocation1 = player.getUserId();
        
        if (player.getChatId() != null) {
            bot.sendTextMessage(player.getChatId(), 
                    "📸 Пожалуйста, отправьте фото, подтверждающее, что вы находитесь у Пункта 1 реактора.\n\n⚠️ ВАЖНО: Для починки реактора необходимы фотографии с ОБЕИХ локаций одновременно!");
        }
        
        // Notify all players about progress
        for (Player p : lobby.getPlayerList()) {
            if (p.getChatId() != null && !p.getUserId().equals(player.getUserId())) {
                bot.sendTextMessage(p.getChatId(), player.getUserName() + " находится у Пункта 1 реактора и готовится отправить фото!");
            }
        }
        
        logger.info("Player {} is at reactor location 1 in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Handles reactor location 2 action.
     *
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleReactorLocation2Action(AmongUsBot bot, GameLobby lobby, Player player) {
        if (activeSabotage != SabotageType.REACTOR) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Реактор сейчас не в аварийном состоянии.");
            }
            return;
        }
        
        // Allow impostors to fix reactor too - removed impostor check
        
        if (playerAtReactorLocation2 != null) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), 
                        "Другой игрок уже находится у Пункта 2. Пожалуйста, идите к Пункту 1.");
            }
            return;
        }
        
        // Cannot be at both locations
        if (playerAtReactorLocation1 != null && playerAtReactorLocation1.equals(player.getUserId())) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), "Вы не можете быть одновременно в двух местах!");
            }
            return;
        }
        
        playerAtReactorLocation2 = player.getUserId();
        
        if (player.getChatId() != null) {
            bot.sendTextMessage(player.getChatId(), 
                    "📸 Пожалуйста, отправьте фото, подтверждающее, что вы находитесь у Пункта 2 реактора.\n\n⚠️ ВАЖНО: Для починки реактора необходимы фотографии с ОБЕИХ локаций одновременно!");
        }
        
        // Notify all players about progress
        for (Player p : lobby.getPlayerList()) {
            if (p.getChatId() != null && !p.getUserId().equals(player.getUserId())) {
                bot.sendTextMessage(p.getChatId(), player.getUserName() + " находится у Пункта 2 реактора и готовится отправить фото!");
            }
        }
        
        logger.info("Player {} is at reactor location 2 in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Checks if any win conditions have been met.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @return The next game state if a win condition is met, null otherwise
     */
    private GameState checkWinConditions(AmongUsBot bot, GameLobby lobby) {
        List<Player> players = lobby.getPlayerList();
        int aliveCrewmates = 0;
        int aliveImpostors = 0;
        int taskCompletionPercent = 0;
        int crewmateCount = 0;
        
        // Count alive players and calculate task completion
        for (Player player : players) {
            if (player.isAlive()) {
                if (player.isImpostor()) {
                    aliveImpostors++;
                } else {
                    aliveCrewmates++;
                }
            }
            
            if (!player.isImpostor()) {
                taskCompletionPercent += player.getTaskCompletionPercentage();
                crewmateCount++;
            }
        }
        
        // Calculate average task completion
        taskCompletionPercent = crewmateCount > 0 ? taskCompletionPercent / crewmateCount : 0;
        
        logger.debug("Game {} win condition check: {} alive crewmates, {} alive impostors, {}% tasks completed", 
                lobby.getLobbyCode(), aliveCrewmates, aliveImpostors, taskCompletionPercent);
        
        // Check win conditions
        if (aliveImpostors == 0) {
            // All impostors are dead - Crewmates win
            logger.info("Game {} ended: Crewmates win (all impostors ejected)", lobby.getLobbyCode());
            return new GameOverState("Члены экипажа", "Все предатели были выброшены с корабля!");
        } else if (aliveImpostors >= aliveCrewmates) {
            // Equal or more impostors than crewmates - Impostors win
            logger.info("Game {} ended: Impostors win (not enough crewmates left)", lobby.getLobbyCode());
            return new GameOverState("Предатели", "Предатели устранили достаточно членов экипажа!");
        } else if (taskCompletionPercent >= 100) {
            // All tasks completed - Crewmates win
            logger.info("Game {} ended: Crewmates win (all tasks completed)", lobby.getLobbyCode());
            return new GameOverState("Члены экипажа", "Все задания были выполнены!");
        }
        
        // No win condition met
        return null;
    }
    
    /**
     * Updates the action keyboard for a player.
     * This is called after task completion to refresh the available actions.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player to update
     */
    public void updatePlayerActionKeyboard(AmongUsBot bot, GameLobby lobby, Player player) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot update action keyboard", player.getUserId());
            return;
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(player.getChatId());
        
        // Use correct progress calculation for impostors vs crewmates
        int progressPercent;
        if (player.isImpostor()) {
            progressPercent = getFakeTaskCompletionPercentage(player.getUserId());
        } else {
            progressPercent = player.getTaskCompletionPercentage();
        }
        
        message.setText("Ваши задания обновлены. Прогресс: " + progressPercent + "%");
        message.setReplyMarkup(createActionKeyboard(lobby, player));
        
        bot.executeMethod(message);
        logger.debug("Updated action keyboard for player {} in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Checks win conditions and updates game state if needed.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     */
    public void checkAndUpdateWinConditions(AmongUsBot bot, GameLobby lobby) {
        GameState nextState = checkWinConditions(bot, lobby);
        if (nextState != null) {
            // A win condition has been met, transition to the new state
            lobby.setGameState(nextState);
            nextState.onEnter(bot, lobby);
            logger.info("Game {} transitioned to {} after win condition check", 
                    lobby.getLobbyCode(), nextState.getStateName());
        }
    }
    
    /**
     * Checks if a player is currently trying to fix the lights.
     * 
     * @param userId The player ID to check
     * @return True if this player is fixing lights
     */
    public boolean isPlayerFixingLights(Long userId) {
        return activeSabotage == SabotageType.LIGHTS && 
               playerFixingLights != null && 
               playerFixingLights.equals(userId);
    }
    
    /**
     * Checks if a player is at either reactor location.
     * 
     * @param userId The player ID to check
     * @return True if this player is at a reactor location
     */
    public boolean isPlayerAtReactorLocation(Long userId) {
        return activeSabotage == SabotageType.REACTOR && 
               ((playerAtReactorLocation1 != null && playerAtReactorLocation1.equals(userId)) || 
                (playerAtReactorLocation2 != null && playerAtReactorLocation2.equals(userId)));
    }
    
    /**
     * Confirms and processes a lights fix.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player who fixed the lights
     */
    public void confirmLightsFix(AmongUsBot bot, GameLobby lobby, Player player) {
        // Reset sabotage state
        activeSabotage = SabotageType.NONE;
        playerFixingLights = null;
        
        // Notify all players
        for (Player p : lobby.getPlayerList()) {
            if (p.getChatId() != null) {
                bot.sendTextMessage(p.getChatId(), 
                        "💡 Свет восстановлен! " + player.getUserName() + " починил освещение.");
            }
        }
        
        // Update action keyboards for all players
        for (Player p : lobby.getPlayerList()) {
            updatePlayerActionKeyboard(bot, lobby, p);
        }
        
        logger.info("Player {} fixed lights sabotage in game {}", 
                player.getUserId(), lobby.getLobbyCode());
    }
    
    /**
     * Confirms that the reactor fix has been completed by having players at both locations.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player who triggered the fix
     */
    public void confirmReactorFix(AmongUsBot bot, GameLobby lobby, Player player) {
        // Определяем, с какой локации игрок
        boolean isAtLocation1 = playerAtReactorLocation1 != null && playerAtReactorLocation1.equals(player.getUserId());
        boolean isAtLocation2 = playerAtReactorLocation2 != null && playerAtReactorLocation2.equals(player.getUserId());
        
        if (!isAtLocation1 && !isAtLocation2) {
            logger.warn("Player {} tried to confirm reactor fix but is not at any reactor location", player.getUserId());
            return;
        }
        
        // Отмечаем получение фотографии с соответствующей локации
        if (isAtLocation1) {
            photoReceivedFromLocation1 = true;
            logger.info("Photo received from reactor location 1 by player {} in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            
            // Уведомляем всех игроков о прогрессе
            for (Player p : lobby.getPlayerList()) {
                if (p.getChatId() != null) {
                    bot.sendTextMessage(p.getChatId(), 
                            "📸 " + player.getUserName() + " отправил фото с Пункта 1 реактора! " +
                            (photoReceivedFromLocation2 ? "Ожидаем подтверждения с Пункта 2..." : "Нужно фото с Пункта 2!"));
                }
            }
        } else if (isAtLocation2) {
            photoReceivedFromLocation2 = true;
            logger.info("Photo received from reactor location 2 by player {} in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            
            // Уведомляем всех игроков о прогрессе
            for (Player p : lobby.getPlayerList()) {
                if (p.getChatId() != null) {
                    bot.sendTextMessage(p.getChatId(), 
                            "📸 " + player.getUserName() + " отправил фото с Пункта 2 реактора! " +
                            (photoReceivedFromLocation1 ? "Ожидаем подтверждения с Пункта 1..." : "Нужно фото с Пункта 1!"));
                }
            }
        }
        
        // Проверяем, получены ли фотографии с обеих локаций
        if (photoReceivedFromLocation1 && photoReceivedFromLocation2) {
            // Реактор починен! Отключаем таймер мелтдауна
            if (reactorMeltdownTimer != null && !reactorMeltdownTimer.isDone()) {
                reactorMeltdownTimer.cancel(true);
                reactorMeltdownTimer = null;
            }
            
            // Сбрасываем состояние саботажа
            activeSabotage = SabotageType.NONE;
            playerAtReactorLocation1 = null;
            playerAtReactorLocation2 = null;
            photoReceivedFromLocation1 = false;
            photoReceivedFromLocation2 = false;
            reactorSabotageStartTime = null;
            
            // Уведомляем всех игроков об успешной починке
            for (Player p : lobby.getPlayerList()) {
                if (p.getChatId() != null) {
                    bot.sendTextMessage(p.getChatId(), 
                            "✅ АВАРИЯ УСТРАНЕНА! Реактор стабилизирован благодаря совместным усилиям команды! 🎉");
                }
            }
            
            // Обновляем клавиатуры действий для всех игроков
            for (Player p : lobby.getPlayerList()) {
                updatePlayerActionKeyboard(bot, lobby, p);
            }
            
            logger.info("Reactor sabotage successfully fixed in game {} with photos from both locations", 
                    lobby.getLobbyCode());
        } else {
            // Реактор еще не починен, просто уведомляем игрока об успешной отправке фото
            if (player.getChatId() != null) {
                String remainingLocation = isAtLocation1 ? "Пункта 2" : "Пункта 1";
                bot.sendTextMessage(player.getChatId(), 
                        "✅ Ваше фото принято! Теперь ожидаем фото с " + remainingLocation + " реактора.");
            }
        }
    }
    
    /**
     * Checks if kill action is on cooldown for a player.
     * 
     * @param userId The user ID to check
     * @param lobby The game lobby for settings
     * @return True if kill is on cooldown, false otherwise
     */
    private boolean isKillOnCooldown(Long userId, GameLobby lobby) {
        Long lastKillTime = lastKillTimeByImpostor.get(userId);
        if (lastKillTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastKill = currentTime - lastKillTime;
        long killCooldownMs = lobby.getSettings().getKillCooldown() * 1000L;
        
        return timeSinceLastKill < killCooldownMs;
    }
    
    /**
     * Gets remaining kill cooldown time in seconds.
     * 
     * @param userId The user ID to check
     * @param lobby The game lobby for settings
     * @return Remaining cooldown in seconds, or 0 if not on cooldown
     */
    private int getKillCooldownRemaining(Long userId, GameLobby lobby) {
        Long lastKillTime = lastKillTimeByImpostor.get(userId);
        if (lastKillTime == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastKill = currentTime - lastKillTime;
        long killCooldownMs = lobby.getSettings().getKillCooldown() * 1000L;
        
        if (timeSinceLastKill >= killCooldownMs) {
            return 0;
        }
        
        return (int)((killCooldownMs - timeSinceLastKill) / 1000);
    }
    
    /**
     * Handles kill cooldown notification.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param player The player
     */
    private void handleKillCooldownAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (!player.isImpostor()) {
            logger.warn("Non-impostor player {} attempted to check kill cooldown in game {}", 
                    player.getUserId(), lobby.getLobbyCode());
            return;
        }
        
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, cannot send cooldown message", player.getUserId());
            return;
        }
        
        int remainingCooldown = getKillCooldownRemaining(player.getUserId(), lobby);
        String message;
        
        if (remainingCooldown > 0) {
            message = "Ваша способность убийства перезаряжается. Подождите " + remainingCooldown + 
                    " секунд, прежде чем вы сможете убить снова.";
        } else {
            message = "Ваша способность убийства готова к использованию!";
            // Update the keyboard since cooldown might have expired
            updatePlayerActionKeyboard(bot, lobby, player);
        }
        
        bot.sendTextMessage(player.getChatId(), message);
        logger.debug("Sent kill cooldown status to player {} in game {}: {} seconds remaining", 
                player.getUserId(), lobby.getLobbyCode(), remainingCooldown);
    }
    
    /**
     * Mark a fake task as completed for an impostor.
     * 
     * @param userId The impostor's user ID
     * @param fakeTaskIndex The index of the fake task to mark as completed
     * @return True if successfully marked as completed, false otherwise
     */
    public boolean completeFakeTask(Long userId, Integer fakeTaskIndex) {
        List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(userId);
        if (completionStatus == null || fakeTaskIndex < 0 || fakeTaskIndex >= completionStatus.size()) {
            return false;
        }
        
        if (!completionStatus.get(fakeTaskIndex)) {
            completionStatus.set(fakeTaskIndex, true);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the name of a fake task for an impostor.
     * 
     * @param userId The impostor's user ID
     * @param fakeTaskIndex The index of the fake task
     * @return The name of the fake task, or null if not found
     */
    public String getFakeTaskName(Long userId, Integer fakeTaskIndex) {
        List<String> impostor_tasks = fakeTasksByImpostor.get(userId);
        if (impostor_tasks == null || fakeTaskIndex < 0 || fakeTaskIndex >= impostor_tasks.size()) {
            return null;
        }
        
        return impostor_tasks.get(fakeTaskIndex);
    }
    
    /**
     * Gets the number of completed fake tasks for an impostor.
     * 
     * @param userId The impostor's user ID
     * @return The number of completed fake tasks
     */
    public int getCompletedFakeTaskCount(Long userId) {
        List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(userId);
        if (completionStatus == null) {
            return 0;
        }
        
        int count = 0;
        for (Boolean completed : completionStatus) {
            if (completed) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Gets the total number of fake tasks for an impostor.
     * 
     * @param userId The impostor's user ID
     * @return The total number of fake tasks
     */
    public int getTotalFakeTaskCount(Long userId) {
        List<String> impostor_tasks = fakeTasksByImpostor.get(userId);
        return impostor_tasks != null ? impostor_tasks.size() : 0;
    }
    
    /**
     * Gets the fake task completion percentage for an impostor.
     * 
     * @param userId The impostor's user ID
     * @return The completion percentage (0-100)
     */
    public int getFakeTaskCompletionPercentage(Long userId) {
        List<Boolean> completionStatus = fakeTaskCompletionByImpostor.get(userId);
        if (completionStatus == null || completionStatus.isEmpty()) {
            return 0;
        }
        
        int completedCount = 0;
        for (Boolean completed : completionStatus) {
            if (completed) {
                completedCount++;
            }
        }
        
        return (int) Math.round((double) completedCount / completionStatus.size() * 100);
    }
    
    /**
     * Handles "back to main" button in sabotage menu
     */
    private void handleBackToMainAction(AmongUsBot bot, GameLobby lobby, Player player) {
        // Delete the sabotage menu message if we have its ID
        Integer menuMessageId = sabotageMenuMessageIds.get(player.getUserId());
        if (menuMessageId != null && player.getChatId() != null) {
            boolean deleted = bot.deleteMessage(player.getChatId(), menuMessageId);
            if (deleted) {
                sabotageMenuMessageIds.remove(player.getUserId());
                logger.debug("Deleted sabotage menu message {} for player {}", 
                        menuMessageId, player.getUserId());
            }
        }
        
        // Update the player's keyboard
        updatePlayerActionKeyboard(bot, lobby, player);
    }

    private void handleEmergencyMeetingAction(AmongUsBot bot, GameLobby lobby, Player player) {
        if (lobby == null || player == null) {
            logger.warn("Invalid parameters for emergency meeting action");
            return;
        }

        // Check if there's an active sabotage
        if (activeSabotage != SabotageType.NONE) {
            bot.sendTextMessage(player.getChatId(), 
                "❌ Нельзя созвать экстренное собрание во время саботажа! Сначала устраните саботаж.");
            updatePlayerActionKeyboard(bot, lobby, player);
            return;
        }

        // Check if player has reached emergency meeting limit
        int maxMeetings = lobby.getSettings().getEmergencyMeetings();
        if (player.hasReachedEmergencyMeetingLimit(maxMeetings)) {
            bot.sendTextMessage(player.getChatId(), 
                "❌ Вы уже использовали все свои экстренные собрания (" + maxMeetings + ").");
            updatePlayerActionKeyboard(bot, lobby, player);
            return;
        }

        // Check if another emergency meeting is already in progress
        if (emergencyMeetingInProgress.compareAndSet(false, true)) {
            // Increment the player's emergency meeting usage
            player.incrementEmergencyMeetingsUsed();
            
            // Notify all players about the emergency meeting
            for (Player p : lobby.getPlayerList()) {
                if (p.getChatId() != null) {
                    bot.sendTextMessage(p.getChatId(), 
                        "🚨 " + player.getUserName() + " созвал экстренное собрание!");
                }
            }
            
            lobby.addGameEvent(player.getUserId(), "EMERGENCY_MEETING", "Созвал экстренное собрание");
            
            // Transition to discussion state
            GameState discussionState = new DiscussionState(player.getUserName(), null);
            lobby.setGameState(discussionState);
            discussionState.onEnter(bot, lobby);
        } else {
            bot.sendTextMessage(player.getChatId(), 
                "❌ Экстренное собрание уже созывается другим игроком.");
            updatePlayerActionKeyboard(bot, lobby, player);
        }
    }

    private void handleTaskCompletion(AmongUsBot bot, GameLobby lobby, Player player) {
        int taskIndex = player.getAwaitingPhotoForTaskIndex();
        if (taskIndex >= 0 && taskIndex < player.getTotalTaskCount()) {
            // Complete the task
            player.completeTask(taskIndex);
            player.setAwaitingPhotoForTaskIndex(-1);

            if (player.getChatId() != null) {
                // Use correct progress calculation for impostors vs crewmates
                int progressPercent;
                if (player.isImpostor()) {
                    progressPercent = getFakeTaskCompletionPercentage(player.getUserId());
                } else {
                    progressPercent = player.getTaskCompletionPercentage();
                }
                
                bot.sendTextMessage(player.getChatId(), 
                    "✅ Задание выполнено! Прогресс: " + progressPercent + "%");
            }

            // Log task completion
            logger.info("Player {} completed task {} in game {}", 
                    player.getUserId(), taskIndex, lobby.getLobbyCode());

            // Update player's action keyboard
            updatePlayerActionKeyboard(bot, lobby, player);

            // Check win conditions after task completion
            GameState nextState = checkWinConditions(bot, lobby);
            if (nextState != null) {
                lobby.setGameState(nextState);
                nextState.onEnter(bot, lobby);
            }
        }
    }

    /**
     * Initializes fake tasks for an impostor with the correct number based on settings.
     * 
     * @param impostorId The impostor's user ID
     * @param taskCount The number of tasks to assign (from lobby settings)
     */
    private void initializeFakeTasksForImpostor(Long impostorId, int taskCount) {
        List<String> fakeTasks = Arrays.asList(
            "Сочный насос", "Наклейка", "Разукрашка", "Холст", "Фотограф", 
            "Черепашья меткость", "Спайдер-мен", "Пазл", "Кольцеброс", "Математика", "Стихоплет"
        );
        
        // Create a shuffled copy of the fake tasks list
        List<String> shuffledTasks = new ArrayList<>(fakeTasks);
        Collections.shuffle(shuffledTasks);
        
        // Select the correct number of fake tasks based on settings
        int actualTaskCount = Math.min(taskCount, shuffledTasks.size());
        List<String> selectedTasks = new ArrayList<>(shuffledTasks.subList(0, actualTaskCount));
        
        // Store the selected tasks for this impostor
        fakeTasksByImpostor.put(impostorId, selectedTasks);
        
        // Initialize all tasks as not completed
        List<Boolean> completionStatus = new ArrayList<>();
        for (int i = 0; i < selectedTasks.size(); i++) {
            completionStatus.add(false);
        }
        fakeTaskCompletionByImpostor.put(impostorId, completionStatus);
        
        logger.debug("Initialized {} fake tasks for impostor {}", actualTaskCount, impostorId);
    }
}
// COMPLETED: GameActiveState class 