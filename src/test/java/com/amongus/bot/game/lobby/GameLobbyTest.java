package com.amongus.bot.game.lobby;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.amongus.bot.models.GameEvent;
import java.util.List;

class GameLobbyTest {
    private GameLobby lobby;
    private static final String LOBBY_CODE = "TEST123";
    private static final Long HOST_ID = 123L;
    private static final String HOST_NAME = "TestHost";

    @BeforeEach
    void setUp() {
        lobby = new GameLobby(LOBBY_CODE, HOST_ID, HOST_NAME);
    }

    @Test
    void testLobbyCreation() {
        assertNotNull(lobby);
        assertEquals(LOBBY_CODE, lobby.getLobbyCode());
        assertEquals(HOST_ID, lobby.getHostId());
        assertEquals(1, lobby.getPlayerCount());
        assertTrue(lobby.hasPlayer(HOST_ID));
        assertTrue(lobby.isHost(HOST_ID));
    }

    @Test
    void testAddPlayer() {
        Long newPlayerId = 456L;
        String newPlayerName = "NewPlayer";
        
        assertTrue(lobby.addPlayer(newPlayerId, newPlayerName));
        assertEquals(2, lobby.getPlayerCount());
        assertTrue(lobby.hasPlayer(newPlayerId));
        assertFalse(lobby.isHost(newPlayerId));
    }

    @Test
    void testAddDuplicatePlayer() {
        assertFalse(lobby.addPlayer(HOST_ID, HOST_NAME));
        assertEquals(1, lobby.getPlayerCount());
    }

    @Test
    void testRemovePlayer() {
        Long playerId = 456L;
        String playerName = "TestPlayer";
        lobby.addPlayer(playerId, playerName);
        
        assertTrue(lobby.removePlayer(playerId));
        assertEquals(1, lobby.getPlayerCount());
        assertFalse(lobby.hasPlayer(playerId));
    }

    @Test
    void testRemoveHost() {
        assertTrue(lobby.removePlayer(HOST_ID));
        assertTrue(lobby.isEmpty());
    }

    @Test
    void testAssignNewHost() {
        Long playerId = 456L;
        String playerName = "NewHost";
        lobby.addPlayer(playerId, playerName);
        
        assertTrue(lobby.removePlayer(HOST_ID));
        assertTrue(lobby.assignNewHost());
        assertEquals(playerId, lobby.getHostId());
        assertTrue(lobby.isHost(playerId));
    }

    @Test
    void testGameEvents() {
        String action = "TEST_ACTION";
        String details = "Test details";
        
        // Add a game event
        GameEvent event = lobby.addGameEvent(HOST_ID, action, details);
        assertNotNull(event);
        assertEquals(HOST_ID, event.getUserId());
        assertEquals(action, event.getAction());
        assertEquals(details, event.getDetails());
        
        // Test getting events
        List<GameEvent> events = lobby.getGameEvents();
        assertEquals(1, events.size());
        assertEquals(event, events.get(0));
        
        // Test getting events by type
        List<GameEvent> actionEvents = lobby.getEventsByType(action);
        assertEquals(1, actionEvents.size());
        assertEquals(event, actionEvents.get(0));
        
        // Test getting player events
        List<GameEvent> playerEvents = lobby.getPlayerEvents(HOST_ID);
        assertEquals(1, playerEvents.size());
        assertEquals(event, playerEvents.get(0));
        
        // Test clearing events
        lobby.clearGameEvents();
        assertTrue(lobby.getGameEvents().isEmpty());
    }

    @Test
    void testLobbySettings() {
        LobbySettings settings = lobby.getSettings();
        assertNotNull(settings);
        
        // Test updating settings
        assertTrue(lobby.updateSetting("impostorCount", 2));
        assertEquals(2, settings.getImpostorCount());
        
        // Test resetting settings
        lobby.resetSettings();
        assertEquals(1, settings.getImpostorCount()); // Default value
    }

    @Test
    void testMaxPlayersLimit() {
        // Try to add more than MAX_PLAYERS
        for (int i = 1; i < 10; i++) {
            assertTrue(lobby.addPlayer((long) i + 100, "Player" + i));
        }
        
        // Try to add one more player
        assertFalse(lobby.addPlayer(200L, "ExtraPlayer"));
        assertEquals(10, lobby.getPlayerCount());
    }

    @Test
    void testHasEnoughPlayers() {
        // Initially not enough players
        assertFalse(lobby.hasEnoughPlayers());
        
        // Add players to reach minimum
        for (int i = 1; i < 4; i++) {
            lobby.addPlayer((long) i + 100, "Player" + i);
        }
        
        assertTrue(lobby.hasEnoughPlayers());
    }
} 