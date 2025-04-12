package io.github.ktauchathuranga.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A Bluetooth HID keyboard controller that sends Win+L or unlocks with Space + user-defined password.
 * Password is stored securely using EncryptedSharedPreferences. Requires API 28+.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Utils";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final String UNKNOWN_DEVICE_NAME = "Unknown Device";
    private static final int CONNECT_RETRY_DELAY_MS = 1000;
    private static final int MAX_CONNECT_RETRIES = 3;
    private static final String PREFS_NAME = "UtilsPrefs";
    private static final String KEY_PASSWORD = "unlock_password";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private TextView connectionStatusTextView;
    private Button connectToggleButton;
    private Button sendKeyButton;
    private Button unlockButton;
    private Button spaceButton;
    private Button passwordButton;
    private Button changePasswordButton;
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isHidServiceReady = false;
    private EncryptedSharedPreferences encryptedPrefs;

    // HID descriptor for a standard USB keyboard
    private static final byte[] HID_DESCRIPTOR = {
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

    // HID reports
    private static final byte[] REPORT_WIN_L_PRESS = {0x08, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00}; // Left GUI + L
    private static final byte[] REPORT_SPACE_PRESS = {0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x00}; // Space
    private static final byte[] REPORT_RELEASE = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};     // Key release

    // HID key codes
    private static final byte[] DIGIT_KEY_CODES = {(byte) 0x27, (byte) 0x1E, (byte) 0x1F, (byte) 0x20, (byte) 0x21, (byte) 0x22, (byte) 0x23, (byte) 0x24, (byte) 0x25, (byte) 0x26};
    private static final byte[] LETTER_KEY_CODES = {0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D};
    private static final char[] SYMBOLS = {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '=', '+', '[', ']', '\\', ';', '\'', ',', '.', '/', '`', '~', '_', '{', '}', '|', ':', '"', '<', '>', '?'};
    private static final byte[] SYMBOL_KEY_CODES = {0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38, 0x35, 0x23, 0x2D, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38};
    private static final boolean[] SYMBOL_REQUIRES_SHIFT = {true, true, true, true, true, true, true, true, true, true, false, false, true, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true};
    private static final byte SHIFT_MODIFIER = (byte) 0x02;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeEncryptedPrefs();
        initializeUiElements();
        initializeBluetoothAdapter();
        setupBluetoothEnableLauncher();
        requestBluetoothPermissions();

        connectToggleButton.setOnClickListener(v -> toggleConnection());
        sendKeyButton.setOnClickListener(v -> sendWinLCommand());
        unlockButton.setOnClickListener(v -> sendUnlockCommand());
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());
        spaceButton.setOnClickListener(v -> sendSpaceCommand());
        passwordButton.setOnClickListener(v -> sendPasswordCommand());
    }

    private void initializeEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            encryptedPrefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    this,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d(TAG, "EncryptedSharedPreferences initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage(), e);
            showToast("Failed to initialize secure storage");
            finish();
        }
    }

    private void initializeUiElements() {
        connectionStatusTextView = findViewById(R.id.status_text);
        connectToggleButton = findViewById(R.id.connect_button);
        sendKeyButton = findViewById(R.id.send_button);
        unlockButton = findViewById(R.id.unlock_button);
        changePasswordButton = findViewById(R.id.change_password_button);
        spaceButton = findViewById(R.id.space_button);
        passwordButton = findViewById(R.id.password_button);
        updateUiState(false);
    }

    private void initializeBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            showToast(R.string.bluetooth_not_supported);
            finish();
        }
    }

    private void setupBluetoothEnableLauncher() {
        bluetoothEnableLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user");
                enableBluetoothAndRegisterHid();
            } else {
                Log.w(TAG, "Bluetooth enable request denied");
                showToast(R.string.bluetooth_required);
                finish();
            }
        });
    }

    private void requestBluetoothPermissions() {
        String[] permissions = getRequiredPermissions();
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            Log.d(TAG, "All permissions already granted");
            enableBluetoothAndRegisterHid();
        }
    }

    private String[] getRequiredPermissions() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ?
                new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT} :
                new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canPerformBluetoothOperation() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission missing");
            showToast(R.string.bluetooth_permission_required);
            return false;
        }
        return true;
    }

    private String getDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null && !name.isEmpty() ? name : UNKNOWN_DEVICE_NAME;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to get device name: " + e.getMessage(), e);
            return UNKNOWN_DEVICE_NAME;
        }
    }

    private void enableBluetoothAndRegisterHid() {
        if (!canPerformBluetoothOperation()) return;
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth disabled, requesting enable");
            bluetoothEnableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            Log.d(TAG, "Bluetooth enabled, registering HID device");
            registerHidDevice();
        }
    }

    private void toggleConnection() {
        if (connectedDevice == null) {
            showDeviceSelectionDialog();
        } else {
            disconnectDevice();
        }
    }

    private void showDeviceSelectionDialog() {
        if (!canPerformBluetoothOperation()) return;
        if (!isHidServiceReady || hidDevice == null) {
            Log.w(TAG, "HID service not ready for device selection");
            showToast(R.string.hid_service_not_ready);
            registerHidDevice();
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (pairedDevices.isEmpty()) {
                    Log.w(TAG, "No paired devices found");
                    showToast(R.string.no_paired_devices);
                    return;
                }

                ArrayList<String> deviceNames = new ArrayList<>();
                ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
                for (BluetoothDevice device : pairedDevices) {
                    String name = getDeviceName(device);
                    deviceNames.add(name + " (" + device.getAddress() + ")");
                }

                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.select_device_title)
                        .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> connectToDevice(deviceList.get(which), 0))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setCancelable(true)
                        .show());
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to access paired devices: " + e.getMessage(), e);
                showToast(R.string.permission_denied_paired_devices);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in device selection: " + e.getMessage(), e);
                showToast(R.string.connection_failed_generic);
            }
        });
    }

    private void connectToDevice(BluetoothDevice device, int retryCount) {
        if (!canPerformBluetoothOperation()) return;
        String deviceName = getDeviceName(device);
        Log.d(TAG, "Connecting to " + deviceName + " (Retry " + retryCount + ")");
        showToast(getString(R.string.connecting_to, deviceName));
        connectToggleButton.setEnabled(false);

        if (!isHidServiceReady || hidDevice == null) {
            if (retryCount < MAX_CONNECT_RETRIES) {
                Log.w(TAG, "HID service not ready, retrying " + (retryCount + 1) + "/" + MAX_CONNECT_RETRIES);
                showToast("HID service not ready, retrying...");
                registerHidDevice();
                mainHandler.postDelayed(() -> connectToDevice(device, retryCount + 1), CONNECT_RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Max retries reached for HID service initialization");
                showToast("Failed to initialize HID service after retries");
                connectToggleButton.setEnabled(true);
            }
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                hidDevice.connect(device);
                Log.d(TAG, "Connection request sent to " + deviceName);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException connecting to " + deviceName + ": " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showToast(R.string.connection_failed_permission);
                    connectToggleButton.setEnabled(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error connecting to " + deviceName + ": " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showToast(R.string.connection_failed_generic);
                    connectToggleButton.setEnabled(true);
                });
            }
        });
    }

    private void disconnectDevice() {
        if (!canPerformBluetoothOperation() || hidDevice == null || connectedDevice == null) return;
        backgroundExecutor.execute(() -> {
            try {
                hidDevice.disconnect(connectedDevice);
                Log.d(TAG, "Disconnect request sent for " + getDeviceName(connectedDevice));
                showToast(R.string.disconnecting);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to disconnect: " + e.getMessage(), e);
                showToast(R.string.disconnect_failed_permission);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during disconnect: " + e.getMessage(), e);
                showToast(R.string.disconnect_failed_generic);
            }
        });
    }

    private void sendWinLCommand() {
        if (!canPerformBluetoothOperation() || !isConnected()) {
            showToast(R.string.not_connected);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                hidDevice.sendReport(connectedDevice, 0, REPORT_WIN_L_PRESS);
                Thread.sleep(50);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Log.d(TAG, "Win+L command sent successfully");
                showToast(R.string.win_l_sent);
                // Prompt for password if not set (assuming Win+L might precede an unlock)
                String storedPassword = encryptedPrefs.getString(KEY_PASSWORD, null);
                if (storedPassword == null) {
                    runOnUiThread(this::showPasswordInputDialog);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send Win+L: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_permission);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending Win+L", e);
                Thread.currentThread().interrupt();
                showToast(R.string.send_command_failed_generic);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending Win+L: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_generic);
            }
        });
    }

    private void sendUnlockCommand() {
        if (!canPerformBluetoothOperation() || !isConnected()) {
            showToast(R.string.not_connected);
            return;
        }
        String storedPassword = encryptedPrefs.getString(KEY_PASSWORD, null);
        if (storedPassword == null) {
            showPasswordInputDialog();
            return;
        }
        sendUnlockWithPassword(storedPassword);
    }

    private void sendUnlockWithPassword(String password) {
        backgroundExecutor.execute(() -> {
            try {
                // Send Space
                hidDevice.sendReport(connectedDevice, 0, REPORT_SPACE_PRESS);
                Thread.sleep(50);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Thread.sleep(400);

                // Send password
                for (char c : password.toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(20);
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(20);
                }
                Log.d(TAG, "Unlock command sent: Space + [password]");
                showToast("Unlocked with stored password");
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send unlock command: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_permission);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending unlock command", e);
                Thread.currentThread().interrupt();
                showToast(R.string.send_command_failed_generic);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending unlock command: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_generic);
            }
        });
    }

    private void sendSpaceCommand() {
        if (!canPerformBluetoothOperation() || !isConnected()) {
            showToast(R.string.not_connected);
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                hidDevice.sendReport(connectedDevice, 0, REPORT_SPACE_PRESS);
                Thread.sleep(50);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Log.d(TAG, "Space command sent successfully");
                showToast("Space sent");
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send Space: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_permission);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending Space", e);
                Thread.currentThread().interrupt();
                showToast(R.string.send_command_failed_generic);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending Space: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_generic);
            }
        });
    }

    private void sendPasswordCommand() {
        if (!canPerformBluetoothOperation() || !isConnected()) {
            showToast(R.string.not_connected);
            return;
        }
        String storedPassword = encryptedPrefs.getString(KEY_PASSWORD, null);
        if (storedPassword == null) {
            showPasswordInputDialog();
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                // Send password without initial space
                for (char c : storedPassword.toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(20);
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(20);
                }
                Log.d(TAG, "Password command sent successfully");
                showToast("Password sent");
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send password: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_permission);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while sending password", e);
                Thread.currentThread().interrupt();
                showToast(R.string.send_command_failed_generic);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending password: " + e.getMessage(), e);
                showToast(R.string.send_command_failed_generic);
            }
        });
    }

    private byte[] getHidReport(char c) {
        byte modifier = 0x00;
        byte keyCode;

        if (c >= '0' && c <= '9') {
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

    private void showPasswordInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Unlock Password");

        final EditText input = new EditText(this);
        input.setHint("Password");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (password.isEmpty()) {
                showToast("Password cannot be empty");
                return;
            }
            try {
                encryptedPrefs.edit().putString(KEY_PASSWORD, password).apply();
                Log.d(TAG, "Password saved securely");
                showToast("Password saved");
                if (isConnected()) {
                    sendUnlockWithPassword(password); // Send immediately if connected
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save password: " + e.getMessage(), e);
                showToast("Failed to save password");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.setCancelable(false);
        builder.show();
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Unlock Password");

        final EditText input = new EditText(this);
        input.setHint("New Password");
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newPassword = input.getText().toString().trim();
            if (newPassword.isEmpty()) {
                showToast("Password cannot be empty");
                return;
            }
            try {
                encryptedPrefs.edit().putString(KEY_PASSWORD, newPassword).apply();
                Log.d(TAG, "Password updated securely");
                showToast("Password updated");
            } catch (Exception e) {
                Log.e(TAG, "Failed to update password: " + e.getMessage(), e);
                showToast("Failed to update password");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.setCancelable(true);
        builder.show();
    }

    private void registerHidDevice() {
        if (!canPerformBluetoothOperation()) return;
        backgroundExecutor.execute(() -> {
            try {
                bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        if (profile != BluetoothProfile.HID_DEVICE) return;
                        hidDevice = (BluetoothHidDevice) proxy;
                        Log.d(TAG, "HID service connected");
                        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                                "BluetoothHIDKeyboard", "Virtual Keyboard", "xAI", (byte) 0x40, HID_DESCRIPTOR
                        );
                        try {
                            hidDevice.registerApp(sdp, null, null, new MainThreadExecutor(), new HidCallback());
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to register HID app: " + e.getMessage(), e);
                            showToast(R.string.hid_registration_failed_permission);
                        } catch (Exception e) {
                            Log.e(TAG, "Unexpected error registering HID app: " + e.getMessage(), e);
                            showToast(R.string.hid_registration_failed_generic);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            Log.w(TAG, "HID service disconnected");
                            hidDevice = null;
                            isHidServiceReady = false;
                            showToast(R.string.hid_service_disconnected);
                            updateUiState(false);
                        }
                    }
                }, BluetoothProfile.HID_DEVICE);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to initialize HID service: " + e.getMessage(), e);
                showToast(R.string.hid_service_init_failed_permission);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error initializing HID service: " + e.getMessage(), e);
                showToast(R.string.hid_service_init_failed_generic);
            }
        });
    }

    private boolean isConnected() {
        return connectedDevice != null && hidDevice != null && isHidServiceReady;
    }

    private void updateUiState(boolean isConnected) {
        runOnUiThread(() -> {
            if (isConnected && connectedDevice != null) {
                connectionStatusTextView.setText(getString(R.string.connected_to, getDeviceName(connectedDevice)));
                connectToggleButton.setText(R.string.disconnect);
                sendKeyButton.setEnabled(true);
                unlockButton.setEnabled(true);
                findViewById(R.id.space_button).setEnabled(true);
                findViewById(R.id.password_button).setEnabled(true);
                changePasswordButton.setEnabled(true);
            } else {
                connectionStatusTextView.setText(R.string.not_connected);
                connectToggleButton.setText(R.string.connect);
                sendKeyButton.setEnabled(false);
                unlockButton.setEnabled(false);
                findViewById(R.id.space_button).setEnabled(false);
                findViewById(R.id.password_button).setEnabled(false);
                changePasswordButton.setEnabled(true);
            }
            connectToggleButton.setEnabled(true);
        });
    }

    private class HidCallback extends BluetoothHidDevice.Callback {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            Log.d(TAG, "HID app status changed: registered=" + registered);
            isHidServiceReady = registered;
            showToast(registered ? R.string.hid_registered_success : R.string.hid_registration_failed);
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            String deviceName = getDeviceName(device);
            Log.d(TAG, "Connection state changed to " + state + " for " + deviceName);
            switch (state) {
                case BluetoothProfile.STATE_CONNECTED:
                    connectedDevice = device;
                    updateUiState(true);
                    showToast(getString(R.string.connected_to, deviceName));
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    connectedDevice = null;
                    updateUiState(false);
                    showToast(R.string.disconnected);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    connectionStatusTextView.setText(getString(R.string.connecting_to, deviceName));
                    break;
                case BluetoothProfile.STATE_DISCONNECTING:
                    connectionStatusTextView.setText(R.string.disconnecting);
                    break;
                default:
                    Log.w(TAG, "Unhandled connection state: " + state);
            }
        }
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable r) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS && grantResults.length > 0) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(TAG, "All permissions granted, proceeding");
                enableBluetoothAndRegisterHid();
            } else {
                Log.w(TAG, "Permissions denied, exiting");
                showToast(R.string.bluetooth_permissions_required);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidDevice != null && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            backgroundExecutor.execute(() -> {
                try {
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
    }

    private void showToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}