package com.amongus.bot.game.tasks;

/**
 * Abstract base class for task implementations.
 */
public abstract class AbstractTask implements Task {
    private final String name;
    private final String description;
    private final TaskDifficulty difficulty;
    private boolean completed;
    private Long ownerId; // Player ID who owns this task
    
    /**
     * Creates a new task with the specified properties.
     * 
     * @param name The name of the task
     * @param description The description of the task
     * @param difficulty The difficulty level of the task
     */
    public AbstractTask(String name, String description, TaskDifficulty difficulty) {
        this.name = name;
        this.description = description;
        this.difficulty = difficulty;
        this.completed = false;
        this.ownerId = null;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public boolean isCompleted() {
        return completed;
    }
    
    @Override
    public void complete() {
        this.completed = true;
    }
    
    @Override
    public TaskDifficulty getDifficulty() {
        return difficulty;
    }
    
    @Override
    public Long getOwnerId() {
        return ownerId;
    }
    
    @Override
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
    
    @Override
    public Task duplicate() {
        AbstractTask copy = null;
        if (this instanceof SimpleTask) {
            copy = new SimpleTask(name, description, difficulty);
        } else {
            // Default fallback for any other AbstractTask subclass
            copy = new SimpleTask(name, description, difficulty);
        }
        // Ensure owner ID is not copied
        copy.ownerId = null;
        return copy;
    }
}
// COMPLETED: AbstractTask class 