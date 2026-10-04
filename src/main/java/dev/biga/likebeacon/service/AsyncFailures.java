package dev.biga.likebeacon.service;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;

final class AsyncFailures {

    private AsyncFailures() {
    }

    static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    static boolean isConstraintViolation(Throwable failure) {
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
