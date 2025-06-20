package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LobbyStateTest {

    @Mock
    private AmongUsBot bot;
    
    @Mock
    private GameLobby lobby;
    
    private LobbyState lobbyState;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lobbyState = new LobbyState();
    }
    
    @Test
    void testGetStateName() {
        assertEquals("LOBBY", lobbyState.getStateName());
    }
    
    @Test
    void testOnEnter() {
        // Подготовка тестовых данных
        Player host = new Player(1L, "Host");
        host.setChatId(100L);
        Player player = new Player(2L, "Player");
        player.setChatId(200L);
        
        when(lobby.getPlayerList()).thenReturn(java.util.Arrays.asList(host, player));
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.isHost(1L)).thenReturn(true);
        when(lobby.isHost(2L)).thenReturn(false);
        
        // Выполнение метода
        lobbyState.onEnter(bot, lobby);
        
        // Проверка результатов
        verify(bot, times(2)).executeMethod(any(SendMessage.class));
    }
    
    @Test
    void testHandleUpdateStartGame() {
        // Подготовка тестовых данных
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();
        
        user.setId(1L);
        chat.setId(100L);
        message.setChat(chat);
        callbackQuery.setFrom(user);
        callbackQuery.setMessage(message);
        callbackQuery.setData("start_game");
        update.setCallbackQuery(callbackQuery);
        
        when(lobby.isHost(1L)).thenReturn(true);
        
        // Выполнение метода
        GameState nextState = lobbyState.handleUpdate(bot, lobby, update);
        
        // Проверка результатов
        assertNotNull(nextState);
        assertTrue(nextState instanceof SetupState);
    }
    
    @Test
    void testHandleUpdateNonHost() {
        // Подготовка тестовых данных
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();
        
        user.setId(2L);
        chat.setId(200L);
        message.setChat(chat);
        callbackQuery.setFrom(user);
        callbackQuery.setMessage(message);
        callbackQuery.setData("start_game");
        update.setCallbackQuery(callbackQuery);
        
        when(lobby.isHost(2L)).thenReturn(false);
        
        // Выполнение метода
        GameState nextState = lobbyState.handleUpdate(bot, lobby, update);
        
        // Проверка результатов
        assertNull(nextState);
    }
    
    @Test
    void testCanPerformAction() {
        // Тест для хоста
        when(lobby.isHost(1L)).thenReturn(true);
        assertTrue(lobbyState.canPerformAction(lobby, 1L, "start_game"));
        
        // Тест для обычного игрока
        when(lobby.isHost(2L)).thenReturn(false);
        assertFalse(lobbyState.canPerformAction(lobby, 2L, "start_game"));
        
        // Тест для неизвестного действия
        assertFalse(lobbyState.canPerformAction(lobby, 1L, "unknown_action"));
    }
} 