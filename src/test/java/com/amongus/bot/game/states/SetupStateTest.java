package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.models.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SetupStateTest {

    @Mock private AmongUsBot bot;
    @Mock private GameLobby lobby;
    @Mock private LobbySettings settings;

    private SetupState setupState;
    private List<Player> players;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        setupState = new SetupState();

        // Создаем список игроков для тестов
        players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Player player = new Player((long) i, "Player" + i);
            player.setChatId((long) (100 + i));
            players.add(player);
        }

        // Настраиваем моки
        when(lobby.getPlayerList()).thenReturn(players);
        when(lobby.getPlayerCount()).thenReturn(players.size());
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getSettings()).thenReturn(settings);
        when(settings.getTasksPerPlayer()).thenReturn(3);

        // ИСПРАВЛЕНО: Мокаем adjustImpostorCount
        doNothing().when(settings).adjustImpostorCount(anyInt());
        when(settings.getImpostorCount()).thenReturn(1);

        // Мокаем executeMethod
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(new Message());
    }

    @Test
    void testGetStateName() {
        assertEquals("SETUP", setupState.getStateName());
    }

    @Test
    void testOnEnter() {
        setupState.onEnter(bot, lobby);

        // Проверяем, что настройки были скорректированы
        verify(settings).adjustImpostorCount(5);

        // Проверяем, что роли были назначены
        int impostorCount = 0;
        int crewmateCount = 0;
        for (Player player : players) {
            assertNotNull(player.getRole(), "Role should be assigned");
            if (player.getRole().isImpostor()) {
                impostorCount++;
            } else {
                crewmateCount++;
            }
        }

        assertTrue(impostorCount > 0, "Should have at least 1 impostor");
        assertTrue(crewmateCount > 0, "Should have at least 1 crewmate");

        // Проверяем отправку сообщений
        verify(bot, atLeast(players.size())).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleUpdate() {
        // Подготовка
        Update update = new Update();

        // Выполнение
        GameState nextState = setupState.handleUpdate(bot, lobby, update);

        // ИСПРАВЛЕНО: SetupState всегда переходит в GameActiveState
        assertNotNull(nextState);
        assertTrue(nextState instanceof GameActiveState);
    }

    @Test
    void testCanPerformAction() {
        // В состоянии настройки никакие действия не разрешены
        assertFalse(setupState.canPerformAction(lobby, 1L, "any_action"));
    }

    @Test
    void testOnExit() {
        // Выполнение
        setupState.onExit(bot, lobby);

        // В текущей реализации onExit только логирует
        verify(lobby, atLeastOnce()).getLobbyCode();
    }
}
