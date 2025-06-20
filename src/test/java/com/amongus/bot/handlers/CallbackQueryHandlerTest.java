package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.states.GameState;
import com.amongus.bot.game.states.LobbyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallbackQueryHandlerTest {

    @Mock private AmongUsBot bot;
    @Mock private LobbyManager lobbyManager;
    @Mock private GameLobby lobby;
    @Mock private GameState gameState;

    private CallbackQueryHandler callbackQueryHandler;

    @BeforeEach
    void setUp() throws TelegramApiException {
        MockitoAnnotations.openMocks(this);
        callbackQueryHandler = new CallbackQueryHandler(bot, lobbyManager);

        // Настраиваем базовые моки
        when(lobbyManager.getLobbyForPlayer(any(Long.class))).thenReturn(lobby);
        when(lobby.getGameState()).thenReturn(gameState);
        when(lobby.getLobbyCode()).thenReturn("TEST123");

        // ИСПРАВЛЕНО: Мокаем execute для AnswerCallbackQuery (из AmongUsBot видно, что он использует execute)
        when(bot.execute(any(AnswerCallbackQuery.class))).thenReturn(true);

        // Мокаем setGameState
        doNothing().when(lobby).setGameState(any(GameState.class));
    }

    @Test
    void testHandleCallbackWithLobbyAndStateTransition() throws TelegramApiException {
        Update update = createCallbackUpdate("start_game", 1L, 101L);
        LobbyState newState = new LobbyState();

        when(gameState.handleUpdate(bot, lobby, update)).thenReturn(newState);

        callbackQueryHandler.handle(update);

        verify(gameState).handleUpdate(bot, lobby, update);
        verify(lobby).setGameState(newState);
        verify(bot).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void testHandleCallbackWithLobbyNoStateTransition() throws TelegramApiException {
        Update update = createCallbackUpdate("vote:2", 1L, 101L);

        when(gameState.handleUpdate(bot, lobby, update)).thenReturn(null);

        callbackQueryHandler.handle(update);

        verify(gameState).handleUpdate(bot, lobby, update);
        verify(lobby, never()).setGameState(any(GameState.class));
        verify(bot).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void testHandleCallbackWithoutLobby() throws TelegramApiException {
        Update update = createCallbackUpdate("start_game", 1L, 101L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(null);

        callbackQueryHandler.handle(update);

        verify(gameState, never()).handleUpdate(any(), any(), any());
        verify(bot).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void testHandleNullUpdate() {
        assertThrows(NullPointerException.class, () -> callbackQueryHandler.handle(null));
    }

    @Test
    void testHandleUpdateWithoutCallbackQuery() {
        Update update = new Update();
        update.setMessage(mock(Message.class));

        assertThrows(NullPointerException.class, () -> callbackQueryHandler.handle(update));
    }

    private Update createCallbackUpdate(String data, Long userId, Long chatId) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();

        callbackQuery.setId("callback_" + System.currentTimeMillis());
        user.setId(userId);
        user.setFirstName("TestUser");
        chat.setId(chatId);

        message.setChat(chat);
        message.setMessageId(1);
        callbackQuery.setFrom(user);
        callbackQuery.setMessage(message);
        callbackQuery.setData(data);

        update.setCallbackQuery(callbackQuery);
        return update;
    }
}
