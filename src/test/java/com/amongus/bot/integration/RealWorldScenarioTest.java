package com.amongus.bot.integration;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbyManager;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.game.states.*;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.roles.Crewmate;
import com.amongus.bot.game.roles.Impostor;
import com.amongus.bot.game.tasks.SimpleTask;
import com.amongus.bot.game.tasks.TaskDifficulty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests simulating real-world scenarios that have caused issues in production.
 * These tests simulate the kinds of edge cases and user behaviors that occurred during actual gameplay.
 */
class RealWorldScenarioTest {
    
    @Mock
    private AmongUsBot bot;
    
    @Mock
    private Message message;
    
    private GameLobby lobby;
    private LobbySettings settings;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        settings = new LobbySettings();
        lobby = new GameLobby("REAL", 1L, "Host");
        
        // Set up 8 players for realistic game scenario
        for (int i = 1; i <= 8; i++) {
            lobby.addPlayer(Long.valueOf(i), "Player" + i);
            Player player = lobby.getPlayer(Long.valueOf(i));
            player.setChatId(Long.valueOf(i * 100));
            
            if (i <= 2) {
                player.setRole(new Impostor());
            } else {
                player.setRole(new Crewmate());
                // Add realistic tasks
                player.setTasks(Arrays.asList(
                    new SimpleTask("Fix Wiring", "Fix the wiring in Electrical", TaskDifficulty.EASY),
                    new SimpleTask("Scan", "Submit scan at MedBay", TaskDifficulty.MEDIUM),
                    new SimpleTask("Fuel Engines", "Fuel the engines", TaskDifficulty.HARD)
                ));
            }
        }
        
