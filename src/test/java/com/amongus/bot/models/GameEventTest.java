package com.amongus.bot.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;

class GameEventTest {
    private static final Long USER_ID = 123L;
    private static final String USER_NAME = "TestPlayer";
    private static final String ACTION = "KILL";
    private static final String DETAILS = "Test details";

    @Test
    void testEventCreation() {
        GameEvent event = new GameEvent(USER_ID, USER_NAME, ACTION, DETAILS);
        
        assertEquals(USER_ID, event.getUserId());
        assertEquals(USER_NAME, event.getUserName());
        assertEquals(ACTION, event.getAction());
        assertEquals(DETAILS, event.getDetails());
        assertNotNull(event.getTimestamp());
        assertNull(event.getPhotoFileId());
        assertFalse(event.hasPhoto());
    }

    @Test
    void testPhotoManagement() {
        GameEvent event = new GameEvent(USER_ID, USER_NAME, ACTION, DETAILS);
        String photoId = "test_photo_123";
        
        event.setPhotoFileId(photoId);
        assertTrue(event.hasPhoto());
        assertEquals(photoId, event.getPhotoFileId());
    }

    @Test
    void testEmptyPhotoId() {
        GameEvent event = new GameEvent(USER_ID, USER_NAME, ACTION, DETAILS);
        
        event.setPhotoFileId("");
        assertFalse(event.hasPhoto());
        assertEquals("", event.getPhotoFileId());
    }

    @ParameterizedTest
    @CsvSource({
        "KILL,Убийство",
        "TASK,Задание",
        "REPORT,Сообщение о теле",
        "MEETING,Созыв собрания",
        "VOTE,Голосование",
        "VOTE_RESULT,Результат голосования",
        "SABOTAGE,Саботаж",
        "FIX_LIGHTS,Починка света",
        "FIX_REACTOR,Починка реактора",
        "SCAN,Сканирование",
        "FAKE_TASK,Имитация задания",
        "GAME_OVER,Окончание игры",
        "UNKNOWN_ACTION,UNKNOWN_ACTION"
    })
    void testActionDescriptions(String action, String expectedDescription) {
        GameEvent event = new GameEvent(USER_ID, USER_NAME, action, DETAILS);
        String formattedDescription = event.getFormattedDescription();
        
        assertTrue(formattedDescription.contains(expectedDescription));
        assertTrue(formattedDescription.contains(USER_NAME));
        assertTrue(formattedDescription.contains(DETAILS));
    }

    @Test
    void testFormattedDescriptionFormat() {
        GameEvent event = new GameEvent(USER_ID, USER_NAME, ACTION, DETAILS);
        String formatted = event.getFormattedDescription();
        
        // Check time format [HH:MM:SS]
        assertTrue(formatted.matches("\\[\\d{2}:\\d{2}:\\d{2}\\].*"));
        
        // Check all parts are present
        assertTrue(formatted.contains(USER_NAME));
        assertTrue(formatted.contains("Убийство")); // ACTION = "KILL"
        assertTrue(formatted.contains(DETAILS));
    }

    @Test
    void testTimestampCreation() {
        Date before = new Date();
        GameEvent event = new GameEvent(USER_ID, USER_NAME, ACTION, DETAILS);
        Date after = new Date();
        
        Date eventTime = event.getTimestamp();
        
        // Event timestamp should be between before and after
        assertTrue(eventTime.compareTo(before) >= 0);
        assertTrue(eventTime.compareTo(after) <= 0);
    }
} 