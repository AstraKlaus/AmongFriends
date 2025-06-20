package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;
import com.amongus.bot.game.roles.Impostor;
import com.amongus.bot.game.roles.Crewmate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameOverStateTest {

    @Mock private AmongUsBot bot;
    @Mock private GameLobby lobby;

    private GameOverState gameOverState;
    private List<Player> players;
    private List<GameEvent> gameEvents;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Создаем игроков
        players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Player player = new Player((long) i, "Player" + i);
            player.setChatId((long) (100 + i));
            player.setRole(i <= 2 ? new Impostor() : new Crewmate());
            if (i == 2) {
                player.kill(); // Второй игрок убит
            }
            players.add(player);
        }

        // Создаем события игры
        gameEvents = Arrays.asList(
                new GameEvent(1L, "Player1", "KILL", "Player1 killed Player2"),
                new GameEvent(3L, "Player3", "TASK", "Player3 completed task"),
                new GameEvent(4L, "Player4", "VOTE", "Player4 voted for Player1")
        );

        when(lobby.getPlayerList()).thenReturn(players);
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getHostId()).thenReturn(1L);
        when(lobby.isHost(1L)).thenReturn(true);
        when(lobby.isHost(any())).thenReturn(false);
        when(lobby.isHost(1L)).thenReturn(true);
        when(lobby.getSortedGameEvents()).thenReturn(gameEvents);
        when(lobby.getPlayer(1L)).thenReturn(players.get(0));
        when(lobby.getPlayerCount()).thenReturn(players.size());

        // Мокаем executeMethod
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(new Message());
        doNothing().when(lobby).clearGameEvents();
        when(lobby.addGameEvent(any(Long.class), anyString(), anyString())).thenReturn(mock(GameEvent.class));
    }

    @Test
    void testGetStateName() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        assertEquals("GAME_OVER", gameOverState.getStateName());
    }

    @Test
    void testCrewmatesWin() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        gameOverState.onEnter(bot, lobby);
        
        // Verify messages were sent to all players
        verify(bot, times(players.size() * 2)).executeMethod(any(SendMessage.class));
    }
    
    @Test
    void testImpostorsWin() {
        gameOverState = new GameOverState("Impostors", "Eliminated all crewmates");
        gameOverState.onEnter(bot, lobby);
        
        // Verify messages were sent to all players
        verify(bot, times(players.size() * 2)).executeMethod(any(SendMessage.class));
    }
    
    @Test
    void testOnEnterWithNoGameEvents() {
        // Test with empty game events
        when(lobby.getSortedGameEvents()).thenReturn(new ArrayList<>());
        when(lobby.getGameEvents()).thenReturn(new ArrayList<>());
        
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        gameOverState.onEnter(bot, lobby);
        
        // Should still send messages to all players
        verify(bot, times(players.size() * 2)).executeMethod(any(SendMessage.class));
    }
    
    @Test
    void testHandleUpdateNewGame() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        
        // Create a mock update for new game
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn("new_game");
        when(callbackQuery.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(1L); // Host
        
        GameState nextState = gameOverState.handleUpdate(bot, lobby, update);
        
        // Should transition to LobbyState
        assertNotNull(nextState);
        assertTrue(nextState instanceof LobbyState);
    }
    
    @Test
    void testHandleUpdateNonHost() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        
        // Create a mock update for new game from non-host
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn("new_game");
        when(callbackQuery.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(2L); // Not host
        
        GameState nextState = gameOverState.handleUpdate(bot, lobby, update);
        
        // Should stay in same state
        assertNull(nextState);
    }
    
    @Test
    void testCanPerformAction() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        
        // Test that only host can start new game
        assertTrue(gameOverState.canPerformAction(lobby, 1L, "new_game"));
        assertFalse(gameOverState.canPerformAction(lobby, 2L, "new_game"));
        
        // Test that no other actions are allowed
        assertFalse(gameOverState.canPerformAction(lobby, 1L, "kill"));
        assertFalse(gameOverState.canPerformAction(lobby, 1L, "task"));
        assertFalse(gameOverState.canPerformAction(lobby, 1L, "emergency"));
    }
    
    @Test
    void testOnExit() {
        gameOverState = new GameOverState("Crewmates", "All tasks completed");
        gameOverState.onExit(bot, lobby);
        
        // onExit should execute without errors
        verify(lobby, atLeastOnce()).getLobbyCode();
    }
    
    @Test
    void testWinnerAndReasonStorage() {
        String winner = "Crewmates";
        String winReason = "All tasks completed";
        gameOverState = new GameOverState(winner, winReason);
        
        // Test that the state correctly stores winner and reason
        assertEquals("GAME_OVER", gameOverState.getStateName());
        
        // The winner and reason should be used in onEnter
        gameOverState.onEnter(bot, lobby);
        verify(bot, times(players.size() * 2)).executeMethod(any(SendMessage.class));
    }
}
