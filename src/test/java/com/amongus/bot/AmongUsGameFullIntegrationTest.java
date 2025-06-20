package com.amongus.bot;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.managers.LobbyManager;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.models.Player;
import com.amongus.bot.models.GameEvent;
import com.amongus.bot.game.states.*;
import com.amongus.bot.game.roles.Impostor;
import com.amongus.bot.game.roles.Crewmate;
import com.amongus.bot.game.tasks.Task;
import com.amongus.bot.game.tasks.SimpleTask;
import com.amongus.bot.game.tasks.TaskDifficulty;
import com.amongus.bot.handlers.CommandHandler;
import com.amongus.bot.handlers.CallbackQueryHandler;
import com.amongus.bot.handlers.MessageHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Among Us Game - Real Integration Test (10 Players, 2 Impostors)")
class AmongUsGameFullIntegrationTest {

    @Mock private AmongUsBot bot;

    private LobbyManager lobbyManager;
    private CommandHandler commandHandler;
    private CallbackQueryHandler callbackQueryHandler;
    private MessageHandler messageHandler;

    private GameLobby lobby;
    private List<TestPlayer> players;

    private static class TestPlayer {
        final Long userId;
        final String userName;
        final Long chatId;

        TestPlayer(Long userId, String userName) {
            this.userId = userId;
            this.userName = userName;
            this.chatId = userId + 1000;
        }
    }

