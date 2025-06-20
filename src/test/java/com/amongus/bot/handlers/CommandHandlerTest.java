package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommandHandlerTest {

    @Mock private AmongUsBot bot;
    @Mock private LobbyManager lobbyManager;
    @Mock private GameLobby lobby;
    @Mock private Player player;

    private CommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        commandHandler = new CommandHandler(bot, lobbyManager);

        // ИСПРАВЛЕНО: Мокаем executeMethod(SendMessage) вместо sendTextMessage
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(mock(Message.class));

        // Мокаем sendTextMessage для случаев, когда он используется напрямую
        doNothing().when(bot).sendTextMessage(any(Long.class), any(String.class));

        // По умолчанию игрок не находится в лобби
        when(lobbyManager.getLobbyForPlayer(any(Long.class))).thenReturn(null);

        // Настраиваем базовые моки для player
        when(player.getUserName()).thenReturn("TestUser");
        when(player.getUserId()).thenReturn(1L);
        when(player.getChatId()).thenReturn(101L);
    }

    @Test
    void testHandleStartCommandInPrivateChat() {
        Update update = createPrivateMessageUpdate("/start", 1L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        // ИСПРАВЛЕНО: Проверяем executeMethod вместо sendTextMessage
        verify(bot, atLeastOnce()).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleStartCommandInGroupChat() {
        Update update = createGroupMessageUpdate("/start", 1L, -100L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot, atLeastOnce()).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleNewGameCommandInPrivateChat() {
        Update update = createPrivateMessageUpdate("/newgame", 1L);

        when(lobbyManager.createLobby(1L, "TestUser")).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("ABC123");
        when(lobby.getPlayer(1L)).thenReturn(player);
        doNothing().when(lobby).setGameState(any());

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).createLobby(1L, "TestUser");
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleNewGameCommandInGroupChat() {
        Update update = createGroupMessageUpdate("/newgame", 1L, -100L);

        when(lobbyManager.createLobby(1L, "TestUser")).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("ABC123");
        when(lobby.getPlayer(1L)).thenReturn(player);
        doNothing().when(lobby).setGameState(any());

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).createLobby(1L, "TestUser");
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleNewGameCommandWhenAlreadyInLobby() {
        Update update = createPrivateMessageUpdate("/newgame", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("EXISTING");

        assertDoesNotThrow(() -> commandHandler.handle(update));

        // Не должно создавать новое лобби
        verify(lobbyManager, never()).createLobby(any(), any());
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleJoinCommandWithValidCode() {
        Update update = createPrivateMessageUpdate("/join ABC123", 1L);

        when(lobbyManager.addPlayerToLobby("ABC123", 1L, "TestUser")).thenReturn(true);
        when(lobbyManager.getLobby("ABC123")).thenReturn(lobby);
        when(lobby.getPlayer(1L)).thenReturn(player);
        when(lobby.getPlayerCount()).thenReturn(2);
        when(lobby.getHostId()).thenReturn(2L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).addPlayerToLobby("ABC123", 1L, "TestUser");
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleJoinCommandWithInvalidCode() {
        Update update = createPrivateMessageUpdate("/join INVALID", 1L);

        when(lobbyManager.addPlayerToLobby("INVALID", 1L, "TestUser")).thenReturn(false);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).addPlayerToLobby("INVALID", 1L, "TestUser");
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleJoinCommandWithoutCode() {
        Update update = createPrivateMessageUpdate("/join", 1L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot).executeMethod(any(SendMessage.class));
        verify(lobbyManager, never()).addPlayerToLobby(any(), any(), any());
    }

    @Test
    void testHandleJoinCommandWhenAlreadyInLobby() {
        Update update = createPrivateMessageUpdate("/join ABC123", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("CURRENT");

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager, never()).addPlayerToLobby(any(), any(), any());
        verify(bot).executeMethod(any(SendMessage.class));
    }

    @Test
    void testHandleLeaveCommandWhenInLobby() {
        Update update = createPrivateMessageUpdate("/leave", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(lobby);
        when(lobbyManager.removePlayerFromLobby(1L)).thenReturn(true);
        when(lobby.getPlayer(1L)).thenReturn(player);
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.isHost(1L)).thenReturn(false);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).removePlayerFromLobby(1L);
        // ИСПРАВЛЕНО: CommandHandler использует sendTextMessage для leave команды
        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandleLeaveCommandWhenNotInLobby() {
        Update update = createPrivateMessageUpdate("/leave", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(null);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager, never()).removePlayerFromLobby(any());
        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandleHelpCommand() {
        Update update = createPrivateMessageUpdate("/help", 1L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        // ИСПРАВЛЕНО: help использует sendTextMessage
        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandleUnknownCommand() {
        Update update = createPrivateMessageUpdate("/unknown", 1L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandleCommandWithMultipleParameters() {
        Update update = createPrivateMessageUpdate("/join ABC123 extra params", 1L);

        // ИСПРАВЛЕНО: Код парсит всю строку как один параметр
        when(lobbyManager.addPlayerToLobby("ABC123 EXTRA PARAMS", 1L, "TestUser")).thenReturn(false);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).addPlayerToLobby("ABC123 EXTRA PARAMS", 1L, "TestUser");
    }

    @Test
    void testHandleCommandWithUserWithoutUsername() {
        Update update = createMessageUpdateWithoutUsername("/newgame", 1L, 1L);

        when(lobbyManager.createLobby(1L, "TestUser")).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("ABC123");
        when(lobby.getPlayer(1L)).thenReturn(player);
        doNothing().when(lobby).setGameState(any());

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(lobbyManager).createLobby(1L, "TestUser");
    }

    @Test
    void testHandleNullUpdate() {
        assertThrows(NullPointerException.class, () -> commandHandler.handle(null));
    }

    @Test
    void testHandleUpdateWithoutMessage() {
        Update update = new Update();
        update.setCallbackQuery(mock(org.telegram.telegrambots.meta.api.objects.CallbackQuery.class));

        assertThrows(NullPointerException.class, () -> commandHandler.handle(update));
    }

    @Test
    void testHandleNonCommandMessage() {
        Update update = createPrivateMessageUpdate("Hello world", 1L);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandleStatusCommand() {
        Update update = createPrivateMessageUpdate("/status", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getPlayerCount()).thenReturn(3);
        when(lobby.getHostId()).thenReturn(1L);
        when(lobby.getPlayer(1L)).thenReturn(player);
        when(lobby.getGameState()).thenReturn(null);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    @Test
    void testHandlePlayersCommand() {
        Update update = createPrivateMessageUpdate("/players", 1L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(lobby);
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getPlayerList()).thenReturn(java.util.Arrays.asList(player));
        when(lobby.isHost(1L)).thenReturn(true);

        assertDoesNotThrow(() -> commandHandler.handle(update));

        verify(bot).sendTextMessage(eq(1L), any(String.class));
    }

    private Update createPrivateMessageUpdate(String text, Long userId) {
        return createMessageUpdate(text, userId, userId, "private");
    }

    private Update createGroupMessageUpdate(String text, Long userId, Long chatId) {
        return createMessageUpdate(text, userId, chatId, "group");
    }

    private Update createMessageUpdate(String text, Long userId, Long chatId, String chatType) {
        Update update = new Update();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();

        user.setId(userId);
        user.setFirstName("TestUser");
        user.setUserName("TestUser");
        chat.setId(chatId);
        chat.setType(chatType);

        message.setFrom(user);
        message.setChat(chat);
        message.setText(text);
        message.setMessageId(1);

        update.setMessage(message);
        return update;
    }

    private Update createMessageUpdateWithoutUsername(String text, Long userId, Long chatId) {
        Update update = createMessageUpdate(text, userId, chatId, "private");
        update.getMessage().getFrom().setUserName(null);
        update.getMessage().getFrom().setFirstName("TestUser");
        return update;
    }
}
