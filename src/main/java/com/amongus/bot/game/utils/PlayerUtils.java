package com.amongus.bot.game.utils;

import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.GameConstants;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for efficient player operations to reduce multiple iterations
 */
public final class PlayerUtils {
    
    private PlayerUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Get players by role efficiently with single iteration
     */
    public static Map<String, List<Player>> groupPlayersByRole(GameLobby lobby) {
        if (lobby == null) {
            return Collections.emptyMap();
        }
        
        return lobby.getPlayerList().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                    player -> player.getRole() != null ? player.getRole().toString() : "UNKNOWN",
                    ConcurrentHashMap::new,
                    Collectors.toList()
                ));
    }
    
    /**
     * Get alive and dead players in one iteration
     */
    public static Map<String, List<Player>> groupPlayersByLifeStatus(GameLobby lobby) {
        if (lobby == null) {
            return Collections.emptyMap();
        }
        
        Map<Boolean, List<Player>> partitioned = lobby.getPlayerList().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.partitioningBy(Player::isAlive));
        
        Map<String, List<Player>> result = new ConcurrentHashMap<>();
        result.put("alive", partitioned.get(true));
        result.put("dead", partitioned.get(false));
        return result;
    }
    
    /**
     * Find players by multiple criteria efficiently
     */
    public static List<Player> findPlayers(GameLobby lobby, Predicate<Player> criteria) {
        if (lobby == null || criteria == null) {
            return Collections.emptyList();
        }
        
        return lobby.getPlayerList().stream()
                .filter(Objects::nonNull)
                .filter(criteria)
                .collect(Collectors.toList());
    }
    
    /**
     * Get alive players efficiently
     */
    public static List<Player> getAlivePlayers(GameLobby lobby) {
        return findPlayers(lobby, Player::isAlive);
    }
    
    /**
     * Get dead players efficiently
     */
    public static List<Player> getDeadPlayers(GameLobby lobby) {
        return findPlayers(lobby, player -> !player.isAlive());
    }
    
    /**
     * Get impostors efficiently
     */
    public static List<Player> getImpostors(GameLobby lobby) {
        return findPlayers(lobby, player -> 
            player.getRole() != null && player.getRole().toString().equals("IMPOSTOR"));
    }
    
    /**
     * Get crewmates efficiently
     */
    public static List<Player> getCrewmates(GameLobby lobby) {
        return findPlayers(lobby, player -> 
            player.getRole() != null && player.getRole().toString().equals("CREWMATE"));
    }
    
    /**
     * Get alive impostors efficiently
     */
    public static List<Player> getAliveImpostors(GameLobby lobby) {
        return findPlayers(lobby, player -> 
            player.isAlive() && 
            player.getRole() != null && 
            player.getRole().toString().equals("IMPOSTOR"));
    }
    
    /**
     * Get alive crewmates efficiently
     */
    public static List<Player> getAliveCrewmates(GameLobby lobby) {
        return findPlayers(lobby, player -> 
            player.isAlive() && 
            player.getRole() != null && 
            player.getRole().toString().equals("CREWMATE"));
    }
    
    /**
     * Send message to multiple players efficiently with validation
     */
    public static void sendMessageToPlayers(List<Player> players, String message, 
                                          TelegramLongPollingBot bot) {
        if (players == null || players.isEmpty() || message == null || bot == null) {
            return;
        }
        
        players.parallelStream()
                .filter(Objects::nonNull)
                .filter(player -> player.getUserId() != null)
                .forEach(player -> {
                    try {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(player.getUserId().toString());
                        sendMessage.setText(message);
                        bot.execute(sendMessage);
                    } catch (TelegramApiException e) {
                        // Log error but continue with other players
                        System.err.println("Failed to send message to player " + 
                                         player.getUserName() + ": " + e.getMessage());
                    }
                });
    }
    
    /**
     * Validate player action with comprehensive checks
     */
    public static boolean isValidPlayerAction(GameLobby lobby, Long userId) {
        if (lobby == null || userId == null) {
            return false;
        }
        
        Player player = lobby.getPlayer(userId);
        return player != null && player.isAlive();
    }
    
    /**
     * Get player with null safety
     */
    public static Optional<Player> safeGetPlayer(GameLobby lobby, Long userId) {
        if (lobby == null || userId == null) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(lobby.getPlayer(userId));
    }
    
    /**
     * Count players by criteria efficiently
     */
    public static long countPlayers(GameLobby lobby, Predicate<Player> criteria) {
        if (lobby == null || criteria == null) {
            return 0;
        }
        
        return lobby.getPlayerList().stream()
                .filter(Objects::nonNull)
                .filter(criteria)
                .count();
    }
    
    /**
     * Check if bulk operation should be used based on player count
     */
    public static boolean shouldUseBulkOperation(GameLobby lobby) {
        return lobby != null && 
               lobby.getPlayerList().size() >= GameConstants.BULK_OPERATION_THRESHOLD;
    }
    
    /**
     * Batch update players efficiently
     */
    public static void batchUpdatePlayers(List<Player> players, 
                                        java.util.function.Consumer<Player> updater) {
        if (players == null || players.isEmpty() || updater == null) {
            return;
        }
        
        if (players.size() >= GameConstants.BULK_OPERATION_THRESHOLD) {
            players.parallelStream()
                    .filter(Objects::nonNull)
                    .forEach(updater);
        } else {
            players.stream()
                    .filter(Objects::nonNull)
                    .forEach(updater);
        }
    }
} 