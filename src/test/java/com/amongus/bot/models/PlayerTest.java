package com.amongus.bot.models;

import com.amongus.bot.game.roles.Role;
import com.amongus.bot.game.tasks.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

class PlayerTest {
    private Player player;
    private static final Long USER_ID = 123L;
    private static final String USER_NAME = "TestPlayer";
    private static final Long CHAT_ID = 456L;

    @Mock
    private Role mockRole;
    @Mock
    private Task mockTask1;
    @Mock
    private Task mockTask2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        player = new Player(USER_ID, USER_NAME);
    }

    @Test
    void testPlayerCreation() {
        assertEquals(USER_ID, player.getUserId());
        assertEquals(USER_NAME, player.getUserName());
        assertNull(player.getChatId());
        assertNull(player.getRole());
        assertTrue(player.isAlive());
        assertEquals(0, player.getCompletedTaskCount());
        assertEquals(0, player.getTotalTaskCount());
        assertEquals(0, player.getEmergencyMeetingsUsed());
    }

    @Test
    void testChatIdManagement() {
        player.setChatId(CHAT_ID);
        assertEquals(CHAT_ID, player.getChatId());
    }

    @Test
    void testRoleManagement() {
        when(mockRole.isImpostor()).thenReturn(true);
        player.setRole(mockRole);
        
        assertEquals(mockRole, player.getRole());
        assertTrue(player.isImpostor());
    }

    @Test
    void testPlayerDeath() {
        assertTrue(player.isAlive());
        player.kill();
        assertFalse(player.isAlive());
    }

    @Test
    void testTaskManagement() {
        when(mockTask1.isCompleted()).thenReturn(false);
        when(mockTask2.isCompleted()).thenReturn(false);
        
        List<Task> tasks = Arrays.asList(mockTask1, mockTask2);
        player.setTasks(tasks);
        
        assertEquals(2, player.getTotalTaskCount());
        assertEquals(0, player.getCompletedTaskCount());
        assertEquals(0, player.getTaskCompletionPercentage());
        assertFalse(player.hasCompletedAllTasks());
        
        assertTrue(player.completeTask(0));
        verify(mockTask1).complete();
        assertEquals(1, player.getCompletedTaskCount());
        assertEquals(50, player.getTaskCompletionPercentage());
        
        assertTrue(player.completeTask(1));
        verify(mockTask2).complete();
        assertEquals(2, player.getCompletedTaskCount());
        assertEquals(100, player.getTaskCompletionPercentage());
        assertTrue(player.hasCompletedAllTasks());
    }

    @Test
    void testInvalidTaskCompletion() {
        when(mockTask1.isCompleted()).thenReturn(false);
        player.setTasks(Arrays.asList(mockTask1));
        
        assertFalse(player.completeTask(-1));
        assertFalse(player.completeTask(1));
        assertEquals(0, player.getCompletedTaskCount());
    }

    @Test
    void testEmergencyMeetings() {
        assertEquals(0, player.getEmergencyMeetingsUsed());
        assertFalse(player.hasReachedEmergencyMeetingLimit(2));
        
        player.incrementEmergencyMeetingsUsed();
        assertEquals(1, player.getEmergencyMeetingsUsed());
        assertFalse(player.hasReachedEmergencyMeetingLimit(2));
        
        player.incrementEmergencyMeetingsUsed();
        assertEquals(2, player.getEmergencyMeetingsUsed());
        assertTrue(player.hasReachedEmergencyMeetingLimit(2));
    }

    @Test
    void testPhotoConfirmation() {
        assertFalse(player.isAwaitingPhotoConfirmation());
        assertNull(player.getAwaitingPhotoForTaskIndex());
        
        player.setAwaitingPhotoForTaskIndex(1);
        assertTrue(player.isAwaitingPhotoConfirmation());
        assertEquals(Integer.valueOf(1), player.getAwaitingPhotoForTaskIndex());
    }

    @Test
    void testFakeTaskPhotoConfirmation() {
        assertFalse(player.isAwaitingFakeTaskPhotoConfirmation());
        assertNull(player.getAwaitingPhotoForFakeTask());
        
        player.setAwaitingPhotoForFakeTask(1);
        assertTrue(player.isAwaitingFakeTaskPhotoConfirmation());
        assertEquals(Integer.valueOf(1), player.getAwaitingPhotoForFakeTask());
    }

    @Test
    void testReset() {
        // Setup player with various states
        player.setChatId(CHAT_ID);
        player.setRole(mockRole);
        player.kill();
        player.setTasks(Arrays.asList(mockTask1, mockTask2));
        player.completeTask(0);
        player.incrementEmergencyMeetingsUsed();
        player.setAwaitingPhotoForTaskIndex(1);
        player.setAwaitingPhotoForFakeTask(1);
        
        // Reset player
        player.reset();
        
        // Verify reset state
        assertEquals(CHAT_ID, player.getChatId()); // ChatId should persist
        assertNull(player.getRole());
        assertTrue(player.isAlive());
        assertEquals(0, player.getTotalTaskCount());
        assertEquals(0, player.getCompletedTaskCount());
        assertEquals(0, player.getEmergencyMeetingsUsed());
        assertNull(player.getAwaitingPhotoForTaskIndex());
        assertNull(player.getAwaitingPhotoForFakeTask());
    }
} 