package com.amongus.bot.game.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class TaskTest {
    private static final String TASK_NAME = "Test Task";
    private static final String TASK_DESCRIPTION = "Test Description";
    private static final Long OWNER_ID = 123L;

    @Test
    void testSimpleTaskCreation() {
        Task task = new SimpleTask(TASK_NAME, TASK_DESCRIPTION, TaskDifficulty.EASY);
        
        assertEquals(TASK_NAME, task.getName());
        assertEquals(TASK_DESCRIPTION, task.getDescription());
        assertEquals(TaskDifficulty.EASY, task.getDifficulty());
        assertFalse(task.isCompleted());
        assertNull(task.getOwnerId());
    }

    @Test
    void testTaskCompletion() {
        Task task = new SimpleTask(TASK_NAME, TASK_DESCRIPTION, TaskDifficulty.MEDIUM);
        
        assertFalse(task.isCompleted());
        task.complete();
        assertTrue(task.isCompleted());
    }

    @Test
    void testTaskOwnership() {
        Task task = new SimpleTask(TASK_NAME, TASK_DESCRIPTION, TaskDifficulty.HARD);
        
        assertNull(task.getOwnerId());
        task.setOwnerId(OWNER_ID);
        assertEquals(OWNER_ID, task.getOwnerId());
    }

    @Test
    void testTaskDuplication() {
        Task original = new SimpleTask(TASK_NAME, TASK_DESCRIPTION, TaskDifficulty.MEDIUM);
        original.setOwnerId(OWNER_ID);
        original.complete();
        
        Task duplicate = original.duplicate();
        
        // Проверяем, что базовые свойства скопированы
        assertEquals(original.getName(), duplicate.getName());
        assertEquals(original.getDescription(), duplicate.getDescription());
        assertEquals(original.getDifficulty(), duplicate.getDifficulty());
        
        // Проверяем, что состояние и владелец не копируются
        assertFalse(duplicate.isCompleted());
        assertNull(duplicate.getOwnerId());
    }

    @ParameterizedTest
    @EnumSource(TaskDifficulty.class)
    void testTaskDifficultyDisplayNames(TaskDifficulty difficulty) {
        String displayName = difficulty.getDisplayName();
        assertNotNull(displayName);
        assertFalse(displayName.isEmpty());
        
        switch (difficulty) {
            case EASY:
                assertEquals("Легко", displayName);
                break;
            case MEDIUM:
                assertEquals("Средне", displayName);
                break;
            case HARD:
                assertEquals("Сложно", displayName);
                break;
        }
    }

    @Test
    void testMultipleTasksIndependence() {
        Task task1 = new SimpleTask("Task 1", "Description 1", TaskDifficulty.EASY);
        Task task2 = new SimpleTask("Task 2", "Description 2", TaskDifficulty.MEDIUM);
        
        task1.complete();
        assertTrue(task1.isCompleted());
        assertFalse(task2.isCompleted());
        
        task1.setOwnerId(OWNER_ID);
        assertNull(task2.getOwnerId());
    }

    @Test
    void testTaskStateConsistency() {
        Task task = new SimpleTask(TASK_NAME, TASK_DESCRIPTION, TaskDifficulty.EASY);
        
        // Проверяем неизменность базовых свойств
        String initialName = task.getName();
        String initialDescription = task.getDescription();
        TaskDifficulty initialDifficulty = task.getDifficulty();
        
        task.complete();
        task.setOwnerId(OWNER_ID);
        
        assertEquals(initialName, task.getName());
        assertEquals(initialDescription, task.getDescription());
        assertEquals(initialDifficulty, task.getDifficulty());
    }
} 