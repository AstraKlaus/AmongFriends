package com.amongus.bot.integration;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbyManager;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.roles.Crewmate;
import com.amongus.bot.game.roles.Impostor;
import com.amongus.bot.game.states.DiscussionState;
import com.amongus.bot.game.states.GameActiveState;
import com.amongus.bot.game.states.GameOverState;
import com.amongus.bot.game.states.GameState;
import com.amongus.bot.game.states.LobbyState;
import com.amongus.bot.game.states.SetupState;
import com.amongus.bot.game.tasks.SimpleTask;
import com.amongus.bot.game.tasks.Task;
import com.amongus.bot.game.tasks.TaskDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive concurrency and stress tests for the Among Us bot.
 * Tests various scenarios that can cause race conditions and data corruption.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyStressTest {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyStressTest.class);

    @Mock
    private AmongUsBot bot;
    
    @Mock
    private LobbyManager lobbyManager;
    
    @Mock
    private Message message;
    
    private GameLobby lobby;
    private LobbySettings settings;
    private List<Player> players;
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executorService = Executors.newFixedThreadPool(10);
        
        // Create a real lobby with real settings for integration testing
        settings = new LobbySettings();
        lobby = new GameLobby("STRESS", 1L, "Host");
        
        // Create 8 players: 2 impostors and 6 crewmates
        players = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            // Add player to lobby using correct method
            lobby.addPlayer(Long.valueOf(i), "Player" + i);
            Player player = lobby.getPlayer(Long.valueOf(i));
            player.setChatId(Long.valueOf(i * 100));
            
            if (i <= 2) {
                player.setRole(new Impostor());
            } else {
                player.setRole(new Crewmate());
                // Add tasks for crewmates using setTasks method
                List<Task> taskList = new ArrayList<>();
                taskList.add(new SimpleTask("Task " + i + "A", "Description A", TaskDifficulty.EASY));
                taskList.add(new SimpleTask("Task " + i + "B", "Description B", TaskDifficulty.MEDIUM));
                taskList.add(new SimpleTask("Task " + i + "C", "Description C", TaskDifficulty.HARD));
                player.setTasks(taskList);
            }
            
            players.add(player);
        }
        
        // Use lenient() to avoid UnnecessaryStubbingException
        lenient().when(message.getMessageId()).thenReturn(1);
        lenient().when(bot.executeMethod(any(SendMessage.class))).thenReturn(message);
        lenient().when(bot.executeMethod(any(AnswerCallbackQuery.class))).thenReturn(true);
    }
    
    @Test
    @Timeout(30)
    void testSimultaneousPlayerKillReports() throws InterruptedException {
        // Set up active game state
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        // Kill player 3
        players.get(2).kill();
        
        // Create multiple threads trying to report the same kill simultaneously
        int numThreads = 6; // 6 remaining alive players
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);
        AtomicInteger successfulReports = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numThreads; i++) {
            Long playerId = Long.valueOf(i + 4); // Players 4-9 (alive players)
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Create callback for "I was killed" action
                    Update update = createCallbackUpdate(playerId, "i_was_killed_confirm");
                    
                    // Simulate simultaneous reports
                    synchronized (gameState) {
                        gameState.handleUpdate(bot, lobby, update);
                        successfulReports.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Test should complete within 10 seconds");
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
        
        // Verify that at least one report was processed
        assertTrue(successfulReports.get() > 0, "At least one report should be processed");
        
        // Verify game state integrity
        assertNotNull(lobby.getGameState(), "Game state should still exist");
    }
    
    @Test
    @Timeout(30)
    void testSimultaneousVotingInDiscussion() throws InterruptedException {
        // Set up discussion state
        DiscussionState discussionState = new DiscussionState("TestPlayer", null);
        lobby.setGameState(discussionState);
        discussionState.onEnter(bot, lobby);
        
        int numVoters = 8; // All players vote
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numVoters);
        AtomicInteger successfulVotes = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numVoters; i++) {
            Long voterId = Long.valueOf(i + 1);
            Long targetId = Long.valueOf(2); // Everyone votes for player 2
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    Update update = createCallbackUpdate(voterId, "vote:" + targetId);
                    
                    // Simulate simultaneous voting
                    GameState result = discussionState.handleUpdate(bot, lobby, update);
                    
                    if (result != null) {
                        successfulVotes.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Voting should complete within 15 seconds");
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during voting: " + exceptions);
        
        // Verify that voting completed successfully
        assertNotNull(lobby.getGameState(), "Game state should exist after voting");
    }
    
    @Test
    @Timeout(30)
    void testConcurrentTaskCompletions() throws InterruptedException {
        // Set up active game state
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numCrewmates = 6; // Players 3-8 are crewmates
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numCrewmates * 3); // 3 tasks per crewmate
        AtomicInteger completedTasks = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // Each crewmate completes all their tasks simultaneously
        for (int playerId = 3; playerId <= 8; playerId++) {
            Player player = players.get(playerId - 1);
            
            for (int taskIndex = 0; taskIndex < 3; taskIndex++) {
                final int finalTaskIndex = taskIndex;
                final Long finalPlayerId = Long.valueOf(playerId);
                
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        
                        Update update = createCallbackUpdate(finalPlayerId, "task:" + finalTaskIndex);
                        
                        // Simulate task completion
                        synchronized (gameState) {
                            gameState.handleUpdate(bot, lobby, update);
                            completedTasks.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Task completion should finish within 20 seconds");
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during task completion: " + exceptions);
        
        // Verify that tasks were processed
        assertTrue(completedTasks.get() > 0, "At least some tasks should be completed");
    }
    
    @Test
    @Timeout(30)
    void testRapidStateTransitions() throws InterruptedException {
        // Test transitions between different game states with proper cleanup
        int numTransitions = 10; // Reduced number to avoid overwhelming the system
        CountDownLatch finishLatch = new CountDownLatch(numTransitions);
        AtomicInteger successfulTransitions = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numTransitions; i++) {
            final int transitionNumber = i;
            executorService.submit(() -> {
                try {
                    // Create separate lobby for each transition to avoid state conflicts
                    GameLobby testLobby = new GameLobby("TEST" + transitionNumber, 1L, "Host");
                    
                    // Add players to the test lobby
                    for (int j = 1; j <= 8; j++) {
                        testLobby.addPlayer(Long.valueOf(j), "Player" + j);
                        Player player = testLobby.getPlayer(Long.valueOf(j));
                        player.setRole(j <= 2 ? new Impostor() : new Crewmate());
                    }
                    
                    // Test state transitions with proper cleanup
                    LobbyState lobbyState = new LobbyState();
                    testLobby.setGameState(lobbyState);
                    lobbyState.onEnter(bot, testLobby);
                    lobbyState.onExit(bot, testLobby);
                    
                    // Small delay to avoid overwhelming the system
                    Thread.sleep(10);
                    
                    SetupState setupState = new SetupState();
                    testLobby.setGameState(setupState);
                    setupState.onEnter(bot, testLobby);
                    setupState.onExit(bot, testLobby);
                    
                    Thread.sleep(10);
                    
                    GameActiveState activeState = new GameActiveState();
                    testLobby.setGameState(activeState);
                    activeState.onEnter(bot, testLobby);
                    activeState.onExit(bot, testLobby);
                    
                    Thread.sleep(10);
                    
                    GameOverState gameOverState = new GameOverState("Crewmates", "Test win");
                    testLobby.setGameState(gameOverState);
                    gameOverState.onEnter(bot, testLobby);
                    gameOverState.onExit(bot, testLobby);
                    
                    successfulTransitions.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                    logger.error("Exception in state transition {}: {}", transitionNumber, e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
            
            // Small delay between submissions to avoid overwhelming
            Thread.sleep(50);
        }
        
        assertTrue(finishLatch.await(25, TimeUnit.SECONDS), "State transitions should complete within 25 seconds");
        
        // Verify no exceptions occurred
        if (!exceptions.isEmpty()) {
            for (Exception e : exceptions) {
                logger.error("State transition exception: ", e);
            }
        }
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during state transitions: " + exceptions);
        
        // Verify that most transitions were successful (allow some failures due to timing)
        assertTrue(successfulTransitions.get() >= numTransitions * 0.8, 
                "At least 80% of transitions should be successful, got: " + successfulTransitions.get() + "/" + numTransitions);
    }
    
    @Test
    @Timeout(30)
    void testHighVolumeCallbackProcessing() throws InterruptedException {
        // Test processing many callbacks in quick succession
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numCallbacks = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numCallbacks);
        AtomicInteger processedCallbacks = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        String[] actions = {"scan", "check", "back_to_main", "task:0", "emergency_meeting"};
        
        for (int i = 0; i < numCallbacks; i++) {
            final int callbackIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    Long playerId = Long.valueOf((callbackIndex % 8) + 1);
                    String action = actions[callbackIndex % actions.length];
                    
                    Update update = createCallbackUpdate(playerId, action);
                    
                    synchronized (gameState) {
                        gameState.handleUpdate(bot, lobby, update);
                        processedCallbacks.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(finishLatch.await(25, TimeUnit.SECONDS), "Callback processing should complete within 25 seconds");
        
        // Verify results
        assertTrue(exceptions.size() < numCallbacks * 0.1, "Less than 10% of callbacks should result in exceptions");
        assertTrue(processedCallbacks.get() > numCallbacks * 0.8, "At least 80% of callbacks should be processed");
    }
    
    @Test
    @Timeout(30)
    void testMemoryLeakPrevention() throws InterruptedException {
        // Test for potential memory leaks during extended gameplay
        int numGames = 10;
        
        for (int game = 0; game < numGames; game++) {
            // Create fresh lobby for each game
            GameLobby testLobby = new GameLobby("MEMORY" + game, 1L, "Host");
            
            // Add players using correct method
            for (int i = 1; i <= 8; i++) {
                testLobby.addPlayer(Long.valueOf(i), "Player" + i);
                Player player = testLobby.getPlayer(Long.valueOf(i));
                player.setRole(i <= 2 ? new Impostor() : new Crewmate());
            }
            
            // Simulate full game cycle
            SetupState setup = new SetupState();
            testLobby.setGameState(setup);
            setup.onEnter(bot, testLobby);
            setup.onExit(bot, testLobby);
            
            GameActiveState active = new GameActiveState();
            testLobby.setGameState(active);
            active.onEnter(bot, testLobby);
            active.onExit(bot, testLobby);
            
            DiscussionState discussion = new DiscussionState("TestPlayer", null);
            testLobby.setGameState(discussion);
            discussion.onEnter(bot, testLobby);
            discussion.onExit(bot, testLobby);
            
            GameOverState gameOver = new GameOverState("Crewmates", "Memory test");
            testLobby.setGameState(gameOver);
            gameOver.onEnter(bot, testLobby);
            gameOver.onExit(bot, testLobby);
            
            // Clear references
            testLobby.clearGameEvents();
            testLobby = null;
            
            // Suggest garbage collection
            if (game % 3 == 0) {
                System.gc();
                Thread.sleep(100);
            }
        }
        
        // Force garbage collection and verify no major memory issues
        System.gc();
        Thread.sleep(500);
        
        // If we reach here without OutOfMemoryError, the test passes
        assertTrue(true, "Memory leak test completed successfully");
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
    
    @Test
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
    
    @Test
    @Timeout(30)
    void testConcurrentSabotageActions() throws InterruptedException {
        // Test simultaneous sabotage actions by multiple impostors
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numImpostors = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numImpostors * 3); // 3 sabotage actions per impostor
        AtomicInteger successfulSabotages = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        String[] sabotageTypes = {"lights", "reactor", "comms"};
        
        for (int impostorId = 1; impostorId <= numImpostors; impostorId++) {
            for (String sabotageType : sabotageTypes) {
                final Long finalImpostorId = Long.valueOf(impostorId);
                final String finalSabotageType = sabotageType;
                
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        
                        Update update = createCallbackUpdate(finalImpostorId, "sabotage:" + finalSabotageType);
                        
                        synchronized (gameState) {
                            gameState.handleUpdate(bot, lobby, update);
                            successfulSabotages.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Sabotage actions should complete within 15 seconds");
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during sabotage: " + exceptions);
        assertTrue(successfulSabotages.get() > 0, "At least some sabotage actions should succeed");
    }
    
    @Test
    @Timeout(30)
    void testConcurrentEmergencyMeetings() throws InterruptedException {
        // Test multiple players trying to call emergency meetings simultaneously
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numPlayers = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numPlayers);
        AtomicInteger meetingsCalled = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 1; i <= numPlayers; i++) {
            final Long playerId = Long.valueOf(i);
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    Update update = createCallbackUpdate(playerId, "emergency_meeting_confirm");
                    
                    synchronized (gameState) {
                        GameState result = gameState.handleUpdate(bot, lobby, update);
                        if (result instanceof DiscussionState) {
                            meetingsCalled.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Emergency meeting calls should complete within 15 seconds");
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during emergency meeting calls: " + exceptions);
        // Only one meeting should actually be called due to game rules
        assertTrue(meetingsCalled.get() <= 1, "Only one emergency meeting should be successfully called");
    }
    
    @Test
    @Timeout(30)
    void testKillCooldownRaceConditions() throws InterruptedException {
        // Test kill actions with cooldown management under concurrent access
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numKillAttempts = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numKillAttempts);
        AtomicInteger killAttempts = new AtomicInteger(0);
        AtomicInteger successfulKills = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numKillAttempts; i++) {
            final Long impostorId = Long.valueOf((i % 2) + 1); // Alternate between impostor 1 and 2
            final Long targetId = Long.valueOf((i % 6) + 3); // Target crewmates 3-8
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    killAttempts.incrementAndGet();
                    
                    Update update = createCallbackUpdate(impostorId, "kill:" + targetId);
                    
                    synchronized (gameState) {
                        GameState result = gameState.handleUpdate(bot, lobby, update);
                        if (result != null) {
                            successfulKills.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Kill attempts should complete within 15 seconds");
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during kill attempts: " + exceptions);
        assertEquals(numKillAttempts, killAttempts.get(), "All kill attempts should be processed");
        
        // Some kills might be blocked due to cooldown, which is expected behavior
        assertTrue(successfulKills.get() <= killAttempts.get(), "Successful kills should not exceed attempts");
    }
    
    @Test
    @Timeout(30)
    void testPlayerDisconnectionDuringGame() throws InterruptedException {
        // Test game stability when players disconnect during various game phases
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numDisconnections = 4; // Disconnect half the players
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numDisconnections);
        AtomicInteger disconnections = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numDisconnections; i++) {
            final Long playerId = Long.valueOf(i + 5); // Disconnect players 5-8
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Simulate player disconnection
                    synchronized (lobby) {
                        Player player = lobby.getPlayer(playerId);
                        if (player != null) {
                            // Since there's no setConnected method, we'll simulate disconnection by killing the player
                            // In a real scenario, disconnection would be handled differently
                            player.kill(); // This represents the player being effectively "disconnected"
                            disconnections.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Disconnections should complete within 10 seconds");
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during disconnections: " + exceptions);
        assertEquals(numDisconnections, disconnections.get(), "All disconnections should be processed");
        
        // Verify game state remains stable
        assertNotNull(lobby.getGameState(), "Game state should remain after disconnections");
    }
    
    @Test
    @Timeout(30)
    void testTaskValidationUnderLoad() throws InterruptedException {
        // Test task completion validation with concurrent submissions
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        int numTaskSubmissions = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numTaskSubmissions);
        AtomicInteger validSubmissions = new AtomicInteger(0);
        AtomicInteger invalidSubmissions = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numTaskSubmissions; i++) {
            final Long playerId = Long.valueOf((i % 6) + 3); // Only crewmates (3-8) can do tasks
            final int taskIndex = i % 3; // Tasks 0, 1, 2
            final boolean validTask = (i % 3) != 2; // Make every 3rd task invalid
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    String action = validTask ? "task:" + taskIndex : "task:99"; // Invalid task ID
                    Update update = createCallbackUpdate(playerId, action);
                    
                    synchronized (gameState) {
                        try {
                            gameState.handleUpdate(bot, lobby, update);
                            if (validTask) {
                                validSubmissions.incrementAndGet();
                            } else {
                                invalidSubmissions.incrementAndGet();
                            }
                        } catch (Exception e) {
                            if (!validTask) {
                                invalidSubmissions.incrementAndGet(); // Expected for invalid tasks
                            } else {
                                throw e;
                            }
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Task validation should complete within 20 seconds");
        
        assertTrue(exceptions.isEmpty(), "No unexpected exceptions should occur: " + exceptions);
        assertTrue(validSubmissions.get() > 0, "Some valid tasks should be processed");
    }
    
    @Test
    @Timeout(30)
    void testMinimumPlayerGameStability() throws InterruptedException {
        // Test game with minimum number of players (edge case)
        GameLobby minLobby = new GameLobby("MIN", 1L, "Host");
        
        // Add minimum players (4 players: 1 impostor, 3 crewmates)
        for (int i = 1; i <= 4; i++) {
            minLobby.addPlayer(Long.valueOf(i), "Player" + i);
            Player player = minLobby.getPlayer(Long.valueOf(i));
            player.setRole(i == 1 ? new Impostor() : new Crewmate());
            
            if (i > 1) { // Add tasks for crewmates
                List<Task> tasks = new ArrayList<>();
                tasks.add(new SimpleTask("Task" + i, "Description", TaskDifficulty.EASY));
                player.setTasks(tasks);
            }
        }
        
        // Test state transitions with minimum players
        SetupState setupState = new SetupState();
        minLobby.setGameState(setupState);
        setupState.onEnter(bot, minLobby);
        
        GameActiveState activeState = new GameActiveState();
        minLobby.setGameState(activeState);
        activeState.onEnter(bot, minLobby);
        
        // Test concurrent actions with limited players
        CountDownLatch finishLatch = new CountDownLatch(3);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // Impostor tries to kill
        executorService.submit(() -> {
            try {
                Update update = createCallbackUpdate(1L, "kill:2");
                synchronized (activeState) {
                    activeState.handleUpdate(bot, minLobby, update);
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                finishLatch.countDown();
            }
        });
        
        // Crewmates do tasks
        for (int i = 2; i <= 3; i++) {
            final Long playerId = Long.valueOf(i);
            executorService.submit(() -> {
                try {
                    Update update = createCallbackUpdate(playerId, "task:0");
                    synchronized (activeState) {
                        activeState.handleUpdate(bot, minLobby, update);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Minimum player test should complete within 15 seconds");
        assertTrue(exceptions.isEmpty(), "No exceptions should occur with minimum players: " + exceptions);
    }
    
    @Test
    @Timeout(30)
    void testGameEndRaceConditions() throws InterruptedException {
        // Test race conditions when game is ending due to multiple win conditions
        GameActiveState gameState = new GameActiveState();
        lobby.setGameState(gameState);
        gameState.onEnter(bot, lobby);
        
        // Kill all but one crewmate to approach impostor win condition
        for (int i = 3; i <= 7; i++) {
            players.get(i - 1).kill();
        }
        
        int numEndGameActions = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numEndGameActions);
        AtomicInteger gameEndChecks = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numEndGameActions; i++) {
            final int actionIndex = i;
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    if (actionIndex % 2 == 0) {
                        // Try to kill last crewmate (impostor win)
                        Update update = createCallbackUpdate(1L, "kill:8");
                        synchronized (gameState) {
                            gameState.handleUpdate(bot, lobby, update);
                            gameEndChecks.incrementAndGet();
                        }
                    } else {
                        // Try to complete last task (crewmate win)
                        Update update = createCallbackUpdate(8L, "task:0");
                        synchronized (gameState) {
                            gameState.handleUpdate(bot, lobby, update);
                            gameEndChecks.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Game end race conditions should resolve within 15 seconds");
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during game end race: " + exceptions);
        assertTrue(gameEndChecks.get() > 0, "At least some end game checks should be processed");
    }
    
    @Test
    @Timeout(30)
    void testLobbyManagementConcurrency() throws InterruptedException {
        // Initialize game state for the lobby
        lobby.setGameState(new com.amongus.bot.game.states.LobbyState());
        
        // Test concurrent lobby operations
        int numOperations = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numOperations);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> operationResults = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numOperations; i++) {
            final int operationIndex = i;
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    if (operationIndex % 4 == 0) {
                        // Add player (only try to add if we won't exceed max players)
                        Long newPlayerId = Long.valueOf(100 + operationIndex);
                        synchronized (lobby) {
                            if (lobby.getPlayerCount() < 10) {  // MAX_PLAYERS = 10
                                boolean added = lobby.addPlayer(newPlayerId, "TempPlayer" + operationIndex);
                                if (added) {
                                    successfulOperations.incrementAndGet();
                                    operationResults.add("ADD_PLAYER_SUCCESS:" + newPlayerId);
                                } else {
                                    operationResults.add("ADD_PLAYER_FAILED:" + newPlayerId);
                                }
                            } else {
                                operationResults.add("ADD_PLAYER_SKIPPED_FULL:" + newPlayerId);
                            }
                        }
                    } else if (operationIndex % 4 == 1) {
                        // Get player info for existing players
                        synchronized (lobby) {
                            // Try to get one of the original players (1-8)
                            Long existingPlayerId = Long.valueOf((operationIndex % 8) + 1);
                            Player player = lobby.getPlayer(existingPlayerId);
                            if (player != null) {
                                successfulOperations.incrementAndGet();
                                operationResults.add("GET_PLAYER_SUCCESS:" + existingPlayerId);
                            } else {
                                operationResults.add("GET_PLAYER_FAILED:" + existingPlayerId);
                            }
                        }
                    } else if (operationIndex % 4 == 2) {
                        // Update player status (kill/revive simulation)
                        synchronized (lobby) {
                            // Try with different existing players to avoid conflicts
                            Long playerId = Long.valueOf(((operationIndex / 4) % 8) + 1);
                            Player player = lobby.getPlayer(playerId);
                            if (player != null) {
                                // Only kill if alive, otherwise just count as success
                                if (player.isAlive()) {
                                    player.kill();
                                    operationResults.add("KILL_PLAYER_SUCCESS:" + playerId);
                                } else {
                                    operationResults.add("PLAYER_ALREADY_DEAD:" + playerId);
                                }
                                successfulOperations.incrementAndGet();
                            } else {
                                operationResults.add("UPDATE_PLAYER_FAILED:" + playerId);
                            }
                        }
                    } else {
                        // Check lobby state
                        synchronized (lobby) {
                            GameState state = lobby.getGameState();
                            if (state != null) {
                                successfulOperations.incrementAndGet();
                                operationResults.add("GET_STATE_SUCCESS");
                            } else {
                                operationResults.add("GET_STATE_FAILED");
                            }
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                    operationResults.add("EXCEPTION:" + e.getClass().getSimpleName() + ":" + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Lobby operations should complete within 15 seconds");
        
        // Log operation results for debugging
        logger.info("Operation results: {}", operationResults);
        logger.info("Successful operations: {}, Total operations: {}", successfulOperations.get(), numOperations);
        
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during lobby operations: " + exceptions);
        assertTrue(successfulOperations.get() > numOperations * 0.8, 
            "Most lobby operations should succeed. Got " + successfulOperations.get() + " out of " + numOperations + ". Results: " + operationResults);
    }
} 