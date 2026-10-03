package dev.biga.likebeacon.database;

import java.sql.Connection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Serializes all reads performed on the dedicated SQLite read connection. */
public final class DatabaseReadExecutor {

    private static final Logger LOGGER = Logger.getLogger(DatabaseReadExecutor.class.getName());

    private final Connection connection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "likebeacon-db-reader"));

    public DatabaseReadExecutor(DatabaseManager databaseManager) {
        this.connection = databaseManager.getReadConnection();
    }

    public <T> CompletableFuture<T> submit(ReadTask<T> task) {
        return submitCallable(() -> task.execute(connection));
    }

    private <T> CompletableFuture<T> submitCallable(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warning("DatabaseReadExecutor did not terminate within 5 s; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface ReadTask<T> {
        T execute(Connection connection) throws Exception;
    }
}
