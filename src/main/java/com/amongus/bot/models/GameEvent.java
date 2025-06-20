package com.amongus.bot.models;

import java.util.Date;

/**
 * Класс для хранения игровых событий для финального отчета.
 */
public class GameEvent {
    private final Long userId;
    private final String userName;
    private final String action;
    private final String details;
    private final Date timestamp;
    private String photoFileId; // ID фото в Telegram, если есть
    
    /**
     * Создает новое игровое событие.
     * 
     * @param userId ID пользователя, совершившего действие
     * @param userName Имя пользователя
     * @param action Тип действия (например, "KILL", "TASK", "REPORT", "VOTE")
     * @param details Детали события
     */
    public GameEvent(Long userId, String userName, String action, String details) {
        this.userId = userId;
        this.userName = userName;
        this.action = action;
        this.details = details;
        this.timestamp = new Date();
        this.photoFileId = null;
    }
    
    /**
     * Добавляет ID фото к событию.
     * 
     * @param photoFileId ID фото в Telegram
     */
    public void setPhotoFileId(String photoFileId) {
        this.photoFileId = photoFileId;
    }
    
    /**
     * Проверяет, имеет ли событие прикрепленное фото.
     * 
     * @return true, если есть фото
     */
    public boolean hasPhoto() {
        return photoFileId != null && !photoFileId.isEmpty();
    }
    
    /**
     * Получает ID пользователя.
     * 
     * @return ID пользователя
     */
    public Long getUserId() {
        return userId;
    }
    
    /**
     * Получает имя пользователя.
     * 
     * @return Имя пользователя
     */
    public String getUserName() {
        return userName;
    }
    
    /**
     * Получает тип действия.
     * 
     * @return Тип действия
     */
    public String getAction() {
        return action;
    }
    
    /**
     * Получает детали события.
     * 
     * @return Детали события
     */
    public String getDetails() {
        return details;
    }
    
    /**
     * Получает временную метку события.
     * 
     * @return Временная метка
     */
    public Date getTimestamp() {
        return timestamp;
    }
    
    /**
     * Получает ID фото в Telegram.
     * 
     * @return ID фото или null, если фото нет
     */
    public String getPhotoFileId() {
        return photoFileId;
    }
    
    /**
     * Получает форматированное представление события для отчета.
     * 
     * @return Строка с описанием события
     */
    public String getFormattedDescription() {
        // Форматируем время как ЧЧ:ММ:СС
        String timeString = String.format("%tH:%tM:%tS", timestamp, timestamp, timestamp);
        
        String description = String.format("[%s] %s %s - %s: %s", 
                timeString, getActionEmoji(), userName, getActionDescription(), details);
        
        return description;
    }
    
    /**
     * Получает эмодзи для типа действия.
     * 
     * @return Эмодзи, соответствующее действию
     */
    private String getActionEmoji() {
        switch (action.toUpperCase()) {
            case "KILL":
                return "🔪";
            case "TASK":
                return "📋";
            case "REPORT":
                return "🚨";
            case "MEETING":
                return "📢";
            case "VOTE":
                return "🗳️";
            case "EJECTED":
                return "🚪";
            case "VOTE_RESULT":
                return "📊";
            case "SABOTAGE":
                return "⚡";
            case "FIX_LIGHTS":
                return "💡";
            case "FIX_REACTOR":
                return "⚛️";
            case "SCAN":
                return "🔍";
            case "FAKE_TASK":
                return "🎭";
            case "GAME_OVER":
                return "🏁";
            default:
                return "📝";
        }
    }
    
    /**
     * Преобразует код действия в читаемую форму.
     * 
     * @return Описание действия на русском
     */
    private String getActionDescription() {
        switch (action.toUpperCase()) {
            case "KILL":
                return "Убийство";
            case "TASK":
                return "Задание";
            case "REPORT":
                return "Сообщение о теле";
            case "MEETING":
                return "Созыв собрания";
            case "VOTE":
                return "Голосование";
            case "EJECTED":
                return "Исключение";
            case "VOTE_RESULT":
                return "Результат голосования";
            case "SABOTAGE":
                return "Саботаж";
            case "FIX_LIGHTS":
                return "Починка света";
            case "FIX_REACTOR":
                return "Починка реактора";
            case "SCAN":
                return "Сканирование";
            case "FAKE_TASK":
                return "Имитация задания";
            case "GAME_OVER":
                return "Окончание игры";
            default:
                return action;
        }
    }
} 