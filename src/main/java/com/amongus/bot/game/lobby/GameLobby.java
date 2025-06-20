package com.amongus.bot.game.lobby;

import com.amongus.bot.game.states.GameState;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a game lobby where players can join before starting a game.
 */
public class GameLobby {
    private static final Logger logger = LoggerFactory.getLogger(GameLobby.class);
    
    // Constants
    public static final int MIN_PLAYERS = 4;
    private static final int MAX_PLAYERS = 10;
    
    // Lobby properties
    private final String lobbyCode;
    private volatile Long hostId;
    private final Map<Long, Player> players;
    private volatile GameState gameState;
    private final LobbySettings settings;
    
    // История событий для отчета в конце игры
    private final List<GameEvent> gameEvents;
    
    public GameLobby(String lobbyCode, Long hostId, String hostName) {
        this.lobbyCode = lobbyCode;
        this.hostId = hostId;
        this.players = new ConcurrentHashMap<>();
        this.gameState = null;
        this.settings = new LobbySettings();
        this.gameEvents = Collections.synchronizedList(new ArrayList<>());
        
        // Add the host as the first player
        players.put(hostId, new Player(hostId, hostName));
        
        logger.info("Created new GameLobby with code {} and host {}", lobbyCode, hostId);
    }
    
    /**
     * Добавляет событие в историю игры.
     * 
     * @param userId ID пользователя, совершившего действие
     * @param action Тип действия
     * @param details Подробности действия
     * @return Созданное событие
     */
    public GameEvent addGameEvent(Long userId, String action, String details) {
        // Если userId равен null, создаем системное событие без привязки к игроку
        if (userId == null) {
            GameEvent event = new GameEvent(null, "SYSTEM", action, details);
            gameEvents.add(event);
            logger.debug("Added system game event: {} in lobby {}", event.getFormattedDescription(), lobbyCode);
            return event;
        }
        
        Player player = getPlayer(userId);
        if (player == null) {
            logger.warn("Cannot add game event for user {} - not in lobby {}", userId, lobbyCode);
            return null;
        }
        
        GameEvent event = new GameEvent(userId, player.getUserName(), action, details);
        gameEvents.add(event);
        logger.debug("Added game event: {} in lobby {}", event.getFormattedDescription(), lobbyCode);
        return event;
    }
    
    /**
     * Получает список всех событий в хронологическом порядке.
     * 
     * @return Список событий
     */
    public List<GameEvent> getGameEvents() {
        synchronized (gameEvents) {
            return new ArrayList<>(gameEvents);
        }
    }
    
    /**
     * Получает отсортированный по времени список событий.
     * 
     * @return Отсортированный список событий
     */
    public List<GameEvent> getSortedGameEvents() {
        synchronized (gameEvents) {
            List<GameEvent> sortedEvents = new ArrayList<>(gameEvents);
            Collections.sort(sortedEvents, Comparator.comparing(GameEvent::getTimestamp));
            return sortedEvents;
        }
    }
    
    /**
     * Получает события определенного типа.
     * 
     * @param action Тип действия для фильтрации
     * @return Список событий указанного типа
     */
    public List<GameEvent> getEventsByType(String action) {
        List<GameEvent> filteredEvents = new ArrayList<>();
        synchronized (gameEvents) {
            for (GameEvent event : gameEvents) {
                if (event.getAction().equalsIgnoreCase(action)) {
                    filteredEvents.add(event);
                }
            }
        }
        return filteredEvents;
    }
    
    /**
     * Получает события конкретного игрока.
     * 
     * @param userId ID игрока
     * @return Список событий игрока
     */
    public List<GameEvent> getPlayerEvents(Long userId) {
        List<GameEvent> playerEvents = new ArrayList<>();
        synchronized (gameEvents) {
            for (GameEvent event : gameEvents) {
                if (event.getUserId() != null && event.getUserId().equals(userId)) {
                    playerEvents.add(event);
                }
            }
        }
        return playerEvents;
    }
    
    /**
     * Очищает историю событий.
     */
    public void clearGameEvents() {
        gameEvents.clear();
        logger.debug("Cleared game events for lobby {}", lobbyCode);
    }
    
    /**
     * Adds a player to the lobby.
     * 
     * @param userId The Telegram user ID of the player
     * @param userName The Telegram username of the player
     * @return True if the player was added, false otherwise
     */
    public boolean addPlayer(Long userId, String userName) {
        // Check if player is already in the lobby
        if (players.containsKey(userId)) {
            logger.warn("Player {} is already in lobby {}", userName, lobbyCode);
            return false;
        }
        
        // Check if lobby is full
        if (players.size() >= MAX_PLAYERS) {
            logger.warn("Cannot add player {} to lobby {}. Lobby is full", userName, lobbyCode);
            return false;
        }
        
        // Check if game is already started (but allow joining in LobbyState)
        if (gameState != null && !(gameState instanceof com.amongus.bot.game.states.LobbyState)) {
            logger.warn("Cannot add player {} to lobby {}. Game already in progress", userName, lobbyCode);
            return false;
        }
        
        // Add player to the lobby
        players.put(userId, new Player(userId, userName));
        logger.info("Player {} added to lobby {}", userName, lobbyCode);
        
        // Adjust impostor count if needed based on the new player count
        settings.adjustImpostorCount(players.size());
        
        return true;
    }
    
