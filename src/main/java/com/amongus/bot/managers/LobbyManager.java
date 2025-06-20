package com.amongus.bot.managers;

import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Objects;

/**
 * Manages game lobbies for the Among Us bot.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class LobbyManager {
    private static final Logger logger = LoggerFactory.getLogger(LobbyManager.class);
    private static final int LOBBY_CODE_LENGTH = 6;
    private static final String LOBBY_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    // Thread-safe collections
    private final Map<String, GameLobby> lobbiesByCode;
    private final Map<Long, String> playerLobbies;
    
    public LobbyManager() {
        this.lobbiesByCode = new ConcurrentHashMap<>();
        this.playerLobbies = new ConcurrentHashMap<>();
        logger.info("LobbyManager initialized with thread-safe collections");
    }
    
    /**
     * Creates a new game lobby with the specified host.
     * 
     * @param hostId The host's user ID
     * @param hostName The host's username
     * @return The newly created game lobby
     * @throws IllegalArgumentException if hostId or hostName is null/empty
     */
    public GameLobby createLobby(Long hostId, String hostName) {
        // Input validation
        if (hostId == null) {
            throw new IllegalArgumentException("Host ID cannot be null");
        }
        if (hostName == null || hostName.trim().isEmpty()) {
            throw new IllegalArgumentException("Host name cannot be null or empty");
        }
        
        // Check if host is already in a lobby
        if (playerLobbies.containsKey(hostId)) {
            String existingLobbyCode = playerLobbies.get(hostId);
            logger.warn("Host {} is already in lobby {}, removing from previous lobby", 
                    hostId, existingLobbyCode);
            removePlayerFromLobby(hostId);
        }
        
        String lobbyCode = generateLobbyCode();
        GameLobby lobby = new GameLobby(lobbyCode, hostId, hostName);
        
        lobbiesByCode.put(lobbyCode, lobby);
        playerLobbies.put(hostId, lobbyCode);
        
        logger.info("Created new lobby {} with host {} ({})", lobbyCode, hostName, hostId);
        return lobby;
    }
    
    /**
     * Gets a lobby by its code with null safety.
     * 
     * @param lobbyCode The lobby code
     * @return The game lobby, or null if not found
     */
    public GameLobby getLobby(String lobbyCode) {
        if (lobbyCode == null || lobbyCode.trim().isEmpty()) {
            logger.warn("Attempted to get lobby with null or empty lobby code");
            return null;
        }
        
        return lobbiesByCode.get(lobbyCode.trim().toUpperCase());
    }
    
    /**
     * Gets the lobby for a player with null safety.
     * 
     * @param userId The player's user ID
     * @return The game lobby, or null if the player is not in a lobby
     */
    public GameLobby getLobbyForPlayer(Long userId) {
        if (userId == null) {
            logger.warn("Attempted to get lobby for null user ID");
            return null;
        }
        
        String lobbyCode = playerLobbies.get(userId);
        return lobbyCode != null ? lobbiesByCode.get(lobbyCode) : null;
    }
    
    /**
     * Adds a player to a lobby with comprehensive validation.
     * 
     * @param lobbyCode The lobby code
     * @param userId The player's user ID
     * @param userName The player's username
     * @return True if the player was added, false otherwise
     */
    public boolean addPlayerToLobby(String lobbyCode, Long userId, String userName) {
        // Input validation
        if (lobbyCode == null || lobbyCode.trim().isEmpty()) {
            logger.warn("Attempted to add player to lobby with null or empty lobby code");
            return false;
        }
        if (userId == null) {
            logger.warn("Attempted to add null user ID to lobby {}", lobbyCode);
            return false;
        }
        if (userName == null || userName.trim().isEmpty()) {
            logger.warn("Attempted to add user {} with null or empty username to lobby {}", 
                    userId, lobbyCode);
            return false;
        }
        
        String normalizedLobbyCode = lobbyCode.trim().toUpperCase();
        GameLobby lobby = lobbiesByCode.get(normalizedLobbyCode);
        if (lobby == null) {
            logger.warn("Player {} tried to join non-existent lobby {}", userId, normalizedLobbyCode);
            return false;
        }
        
        // Remove player from previous lobby if exists
        if (playerLobbies.containsKey(userId)) {
            String previousLobby = playerLobbies.get(userId);
            if (!Objects.equals(previousLobby, normalizedLobbyCode)) {
                logger.info("Player {} is moving from lobby {} to {}", 
                        userId, previousLobby, normalizedLobbyCode);
                removePlayerFromLobby(userId);
            }
        }
        
        if (lobby.addPlayer(userId, userName.trim())) {
            playerLobbies.put(userId, normalizedLobbyCode);
            logger.info("Player {} ({}) joined lobby {}", userId, userName, normalizedLobbyCode);
            return true;
        }
        
        logger.warn("Failed to add player {} to lobby {} (lobby may be full or game in progress)", 
                userId, normalizedLobbyCode);
        return false;
    }
    
    /**
     * Removes a player from their current lobby with cleanup.
     * 
     * @param userId The player's user ID
     * @return True if the player was removed, false if they weren't in a lobby
     */
    public boolean removePlayerFromLobby(Long userId) {
        if (userId == null) {
            logger.warn("Attempted to remove null user ID from lobby");
            return false;
        }
        
        String lobbyCode = playerLobbies.remove(userId);
        if (lobbyCode == null) {
            logger.debug("Player {} was not in any lobby", userId);
            return false;
        }
        
        GameLobby lobby = lobbiesByCode.get(lobbyCode);
        if (lobby == null) {
            logger.warn("Player {} was in non-existent lobby {}", userId, lobbyCode);
            return false;
        }
        
        lobby.removePlayer(userId);
        
        // If the lobby is empty or has no host, remove it
        if (lobby.isEmpty()) {
            lobbiesByCode.remove(lobbyCode);
            logger.info("Removed empty lobby {}", lobbyCode);
        } else if (lobby.getHostId() == null || !lobby.getPlayerList().stream()
                .anyMatch(player -> Objects.equals(player.getUserId(), lobby.getHostId()))) {
            // Host left but lobby not empty - assign new host
            assignNewHost(lobby);
        }
        
        logger.info("Player {} left lobby {}", userId, lobbyCode);
        return true;
    }
    
    /**
     * Assigns a new host when the current host leaves.
     * 
     * @param lobby The lobby needing a new host
     */
    private void assignNewHost(GameLobby lobby) {
        if (lobby.isEmpty()) {
            return;
        }
        
        boolean success = lobby.assignNewHost();
        if (success) {
            logger.info("Assigned new host to lobby {}", lobby.getLobbyCode());
        } else {
            logger.warn("Failed to assign new host to lobby {}", lobby.getLobbyCode());
        }
    }
    
    /**
     * Closes a lobby and removes all players.
     * 
     * @param lobbyCode The lobby code
     * @return True if the lobby was closed, false if it didn't exist
     */
    public boolean closeLobby(String lobbyCode) {
        if (lobbyCode == null || lobbyCode.trim().isEmpty()) {
            logger.warn("Attempted to close lobby with null or empty lobby code");
            return false;
        }
        
        String normalizedLobbyCode = lobbyCode.trim().toUpperCase();
        GameLobby lobby = lobbiesByCode.remove(normalizedLobbyCode);
        if (lobby == null) {
            logger.warn("Attempted to close non-existent lobby {}", normalizedLobbyCode);
            return false;
        }
        
        // Remove all players from their mappings
        for (Player player : lobby.getPlayerList()) {
            Long playerId = player.getUserId();
            playerLobbies.remove(playerId);
        }
        
        // Cleanup lobby resources
        try {
            if (lobby.getGameState() != null) {
                lobby.getGameState().onExit(null, lobby);
            }
        } catch (Exception e) {
            logger.error("Error during lobby {} cleanup: {}", normalizedLobbyCode, e.getMessage());
        }
        
        logger.info("Closed lobby {} with {} players", normalizedLobbyCode, lobby.getPlayerCount());
        return true;
    }
    
    /**
     * Gets current statistics about lobbies.
     * 
     * @return Statistics string
     */
    public String getStatistics() {
        int totalLobbies = lobbiesByCode.size();
        int totalPlayers = playerLobbies.size();
        
        return String.format("LobbyManager Stats - Total Lobbies: %d, Total Players: %d", 
                totalLobbies, totalPlayers);
    }
    
    /**
     * Force cleanup of all lobbies (for shutdown).
     */
    public void shutdown() {
        logger.info("Shutting down LobbyManager...");
        
        // Close all lobbies
        for (String lobbyCode : lobbiesByCode.keySet()) {
            closeLobby(lobbyCode);
        }
        
        // Clear collections
        lobbiesByCode.clear();
        playerLobbies.clear();
        
        logger.info("LobbyManager shutdown completed");
    }
    
    /**
     * Generates a unique lobby code with collision detection.
     * 
     * @return A unique lobby code
     */
    private String generateLobbyCode() {
        Random random = new Random();
        String lobbyCode;
        int attempts = 0;
        final int maxAttempts = 100; // Prevent infinite loop
        
        do {
            StringBuilder sb = new StringBuilder(LOBBY_CODE_LENGTH);
            for (int i = 0; i < LOBBY_CODE_LENGTH; i++) {
                int index = random.nextInt(LOBBY_CODE_CHARS.length());
                sb.append(LOBBY_CODE_CHARS.charAt(index));
            }
            lobbyCode = sb.toString();
            attempts++;
            
            if (attempts >= maxAttempts) {
                logger.error("Failed to generate unique lobby code after {} attempts", maxAttempts);
                throw new RuntimeException("Unable to generate unique lobby code");
            }
        } while (lobbiesByCode.containsKey(lobbyCode));
        
        logger.debug("Generated lobby code {} in {} attempts", lobbyCode, attempts);
        return lobbyCode;
    }
} 