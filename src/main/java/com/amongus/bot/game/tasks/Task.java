package com.amongus.bot.game.tasks;

/**
 * Interface representing a task that players can complete in the game.
 */
public interface Task {
    
    /**
     * Gets the name of the task.
     * 
     * @return The task name
     */
    String getName();
    
    /**
     * Gets the description of the task for display to the player.
     * 
     * @return The task description
     */
    String getDescription();
    
    /**
     * Checks if the task has been completed.
     * 
     * @return True if the task is completed
     */
    boolean isCompleted();
    
    /**
     * Marks the task as completed.
     */
    void complete();
    
    /**
     * Gets the task difficulty (easy, medium, hard).
     * 
     * @return The task difficulty
     */
    TaskDifficulty getDifficulty();
    
    /**
     * Gets the ID of the player who owns this task.
     * 
     * @return The player ID
     */
    Long getOwnerId();
    
    /**
     * Sets the owner ID for this task.
     * @param ownerId The player ID who owns this task
     */
    void setOwnerId(Long ownerId);
    
    /**
     * Creates a duplicate of this task with the same properties but different instance.
     * @return A new Task instance with the same properties.
     */
    Task duplicate();
}
// COMPLETED: Task interface 