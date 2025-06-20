package com.amongus.bot.models;

import com.amongus.bot.game.roles.Role;
import com.amongus.bot.game.tasks.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player in the Among Us game.
 */
public class Player {
    private final Long userId;
    private final String userName;
    private Long chatId; // Telegram chat ID
    private Role role;
    private boolean alive;
    private boolean ejected; // True if player was voted out/ejected, false if killed
    private List<Task> tasks;
    private int completedTaskCount;
    private Integer awaitingPhotoForTaskIndex; // Index of task waiting for photo confirmation
    private Integer awaitingPhotoForFakeTask; // Index of fake task waiting for photo confirmation
    private int emergencyMeetingsUsed; // Counter for emergency meetings called by this player
    
    /**
     * Creates a new player with the specified Telegram user ID and username.
     * 
     * @param userId The Telegram user ID of the player
     * @param userName The Telegram username of the player
     */
    public Player(Long userId, String userName) {
        this.userId = userId;
        this.userName = userName;
        this.chatId = null; // Will be set when the player interacts with the bot
        this.role = null;
        this.alive = true;
        this.ejected = false;
        this.tasks = new ArrayList<>();
        this.completedTaskCount = 0;
        this.awaitingPhotoForTaskIndex = null;
        this.awaitingPhotoForFakeTask = null;
        this.emergencyMeetingsUsed = 0;
    }
    
    /**
     * Gets the Telegram user ID of the player.
     * 
     * @return The user ID
     */
    public Long getUserId() {
        return userId;
    }
    
    /**
     * Gets the Telegram username of the player.
     * 
     * @return The username
     */
    public String getUserName() {
        return userName;
    }
    
    /**
     * Gets the Telegram chat ID of the player.
     * 
     * @return The chat ID or null if not set
     */
    public Long getChatId() {
        return chatId;
    }
    
    /**
     * Sets the Telegram chat ID of the player.
     * 
     * @param chatId The chat ID to set
     */
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    /**
     * Gets the role of the player (Crewmate or Impostor).
     * 
     * @return The player's role
     */
    public Role getRole() {
        return role;
    }
    
    /**
     * Sets the role of the player.
     * 
     * @param role The new role
     */
    public void setRole(Role role) {
        this.role = role;
    }
    
    /**
     * Checks if the player is alive.
     * 
     * @return True if the player is alive, false if dead
     */
    public boolean isAlive() {
        return alive;
    }
    
    /**
     * Marks the player as dead (killed by an impostor).
     */
    public void kill() {
        this.alive = false;
        this.ejected = false; // Killed, not ejected
    }
    
    /**
     * Marks the player as ejected (voted out).
     */
    public void eject() {
        this.alive = false;
        this.ejected = true; // Ejected, not killed
    }
    
    /**
     * Checks if the player was ejected (voted out).
     * 
     * @return True if the player was ejected, false if killed or alive
     */
    public boolean isEjected() {
        return ejected;
    }
    
    /**
     * Checks if the player was killed (not ejected).
     * 
     * @return True if the player was killed by an impostor, false if ejected or alive
     */
    public boolean wasKilled() {
        return !alive && !ejected;
    }
    
    /**
     * Gets the list of tasks assigned to the player.
     * 
     * @return The list of tasks
     */
    public List<Task> getTasks() {
        return tasks;
    }
    
