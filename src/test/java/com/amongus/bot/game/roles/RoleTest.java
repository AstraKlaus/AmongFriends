package com.amongus.bot.game.roles;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    
    @Test
    void testImpostorRole() {
        Role impostor = new Impostor();
        
        assertEquals("Предатель", impostor.getRoleName());
        assertTrue(impostor.isImpostor());
        assertEquals("Устраивайте саботаж и убивайте членов экипажа, не попадаясь.", impostor.getDescription());
        assertTrue(impostor.canKill());
        assertFalse(impostor.hasTasks());
    }
    
    @Test
    void testCrewmateRole() {
        Role crewmate = new Crewmate();
        
        assertEquals("Член экипажа", crewmate.getRoleName());
        assertFalse(crewmate.isImpostor());
        assertEquals("Выполняйте задания и найдите предателей среди членов экипажа.", crewmate.getDescription());
        assertFalse(crewmate.canKill());
        assertTrue(crewmate.hasTasks());
    }
    
    @Test
    void testRoleEquality() {
        Role impostor1 = new Impostor();
        Role impostor2 = new Impostor();
        Role crewmate1 = new Crewmate();
        Role crewmate2 = new Crewmate();
        
        // Проверяем, что разные экземпляры одной роли равны
        assertEquals(impostor1.getRoleName(), impostor2.getRoleName());
        assertEquals(impostor1.isImpostor(), impostor2.isImpostor());
        assertEquals(impostor1.getDescription(), impostor2.getDescription());
        assertEquals(impostor1.canKill(), impostor2.canKill());
        assertEquals(impostor1.hasTasks(), impostor2.hasTasks());
        
        assertEquals(crewmate1.getRoleName(), crewmate2.getRoleName());
        assertEquals(crewmate1.isImpostor(), crewmate2.isImpostor());
        assertEquals(crewmate1.getDescription(), crewmate2.getDescription());
        assertEquals(crewmate1.canKill(), crewmate2.canKill());
        assertEquals(crewmate1.hasTasks(), crewmate2.hasTasks());
        
        // Проверяем, что разные роли не равны
        assertNotEquals(impostor1.getRoleName(), crewmate1.getRoleName());
        assertNotEquals(impostor1.isImpostor(), crewmate1.isImpostor());
        assertNotEquals(impostor1.getDescription(), crewmate1.getDescription());
        assertNotEquals(impostor1.canKill(), crewmate1.canKill());
        assertNotEquals(impostor1.hasTasks(), crewmate1.hasTasks());
    }
    
    @Test
    void testRoleImmutability() {
        Role impostor = new Impostor();
        Role crewmate = new Crewmate();
        
        // Проверяем, что повторные вызовы методов возвращают те же значения
        String impostorName = impostor.getRoleName();
        assertEquals(impostorName, impostor.getRoleName());
        
        String crewmateName = crewmate.getRoleName();
        assertEquals(crewmateName, crewmate.getRoleName());
        
        String impostorDesc = impostor.getDescription();
        assertEquals(impostorDesc, impostor.getDescription());
        
        String crewmateDesc = crewmate.getDescription();
        assertEquals(crewmateDesc, crewmate.getDescription());
    }
} 