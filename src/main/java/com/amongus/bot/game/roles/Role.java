package com.amongus.bot.game.roles;

/**
 * Interface representing a player's role in the game.
 */
public interface Role {
    
    /**
     * Gets the name of the role.
     * 
     * @return The role name
     */
    String getRoleName();
    
    /**
     * Checks if this role is an impostor.
     * 
     * @return True if the role is an impostor, false for crewmates
     */
    boolean isImpostor();
    
    /**
     * Gets the description of the role for display to the player.
     * 
     * @return The role description
     */
    String getDescription();
    
    /**
     * Checks if this role can perform a kill action.
     * 
     * @return True if the role can kill other players
     */
    boolean canKill();
    
    /**
     * Checks if this role needs to complete tasks.
     * 
     * @return True if the role has tasks to complete
     */
    boolean hasTasks();
}
// COMPLETED: Role interface 