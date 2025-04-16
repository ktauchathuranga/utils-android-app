package io.github.ktauchathuranga.utils;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BluetoothHidService extends Service {

    private static final String TAG = "BluetoothHidService";
    private static final String PREFS_NAME = "UtilsPrefs";
    private static final String KEY_WIDGET_DEVICE_ADDRESS = "widget_device_address_";
    private static final String KEY_PASSWORD = "unlock_password";
    private static final String KEY_SPEED_PROFILE = "last_speed_profile";
    private static final int CONNECT_RETRY_DELAY_MS = 1000;
    private static final int MAX_CONNECT_RETRIES = 3;

    // HID descriptor and reports from MainActivity
    public static final byte[] HID_DESCRIPTOR = {
            (byte) 0x05, (byte) 0x01, // USAGE_PAGE (Generic Desktop)
            (byte) 0x09, (byte) 0x06, // USAGE (Keyboard)
            (byte) 0xa1, (byte) 0x01, // COLLECTION (Application)
            (byte) 0x05, (byte) 0x07, // USAGE_PAGE (Keyboard)
            (byte) 0x19, (byte) 0xe0, // USAGE_MINIMUM (Keyboard LeftControl)
            (byte) 0x29, (byte) 0xe7, // USAGE_MAXIMUM (Keyboard Right GUI)
            (byte) 0x15, (byte) 0x00, // LOGICAL_MINIMUM (0)
            (byte) 0x25, (byte) 0x01, // LOGICAL_MAXIMUM (1)
            (byte) 0x75, (byte) 0x01, // REPORT_SIZE (1)
            (byte) 0x95, (byte) 0x08, // REPORT_COUNT (8)
            (byte) 0x81, (byte) 0x02, // INPUT (Data,Var,Abs) - Modifier byte
            (byte) 0x95, (byte) 0x01, // REPORT_COUNT (1)
            (byte) 0x75, (byte) 0x08, // REPORT_SIZE (8)
            (byte) 0x81, (byte) 0x01, // INPUT (Cnst,Ary,Abs) - Reserved byte
            (byte) 0x95, (byte) 0x05, // REPORT_COUNT (5)
            (byte) 0x75, (byte) 0x01, // REPORT_SIZE (1)
            (byte) 0x05, (byte) 0x08, // USAGE_PAGE (LEDs)
            (byte) 0x19, (byte) 0x01, // USAGE_MINIMUM (Num Lock)
            (byte) 0x29, (byte) 0x05, // USAGE_MAXIMUM (Kana)
            (byte) 0x91, (byte) 0x02, // OUTPUT (Data,Var,Abs) - LED report
            (byte) 0x95, (byte) 0x01, // REPORT_COUNT (1)
            (byte) 0x75, (byte) 0x03, // REPORT_SIZE (3)
            (byte) 0x91, (byte) 0x01, // OUTPUT (Cnst,Ary,Abs) - LED padding
            (byte) 0x95, (byte) 0x06, // REPORT_COUNT (6)
            (byte) 0x75, (byte) 0x08, // REPORT_SIZE (8)
            (byte) 0x15, (byte) 0x00, // LOGICAL_MINIMUM (0)
            (byte) 0x25, (byte) 0x65, // LOGICAL_MAXIMUM (101)
            (byte) 0x05, (byte) 0x07, // USAGE_PAGE (Keyboard)
            (byte) 0x19, (byte) 0x00, // USAGE_MINIMUM (Reserved)
            (byte) 0x29, (byte) 0x65, // USAGE_MAXIMUM (Keyboard Application)
            (byte) 0x81, (byte) 0x00, // INPUT (Data,Ary,Abs) - Key array
            (byte) 0xc0              // END_COLLECTION
    };

    public static final byte[] REPORT_WIN_L_PRESS = {0x08, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00}; // Left GUI + L
    public static final byte[] REPORT_SPACE_PRESS = {0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x00}; // Space
    public static final byte[] REPORT_RELEASE = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};     // Key release
    public static final byte[] REPORT_WIN_R_PRESS = {0x08, 0x00, 0x15, 0x00, 0x00, 0x00, 0x00, 0x00}; // Left GUI + R

    // HID key codes
    private static final byte[] DIGIT_KEY_CODES = {(byte) 0x27, (byte) 0x1E, (byte) 0x1F, (byte) 0x20, (byte) 0x21, (byte) 0x22, (byte) 0x23, (byte) 0x24, (byte) 0x25, (byte) 0x26};
    private static final byte[] LETTER_KEY_CODES = {0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D};
    private static final char[] SYMBOLS = {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '=', '+', '[', ']', '\\', ';', '\'', ',', '.', '/', '`', '~', '_', '{', '}', '|', ':', '"', '<', '>', '?'};
    private static final byte[] SYMBOL_KEY_CODES = {0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38, 0x35, 0x23, 0x2D, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38};
    private static final boolean[] SYMBOL_REQUIRES_SHIFT = {true, true, true, true, true, true, true, true, true, true, false, false, true, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true};
    private static final byte SHIFT_MODIFIER = (byte) 0x02;

    private EncryptedSharedPreferences encryptedPrefs;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private boolean isHidServiceReady = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    private SpeedProfile selectedSpeedProfile;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            encryptedPrefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    this, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d(TAG, "EncryptedSharedPreferences initialized");
            // Initialize speed profile
            selectedSpeedProfile = getLastSpeedProfile();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage(), e);
        }
        initializeBluetoothAdapter();
    }

    private void initializeBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            updateWidgetStatus("Bluetooth not supported", false);
            stopSelf();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is disabled");
            updateWidgetStatus("Bluetooth disabled", false);
            return;
        }
        registerHidDevice();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called with intent: " + intent);
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);
            switch (action) {
                case BluetoothHidWidgetProvider.ACTION_CONNECT:
                    handleConnectAction();
                    break;
                case BluetoothHidWidgetProvider.ACTION_DISCONNECT:
                    handleDisconnectAction();
                    break;
                case BluetoothHidWidgetProvider.ACTION_WIN_L:
                    handleWinLAction();
                    break;
                case BluetoothHidWidgetProvider.ACTION_UNLOCK:
                    handleUnlockAction();
                    break;
                case BluetoothHidWidgetProvider.ACTION_SHUTDOWN:
                    handleShutdownAction();
                    break;
                default:
                    Log.w(TAG, "Unknown action: " + action);
            }
        } else {
            Log.w(TAG, "Intent or action is null");
        }
        return START_STICKY;
    }

    private void handleConnectAction() {
        Log.d(TAG, "Handling ACTION_CONNECT");
        try {
            String deviceAddress = getDeviceAddress();
            if (deviceAddress == null) {
                Log.w(TAG, "No device address found");
                updateWidgetStatus("No device selected", false);
                return;
            }
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error handling connect action: " + e.getMessage(), e);
            updateWidgetStatus("Connection error", false);
        }
    }

    private void handleDisconnectAction() {
        Log.d(TAG, "Handling ACTION_DISCONNECT");
        disconnectDevice();
    }

    private void handleWinLAction() {
        Log.d(TAG, "Handling ACTION_WIN_L");
        sendWinLCommand();
    }

    private void handleUnlockAction() {
        Log.d(TAG, "Handling ACTION_UNLOCK");
        String storedPassword = encryptedPrefs.getString(KEY_PASSWORD, null);
        if (storedPassword == null) {
            Log.w(TAG, "No password set for unlock");
            updateWidgetStatus("No password set", isConnected());
        } else {
            sendUnlockWithPassword(storedPassword, selectedSpeedProfile);
        }
    }

    private void handleShutdownAction() {
        Log.d(TAG, "Handling ACTION_SHUTDOWN");
        sendPowerCommand("shutdown /s /f /t 0", "Shutdown", selectedSpeedProfile);
    }

    private void connectToDevice(BluetoothDevice device, int retryCount) {
        Log.d(TAG, "Connecting to " + getDeviceName(device) + " (Retry " + retryCount + ")");
        if (!isHidServiceReady || hidDevice == null) {
            if (retryCount < MAX_CONNECT_RETRIES) {
                Log.w(TAG, "HID service not ready, retrying " + (retryCount + 1) + "/" + MAX_CONNECT_RETRIES);
                registerHidDevice();
                mainHandler.postDelayed(() -> connectToDevice(device, retryCount + 1), CONNECT_RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Max retries reached for HID service initialization");
                updateWidgetStatus("Failed to initialize HID", false);
            }
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                hidDevice.connect(device);
                Log.d(TAG, "Connection request sent to " + getDeviceName(device));
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException connecting to " + getDeviceName(device) + ": " + e.getMessage(), e);
                updateWidgetStatus("Permission error", false);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error connecting to " + getDeviceName(device) + ": " + e.getMessage(), e);
                updateWidgetStatus("Connection failed", false);
            }
        });
    }

    private void disconnectDevice() {
        if (hidDevice == null || connectedDevice == null) {
            Log.w(TAG, "No device connected to disconnect");
            updateWidgetStatus("Not connected", false);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                hidDevice.disconnect(connectedDevice);
                Log.d(TAG, "Disconnect request sent for " + getDeviceName(connectedDevice));
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to disconnect: " + e.getMessage(), e);
                updateWidgetStatus("Disconnect error", false);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during disconnect: " + e.getMessage(), e);
                updateWidgetStatus("Disconnect error", false);
            }
        });
    }

    private void sendWinLCommand() {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send Win+L: not connected");
            updateWidgetStatus("Not connected", false);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                hidDevice.sendReport(connectedDevice, 0, REPORT_WIN_L_PRESS);
                Thread.sleep(50);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Log.d(TAG, "Win+L command sent successfully");
                updateWidgetStatus("Win+L sent", true);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send Win+L: " + e.getMessage(), e);
                updateWidgetStatus("Win+L error", true);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending Win+L", e);
                Thread.currentThread().interrupt();
                updateWidgetStatus("Win+L error", true);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending Win+L: " + e.getMessage(), e);
                updateWidgetStatus("Win+L error", true);
            }
        });
    }

    private void sendUnlockWithPassword(String password, SpeedProfile speedProfile) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send unlock: not connected");
            updateWidgetStatus("Not connected", false);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                // Send Space
                hidDevice.sendReport(connectedDevice, 0, REPORT_SPACE_PRESS);
                Thread.sleep(speedProfile.getDelayMs());
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Thread.sleep(400); // Initial delay before password

                // Send password
                for (char c : password.toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(speedProfile.getDelayMs());
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(speedProfile.getDelayMs());
                }
                Log.d(TAG, "Unlock command sent: Space + [password] at " + speedProfile);
                updateWidgetStatus("Unlock sent", true);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send unlock command: " + e.getMessage(), e);
                updateWidgetStatus("Unlock error", true);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending unlock command", e);
                Thread.currentThread().interrupt();
                updateWidgetStatus("Unlock error", true);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending unlock command: " + e.getMessage(), e);
                updateWidgetStatus("Unlock error", true);
            }
        });
    }

    private void sendPowerCommand(String command, String commandName, SpeedProfile speedProfile) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send " + commandName + ": not connected");
            updateWidgetStatus("Not connected", false);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                // Step 1: Open Run dialog (Win+R)
                hidDevice.sendReport(connectedDevice, 0, REPORT_WIN_R_PRESS);
                Thread.sleep(speedProfile.getDelayMs());
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Thread.sleep(400); // Wait for Run dialog

                // Step 2: Type "cmd" and press Enter
                for (char c : "cmd".toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(speedProfile.getDelayMs());
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(speedProfile.getDelayMs());
                }
                byte[] enterReport = getHidReport('\n');
                hidDevice.sendReport(connectedDevice, 0, enterReport);
                Thread.sleep(speedProfile.getDelayMs());
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Thread.sleep(500); // Wait for Command Prompt

                // Step 3: Type the power command and press Enter
                for (char c : command.toCharArray()) {
                    try {
                        byte[] report = getHidReport(c);
                        hidDevice.sendReport(connectedDevice, 0, report);
                        Thread.sleep(speedProfile.getDelayMs());
                        hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                        Thread.sleep(speedProfile.getDelayMs());
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Unsupported character in power command: " + c);
                        continue;
                    }
                }
                hidDevice.sendReport(connectedDevice, 0, enterReport);
                Thread.sleep(speedProfile.getDelayMs());
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);

                // Step 4: Exit Command Prompt
                for (char c : "exit".toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(speedProfile.getDelayMs());
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(speedProfile.getDelayMs());
                }
                hidDevice.sendReport(connectedDevice, 0, enterReport);
                Thread.sleep(speedProfile.getDelayMs());
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);

                Log.d(TAG, commandName + " command sent successfully at " + speedProfile);
                updateWidgetStatus(commandName + " sent", true);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send " + commandName + " command: " + e.getMessage(), e);
                updateWidgetStatus(commandName + " error", true);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending " + commandName + " command", e);
                Thread.currentThread().interrupt();
                updateWidgetStatus(commandName + " error", true);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending " + commandName + " command: " + e.getMessage(), e);
                updateWidgetStatus(commandName + " error", true);
            }
        });
    }

    public static byte[] getHidReport(char c) {
        byte modifier = 0x00;
        byte keyCode;

        if (c == ' ') {
            keyCode = (byte) 0x2C; // HID key code for space
            return new byte[]{modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
        } else if (c == '\n') {
            keyCode = (byte) 0x28; // HID key code for Enter
            return new byte[]{modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
        } else if (c >= '0' && c <= '9') {
            keyCode = DIGIT_KEY_CODES[c - '0'];
        } else if (c >= 'a' && c <= 'z') {
            keyCode = LETTER_KEY_CODES[c - 'a'];
        } else if (c >= 'A' && c <= 'Z') {
            modifier = SHIFT_MODIFIER;
            keyCode = LETTER_KEY_CODES[c - 'A'];
        } else {
            for (int i = 0; i < SYMBOLS.length; i++) {
                if (c == SYMBOLS[i]) {
                    keyCode = SYMBOL_KEY_CODES[i];
                    if (SYMBOL_REQUIRES_SHIFT[i]) {
                        modifier = SHIFT_MODIFIER;
                    }
                    return new byte[]{modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
                }
            }
            throw new IllegalArgumentException("Unsupported character: " + c);
        }
        return new byte[]{modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    private void registerHidDevice() {
        backgroundExecutor.execute(() -> {
            try {
                bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        if (profile != BluetoothProfile.HID_DEVICE) return;
                        hidDevice = (BluetoothHidDevice) proxy;
                        Log.d(TAG, "HID service connected");
                        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                                "BluetoothHIDKeyboard", "Virtual Keyboard", "ktauchathuranga", (byte) 0x40, HID_DESCRIPTOR
                        );
                        try {
                            hidDevice.registerApp(sdp, null, null, backgroundExecutor, new HidCallback());
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to register HID app: " + e.getMessage(), e);
                            updateWidgetStatus("HID registration failed", false);
                        } catch (Exception e) {
                            Log.e(TAG, "Unexpected error registering HID app: " + e.getMessage(), e);
                            updateWidgetStatus("HID registration failed", false);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            Log.w(TAG, "HID service disconnected");
                            hidDevice = null;
                            isHidServiceReady = false;
                            updateWidgetStatus("HID service disconnected", false);
                        }
                    }
                }, BluetoothProfile.HID_DEVICE);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to initialize HID service: " + e.getMessage(), e);
                updateWidgetStatus("HID service init failed", false);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error initializing HID service: " + e.getMessage(), e);
                updateWidgetStatus("HID service init failed", false);
            }
        });
    }

    private boolean isConnected() {
        return connectedDevice != null && hidDevice != null && isHidServiceReady;
    }

    private String getDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null && !name.isEmpty() ? name : "Unknown Device";
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to get device name: " + e.getMessage(), e);
            return "Unknown Device";
        }
    }

    private String getDeviceAddress() {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, BluetoothHidWidgetProvider.class));
            for (int appWidgetId : appWidgetIds) {
                String key = KEY_WIDGET_DEVICE_ADDRESS + appWidgetId;
                String address = encryptedPrefs.getString(key, null);
                if (address != null) {
                    Log.d(TAG, "Found device address: " + address + " for widget ID: " + appWidgetId);
                    return address;
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving device address: " + e.getMessage(), e);
            return null;
        }
    }

    private SpeedProfile getLastSpeedProfile() {
        String speedName = encryptedPrefs.getString(KEY_SPEED_PROFILE, SpeedProfile.MEDIUM.name());
        try {
            return SpeedProfile.valueOf(speedName);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid stored speed profile, defaulting to MEDIUM");
            return SpeedProfile.MEDIUM;
        }
    }

    private void updateWidgetStatus(String statusText, boolean isConnected) {
        Log.d(TAG, "Updating widget with status: " + statusText + ", connected: " + isConnected);
        Intent intent = new Intent(this, BluetoothHidWidgetProvider.class);
        intent.setAction(BluetoothHidWidgetProvider.ACTION_UPDATE_STATUS);
        intent.putExtra(BluetoothHidWidgetProvider.EXTRA_STATUS_TEXT, statusText);
        intent.putExtra(BluetoothHidWidgetProvider.EXTRA_IS_CONNECTED, isConnected);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent for status update");
    }

    private class HidCallback extends BluetoothHidDevice.Callback {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            Log.d(TAG, "HID app status changed: registered=" + registered);
            isHidServiceReady = registered;
            updateWidgetStatus(registered ? "HID registered" : "HID registration failed", false);
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            String deviceName = getDeviceName(device);
            Log.d(TAG, "Connection state changed to " + state + " for " + deviceName);
            switch (state) {
                case BluetoothProfile.STATE_CONNECTED:
                    connectedDevice = device;
                    selectedSpeedProfile = getLastSpeedProfile();
                    updateWidgetStatus("Connected to " + deviceName, true);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    connectedDevice = null;
                    selectedSpeedProfile = null;
                    updateWidgetStatus("Not Connected", false);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    updateWidgetStatus("Connecting to " + deviceName, false);
                    break;
                case BluetoothProfile.STATE_DISCONNECTING:
                    updateWidgetStatus("Disconnecting", true);
                    break;
                default:
                    Log.w(TAG, "Unhandled connection state: " + state);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hidDevice != null) {
            backgroundExecutor.execute(() -> {
                try {
                    if (connectedDevice != null) {
                        hidDevice.disconnect(connectedDevice);
                    }
                    hidDevice.unregisterApp();
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
                    Log.d(TAG, "HID app unregistered and proxy closed");
                } catch (SecurityException e) {
                    Log.e(TAG, "Failed to unregister HID app: " + e.getMessage(), e);
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during cleanup: " + e.getMessage(), e);
                }
            });
        }
        Log.d(TAG, "Service destroyed");
    }

    // SpeedProfile enum from MainActivity
    public enum SpeedProfile {
        SLOW(100),
        MEDIUM(50),
        FAST(30);

        private final int delayMs;

        SpeedProfile(int delayMs) {
            this.delayMs = delayMs;
        }

        public int getDelayMs() {
            return delayMs;
        }
    }
}