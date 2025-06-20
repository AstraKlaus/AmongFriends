package com.amongus.bot.game.lobby;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages game lobbies for Among Us games.
 */
public class LobbyManager {
    private static final Logger logger = LoggerFactory.getLogger(LobbyManager.class);
    
    // Map of lobby codes to GameLobby instances
    private final Map<String, GameLobby> lobbies;
    // Map of user IDs to their current lobby code
    private final Map<Long, String> userLobbies;
    
    // Characters used for generating lobby codes
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private final Random random;
    
    public LobbyManager() {
        this.lobbies = new HashMap<>();
        this.userLobbies = new HashMap<>();
        this.random = new Random();
        
        logger.info("LobbyManager initialized");
    }
    
    /**
     * Creates a new game lobby with the specified host.
     * 
     * @param hostId The Telegram user ID of the host
     * @param hostName The Telegram username of the host
     * @return The newly created GameLobby instance
     */
    public GameLobby createLobby(Long hostId, String hostName) {
        // Generate a unique lobby code
        String lobbyCode = generateLobbyCode();
        
        // Create a new lobby
        GameLobby lobby = new GameLobby(lobbyCode, hostId, hostName);
        
        // Store it in our maps
        lobbies.put(lobbyCode, lobby);
        userLobbies.put(hostId, lobbyCode);
        
        logger.info("Created new lobby with code {} hosted by user {}", lobbyCode, hostId);
        
        return lobby;
    }
    
    /**
     * Adds a player to an existing lobby.
     * 
     * @param lobbyCode The lobby code to join
     * @param userId The Telegram user ID of the player
     * @param userName The Telegram username of the player
     * @return The GameLobby if successfully joined, null otherwise
     */
    public GameLobby joinLobby(String lobbyCode, Long userId, String userName) {
        // Check if lobby exists
        GameLobby lobby = lobbies.get(lobbyCode);
        if (lobby == null) {
            logger.warn("User {} attempted to join non-existent lobby {}", userId, lobbyCode);
            return null;
        }
        
        // Check if the player is already in a different lobby
        if (userLobbies.containsKey(userId) && !userLobbies.get(userId).equals(lobbyCode)) {
            logger.warn("User {} attempted to join lobby {} while already in lobby {}", 
                       userId, lobbyCode, userLobbies.get(userId));
            return null;
        }
        
        // Try to add the player
        boolean added = lobby.addPlayer(userId, userName);
        if (!added) {
            logger.warn("Failed to add user {} to lobby {}", userId, lobbyCode);
            return null;
        }
        
        // Update the user's current lobby
        userLobbies.put(userId, lobbyCode);
        
        logger.info("User {} joined lobby {}", userId, lobbyCode);
        return lobby;
    }
    
    /**
     * Removes a player from their current lobby.
     * 
     * @param userId The Telegram user ID of the player
     * @return True if successfully removed, false otherwise
     */
    public boolean leaveLobby(Long userId) {
        // Check if the player is in a lobby
        if (!userLobbies.containsKey(userId)) {
            logger.warn("User {} attempted to leave but is not in any lobby", userId);
            return false;
        }
        
        String lobbyCode = userLobbies.get(userId);
        GameLobby lobby = lobbies.get(lobbyCode);
        
        if (lobby == null) {
            // This should not happen - inconsistent state
            logger.error("Inconsistent state: User {} is registered in non-existent lobby {}", userId, lobbyCode);
            userLobbies.remove(userId);
            return false;
        }
        
        // Remove the player
        lobby.removePlayer(userId);
        
        // Update the maps
        userLobbies.remove(userId);
        
        // If the lobby is now empty, remove it
        if (lobby.isEmpty()) {
            lobbies.remove(lobbyCode);
            logger.info("Removed empty lobby {}", lobbyCode);
        } else if (userId.equals(lobby.getHostId())) {
            // If the host left, assign a new host
            lobby.assignNewHost();
            logger.info("Assigned new host for lobby {}", lobbyCode);
        }
        
        logger.info("User {} left lobby {}", userId, lobbyCode);
        return true;
    }
    
    /**
     * Gets the current lobby of a player.
     * 
     * @param userId The Telegram user ID of the player
     * @return The GameLobby the player is in, or null if not in any lobby
     */
    public GameLobby getLobbyForUser(Long userId) {
        if (!userLobbies.containsKey(userId)) {
            return null;
        }
        
        String lobbyCode = userLobbies.get(userId);
        return lobbies.get(lobbyCode);
    }
    
    /**
     * Gets a lobby by its code.
     * 
     * @param lobbyCode The lobby code
     * @return The GameLobby with the given code, or null if not found
     */
    public GameLobby getLobbyByCode(String lobbyCode) {
        return lobbies.get(lobbyCode);
    }
    
    /**
     * Generates a unique lobby code.
     * 
     * @return A unique lobby code
     */
    private String generateLobbyCode() {
        String code;
        
        do {
            StringBuilder codeBuilder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                codeBuilder.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = codeBuilder.toString();
        } while (lobbies.containsKey(code));
        
        return code;
    }
}
// COMPLETED: LobbyManager class 