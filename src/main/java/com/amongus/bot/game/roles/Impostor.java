package com.amongus.bot.game.roles;

/**
 * Implementation of the Impostor role.
 */
public class Impostor implements Role {
    
    @Override
    public String getRoleName() {
        return "Предатель";
    }
    
    @Override
    public boolean isImpostor() {
        return true;
    }
    
    @Override
    public String getDescription() {
        return "Устраивайте саботаж и убивайте членов экипажа, не попадаясь.";
    }
    
    @Override
    public boolean canKill() {
        return true;
    }
    
    @Override
    public boolean hasTasks() {
        return false;
    }
}
// COMPLETED: Impostor role 