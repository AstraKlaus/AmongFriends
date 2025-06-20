package com.amongus.bot.handlers;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;
import com.amongus.bot.game.states.GameActiveState;
import com.amongus.bot.game.tasks.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageHandlerTest {

    @Mock private AmongUsBot bot;
    @Mock private LobbyManager lobbyManager;
    @Mock private GameLobby lobby;
    @Mock private Player player;
    @Mock private GameActiveState gameActiveState;
    @Mock private GameEvent gameEvent;
    @Mock private Task task;

    private MessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageHandler = new MessageHandler(bot, lobbyManager);

        // Настраиваем базовые моки
        when(lobbyManager.getLobbyForPlayer(any(Long.class))).thenReturn(lobby);
        when(lobby.getPlayer(any(Long.class))).thenReturn(player);
        when(lobby.getLobbyCode()).thenReturn("TEST123");
        when(lobby.getGameState()).thenReturn(gameActiveState);

        // ИСПРАВЛЕНО: sendTextMessage возвращает void
        doNothing().when(bot).sendTextMessage(any(Long.class), anyString());

        when(lobby.addGameEvent(any(Long.class), anyString(), anyString())).thenReturn(gameEvent);
        when(player.getUserId()).thenReturn(1L);
    }

    @Test
    void testHandleTextMessage() {
        Update update = createTextMessageUpdate("Я видел предателя!", 1L, 101L);

        assertDoesNotThrow(() -> messageHandler.handle(update));

        // Проверяем, что было отправлено дефолтное сообщение
        verify(bot).sendTextMessage(eq(101L), anyString());
    }

    @Test
    void testHandlePhotoMessage() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(player.isAwaitingPhotoConfirmation()).thenReturn(true);
        when(player.getAwaitingPhotoForTaskIndex()).thenReturn(0);
        when(player.getTasks()).thenReturn(Arrays.asList(task));
        when(task.getName()).thenReturn("Test Task");
        when(player.completeTask(0)).thenReturn(true);
        when(player.getCompletedTaskCount()).thenReturn(1);
        when(player.getTotalTaskCount()).thenReturn(3);

        assertDoesNotThrow(() -> messageHandler.handle(update));

        // Проверяем, что задание было завершено
        verify(player).completeTask(0);
        verify(player).setAwaitingPhotoForTaskIndex(null);
    }

    @Test
    void testHandlePhotoForTask() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(player.isAwaitingPhotoConfirmation()).thenReturn(true);
        when(player.getAwaitingPhotoForTaskIndex()).thenReturn(1);
        when(player.getTasks()).thenReturn(Arrays.asList(task, task));
        when(task.getName()).thenReturn("Test Task");
        when(player.completeTask(1)).thenReturn(true);
        when(player.getCompletedTaskCount()).thenReturn(1);
        when(player.getTotalTaskCount()).thenReturn(3);

        messageHandler.handle(update);

        verify(player).setAwaitingPhotoForTaskIndex(null);
        verify(bot).sendTextMessage(eq(101L), contains("выполнено"));
    }

    @Test
    void testHandlePhotoForFakeTask() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(player.isAwaitingPhotoConfirmation()).thenReturn(false);
        when(player.isAwaitingFakeTaskPhotoConfirmation()).thenReturn(true);
        when(player.getAwaitingPhotoForFakeTask()).thenReturn(0);
        when(player.isImpostor()).thenReturn(true);
        when(gameActiveState.completeFakeTask(1L, 0)).thenReturn(true);
        when(gameActiveState.getFakeTaskName(1L, 0)).thenReturn("Fake Task");
        when(gameActiveState.getCompletedFakeTaskCount(1L)).thenReturn(1);
        when(gameActiveState.getTotalFakeTaskCount(1L)).thenReturn(3);

        messageHandler.handle(update);

        verify(player).setAwaitingPhotoForFakeTask(null);
        verify(gameActiveState).completeFakeTask(1L, 0);
    }

    @Test
    void testHandlePhotoWithoutExpectation() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(player.isAwaitingPhotoConfirmation()).thenReturn(false);
        when(player.isAwaitingFakeTaskPhotoConfirmation()).thenReturn(false);
        when(gameActiveState.isPlayerFixingLights(1L)).thenReturn(false);
        when(gameActiveState.isPlayerAtReactorLocation(1L)).thenReturn(false);

        assertDoesNotThrow(() -> messageHandler.handle(update));

        verify(bot).sendTextMessage(eq(101L), contains("нет действия"));
    }

    @Test
    void testHandleMessageWithoutLobby() {
        Update update = createTextMessageUpdate("Hello", 1L, 101L);

        when(lobbyManager.getLobbyForPlayer(1L)).thenReturn(null);

        assertDoesNotThrow(() -> messageHandler.handle(update));

        verify(bot).sendTextMessage(eq(101L), anyString());
    }

    @Test
    void testHandleMessageFromUnknownPlayer() {
        Update update = createTextMessageUpdate("Hello", 1L, 101L);

        when(lobby.getPlayer(1L)).thenReturn(null);

        assertDoesNotThrow(() -> messageHandler.handle(update));

        verify(bot).sendTextMessage(eq(101L), anyString());
    }

    @Test
    void testHandleLightsFix() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(gameActiveState.isPlayerFixingLights(1L)).thenReturn(true);
        when(gameActiveState.isPlayerAtReactorLocation(1L)).thenReturn(false);
        when(player.isAwaitingPhotoConfirmation()).thenReturn(false);
        when(player.isAwaitingFakeTaskPhotoConfirmation()).thenReturn(false);

        messageHandler.handle(update);

        verify(gameActiveState).confirmLightsFix(bot, lobby, player);
        verify(lobby).addGameEvent(eq(1L), eq("FIX_LIGHTS"), anyString());
    }

    @Test
    void testHandleReactorFix() {
        Update update = createPhotoMessageUpdate(1L, 101L);

        when(gameActiveState.isPlayerFixingLights(1L)).thenReturn(false);
        when(gameActiveState.isPlayerAtReactorLocation(1L)).thenReturn(true);
        when(player.isAwaitingPhotoConfirmation()).thenReturn(false);
        when(player.isAwaitingFakeTaskPhotoConfirmation()).thenReturn(false);

        messageHandler.handle(update);

        verify(gameActiveState).confirmReactorFix(bot, lobby, player);
        verify(lobby).addGameEvent(eq(1L), eq("FIX_REACTOR"), anyString());
    }

    @Test
    void testHandleNullUpdate() {
        // Реальный handler выбрасывает NPE при null - это корректное поведение
        assertThrows(NullPointerException.class, () -> messageHandler.handle(null));
    }

    private Update createTextMessageUpdate(String text, Long userId, Long chatId) {
        Update update = new Update();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();

        user.setId(userId);
        user.setFirstName("TestUser");
        chat.setId(chatId);

        message.setFrom(user);
        message.setChat(chat);
        message.setText(text);
        message.setMessageId(1);

        update.setMessage(message);
        return update;
    }

    private Update createPhotoMessageUpdate(Long userId, Long chatId) {
        Update update = createTextMessageUpdate("", userId, chatId);

        // Создаем моки для фото
        PhotoSize photo1 = new PhotoSize();
        photo1.setFileId("photo_file_id_1");
        photo1.setWidth(640);
        photo1.setHeight(480);

        PhotoSize photo2 = new PhotoSize();
        photo2.setFileId("photo_file_id_2");
        photo2.setWidth(1280);
        photo2.setHeight(960);

        List<PhotoSize> photos = Arrays.asList(photo1, photo2);
        update.getMessage().setPhoto(photos);

        return update;
    }
}
