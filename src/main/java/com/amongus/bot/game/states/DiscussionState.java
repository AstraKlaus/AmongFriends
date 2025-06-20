package com.amongus.bot.game.states;

import com.amongus.bot.core.AmongUsBot;
import com.amongus.bot.game.GameConstants;
import com.amongus.bot.game.lobby.GameLobby;
import com.amongus.bot.game.lobby.LobbySettings;
import com.amongus.bot.game.states.GameActiveState;
import com.amongus.bot.models.Player;
import com.amongus.bot.game.utils.PlayerUtils;
import com.amongus.bot.game.utils.ResourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Game state for the discussion phase where players debate who is an impostor.
 */
public class DiscussionState implements GameState {
    private static final Logger logger = LoggerFactory.getLogger(DiscussionState.class);
    
    private static final String STATE_NAME = "DISCUSSION";
    
    // Instance-based collections to prevent memory leaks
    private final Set<Long> hasVoted = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> votes = new ConcurrentHashMap<>();
    private final Map<Long, Integer> votingMessageIds = new ConcurrentHashMap<>();
    
    // Global state management with proper cleanup
    private static final Map<String, DiscussionState> activeDiscussions = new ConcurrentHashMap<>();
    
    // Thread-safe timing management
    private final AtomicBoolean votingPhase = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> discussionTimer;
    private volatile ScheduledFuture<?> votingTimer;
    
    // Shared thread pool for all discussions
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // Discussion settings
    private final String reportedBy;
    private final Long reportedPlayerId; // null for emergency meeting
    
    public DiscussionState(String reportedBy, Long reportedPlayerId) {
        this.reportedBy = reportedBy;
        this.reportedPlayerId = reportedPlayerId;
    }
    
    @Override
    public String getStateName() {
        return STATE_NAME;
    }
    
    @Override
    public void onEnter(AmongUsBot bot, GameLobby lobby) {
        String lobbyCode = lobby.getLobbyCode();
        logger.info("Entered discussion state for game {}", lobbyCode);
        
        // Register this discussion instance
        activeDiscussions.put(lobbyCode, this);
        
        // Send discussion message with voting buttons immediately to all alive players
        String discussionMessage = createDiscussionMessage();
        
        for (Player player : lobby.getPlayerList()) {
            if (player.isAlive() && player.getChatId() != null) {
                sendDiscussionWithVotingMessage(bot, lobby, player, discussionMessage);
            }
        }
        
        // Start combined discussion + voting timer (no separate phases)
        int totalTime = lobby.getSettings().getDiscussionTime() + lobby.getSettings().getVotingTime();
        startCombinedVotingTimer(bot, lobby, totalTime);
        
        logger.info("Started combined discussion/voting phase for {} seconds in game {}", totalTime, lobbyCode);
    }
    
    @Override
    public void onExit(AmongUsBot bot, GameLobby lobby) {
        String lobbyCode = lobby.getLobbyCode();
        logger.info("Exited discussion state for game {}", lobbyCode);
        
        // Clean up resources
        cleanup(lobbyCode);
    }
    
    /**
     * Cleans up all resources associated with this discussion
     */
    private void cleanup(String lobbyCode) {
        // Cancel timers
        if (discussionTimer != null && !discussionTimer.isDone()) {
            discussionTimer.cancel(true);
        }
        if (votingTimer != null && !votingTimer.isDone()) {
            votingTimer.cancel(true);
        }
        
        // Clear collections
        hasVoted.clear();
        votes.clear();
        votingMessageIds.clear();
        
        // Remove from active discussions
        activeDiscussions.remove(lobbyCode);
        
        logger.debug("Cleaned up discussion resources for game {}", lobbyCode);
    }
    
    /**
     * Static method to get discussion instance for a lobby
     */
    public static DiscussionState getDiscussionForLobby(String lobbyCode) {
        return activeDiscussions.get(lobbyCode);
    }
    
