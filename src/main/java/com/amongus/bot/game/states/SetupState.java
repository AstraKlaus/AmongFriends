package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.game.roles.Crewmate;
import com.amongus.bot.game.roles.Impostor;
import com.amongus.bot.game.roles.Role;
import com.amongus.bot.game.tasks.SimpleTask;
import com.amongus.bot.game.tasks.Task;
import com.amongus.bot.game.tasks.TaskDifficulty;
import com.amongus.bot.models.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Game state for setting up the game, assigning roles and tasks.
 */
public class SetupState implements GameState {
    private static final Logger logger = LoggerFactory.getLogger(SetupState.class);
    
    private static final String STATE_NAME = "SETUP";
    private final Random random = new Random();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @Override
    public String getStateName() {
        return STATE_NAME;
    }
    
    @Override
    public void onEnter(AmongUsBot bot, GameLobby lobby) {
        logger.info("Entered setup state for game {}", lobby.getLobbyCode());
        
        // Ensure settings are appropriate for player count
        adjustSettingsForPlayerCount(lobby);
        
        // Assign roles
        logger.debug("Assigning roles for players in lobby {}", lobby.getLobbyCode());
        assignRoles(lobby);
        
        // Assign tasks
        logger.debug("Assigning tasks for players in lobby {}", lobby.getLobbyCode());
        assignTasks(lobby);
        
        // Send initial information to each player
        logger.debug("Sending initial game information to players in lobby {}", lobby.getLobbyCode());
        sendInitialInformation(bot, lobby);
    }
    
    @Override
    public void onExit(AmongUsBot bot, GameLobby lobby) {
        logger.info("Exited setup state for game {}", lobby.getLobbyCode());
    }
    
