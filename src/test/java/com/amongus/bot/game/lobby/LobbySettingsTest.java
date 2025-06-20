package com.amongus.bot.game.lobby;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class LobbySettingsTest {
    private LobbySettings settings;

    @BeforeEach
    void setUp() {
        settings = new LobbySettings();
    }

    @Test
    void testDefaults() {
        assertEquals(LobbySettings.DEFAULT_IMPOSTOR_COUNT, settings.getImpostorCount());
        assertEquals(LobbySettings.DEFAULT_EMERGENCY_MEETINGS, settings.getEmergencyMeetings());
        assertEquals(LobbySettings.DEFAULT_DISCUSSION_TIME, settings.getDiscussionTime());
        assertEquals(LobbySettings.DEFAULT_VOTING_TIME, settings.getVotingTime());
        assertEquals(LobbySettings.DEFAULT_TASKS_PER_PLAYER, settings.getTasksPerPlayer());
        assertEquals(LobbySettings.DEFAULT_KILL_COOLDOWN, settings.getKillCooldown());
    }

    @Test
    void testUpdateSettingValid() {
        assertTrue(settings.updateSetting("impostor_count", 2));
        assertEquals(2, settings.getImpostorCount());
        assertTrue(settings.updateSetting("emergency_meetings", 3));
        assertEquals(3, settings.getEmergencyMeetings());
    }

    @Test
    void testUpdateSettingInvalidName() {
        assertFalse(settings.updateSetting("unknown_setting", 5));
    }

    @Test
    void testUpdateSettingInvalidValue() {
        assertFalse(settings.updateSetting("impostor_count", 0));
        assertEquals(LobbySettings.DEFAULT_IMPOSTOR_COUNT, settings.getImpostorCount());
        assertFalse(settings.updateSetting("impostor_count", 100));
        assertEquals(LobbySettings.DEFAULT_IMPOSTOR_COUNT, settings.getImpostorCount());
    }

    @Test
    void testResetToDefaults() {
        settings.updateSetting("impostor_count", 2);
        settings.resetToDefaults();
        assertEquals(LobbySettings.DEFAULT_IMPOSTOR_COUNT, settings.getImpostorCount());
    }

    @Test
    void testGetAllSettings() {
        Map<String, Integer> all = settings.getAllSettings();
        assertTrue(all.containsKey(LobbySettings.IMPOSTOR_COUNT));
        assertTrue(all.containsKey(LobbySettings.EMERGENCY_MEETINGS));
        assertEquals(LobbySettings.DEFAULT_IMPOSTOR_COUNT, all.get(LobbySettings.IMPOSTOR_COUNT));
    }

    @Test
    void testUserFriendlyNames() {
        assertTrue(settings.updateSetting("impostors", 2));
        assertEquals(2, settings.getImpostorCount());
        assertTrue(settings.updateSetting("meetings", 2));
        assertEquals(2, settings.getEmergencyMeetings());
        assertTrue(settings.updateSetting("discussion", 60));
        assertEquals(60, settings.getDiscussionTime());
        assertTrue(settings.updateSetting("voting", 60));
        assertEquals(60, settings.getVotingTime());
        assertTrue(settings.updateSetting("tasks", 5));
        assertEquals(5, settings.getTasksPerPlayer());
        assertTrue(settings.updateSetting("cooldown", 30));
        assertEquals(30, settings.getKillCooldown());
    }
} 