    /**
     * Static cleanup method for emergency situations
     */
    public static void forceCleanupAll() {
        for (DiscussionState discussion : activeDiscussions.values()) {
            discussion.cleanup("");
        }
        activeDiscussions.clear();
        logger.info("Force cleaned up all discussion resources");
    }
    
    @Override
    public GameState handleUpdate(AmongUsBot bot, GameLobby lobby, Update update) {
        if (!update.hasCallbackQuery()) {
            return null;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        // Validate player
        Player player = lobby.getPlayer(userId);
        if (player == null) {
            logger.warn("Received callback from non-player user {} in game {}", userId, lobby.getLobbyCode());
            return null;
        }
        
        if (!player.isAlive()) {
            logger.info("Dead player {} tried to interact in discussion in game {}", userId, lobby.getLobbyCode());
            return null;
        }
        
        if (callbackData.startsWith("vote:")) {
            if (hasVoted.contains(userId)) {
                logger.info("Player {} already voted in game {}", userId, lobby.getLobbyCode());
                return null;
            }
            
            // Add player to voted list first to prevent duplicate votes
            hasVoted.add(userId);
            
            handleVote(bot, lobby, userId, callbackData);
            
            // Check if all players have voted
            if (allPlayersVoted(lobby)) {
                logger.info("All players voted in game {}, processing results", lobby.getLobbyCode());
                if (votingTimer != null) {
                    votingTimer.cancel(true);
                }
                return processVotingResults(bot, lobby);
            }
        }
        
        return null;
    }
    
    @Override
    public boolean canPerformAction(GameLobby lobby, Long userId, String action) {
        Player player = lobby.getPlayer(userId);
        
        if (player == null || !player.isAlive()) {
            return false;
        }
        
        if (action.startsWith("vote:")) {
            return votingPhase.get();
        }
        
        return false;
    }
    
    private String createDiscussionMessage() {
        if (reportedPlayerId != null) {
            return String.format("💀 %s сообщил о найденном теле!\n\n" +
                    "Начинается обсуждение. Обсудите, кто может быть предателем.", reportedBy);
        } else {
            return String.format("🚨 %s созвал экстренное собрание!\n\n" +
                    "Начинается обсуждение. Обсудите, кто может быть предателем.", reportedBy);
        }
    }
    
    private void sendDiscussionMessage(AmongUsBot bot, Player player, String discussionMessage) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, skipping discussion message", player.getUserId());
            return;
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(player.getChatId());
        message.setText(discussionMessage);
        
        bot.executeMethod(message);
    }
    
    private void sendDiscussionWithVotingMessage(AmongUsBot bot, GameLobby lobby, Player player, String discussionMessage) {
        if (player.getChatId() == null) {
            logger.warn("Player {} has no chatId, skipping discussion with voting message", player.getUserId());
            return;
        }

        // Immediately set voting phase to true since we're combining phases
        votingPhase.set(true);

        String combinedMessage = discussionMessage + "\n\n🗳️ Время голосования! Выберите, за кого голосовать:";
        InlineKeyboardMarkup keyboard = createVotingKeyboard(lobby, player);

        SendMessage message = new SendMessage();
        message.setChatId(player.getChatId());
        message.setText(combinedMessage);
        message.setReplyMarkup(keyboard);

        try {
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = bot.executeMethod(message);
            if (sentMessage != null) {
                votingMessageIds.put(player.getUserId(), sentMessage.getMessageId());
            }
        } catch (Exception e) {
            logger.error("Error sending discussion with voting message to player {}", player.getUserId(), e);
        }
    }
    
    private void startDiscussionTimer(AmongUsBot bot, GameLobby lobby, int discussionTimeSeconds) {
        discussionTimer = scheduler.schedule(() -> {
            try {
                logger.info("Discussion time ended for game {}, starting voting phase", lobby.getLobbyCode());
                startVotingPhase(bot, lobby);
            } catch (Exception e) {
                logger.error("Error during discussion timer execution for game {}", lobby.getLobbyCode(), e);
            }
        }, discussionTimeSeconds, TimeUnit.SECONDS);
    }
    
