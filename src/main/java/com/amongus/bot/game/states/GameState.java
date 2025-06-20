package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.lobby.GameLobby;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Interface representing a state in the Among Us game.
 */
public interface GameState {
    
    /**
     * Gets the name of this game state.
     * 
     * @return The state name
     */
    String getStateName();
    
    /**
     * Called when entering this state.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     */
    void onEnter(AmongUsBot bot, GameLobby lobby);
    
    /**
     * Called when leaving this state.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     */
    void onExit(AmongUsBot bot, GameLobby lobby);
    
    /**
     * Handles an update from the Telegram API in this state.
     * 
     * @param bot The bot instance
     * @param lobby The game lobby
     * @param update The Telegram update
     * @return The next state to transition to, or null to stay in this state
     */
    GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update);
    
    /**
     * Checks if a specific user can perform an action in this state.
     * 
     * @param lobby The game lobby
     * @param userId The user ID
     * @param action The action name
     * @return True if the action is allowed
     */
    boolean canPerformAction(GameLobby lobby, Long userId, String action);
} 