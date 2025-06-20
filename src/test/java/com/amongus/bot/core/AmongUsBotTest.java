package com.amongus.bot.core;

import com.amongus.bot.handlers.CallbackQueryHandler;
import com.amongus.bot.handlers.CommandHandler;
import com.amongus.bot.handlers.MessageHandler;
import com.amongus.bot.managers.LobbyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmongUsBotTest {

    @Mock private LobbyManager lobbyManager;
    @Mock private CommandHandler commandHandler;
    @Mock private MessageHandler messageHandler;
    @Mock private CallbackQueryHandler callbackHandler;

    private AmongUsBot bot;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bot = new AmongUsBot();
    }

    @Test
    void testBotUsernameAndToken() {
        assertNotNull(bot.getBotUsername());
        assertNotNull(bot.getBotToken());
        assertFalse(bot.getBotUsername().isEmpty());
        assertFalse(bot.getBotToken().isEmpty());
    }

    @Test
    void testOnUpdateReceivedWithMessage() {
        Update update = new Update();
        Message message = new Message();
        message.setText("/start");
        update.setMessage(message);

        bot.onUpdateReceived(update);

        // Проверяем, что обновление обработано без исключений
        assertDoesNotThrow(() -> bot.onUpdateReceived(update));
    }

    @Test
    void testOnUpdateReceivedWithCallbackQuery() {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData("test_callback");
        update.setCallbackQuery(callbackQuery);

        bot.onUpdateReceived(update);

        assertDoesNotThrow(() -> bot.onUpdateReceived(update));
    }

    @Test
    void testBotInitialization() {
        assertNotNull(bot);
        // Проверяем, что бот корректно инициализируется
        assertTrue(bot.getBotUsername().length() > 0);
        assertTrue(bot.getBotToken().length() > 0);
    }

    @Test
    void testBotIntegrationFlow() {
        // Создаем реальные объекты для интеграционного тестирования
        LobbyManager realLobbyManager = new LobbyManager();
        AmongUsBot realBot = new AmongUsBot();

        // Тестируем реальный поток обработки
        Update update = createTestUpdate();

        assertDoesNotThrow(() -> realBot.onUpdateReceived(update));
    }

    private Update createTestUpdate() {
        Update update = new Update();
        Message message = new Message();
        message.setText("/start");
        message.setMessageId(1);

        org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
        user.setId(123L);
        user.setFirstName("TestUser");
        message.setFrom(user);

        org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
        chat.setId(456L);
        message.setChat(chat);

        update.setMessage(message);
        return update;
    }

}
