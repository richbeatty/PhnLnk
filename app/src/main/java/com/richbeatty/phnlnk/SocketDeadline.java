package com.richbeatty.phnlnk;

import java.net.Socket;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Absolute startup deadline; SO_TIMEOUT alone can be defeated by dribbling bytes. */
final class SocketDeadline implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "PhnLnk-socket-deadlines");
        thread.setDaemon(true);
        return thread;
    });
    static { TIMER.setRemoveOnCancelPolicy(true); }
    private final ScheduledFuture<?> task;

    private SocketDeadline(Socket socket, long millis) {
        task = TIMER.schedule(() -> {
            try { socket.close(); } catch (java.io.IOException ignored) { }
        }, millis, TimeUnit.MILLISECONDS);
    }
    static SocketDeadline after(Socket socket, long millis) { return new SocketDeadline(socket, millis); }
    @Override public void close() { task.cancel(false); }
}