    /**
     * Removes a player from the lobby.
     * 
     * @param userId The Telegram user ID of the player
     * @return True if the player was removed, false if not in the lobby
     */
    public boolean removePlayer(Long userId) {
        if (!players.containsKey(userId)) {
            logger.warn("Cannot remove player {} from lobby {} - not in the lobby", userId, lobbyCode);
            return false;
        }
        
        players.remove(userId);
        logger.info("Removed player {} from lobby {}", userId, lobbyCode);
        return true;
    }
    
    /**
     * Assigns a new host from the remaining players.
     * Called when the current host leaves.
     * 
     * @return True if a new host was assigned, false if the lobby is empty
     */
    public boolean assignNewHost() {
        if (players.isEmpty()) {
            logger.warn("Cannot assign new host for lobby {} - no players", lobbyCode);
            return false;
        }
        
        // Choose the first available player
        Long newHostId = players.keySet().iterator().next();
        this.hostId = newHostId;
        
        logger.info("Assigned new host {} for lobby {}", newHostId, lobbyCode);
        return true;
    }
    
    /**
     * Checks if the lobby is empty.
     * 
     * @return True if there are no players in the lobby
     */
    public boolean isEmpty() {
        return players.isEmpty();
    }
    
    /**
     * Checks if the lobby has enough players to start a game.
     * 
     * @return True if there are enough players
     */
    public boolean hasEnoughPlayers() {
        return players.size() >= MIN_PLAYERS;
    }
    
    /**
     * Gets the number of players in the lobby.
     * 
     * @return The number of players
     */
    public int getPlayerCount() {
        return players.size();
    }
    
    /**
     * Gets a copy of the list of all players in the lobby.
     * 
     * @return A list of all players
     */
    public List<Player> getPlayerList() {
        return new ArrayList<>(players.values());
    }
    
    // Getters and setters
    
    public String getLobbyCode() {
        return lobbyCode;
    }
    
    public Long getHostId() {
        return hostId;
    }
    
    public Player getPlayer(Long userId) {
        return players.get(userId);
    }
    
    public GameState getGameState() {
        return gameState;
    }
    
    public void setGameState(GameState gameState) {
        this.gameState = gameState;
        logger.info("Set game state for lobby {}: {}", lobbyCode, 
                gameState == null ? "null" : gameState.getClass().getSimpleName());
    }
    
    /**
     * Checks if a player is the host of this lobby.
     * 
     * @param userId The Telegram user ID to check
     * @return True if this user is the host
     */
    public boolean isHost(Long userId) {
        return userId.equals(hostId);
    }
    
    /**
     * Checks if a player is in this lobby.
     * 
     * @param userId The Telegram user ID to check
     * @return True if this user is in the lobby
     */
    public boolean hasPlayer(Long userId) {
        return players.containsKey(userId);
    }
    
    /**
     * Gets the lobby settings.
     * 
     * @return The LobbySettings object
     */
    public LobbySettings getSettings() {
        return settings;
    }
    
    /**
     * Updates a specific setting value.
     * 
     * @param settingName The name of the setting to update
     * @param newValue The new value for the setting
     * @return True if the setting was updated successfully
     */
    public boolean updateSetting(String settingName, int newValue) {
        return settings.updateSetting(settingName, newValue);
    }
    
    /**
     * Resets all lobby settings to their default values.
     */
    public void resetSettings() {
        settings.resetToDefaults();
        logger.info("Reset settings for lobby {}", lobbyCode);
    }
    
    /**
     * Gets the number of alive crewmates.
     * 
     * @return The number of alive crewmates
     */
    public int getAliveCrewmateCount() {
        int count = 0;
        for (Player player : players.values()) {
            if (!player.isImpostor() && player.isAlive()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets the number of alive impostors.
     * 
     * @return The number of alive impostors
     */
    public int getAliveImpostorCount() {
        int count = 0;
        for (Player player : players.values()) {
            if (player.isImpostor() && player.isAlive()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets the percentage of completed tasks across all crewmates.
     * 
     * @return The percentage of completed tasks (0-100)
     */
    public int getTaskCompletionPercentage() {
        int totalTasks = 0;
        int completedTasks = 0;
        
        for (Player player : players.values()) {
            if (!player.isImpostor()) {
                totalTasks += player.getTotalTaskCount();
                completedTasks += player.getCompletedTaskCount();
            }
        }
        
        if (totalTasks == 0) {
            return 100; // Если заданий нет, считаем их выполненными
        }
        
        return (int) (((double) completedTasks / totalTasks) * 100);
    }
    
    /**
     * Checks if a player has reached the emergency meeting limit.
     * 
     * @param userId The player's user ID
     * @return True if the player has reached the limit
     */
    public boolean hasPlayerReachedEmergencyMeetingLimit(Long userId) {
        Player player = getPlayer(userId);
        if (player == null) {
            return true;
        }
        return player.hasReachedEmergencyMeetingLimit(settings.getEmergencyMeetings());
    }
}
// COMPLETED: GameLobby class 