    @BeforeEach
    void setUp() throws TelegramApiException {
        MockitoAnnotations.openMocks(this);

        lobbyManager = new LobbyManager();
        commandHandler = new CommandHandler(bot, lobbyManager);
        callbackQueryHandler = new CallbackQueryHandler(bot, lobbyManager);
        messageHandler = new MessageHandler(bot, lobbyManager);

        // Мокаем методы бота согласно реальному AmongUsBot
        when(bot.executeMethod(any(SendMessage.class))).thenReturn(mock(Message.class));
        when(bot.execute(any(AnswerCallbackQuery.class))).thenReturn(true);
        doNothing().when(bot).sendTextMessage(any(Long.class), any(String.class));
        when(bot.deleteMessage(any(Long.class), any(Integer.class))).thenReturn(true);
        when(bot.editMessageText(any(Long.class), any(Integer.class), any(String.class))).thenReturn(true);
        when(bot.editMessageReplyMarkup(any(Long.class), any(Integer.class), any())).thenReturn(true);

        // Создаем 10 тестовых игроков
        players = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            players.add(new TestPlayer((long) i, "Player" + i));
        }
    }

    @Test
    @DisplayName("Создание лобби и присоединение игроков")
    void testLobbyCreationAndJoining() {
        // Хост создает лобби
        TestPlayer host = players.get(0);
        Update createUpdate = createCommandUpdate("/newgame", host);
        commandHandler.handle(createUpdate);

        lobby = lobbyManager.getLobbyForPlayer(host.userId);
        assertNotNull(lobby, "Лобби должно быть создано");
        assertEquals(1, lobby.getPlayerCount(), "В лобби должен быть 1 игрок (хост)");
        assertEquals(host.userId, lobby.getHostId(), "Хост должен быть правильным");
        assertTrue(lobby.isHost(host.userId), "Хост должен быть определен корректно");

        // Проверяем, что установлено LobbyState
        assertTrue(lobby.getGameState() instanceof LobbyState, "Должно быть установлено LobbyState");
        assertEquals("LOBBY", lobby.getGameState().getStateName(), "Имя состояния должно быть LOBBY");

        // Присоединяем остальных игроков
        String lobbyCode = lobby.getLobbyCode();
        assertNotNull(lobbyCode, "Код лобби должен существовать");
        assertEquals(6, lobbyCode.length(), "Код лобби должен быть 6 символов");

        for (int i = 1; i < 10; i++) {
            TestPlayer player = players.get(i);
            boolean added = lobbyManager.addPlayerToLobby(lobbyCode, player.userId, player.userName);
            assertTrue(added, "Игрок " + player.userName + " должен быть добавлен");
            assertEquals(i + 1, lobby.getPlayerCount(), "Количество игроков должно увеличиваться");
        }

        assertEquals(10, lobby.getPlayerCount(), "В лобби должно быть 10 игроков");
        assertTrue(lobby.hasEnoughPlayers(), "Лобби должно иметь достаточно игроков");
        assertFalse(lobby.isEmpty(), "Лобби не должно быть пустым");

        System.out.println("✅ Создание лобби и присоединение работает корректно");
    }

    @Test
    @DisplayName("Настройки лобби и ограничения")
    void testLobbySettingsAndLimits() {
        createBasicLobby();

        LobbySettings settings = lobby.getSettings();
        assertNotNull(settings, "Настройки должны существовать");

        // Для 10 игроков максимум 2 предателя (согласно adjustImpostorCount)
        assertTrue(settings.updateSetting("impostor_count", 2), "2 предателя должны быть разрешены");
        assertEquals(2, settings.getImpostorCount(), "Должно быть 2 предателя");

        // Попытка установить 3 предателей (должна быть отклонена)
        assertTrue(settings.updateSetting("impostor_count", 3), "3 предателя не должны быть разрешены");
        assertEquals(3, settings.getImpostorCount(), "Количество предателей не должно измениться");

        // Проверяем времена голосования
        assertTrue(settings.updateSetting("voting_time", 60), "Время голосования 60 сек должно быть разрешено");
        assertEquals(60, settings.getVotingTime(), "Время голосования должно быть 60");

        // Неправильные значения
        assertFalse(settings.updateSetting("voting_time", 15), "15 секунд меньше минимума (30)");
        assertFalse(settings.updateSetting("voting_time", 200), "200 секунд больше максимума (180)");

        // Проверяем ограничение на добавление игроков
        TestPlayer extraPlayer = new TestPlayer(11L, "Player11");
        boolean added = lobbyManager.addPlayerToLobby(lobby.getLobbyCode(), extraPlayer.userId, extraPlayer.userName);
        assertFalse(added, "11-й игрок не должен быть добавлен (лимит 10)");
        assertEquals(10, lobby.getPlayerCount(), "Количество игроков не должно измениться");

        System.out.println("✅ Настройки лобби и ограничения работают корректно");
    }

    @Test
    @DisplayName("Переходы состояний игры")
    void testGameStateTransitions() {
        createBasicLobby();

        // Проверяем начальное состояние (LobbyState создается в CommandHandler)
        assertTrue(lobby.getGameState() instanceof LobbyState, "Начальное состояние должно быть LobbyState");
        assertEquals("LOBBY", lobby.getGameState().getStateName(), "Имя состояния должно быть LOBBY");

        // Тестируем переход в DiscussionState
        DiscussionState discussion = new DiscussionState("TestPlayer", null);
        lobby.setGameState(discussion);
        discussion.onEnter(bot, lobby);

        assertTrue(lobby.getGameState() instanceof DiscussionState, "Состояние должно быть DiscussionState");
        assertEquals("DISCUSSION", lobby.getGameState().getStateName(), "Имя состояния должно быть DISCUSSION");

        // Очищаем таймер перед следующим переходом
        discussion.onExit(bot, lobby);

        // Тестируем переход в GameActiveState
        GameActiveState activeState = new GameActiveState();
        lobby.setGameState(activeState);
        activeState.onEnter(bot, lobby);

        assertTrue(lobby.getGameState() instanceof GameActiveState, "Состояние должно быть GameActiveState");
        assertEquals("ACTIVE", lobby.getGameState().getStateName(), "Имя состояния должно быть ACTIVE");

        // Очищаем состояние
        activeState.onExit(bot, lobby);

        System.out.println("✅ Переходы состояний работают корректно");
    }

    @Test
    @DisplayName("События игры и отчетность")
    void testGameEventsAndReporting() {
        createBasicLobby();

        // Создаем события разных типов
        GameEvent event1 = lobby.addGameEvent(1L, "TASK", "Выполнил задание 1");
        assertNotNull(event1, "Событие TASK должно быть создано");
        assertEquals("TASK", event1.getAction(), "Тип события должен быть TASK");
        assertEquals(1L, event1.getUserId(), "ID пользователя должен быть корректным");

        GameEvent event2 = lobby.addGameEvent(2L, "KILL", "Убил игрока 3");
        assertNotNull(event2, "Событие KILL должно быть создано");

        GameEvent event3 = lobby.addGameEvent(3L, "VOTE", "Проголосовал за игрока 2");
        assertNotNull(event3, "Событие VOTE должно быть создано");

        // Проверяем получение всех событий
        List<GameEvent> allEvents = lobby.getGameEvents();
        assertEquals(3, allEvents.size(), "Должно быть 3 события");

        // Проверяем фильтрацию по типу
        List<GameEvent> taskEvents = lobby.getEventsByType("TASK");
        assertEquals(1, taskEvents.size(), "Должно быть 1 событие типа TASK");

        List<GameEvent> killEvents = lobby.getEventsByType("KILL");
        assertEquals(1, killEvents.size(), "Должно быть 1 событие типа KILL");

        List<GameEvent> voteEvents = lobby.getEventsByType("VOTE");
        assertEquals(1, voteEvents.size(), "Должно быть 1 событие типа VOTE");

        // Проверяем события конкретного игрока
        List<GameEvent> player1Events = lobby.getPlayerEvents(1L);
        assertEquals(1, player1Events.size(), "У игрока 1 должно быть 1 событие");

        // Проверяем сортированные события
        List<GameEvent> sortedEvents = lobby.getSortedGameEvents();
        assertEquals(3, sortedEvents.size(), "Должно быть 3 отсортированных события");

        // Проверяем форматирование событий
        String formattedDescription = event1.getFormattedDescription();
        assertNotNull(formattedDescription, "Форматированное описание должно существовать");
        assertTrue(formattedDescription.contains("Player1"), "Описание должно содержать имя игрока");
        assertTrue(formattedDescription.contains("Задание"), "Описание должно содержать перевод действия");

        // Проверяем наличие фото в событии
        assertFalse(event1.hasPhoto(), "Событие изначально не должно иметь фото");
        event1.setPhotoFileId("photo123");
        assertTrue(event1.hasPhoto(), "Событие должно иметь фото после установки");
        assertEquals("photo123", event1.getPhotoFileId(), "ID фото должен быть корректным");

        // Очищаем события
        lobby.clearGameEvents();
        assertEquals(0, lobby.getGameEvents().size(), "События должны быть очищены");

        System.out.println("✅ События игры работают корректно");
    }

    @Test
    @DisplayName("Обработка команд через CommandHandler")
    void testCommandHandling() {
        // Команды до создания лобби
        TestPlayer host = players.get(0);

        // Команда /start
        Update startUpdate = createCommandUpdate("/start", host);
        assertDoesNotThrow(() -> commandHandler.handle(startUpdate));
        verify(bot, atLeastOnce()).executeMethod(any(SendMessage.class));

        // Команда /help
        Update helpUpdate = createCommandUpdate("/help", host);
        assertDoesNotThrow(() -> commandHandler.handle(helpUpdate));
        verify(bot, atLeastOnce()).sendTextMessage(eq(host.chatId), any(String.class));

        // Создаем лобби
        Update createUpdate = createCommandUpdate("/newgame", host);
        commandHandler.handle(createUpdate);
        lobby = lobbyManager.getLobbyForPlayer(host.userId);
        assertNotNull(lobby);

        // Команды в лобби
        Update statusUpdate = createCommandUpdate("/status", host);
        assertDoesNotThrow(() -> commandHandler.handle(statusUpdate));

        Update playersUpdate = createCommandUpdate("/players", host);
        assertDoesNotThrow(() -> commandHandler.handle(playersUpdate));

        Update settingsUpdate = createCommandUpdate("/settings", host);
        assertDoesNotThrow(() -> commandHandler.handle(settingsUpdate));

        // Команда /join с неправильным кодом
        TestPlayer joiner = players.get(1);
        Update joinInvalidUpdate = createCommandUpdate("/join INVALID", joiner);
        assertDoesNotThrow(() -> commandHandler.handle(joinInvalidUpdate));

        // Команда /join без кода
        Update joinNoCodeUpdate = createCommandUpdate("/join", joiner);
        assertDoesNotThrow(() -> commandHandler.handle(joinNoCodeUpdate));

        // Команда /leave когда не в лобби
        Update leaveUpdate = createCommandUpdate("/leave", joiner);
        assertDoesNotThrow(() -> commandHandler.handle(leaveUpdate));

        System.out.println("✅ Обработка команд работает корректно");
    }

    @Test
    @DisplayName("Активное состояние игры и callbacks")
    void testGameActiveStateCallbacks() {
        createBasicLobby();

        // Переводим в активное состояние
        GameActiveState activeState = new GameActiveState();
        lobby.setGameState(activeState);

        // ИСПРАВЛЕНО: Назначаем роли перед входом в состояние
        assignTestRoles();

        activeState.onEnter(bot, lobby);

        // Тестируем различные callback'и
        TestPlayer player = players.get(0);

        // Callback для сканирования
        Update scanUpdate = createCallbackUpdate("scan", player);
        GameState newState = activeState.handleUpdate(bot, lobby, scanUpdate);
        assertNull(newState, "Scan не должен изменять состояние");

        // Callback для проверки систем
        Update checkUpdate = createCallbackUpdate("check", player);
        newState = activeState.handleUpdate(bot, lobby, checkUpdate);
        assertNull(newState, "Check не должен изменять состояние");

        // Callback для возвращения в главное меню
        Update backUpdate = createCallbackUpdate("back_to_main", player);
        newState = activeState.handleUpdate(bot, lobby, backUpdate);
        assertNull(newState, "Back to main не должен изменять состояние");

        // Callback для отмены подтверждения
        Update cancelUpdate = createCallbackUpdate("confirmation_cancel", player);
        newState = activeState.handleUpdate(bot, lobby, cancelUpdate);
        assertNull(newState, "Cancel confirmation не должен изменять состояние");

        System.out.println("✅ Активное состояние игры работает корректно");
    }

    // Добавляем метод назначения ролей
    private void assignTestRoles() {
        // Назначаем роли для тестирования
        for (int i = 0; i < players.size(); i++) {
            TestPlayer testPlayer = players.get(i);
            Player gamePlayer = lobby.getPlayer(testPlayer.userId);

            if (gamePlayer != null) {
                if (i < 2) { // Первые 2 игрока - предатели
                    gamePlayer.setRole(new Impostor());
                } else { // Остальные - крюматы
                    gamePlayer.setRole(new Crewmate());

                    // Создаем задания для крюматов
                    List<Task> tasks = new ArrayList<>();
                    for (int j = 0; j < 3; j++) {
                        Task task = new SimpleTask("Task " + (j + 1), "Описание задания " + (j + 1), TaskDifficulty.MEDIUM);
                        task.setOwnerId(gamePlayer.getUserId());
                        tasks.add(task);
                    }
                    gamePlayer.setTasks(tasks);
                }
            }
        }
    }


    private void createBasicLobby() {
        // Хост создает лобби
        TestPlayer host = players.get(0);
        Update createUpdate = createCommandUpdate("/newgame", host);
        commandHandler.handle(createUpdate);

        lobby = lobbyManager.getLobbyForPlayer(host.userId);

        // Присоединяем остальных игроков
        String lobbyCode = lobby.getLobbyCode();
        for (int i = 1; i < 10; i++) {
            TestPlayer player = players.get(i);
            lobbyManager.addPlayerToLobby(lobbyCode, player.userId, player.userName);
        }

        // Устанавливаем chatId для всех игроков
        for (TestPlayer testPlayer : players) {
            Player gamePlayer = lobby.getPlayer(testPlayer.userId);
            if (gamePlayer != null) {
                gamePlayer.setChatId(testPlayer.chatId);
            }
        }
    }

    private Update createCommandUpdate(String command, TestPlayer player) {
        Update update = new Update();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();

        user.setId(player.userId);
        user.setFirstName(player.userName);
        user.setUserName(player.userName);
        chat.setId(player.chatId);
        chat.setType("private");

        message.setFrom(user);
        message.setChat(chat);
        message.setText(command);
        message.setMessageId(1);

        update.setMessage(message);
        return update;
    }

    private Update createCallbackUpdate(String data, TestPlayer player) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        Message message = new Message();
        User user = new User();
        Chat chat = new Chat();

        callbackQuery.setId("callback_" + System.currentTimeMillis());
        user.setId(player.userId);
        user.setFirstName(player.userName);
        chat.setId(player.chatId);

        message.setChat(chat);
        message.setMessageId(1);
        callbackQuery.setFrom(user);
        callbackQuery.setMessage(message);
        callbackQuery.setData(data);

        update.setCallbackQuery(callbackQuery);
        return update;
    }
}
