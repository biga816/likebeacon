package dev.biga.likebeacon.service.support;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;

public final class AsyncFailures {

    private AsyncFailures() {
    }

    public static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    public static boolean isConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlException.getErrorCode() == 19
                        || (sqlState != null && sqlState.startsWith("23"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
