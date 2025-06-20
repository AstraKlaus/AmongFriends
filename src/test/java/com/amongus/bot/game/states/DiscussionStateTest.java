package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.roles.Crewmate;
import com.amongus.bot.game.roles.Impostor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiscussionStateTest {
    @Mock
    private AmongUsBot bot;
    
    @Mock
    private GameLobby lobby;
    
    @Mock
    private LobbySettings settings;
    
    @Mock
    private Message message;
    
    private DiscussionState discussionState;
    private List<Player> players;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        discussionState = new DiscussionState("TestPlayer", null);
        players = new ArrayList<>();
        
        // Create 5 players: 4 crewmates and 1 impostor
        for (int i = 1; i <= 5; i++) {
            Player player = new Player(Long.valueOf(i), "Player" + i);
            player.setChatId(Long.valueOf(i * 100));
            if (i == 1) {
                player.setRole(new Impostor());
            } else {
                player.setRole(new Crewmate());
            }
            players.add(player);
        }
        
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getPlayerList()).thenReturn(players);
        when(lobby.getSettings()).thenReturn(settings);
        when(settings.getVotingTime()).thenReturn(30);
        when(message.getMessageId()).thenReturn(1);
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(message);
    }
    
    @Test
    void testGetStateName() {
        assertEquals("DISCUSSION", discussionState.getStateName());
    }
    
    @Test
    void testOnEnter() {
        discussionState.onEnter(bot, lobby);
        
        // Verify that voting messages were sent to all players
        verify(bot, times(players.size())).executeMethod(any(SendMessage.class));
    }
    
    @Test
    void testHandleVote() {
        discussionState.onEnter(bot, lobby);
        
        // Create a mock update with a vote callback
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn("vote:2");
        when(callbackQuery.getFrom()).thenReturn(user);
        when(callbackQuery.getId()).thenReturn("callback123");
        when(user.getId()).thenReturn(1L);
        
        // Handle the vote
        discussionState.handleUpdate(bot, lobby, update);
        
        // Verify that the vote was processed
        verify(bot).executeMethod(any(AnswerCallbackQuery.class));
    }
    
    @Test
    void testPreventDoubleVoting() {
        discussionState.onEnter(bot, lobby);
        
        // Create a mock update with a vote callback
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn("vote:2");
        when(callbackQuery.getFrom()).thenReturn(user);
        when(callbackQuery.getId()).thenReturn("callback123");
        when(user.getId()).thenReturn(1L);
        
        // First vote
        discussionState.handleUpdate(bot, lobby, update);
        
        // Try to vote again
        discussionState.handleUpdate(bot, lobby, update);
        
        // Verify that the second vote was rejected
        verify(bot, times(2)).executeMethod(any(AnswerCallbackQuery.class));
    }
    
    @Test
    void testAllPlayersVoted() {
        discussionState.onEnter(bot, lobby);
        
        // Create a mock update with a vote callback
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn("vote:2");
        when(callbackQuery.getId()).thenReturn("callback123");
        
        // Have all players vote
        for (int i = 1; i <= 5; i++) {
            when(user.getId()).thenReturn(Long.valueOf(i));
            when(callbackQuery.getFrom()).thenReturn(user);
            GameState result = discussionState.handleUpdate(bot, lobby, update);
            if (i == 5) {
                // After the last vote, should transition to next state
                assertNotNull(result, "Should transition to next state after all votes");
            }
        }
    }
    
    @Test
    void testCannotPerformActionsInDiscussion() {
        // Test that players cannot perform game actions during discussion
        assertFalse(discussionState.canPerformAction(lobby, 1L, "kill"));
        assertFalse(discussionState.canPerformAction(lobby, 2L, "task"));
        assertFalse(discussionState.canPerformAction(lobby, 3L, "emergency"));
    }
    
    @Test
    void testOnExit() {
        discussionState.onExit(bot, lobby);
        
        // onExit should execute without errors
        // This test verifies that the method can be called successfully
        verify(lobby, atLeastOnce()).getLobbyCode();
    }
}
