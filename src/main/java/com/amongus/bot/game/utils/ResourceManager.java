package com.amongus.bot.game.utils;

import com.amongus.bot.game.GameConstants;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Centralized resource management to prevent memory leaks
 */
public final class ResourceManager {
    
    private static final Logger logger = Logger.getLogger(ResourceManager.class.getName());
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // Shared thread pool for all game operations
    private static ScheduledExecutorService sharedExecutorService;
    
    // Shutdown hook to ensure cleanup
    private static final Thread shutdownHook = new Thread(ResourceManager::forceCleanup);
    
    private ResourceManager() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Initialize resource manager
     */
    public static synchronized void initialize() {
        if (initialized.compareAndSet(false, true)) {
            sharedExecutorService = new ScheduledThreadPoolExecutor(
                GameConstants.CORE_POOL_SIZE,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "AmongUs-GameThread-" + (++counter));
                        thread.setDaemon(true);
                        return thread;
                    }
                }
            );
            
            // Set maximum pool size
            ((ScheduledThreadPoolExecutor) sharedExecutorService).setMaximumPoolSize(GameConstants.MAXIMUM_POOL_SIZE);
            ((ScheduledThreadPoolExecutor) sharedExecutorService).setKeepAliveTime(GameConstants.KEEP_ALIVE_TIME, TimeUnit.SECONDS);
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            logger.info("ResourceManager initialized successfully");
        }
    }
    
    /**
     * Get shared thread pool
     */
    public static ScheduledExecutorService getSharedExecutorService() {
        if (!initialized.get()) {
            initialize();
        }
        return sharedExecutorService;
    }
    
    /**
     * Schedule a task with delay
     */
    public static ScheduledFuture<?> scheduleTask(Runnable task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        return getSharedExecutorService().schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.severe("Error executing scheduled task: " + e.getMessage());
            }
        }, delay, unit);
    }
    
    /**
     * Schedule a repeating task
     */
    public static ScheduledFuture<?> scheduleRepeatingTask(Runnable task, long initialDelay, 
                                                          long period, TimeUnit unit) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        return getSharedExecutorService().scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.severe("Error executing repeating task: " + e.getMessage());
            }
        }, initialDelay, period, unit);
    }
    
    /**
     * Cancel a scheduled task safely
     */
    public static boolean cancelTask(ScheduledFuture<?> future) {
        if (future != null && !future.isDone()) {
            return future.cancel(true);
        }
        return false;
    }
    
    /**
     * Submit a task for execution
     */
    public static Future<?> submitTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        return getSharedExecutorService().submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.severe("Error executing submitted task: " + e.getMessage());
            }
        });
    }
    
    /**
     * Clean up resources for a specific game
     */
    public static void cleanupGameResources(String lobbyCode) {
        if (lobbyCode == null || lobbyCode.trim().isEmpty()) {
            return;
        }
        
        logger.info("Cleaning up resources for lobby: " + lobbyCode);
        
        // Force garbage collection hint (JVM may ignore this)
        System.gc();
    }
    
    /**
     * Shutdown resource manager gracefully
     */
    public static synchronized void shutdown() {
        if (initialized.get() && sharedExecutorService != null) {
            logger.info("Shutting down ResourceManager...");
            
            sharedExecutorService.shutdown();
            try {
                if (!sharedExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warning("Executor did not terminate gracefully, forcing shutdown");
                    sharedExecutorService.shutdownNow();
                    
                    if (!sharedExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.severe("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Interrupted while waiting for executor termination");
                sharedExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            initialized.set(false);
            logger.info("ResourceManager shutdown completed");
        }
    }
    
    /**
     * Force cleanup - used by shutdown hook
     */
    private static void forceCleanup() {
        if (initialized.get()) {
            logger.info("Force cleanup triggered by shutdown hook");
            shutdown();
        }
    }
    
    /**
     * Get current thread pool statistics
     */
    public static String getThreadPoolStats() {
        if (!initialized.get() || !(sharedExecutorService instanceof ThreadPoolExecutor)) {
            return "ResourceManager not initialized or not using ThreadPoolExecutor";
        }
        
        ThreadPoolExecutor executor = (ThreadPoolExecutor) sharedExecutorService;
        return String.format(
            "ThreadPool Stats - Active: %d, Pool Size: %d, Queue Size: %d, Completed: %d",
            executor.getActiveCount(),
            executor.getPoolSize(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount()
        );
    }
    
    /**
     * Check if resource manager is properly initialized
     */
    public static boolean isInitialized() {
        return initialized.get() && sharedExecutorService != null && !sharedExecutorService.isShutdown();
    }
} 