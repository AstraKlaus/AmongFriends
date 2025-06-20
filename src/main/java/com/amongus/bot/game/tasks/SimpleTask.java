package com.amongus.bot.game.tasks;

/**
 * A simple task implementation that can be completed with a single action.
 */
public class SimpleTask extends AbstractTask {
    
    /**
     * Creates a simple task with the specified properties.
     * 
     * @param name The name of the task
     * @param description The description of the task
     * @param difficulty The difficulty level of the task
     */
    public SimpleTask(String name, String description, TaskDifficulty difficulty) {
        super(name, description, difficulty);
    }
}
// COMPLETED: SimpleTask class 