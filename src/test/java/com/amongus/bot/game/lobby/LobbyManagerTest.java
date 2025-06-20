package com.amongus.bot.game.lobby;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyManagerTest {
    private LobbyManager manager;
    private static final Long HOST_ID = 1L;
    private static final String HOST_NAME = "Host";
    private static final Long PLAYER_ID = 2L;
    private static final String PLAYER_NAME = "Player";

    @BeforeEach
    void setUp() {
        manager = new LobbyManager();
    }

    @Test
    void testCreateLobby() {
        GameLobby lobby = manager.createLobby(HOST_ID, HOST_NAME);
        assertNotNull(lobby);
        assertEquals(HOST_ID, lobby.getHostId());
        assertEquals(1, lobby.getPlayerCount());
        assertNotNull(manager.getLobbyByCode(lobby.getLobbyCode()));
        assertEquals(lobby, manager.getLobbyForUser(HOST_ID));
    }

    @Test
    void testJoinLobby() {
        GameLobby lobby = manager.createLobby(HOST_ID, HOST_NAME);
        GameLobby joined = manager.joinLobby(lobby.getLobbyCode(), PLAYER_ID, PLAYER_NAME);
        assertNotNull(joined);
        assertEquals(2, lobby.getPlayerCount());
        assertEquals(lobby, manager.getLobbyForUser(PLAYER_ID));
    }

    @Test
    void testJoinNonExistentLobby() {
        GameLobby joined = manager.joinLobby("FAKECODE", PLAYER_ID, PLAYER_NAME);
        assertNull(joined);
        assertNull(manager.getLobbyForUser(PLAYER_ID));
    }

    @Test
    void testLeaveLobby() {
        GameLobby lobby = manager.createLobby(HOST_ID, HOST_NAME);
        manager.joinLobby(lobby.getLobbyCode(), PLAYER_ID, PLAYER_NAME);
        assertTrue(manager.leaveLobby(PLAYER_ID));
        assertNull(manager.getLobbyForUser(PLAYER_ID));
        assertEquals(1, lobby.getPlayerCount());
    }

    @Test
    void testLeaveLobbyRemovesEmptyLobby() {
        GameLobby lobby = manager.createLobby(HOST_ID, HOST_NAME);
        assertTrue(manager.leaveLobby(HOST_ID));
        assertNull(manager.getLobbyByCode(lobby.getLobbyCode()));
    }

    @Test
    void testHostLeavesAssignsNewHost() {
        GameLobby lobby = manager.createLobby(HOST_ID, HOST_NAME);
        manager.joinLobby(lobby.getLobbyCode(), PLAYER_ID, PLAYER_NAME);
        assertTrue(manager.leaveLobby(HOST_ID));
        assertNotNull(manager.getLobbyByCode(lobby.getLobbyCode()));
        assertEquals(PLAYER_ID, lobby.getHostId());
    }
} 