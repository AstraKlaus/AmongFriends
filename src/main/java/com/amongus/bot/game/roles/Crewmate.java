package com.amongus.bot.game.roles;

/**
 * Implementation of the Crewmate role.
 */
public class Crewmate implements Role {
    
    @Override
    public String getRoleName() {
        return "Член экипажа";
    }
    
    @Override
    public boolean isImpostor() {
        return false;
    }
    
    @Override
    public String getDescription() {
        return "Выполняйте задания и найдите предателей среди членов экипажа.";
    }
    
    @Override
    public boolean canKill() {
        return false;
    }
    
    @Override
    public boolean hasTasks() {
        return true;
    }
}
// COMPLETED: Crewmate role 