    private void startVotingPhase(AmongUsBot bot, GameLobby lobby) {
        votingPhase.set(true);
        
        // Send voting keyboards to all alive players
        for (Player player : lobby.getPlayerList()) {
            if (player.isAlive() && player.getChatId() != null) {
                sendVotingKeyboard(bot, lobby, player);
            }
        }
        
        // Start voting timer
        int votingTime = lobby.getSettings().getVotingTime();
        startVotingTimer(bot, lobby, votingTime);
        
        logger.info("Started voting phase for {} seconds in game {}", votingTime, lobby.getLobbyCode());
    }
    
    private void startVotingTimer(AmongUsBot bot, GameLobby lobby, int votingTimeSeconds) {
        votingTimer = scheduler.schedule(() -> {
            try {
                logger.info("Voting time ended for game {}, processing results", lobby.getLobbyCode());
                GameState nextState = processVotingResults(bot, lobby);
                if (nextState != null) {
                    lobby.setGameState(nextState);
                    nextState.onEnter(bot, lobby);
                }
            } catch (Exception e) {
                logger.error("Error during voting timer execution for game {}", lobby.getLobbyCode(), e);
            }
        }, votingTimeSeconds, TimeUnit.SECONDS);
    }
    
    private void sendVotingKeyboard(AmongUsBot bot, GameLobby lobby, Player voter) {
        if (voter.getChatId() == null) {
            logger.warn("Player {} has no chatId, skipping voting keyboard", voter.getUserId());
            return;
        }
        
        String text = "🗳️ Время голосования! Выберите, за кого голосовать:";
        InlineKeyboardMarkup keyboard = createVotingKeyboard(lobby, voter);
        
        SendMessage message = new SendMessage();
        message.setChatId(voter.getChatId());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        
        try {
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = bot.executeMethod(message);
            if (sentMessage != null) {
                votingMessageIds.put(voter.getUserId(), sentMessage.getMessageId());
            }
        } catch (Exception e) {
            logger.error("Error sending voting keyboard to player {}", voter.getUserId(), e);
        }
    }
    
    private InlineKeyboardMarkup createVotingKeyboard(GameLobby lobby, Player voter) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Thread-safe iteration over players
        List<Player> players = new ArrayList<>(lobby.getPlayerList());
        