    /**
     * Sets the list of tasks for the player.
     * 
     * @param tasks The new task list
     */
    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        this.completedTaskCount = 0;
    }
    
    /**
     * Gets the number of tasks that the player has completed.
     * 
     * @return The number of completed tasks
     */
    public int getCompletedTaskCount() {
        return completedTaskCount;
    }
    
    /**
     * Gets the number of tasks that the player has been assigned.
     * 
     * @return The total number of tasks
     */
    public int getTotalTaskCount() {
        return tasks.size();
    }
    
    /**
     * Marks a task as completed and increments the completed task count.
     * 
     * @param taskIndex The index of the task in the player's task list
     * @return True if the task was successfully completed, false otherwise
     */
    public boolean completeTask(int taskIndex) {
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return false;
        }
        
        Task task = tasks.get(taskIndex);
        if (!task.isCompleted()) {
            task.complete();
            completedTaskCount++;
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if all tasks have been completed by the player.
     * 
     * @return True if all tasks are completed
     */
    public boolean hasCompletedAllTasks() {
        return completedTaskCount == tasks.size();
    }
    
    /**
     * Gets the percentage of tasks completed.
     * 
     * @return The percentage of completed tasks (0-100)
     */
    public int getTaskCompletionPercentage() {
        if (tasks.isEmpty()) {
            // For impostors with no tasks assigned, return 0% to avoid confusion
            // For crewmates with no tasks, return 100% (edge case)
            return isImpostor() ? 0 : 100;
        }
        
        return (int) (((double) completedTaskCount / tasks.size()) * 100);
    }
    
    /**
     * Checks if the player is an impostor.
     * 
     * @return True if the player is an impostor
     */
    public boolean isImpostor() {
        return role != null && role.isImpostor();
    }
    
    /**
     * Checks if the player is currently awaiting a photo for task completion.
     * 
     * @return True if waiting for a photo
     */
    public boolean isAwaitingPhotoConfirmation() {
        return awaitingPhotoForTaskIndex != null;
    }
    
    /**
     * Gets the index of the task that is awaiting photo confirmation.
     * 
     * @return The task index or null if not awaiting confirmation
     */
    public Integer getAwaitingPhotoForTaskIndex() {
        return awaitingPhotoForTaskIndex;
    }
    
    /**
     * Sets the task index that is awaiting photo confirmation.
     * 
     * @param taskIndex The task index or null to clear
     */
    public void setAwaitingPhotoForTaskIndex(Integer taskIndex) {
        this.awaitingPhotoForTaskIndex = taskIndex;
    }
    
    /**
     * Checks if the player is currently awaiting a photo for fake task confirmation.
     * 
     * @return True if waiting for a photo for a fake task
     */
    public boolean isAwaitingFakeTaskPhotoConfirmation() {
        return awaitingPhotoForFakeTask != null;
    }
    
    /**
     * Gets the index of the fake task that is awaiting photo confirmation.
     * 
     * @return The fake task index or null if not awaiting confirmation
     */
    public Integer getAwaitingPhotoForFakeTask() {
        return awaitingPhotoForFakeTask;
    }
    
    /**
     * Sets the fake task index that is awaiting photo confirmation.
     * 
     * @param fakeTaskIndex The fake task index or null to clear
     */
    public void setAwaitingPhotoForFakeTask(Integer fakeTaskIndex) {
        this.awaitingPhotoForFakeTask = fakeTaskIndex;
    }
    
    /**
     * Gets the number of emergency meetings used by this player.
     * 
     * @return The number of emergency meetings used
     */
    public int getEmergencyMeetingsUsed() {
        return emergencyMeetingsUsed;
    }
    
    /**
     * Increments the count of emergency meetings used by this player.
     */
    public void incrementEmergencyMeetingsUsed() {
        this.emergencyMeetingsUsed++;
    }
    
    /**
     * Checks if this player has used all allowed emergency meetings.
     * 
     * @param maxMeetings The maximum number of meetings allowed per player
     * @return True if the player has reached the limit
     */
    public boolean hasReachedEmergencyMeetingLimit(int maxMeetings) {
        return emergencyMeetingsUsed >= maxMeetings;
    }
    
    /**
     * Resets the player's state for a new game.
     * Called when starting a new game with the same players.
     */
    public void reset() {
        this.role = null;
        this.alive = true;
        this.ejected = false;
        this.tasks = new ArrayList<>();
        this.completedTaskCount = 0;
        this.awaitingPhotoForTaskIndex = null;
        this.awaitingPhotoForFakeTask = null;
        this.emergencyMeetingsUsed = 0;
    }
    
    /**
     * Checks if a specific task is completed.
     * 
     * @param taskIndex The index of the task in the player's task list
     * @return True if the task is completed, false if not or if the index is invalid
     */
    public boolean isTaskCompleted(int taskIndex) {
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return false;
        }
        return tasks.get(taskIndex).isCompleted();
    }
    
    /**
     * Gets a specific task from the player's task list.
     * 
     * @param taskIndex The index of the task in the player's task list
     * @return The task at the specified index, or null if the index is invalid
     */
    public Task getTask(int taskIndex) {
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(taskIndex);
    }
} 