package org.radarcns.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import org.radarcns.android.util.AndroidThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static android.content.Context.ALARM_SERVICE;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

/**
 * Process events based on a alarm.
 *
 * The events will be processed in a background Thread and will not wake the device. During processing in the provided
 * Runnable, check that {@link #isDone()} remains {@code false}. Once it turns true, the Runnable should stop
 * processing.
 */
public class OfflineProcessor implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(OfflineProcessor.class);

    private final Context context;
    private final AndroidThreadFactory threadFactory;
    private final BroadcastReceiver receiver;
    private final String requestName;
    private final PendingIntent pendingIntent;
    private final AlarmManager alarmManager;

    private boolean doStop;
    private Thread processorThread;

    /**
     * Creates a processor that will register a BroadcastReceiver and alarm with the given context.
     * @param context context to register a BroadcastReceiver with
     * @param runnable code to run in offline mode
     * @param requestCode a code unique to the application, used to identify the current processor
     * @param requestName a name unique to the application, used to identify the current processor
     */
    public OfflineProcessor(Context context, final Runnable runnable, int requestCode, String requestName) {
        this.context = context;
        this.threadFactory = new AndroidThreadFactory(requestName, THREAD_PRIORITY_BACKGROUND);
        this.doStop = false;
        this.requestName = requestName;
        this.alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        Intent intent = new Intent(requestName);
        pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, 0);

        this.receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (OfflineProcessor.this) {
                    if (doStop) {
                        return;
                    }
                    processorThread = threadFactory.newThread(runnable);
                    processorThread.start();
                }
            }
        };
    }

    /** Start processing at the given interval. */
    public void start(long interval) {
        setInterval(interval);
        context.registerReceiver(this.receiver, new IntentFilter(requestName));
    }

    /** Change the processing interval to the given value. */
    public final void setInterval(long interval) {
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(),
                interval * 1000, pendingIntent);
    }

    /** Whether the processing Runnable should stop execution. */
    public synchronized boolean isDone() {
        return doStop;
    }

    /**
     * Closes the processor.
     *
     * This will deregister any BroadcastReceiver, remove pending alarms and signal the running thread to stop. If
     * processing is currently taking place, it will block until that is actually done. The processing Runnable should
     * query {@link #isDone()} very regularly to stop execution if that is the case.
     */
    @Override
    public void close() {
        alarmManager.cancel(pendingIntent);
        context.unregisterReceiver(receiver);

        Thread localThread;
        synchronized (this) {
            doStop = true;
            localThread = processorThread;
        }
        if (localThread != null) {
            try {
                localThread.join();
            } catch (InterruptedException e) {
                logger.warn("Waiting for processing thread interrupted");
            }
        }
    }
}