        // Add a button for each alive player
        for (Player player : players) {
            // Cannot vote for yourself and cannot vote for dead players
            if (!player.getUserId().equals(voter.getUserId()) && player.isAlive()) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(player.getUserName());
                button.setCallbackData("vote:" + player.getUserId());
                row.add(button);
                keyboard.add(row);
            }
        }
        
        // Add a skip vote button
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        InlineKeyboardButton skipButton = new InlineKeyboardButton();
        skipButton.setText("Пропустить голосование");
        skipButton.setCallbackData("vote:skip");
        skipRow.add(skipButton);
        keyboard.add(skipRow);
        
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    
    /**
     * Handles a vote from a player with safe parsing.
     */
    private void handleVote(AmongUsBot bot, GameLobby lobby, Long voterId, String callbackData) {
        String[] parts = callbackData.split(":");
        if (parts.length < 2) {
            logger.error("Invalid vote callback data: '{}' from player {}", callbackData, voterId);
            return;
        }
        
        String target = parts[1];
        Player voter = lobby.getPlayer(voterId);
        
        if (voter == null || voter.getChatId() == null) {
            logger.warn("Player {} has no chatId or does not exist, cannot handle vote", voterId);
            return;
        }
        
        String lobbyCode = lobby.getLobbyCode();
        Map<Long, Long> votes = this.votes;
        if (votes == null) {
            logger.error("No votes map found for lobby {}", lobbyCode);
            return;
        }
        
        // Check if player already voted (additional safety check)
        if (votes.containsKey(voterId)) {
            logger.warn("Player {} tried to vote again in game {}, ignoring", voterId, lobbyCode);
            return;
        }
        
        // Record the vote
        if (target.equals("skip")) {
            votes.put(voterId, -1L); // -1 represents a skip vote
            updateVotingMessage(bot, lobby, voter, "Вы пропустили голосование.");
            logger.info("Player {} voted to skip in game {}", voterId, lobbyCode);
            lobby.addGameEvent(voterId, "VOTE", "Пропустил голосование");
        } else {
            try {
                long targetId = Long.parseLong(target);
                Player targetPlayer = lobby.getPlayer(targetId);
                
                if (targetPlayer != null && targetPlayer.isAlive()) {
                    votes.put(voterId, targetId);
                    updateVotingMessage(bot, lobby, voter, "Вы проголосовали за " + targetPlayer.getUserName() + ".");
                    logger.info("Player {} voted for player {} in game {}", voterId, targetId, lobbyCode);
                    lobby.addGameEvent(voterId, "VOTE", "Проголосовал за " + targetPlayer.getUserName());
                } else {
                    logger.warn("Player {} tried to vote for non-existent/dead player {} in game {}", 
                            voterId, targetId, lobbyCode);
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid vote target: '{}' from player {}", target, voterId, e);
            }
        }
        
        // Log the vote count
        int voteCount = hasVoted.size();
        logger.debug("Vote count in game {}: {} out of {} alive players", 
                lobbyCode, voteCount, countAlivePlayers(lobby));
    }
    
    private void updateVotingMessage(AmongUsBot bot, GameLobby lobby, Player voter, String text) {
        Integer messageId = votingMessageIds.get(voter.getUserId());
        
        if (messageId != null && voter.getChatId() != null) {
            try {
                boolean deleted = bot.deleteMessage(voter.getChatId(), messageId);
                if (deleted) {
                    logger.debug("Successfully deleted voting message {} for player {}", 
                            messageId, voter.getUserId());
                } else {
                    // Try to edit message if deletion failed
                    EditMessageText editMessage = new EditMessageText();
                    editMessage.setChatId(voter.getChatId());
                    editMessage.setMessageId(messageId);
                    editMessage.setText(text + "\n\n(Голосование завершено)");
                    editMessage.setReplyMarkup(null);
                    bot.executeMethod(editMessage);
                }
            } catch (Exception e) {
                logger.error("Error updating voting message for player {}: {}", 
                        voter.getUserId(), e.getMessage());
            }
            
            // Send new confirmation message
            bot.sendTextMessage(voter.getChatId(), text);
            
            // Remove message ID from tracker
            votingMessageIds.remove(voter.getUserId());
        } else {
            bot.sendTextMessage(voter.getChatId(), text);
        }
    }
    
    private int countAlivePlayers(GameLobby lobby) {
        return (int) lobby.getPlayerList().stream()
                .filter(Player::isAlive)
                .count();
    }
    
    private boolean allPlayersVoted(GameLobby lobby) {
        return hasVoted.size() >= countAlivePlayers(lobby);
    }
    
    private GameState processVotingResults(AmongUsBot bot, GameLobby lobby) {
        logger.info("Processing voting results for game {}", lobby.getLobbyCode());
        
        String lobbyCode = lobby.getLobbyCode();
        Map<Long, Long> votes = this.votes;
        
        if (votes == null) {
            logger.error("No votes found for lobby {}", lobbyCode);
            return new GameActiveState();
        }
        
        // Count votes for each player
        Map<Long, Integer> voteCounts = new HashMap<>();
        int skipVotes = 0;
        
        for (Long targetId : votes.values()) {
            if (targetId == -1L) {
                skipVotes++;
            } else {
                voteCounts.put(targetId, voteCounts.getOrDefault(targetId, 0) + 1);
            }
        }
        
        // Find the player with the most votes
        Long ejectedId = null;
        int maxVotes = 0;
        
        for (Map.Entry<Long, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                ejectedId = entry.getKey();
            } else if (entry.getValue() == maxVotes) {
                // Tie - no one gets ejected
                ejectedId = null;
            }
        }
        
        // Check if skip has the most votes
        if (skipVotes > maxVotes) {
            ejectedId = null;
        }
        
        // Announce the results to all players
        StringBuilder resultMessage = new StringBuilder("🗳️ Результаты голосования:\n\n");
        
        // Show vote counts
        List<Player> players = new ArrayList<>(lobby.getPlayerList());
        for (Player player : players) {
            int voteCount = voteCounts.getOrDefault(player.getUserId(), 0);
            if (voteCount > 0) {
                resultMessage.append(player.getUserName()).append(": ").append(voteCount).append(" голосов\n");
            }
        }
        
        if (skipVotes > 0) {
            resultMessage.append("Пропустить: ").append(skipVotes).append(" голосов\n");
        }
        
        resultMessage.append("\n");
        
        // Announce the result
        if (ejectedId == null) {
            resultMessage.append("Никого не исключили (пропуск или ничья).");
            lobby.addGameEvent(null, "VOTE_RESULT", "Никого не исключили");
        } else {
            Player ejectedPlayer = lobby.getPlayer(ejectedId);
            if (ejectedPlayer != null) {
                ejectedPlayer.eject(); // Use eject() instead of kill() for voted-out players
                String roleName = ejectedPlayer.isImpostor() ? "Предатель" : "Член экипажа";
                resultMessage.append(ejectedPlayer.getUserName())
                        .append(" был исключён. Он был ")
                        .append(roleName).append(".");
                
                lobby.addGameEvent(ejectedId, "EJECTED", 
                        "Исключён голосованием (роль: " + roleName + ")");
                
                logger.info("Player {} ({}) was ejected from game {}", 
                        ejectedPlayer.getUserName(), roleName, lobbyCode);
            }
        }
        
        // Send results to all players
        for (Player player : players) {
            if (player.getChatId() != null) {
                bot.sendTextMessage(player.getChatId(), resultMessage.toString());
            }
        }
        
        // Check win conditions
        GameState winCondition = checkWinConditions(lobby);
        if (winCondition != null) {
            return winCondition;
        }
        
        // Return to active game state
        return new GameActiveState();
    }
    
    private GameState checkWinConditions(GameLobby lobby) {
        List<Player> alivePlayers = lobby.getPlayerList().stream()
                .filter(Player::isAlive)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        long aliveImpostors = alivePlayers.stream()
                .filter(Player::isImpostor)
                .count();
        
        long aliveCrewmates = alivePlayers.stream()
                .filter(player -> !player.isImpostor())
                .count();
        
        // Check impostor win condition
        if (aliveImpostors >= aliveCrewmates) {
            return new GameOverState("Impostors", "Предатели равны или превосходят экипаж!");
        }
        
        // Check crewmate win condition (no impostors left)
        if (aliveImpostors == 0) {
            return new GameOverState("Crewmates", "Все предатели найдены!");
        }
        
        return null; // Game continues
    }

    private void startCombinedVotingTimer(AmongUsBot bot, GameLobby lobby, int totalTimeSeconds) {
        votingTimer = scheduler.schedule(() -> {
            try {
                logger.info("Combined discussion/voting time ended for game {}, processing results", lobby.getLobbyCode());
                GameState nextState = processVotingResults(bot, lobby);
                if (nextState != null) {
                    lobby.setGameState(nextState);
                    nextState.onEnter(bot, lobby);
                }
            } catch (Exception e) {
                logger.error("Error during combined voting timer execution for game {}", lobby.getLobbyCode(), e);
            }
        }, totalTimeSeconds, TimeUnit.SECONDS);
    }
}
// COMPLETED: DiscussionState class 