    @Override
    public GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update) {
        // In this state, we are just waiting for all players to view their roles
        // Then we automatically transition to the game state
        logger.info("Setup complete, transitioning to GameActiveState for lobby {}", lobby.getLobbyCode());
        return new GameActiveState();
    }
    
    @Override
    public boolean canPerformAction(GameLobby lobby, Long userId, String action) {
        // No specific actions in this state
        return false;
    }
    
    /**
     * Adjusts settings based on player count to ensure fair gameplay.
     * 
     * @param lobby The game lobby
     */
    private void adjustSettingsForPlayerCount(GameLobby lobby) {
        int playerCount = lobby.getPlayerCount();
        lobby.getSettings().adjustImpostorCount(playerCount);
        logger.info("Adjusted settings for player count {} in lobby {}", playerCount, lobby.getLobbyCode());
    }
    
    /**
     * Assigns roles (Crewmate or Impostor) to all players.
     * 
     * @param lobby The game lobby
     */
    private void assignRoles(GameLobby lobby) {
        List<Player> players = lobby.getPlayerList();
        int playerCount = players.size();
        
        // Get impostor count from lobby settings
        int impostorCount = lobby.getSettings().getImpostorCount();
        
        logger.info("Assigning roles for game {}: {} impostors, {} crewmates", 
                lobby.getLobbyCode(), impostorCount, playerCount - impostorCount);
        
        // Shuffle players to randomize role assignment
        Collections.shuffle(players);
        
        // Assign roles
        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);
            
            if (i < impostorCount) {
                // Assign Impostor role
                player.setRole(new Impostor());
                logger.info("Assigned Impostor role to player {} in game {}", 
                        player.getUserId(), lobby.getLobbyCode());
            } else {
                // Assign Crewmate role
                player.setRole(new Crewmate());
                logger.info("Assigned Crewmate role to player {} in game {}", 
                        player.getUserId(), lobby.getLobbyCode());
            }
        }
    }
    
    /**
     * Calculates the number of impostors based on player count.
     * 
     * @param playerCount The number of players
     * @return The number of impostors
     */
    private int calculateImpostorCount(int playerCount) {
        if (playerCount <= 5) {
            return 1;
        } else if (playerCount <= 8) {
            return 2;
        } else {
            return 3;
        }
    }
    
    /**
     * Assigns tasks to all players (including impostors).
     * For impostors, tasks are only for disguise and don't count toward crew victory.
     * 
     * @param lobby The game lobby
     */
    private void assignTasks(GameLobby lobby) {
        // In a full implementation, we would have a database of tasks
        // For now, we'll create some sample tasks
        List<Task> availableTasks = createSampleTasks();
        
        // Get tasks per player from settings
        int tasksPerPlayer = lobby.getSettings().getTasksPerPlayer();
        
        for (Player player : lobby.getPlayerList()) {
            if (player.getRole() != null) {
                List<Task> playerTasks = new ArrayList<>();
                
                // Assign random tasks to the player (both crewmates and impostors)
                Collections.shuffle(availableTasks);
                for (int i = 0; i < Math.min(tasksPerPlayer, availableTasks.size()); i++) {
                    // Create a unique copy of the task for this player
                    Task taskCopy = availableTasks.get(i).duplicate();
                    // Set owner ID for the task
                    taskCopy.setOwnerId(player.getUserId());
                    playerTasks.add(taskCopy);
                }
                
                player.setTasks(playerTasks);
                
                logger.info("Assigned {} tasks to player {} in game {}", 
                        playerTasks.size(), player.getUserId(), lobby.getLobbyCode());
            }
        }
    }
    
    /**
     * Creates a sample list of tasks for the game.
     * 
     * @return A list of tasks
     */
    private List<Task> createSampleTasks() {
        List<Task> tasks = new ArrayList<>();
        
        // Easy tasks
        tasks.add(new SimpleTask("Сочный насос", "Насоси шарик на насос и вдуй ему полностью. (2 этаж)", TaskDifficulty.EASY));
        tasks.add(new SimpleTask("Наклейка", "Наклей стикер на общую доску. (Дом на новом участке -> Старый дом)", TaskDifficulty.EASY));
        tasks.add(new SimpleTask("Разукрашка", "Расскрась зверька 3-мя разными цветами. (2 этаж)", TaskDifficulty.EASY));
        tasks.add(new SimpleTask("Холст", "Продолжи общий рисунок на холсте. (Зал)", TaskDifficulty.EASY));
        tasks.add(new SimpleTask("Фотограф", "Сделай фотографию с цветочками. (Улица)", TaskDifficulty.EASY));
        
        // Medium tasks
        tasks.add(new SimpleTask("Черепашья меткость", "Попади 4-мя шариками в область белого круга. (Ворота новый участок)", TaskDifficulty.MEDIUM));
        tasks.add(new SimpleTask("Спайдер-мен", "Найди всех паучков. (2 этаж)", TaskDifficulty.MEDIUM));
        tasks.add(new SimpleTask("Пазл", "Собери пазл, больше нечего сказать. (Стол у костра)", TaskDifficulty.MEDIUM));
        
        // Hard tasks
        tasks.add(new SimpleTask("Кольцеброс", "Забрось по одному кольцу на каждый стержень. (За домом)", TaskDifficulty.HARD));
        tasks.add(new SimpleTask("Математика", "Реши один из листочков с заданиями. (Дом на новом участке)", TaskDifficulty.HARD));
        tasks.add(new SimpleTask("Стихоплет", "Продолжи стишок строчкой в рифму. (Беседка)", TaskDifficulty.HARD));
        
        logger.debug("Created {} sample tasks", tasks.size());
        return tasks;
    }
    
    /**
     * Sends initial information to each player about their role and tasks.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     */
    private void sendInitialInformation(AmongUsBot bot, GameLobby lobby) {
        List<Player> players = lobby.getPlayerList();
        List<Player> impostors = new ArrayList<>();
        
        // First, collect the list of impostors
        for (Player player : players) {
            if (player.isImpostor()) {
                impostors.add(player);
            }
        }
        
        logger.info("Sending role information to {} players in lobby {}", players.size(), lobby.getLobbyCode());
        
        // Send role information to each player (will be deleted in 10 seconds)
        for (Player player : players) {
            if (player.getChatId() == null) {
                logger.warn("Player {} has no chatId, cannot send role information", player.getUserId());
                continue;
            }
            
            StringBuilder roleMessage = new StringBuilder();
            
            // Role information
            Role role = player.getRole();
            roleMessage.append("*Ваша роль: ").append(role.getRoleName()).append("*\n\n");
            roleMessage.append(role.getDescription()).append("\n\n");
            
            // For impostors, show other impostors
            if (player.isImpostor() && impostors.size() > 1) {
                roleMessage.append("*Другие предатели:*\n");
                for (Player impostor : impostors) {
                    if (!impostor.getUserId().equals(player.getUserId())) {
                        roleMessage.append("- ").append(impostor.getUserName()).append("\n");
                    }
                }
                roleMessage.append("\n");
            }
            
            roleMessage.append("*Это сообщение исчезнет через 10 секунд*");
            
            // Send the role message
            SendMessage sendRoleMessage = new SendMessage();
            sendRoleMessage.setChatId(player.getChatId());
            sendRoleMessage.setText(roleMessage.toString());
            sendRoleMessage.setParseMode("Markdown");
            
            Message sentRoleMessage = bot.executeMethod(sendRoleMessage);
            
            // Schedule role message deletion after 10 seconds
            if (sentRoleMessage != null) {
                final Long chatId = player.getChatId();
                final Integer messageId = sentRoleMessage.getMessageId();
                
                logger.debug("Scheduling deletion of role message {} for player {} in 10 seconds", 
                        messageId, player.getUserId());
                
                scheduler.schedule(() -> {
                    bot.deleteMessage(chatId, messageId);
                    logger.debug("Deleted role message {} for player {}", messageId, player.getUserId());
                }, 10, TimeUnit.SECONDS);
            }
            
            logger.debug("Sent role information to player {} ({})", player.getUserName(), player.getUserId());
        }
        
        // Task information will be sent by GameActiveState after the transition
        // Removed duplicate task message sending to avoid double messages
        
        logger.info("Setup complete, scheduling transition to GameActiveState for lobby {} in 2 seconds", 
                lobby.getLobbyCode());
        
        // Schedule transition with bot instance preserved
        scheduler.schedule(() -> {
            // Automatically transition to the active game state
            logger.info("Transitioning to GameActiveState for lobby {}", lobby.getLobbyCode());
            GameActiveState activeState = new GameActiveState();
            this.onExit(bot, lobby);
            lobby.setGameState(activeState);
            activeState.onEnter(bot, lobby);
        }, 2, TimeUnit.SECONDS);
    }
}
// COMPLETED: SetupState class 