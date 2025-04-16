package io.github.ktauchathuranga.utils;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class BluetoothHidWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WidgetProvider";
    private static final String PREFS_NAME = "UtilsPrefs";
    private static final String KEY_WIDGET_DEVICE_ADDRESS = "widget_device_address_";
    public static final String ACTION_CONNECT = "io.github.ktauchathuranga.utils.ACTION_CONNECT";
    public static final String ACTION_DISCONNECT = "io.github.ktauchathuranga.utils.ACTION_DISCONNECT";
    public static final String ACTION_WIN_L = "io.github.ktauchathuranga.utils.ACTION_WIN_L";
    public static final String ACTION_UNLOCK = "io.github.ktauchathuranga.utils.ACTION_UNLOCK";
    public static final String ACTION_SHUTDOWN = "io.github.ktauchathuranga.utils.ACTION_SHUTDOWN";
    public static final String ACTION_SPACE = "io.github.ktauchathuranga.utils.ACTION_SPACE";
    public static final String ACTION_PASSWORD = "io.github.ktauchathuranga.utils.ACTION_PASSWORD";
    public static final String ACTION_RESTART = "io.github.ktauchathuranga.utils.ACTION_RESTART";
    public static final String ACTION_SLEEP = "io.github.ktauchathuranga.utils.ACTION_SLEEP";
    public static final String ACTION_HIBERNATE = "io.github.ktauchathuranga.utils.ACTION_HIBERNATE";
    public static final String ACTION_UPDATE_STATUS = "io.github.ktauchathuranga.utils.ACTION_UPDATE_STATUS";
    public static final String EXTRA_STATUS_TEXT = "status_text";
    public static final String EXTRA_IS_CONNECTED = "is_connected";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");
        for (int appWidgetId : appWidgetIds) {
            try {
                updateWidget(context, appWidgetManager, appWidgetId, context.getString(R.string.widget_not_connected), false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update widget ID " + appWidgetId + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d(TAG, "Widget provider enabled");
        context.startService(new Intent(context, BluetoothHidService.class));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "Widget provider disabled");
        context.stopService(new Intent(context, BluetoothHidService.class));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.d(TAG, "onDeleted called for " + appWidgetIds.length + " widgets");
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            EncryptedSharedPreferences prefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            for (int appWidgetId : appWidgetIds) {
                String key = KEY_WIDGET_DEVICE_ADDRESS + appWidgetId;
                prefs.edit().remove(key).apply();
                Log.d(TAG, "Cleared device address for widget ID: " + appWidgetId);
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to clear SharedPreferences on delete: " + e.getMessage(), e);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (ACTION_UPDATE_STATUS.equals(action)) {
            String statusText = intent.getStringExtra(EXTRA_STATUS_TEXT);
            boolean isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, BluetoothHidWidgetProvider.class));
            Log.d(TAG, "Updating " + appWidgetIds.length + " widgets with status: " + statusText);
            for (int appWidgetId : appWidgetIds) {
                try {
                    updateWidget(context, appWidgetManager, appWidgetId, statusText != null ? statusText : "Not Connected", isConnected);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update widget ID " + appWidgetId + ": " + e.getMessage(), e);
                }
            }
        } else if (action != null && (
                action.equals(ACTION_CONNECT) ||
                        action.equals(ACTION_DISCONNECT) ||
                        action.equals(ACTION_WIN_L) ||
                        action.equals(ACTION_UNLOCK) ||
                        action.equals(ACTION_SHUTDOWN) ||
                        action.equals(ACTION_SPACE) ||
                        action.equals(ACTION_PASSWORD) ||
                        action.equals(ACTION_RESTART) ||
                        action.equals(ACTION_SLEEP) ||
                        action.equals(ACTION_HIBERNATE))) {
            Log.d(TAG, "Attempting to start service for action: " + action);
            Intent serviceIntent = new Intent(context, BluetoothHidService.class);
            serviceIntent.setAction(action);
            try {
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "Service started for action: " + action);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service for action " + action + ": " + e.getMessage(), e);
            }
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String statusText, boolean isConnected) {
        Log.d(TAG, "Updating widget ID " + appWidgetId + " with status: " + statusText + ", connected: " + isConnected);
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.widget_status_text, statusText != null ? statusText : "Not Connected");

            // Set button pending intents
            PendingIntent connectIntent = getPendingIntent(context, isConnected ? ACTION_DISCONNECT : ACTION_CONNECT);
            if (connectIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_connect_button, connectIntent);
            } else {
                Log.e(TAG, "Connect PendingIntent is null for widget ID " + appWidgetId);
            }
            PendingIntent winLIntent = getPendingIntent(context, ACTION_WIN_L);
            if (winLIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_win_l_button, winLIntent);
            }
            PendingIntent unlockIntent = getPendingIntent(context, ACTION_UNLOCK);
            if (unlockIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_unlock_button, unlockIntent);
            }
            PendingIntent shutdownIntent = getPendingIntent(context, ACTION_SHUTDOWN);
            if (shutdownIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_shutdown_button, shutdownIntent);
            }

            // Update button states
            views.setTextViewText(R.id.widget_connect_button, isConnected ? context.getString(R.string.disconnect) : context.getString(R.string.connect));
            views.setBoolean(R.id.widget_win_l_button, "setEnabled", isConnected);
            views.setBoolean(R.id.widget_unlock_button, "setEnabled", isConnected);
            views.setBoolean(R.id.widget_shutdown_button, "setEnabled", isConnected);

            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Widget ID " + appWidgetId + " updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget ID " + appWidgetId + ": " + e.getMessage(), e);
            RemoteViews fallbackViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            fallbackViews.setTextViewText(R.id.widget_status_text, "Error");
            appWidgetManager.updateAppWidget(appWidgetId, fallbackViews);
        }
    }

    private PendingIntent getPendingIntent(Context context, String action) {
        Log.d(TAG, "Creating PendingIntent for action: " + action);
        Intent intent = new Intent(context, BluetoothHidWidgetProvider.class);
        intent.setAction(action);
        try {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Log.d(TAG, "PendingIntent created successfully for action: " + action);
            return pendingIntent;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create PendingIntent for action " + action + ": " + e.getMessage(), e);
            return null;
        }
    }
}