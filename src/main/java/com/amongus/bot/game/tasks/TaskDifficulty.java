package com.amongus.bot.game.tasks;

/**
 * Enum representing the difficulty level of a task.
 */
public enum TaskDifficulty {
    EASY("Легко"),
    MEDIUM("Средне"),
    HARD("Сложно");
    
    private final String displayName;
    
    TaskDifficulty(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the display name of the difficulty level.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
// COMPLETED: TaskDifficulty enum 