        when(message.getMessageId()).thenReturn(1);
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(message);
        when(bot.executeMethod(any(AnswerCallbackQuery.class))).thenReturn(true);
    }
    
    @Test
    @Timeout(15)
    void testSimultaneousDeathReports_RealScenario() {
        // This reproduces the exact issue described: multiple players reporting deaths simultaneously
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        // Kill one player
        Player victim = lobby.getPlayer(3L);
        victim.kill();
        
        // All remaining alive players try to report the body simultaneously
        List<Long> reporters = Arrays.asList(1L, 2L, 4L, 5L, 6L, 7L, 8L);
        List<GameState> results = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // Simulate all players clicking "I found a body" at the same time
        Thread[] threads = new Thread[reporters.size()];
        for (int i = 0; i < reporters.size(); i++) {
            final Long reporterId = reporters.get(i);
            threads[i] = new Thread(() -> {
                try {
                    Update update = createCallbackUpdate(reporterId, "report_body");
                    GameState result = gameState.handleUpdate(bot, lobby, update);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }
        
        // Start all threads at the same time
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join(5000); // 5 second timeout per thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread was interrupted");
            }
        }
        
        // Verify the game handled concurrent reports gracefully
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
        assertTrue(results.size() <= 1, "Only one report should trigger state transition");
        
        // Verify game state is consistent
        assertNotNull(lobby.getGameState(), "Game state should exist");
        
        // The game should transition to discussion state after a body report
        if (!results.isEmpty()) {
            assertTrue(lobby.getGameState() instanceof DiscussionState, 
                      "Game should transition to discussion after body report");
        }
    }
    
    @Test
    @Timeout(15)
    void testRapidVotingChanges() {
        // Test rapid voting changes that could cause race conditions
        DiscussionState discussionState = new DiscussionState("TestPlayer", null);
        lobby.setGameState(discussionState);
        discussionState.onEnter(bot, lobby);
        
        Long voterId = 1L;
        List<Long> targets = Arrays.asList(2L, 3L, 4L, 5L);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // Simulate a player rapidly changing their vote
        Thread[] threads = new Thread[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            final Long targetId = targets.get(i);
            final int index = i; // Make effectively final for lambda
            threads[i] = new Thread(() -> {
                try {
                    Thread.sleep(index * 100); // Slight delay to simulate rapid clicking
                    Update update = createCallbackUpdate(voterId, "vote:" + targetId);
                    discussionState.handleUpdate(bot, lobby, update);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }
        
        // Start all vote change threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread was interrupted");
            }
        }
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during rapid vote changes: " + exceptions);
        
        // Verify game state remains valid
        assertNotNull(lobby.getGameState(), "Game state should remain valid");
    }
    
    @Test
    @Timeout(20)
    void testTaskCompletionSpam() {
        // Test spamming task completion buttons (common user behavior)
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        Long playerId = 3L; // Crewmate
        Player player = lobby.getPlayer(playerId);
        
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<GameState> results = Collections.synchronizedList(new ArrayList<>());
        
        // Simulate spamming task completion for the same task
        Thread[] threads = new Thread[20]; // 20 rapid clicks
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    Update update = createCallbackUpdate(playerId, "task:0"); // Always task 0
                    GameState result = gameState.handleUpdate(bot, lobby, update);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }
        
        // Start all threads rapidly
        for (Thread thread : threads) {
            thread.start();
            try {
                Thread.sleep(10); // Very short delay to simulate rapid clicking
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread was interrupted");
            }
        }
        
        // Verify the task system handled spam gracefully
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during task spam: " + exceptions);
        
        // Verify that the task was only completed once
        assertEquals(1, player.getCompletedTaskCount(), "Task should only be completed once despite spam");
        
        // Verify game state is consistent
        assertNotNull(lobby.getGameState(), "Game state should remain valid");
    }
    
    @Test
    @Timeout(15)
    void testEmergencyMeetingSpam() {
        // Test spamming emergency meeting button
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        Long playerId = 3L; // Crewmate
        Player player = lobby.getPlayer(playerId);
        
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<GameState> results = Collections.synchronizedList(new ArrayList<>());
        
        // Simulate spamming emergency meeting button
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    Update update = createCallbackUpdate(playerId, "emergency_meeting");
                    GameState result = gameState.handleUpdate(bot, lobby, update);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread was interrupted");
            }
        }
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during emergency meeting spam: " + exceptions);
        
        // Only one emergency meeting should be triggered
        assertTrue(results.size() <= 1, "Only one emergency meeting should be triggered");
        
        // Player's emergency meeting count should be properly tracked
        assertTrue(player.getEmergencyMeetingsUsed() <= 1, "Emergency meeting count should be properly tracked");
    }
    
    @Test
    @Timeout(25)
    void testGameStateCorruption_FullGameSimulation() {
        // Simulate a full game with various edge cases that could corrupt state
        
        // Start from lobby state
        LobbyState lobbyState = new LobbyState();
        lobby.setGameState(lobbyState);
        lobbyState.onEnter(bot, lobby);
        
        // Transition to setup
        SetupState setupState = new SetupState();
        lobby.setGameState(setupState);
        setupState.onEnter(bot, lobby);
        
        // Verify all players have proper roles
        int impostorCount = 0;
        int crewmateCount = 0;
        for (Player player : lobby.getPlayerList()) {
            if (player.isImpostor()) {
                impostorCount++;
            } else {
                crewmateCount++;
            }
        }
        assertEquals(2, impostorCount, "Should have exactly 2 impostors");
        assertEquals(6, crewmateCount, "Should have exactly 6 crewmates");
        
        // Transition to active game
        GameActiveState activeState = new GameActiveState();
        lobby.setGameState(activeState);
        activeState.onEnter(bot, lobby);
        
        // Simulate some deaths
        lobby.getPlayer(3L).kill();
        lobby.getPlayer(4L).kill();
        
        // Trigger emergency meeting
        Update emergencyUpdate = createCallbackUpdate(5L, "emergency_meeting");
        GameState result = activeState.handleUpdate(bot, lobby, emergencyUpdate);
        
        // Should transition to discussion
        if (result != null) {
            lobby.setGameState(result);
            assertTrue(result instanceof DiscussionState, "Should transition to discussion state");
            result.onEnter(bot, lobby);
        }
        
        // Test voting
        DiscussionState discussion = (DiscussionState) lobby.getGameState();
        Update voteUpdate = createCallbackUpdate(1L, "vote:2");
        GameState voteResult = discussion.handleUpdate(bot, lobby, voteUpdate);
        
        // Verify game state integrity throughout
        assertNotNull(lobby.getGameState(), "Game state should never be null");
        assertEquals(8, lobby.getPlayerCount(), "Player count should remain consistent");
        
        // Verify alive counts are correct
        int expectedAlive = 0;
        for (Player player : lobby.getPlayerList()) {
            if (player.isAlive()) {
                expectedAlive++;
            }
        }
        assertEquals(6, expectedAlive, "Should have 6 alive players after 2 deaths");
    }
    
    @Test
    @Timeout(10)
    void testInvalidCallbackHandling() {
        // Test handling of invalid/malformed callbacks that might be sent by cheaters or bugs
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        String[] invalidCallbacks = {
            "invalid_action",
            "task:-1",
            "task:999",
            "vote:999",
            "vote:-1",
            "",
            "null",
            "vote:",
            "task:",
            "malformed:data:extra"
        };
        
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (String invalidCallback : invalidCallbacks) {
            try {
                Update update = createCallbackUpdate(1L, invalidCallback);
                gameState.handleUpdate(bot, lobby, update);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        
        // Game should handle invalid callbacks gracefully without throwing exceptions
        assertTrue(exceptions.isEmpty(), "Game should handle invalid callbacks gracefully: " + exceptions);
        
        // Game state should remain valid
        assertNotNull(lobby.getGameState(), "Game state should remain valid after invalid callbacks");
        assertTrue(lobby.getGameState() instanceof GameActiveState, "Should remain in active state");
    }
    
    private Update createCallbackUpdate(Long userId, String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        Message message = mock(Message.class);
        
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getFrom()).thenReturn(user);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn("callback_" + userId + "_" + System.nanoTime());
        when(user.getId()).thenReturn(userId);
        when(message.getChatId()).thenReturn(userId * 100);
        
        return update;
    }
} 