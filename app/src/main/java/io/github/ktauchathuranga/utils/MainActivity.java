package io.github.ktauchathuranga.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Controls a Bluetooth HID keyboard to send Win+L or unlock with Space + predefined text to a paired device.
 * Requires API 28+ due to BluetoothHidDevice usage.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Utils";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final String UNKNOWN_DEVICE_NAME = "Unknown Device";
    private static final String PREDEFINED_TEXT = "heh hee"; // dont look ane

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private TextView connectionStatusTextView;
    private Button connectToggleButton;
    private Button sendKeyButton;
    private Button unlockButton;
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher;

    // HID descriptor for a keyboard (standard USB HID keyboard report descriptor)
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

    // HID report for Win+L press: Left GUI (0x08) + L (0x0F)
    private static final byte[] REPORT_WIN_L_PRESS = {
            (byte) 0x08, (byte) 0x00, (byte) 0x0F, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    // HID report for Space press: Space key (0x2C)
    private static final byte[] REPORT_SPACE_PRESS = {
            (byte) 0x00, (byte) 0x00, (byte) 0x2C, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    // HID report for key release
    private static final byte[] REPORT_RELEASE = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    // HID key codes for digits (0-9)
    private static final byte[] DIGIT_KEY_CODES = {
            (byte) 0x27, // 0
            (byte) 0x1E, // 1
            (byte) 0x1F, // 2
            (byte) 0x20, // 3
            (byte) 0x21, // 4
            (byte) 0x22, // 5
            (byte) 0x23, // 6
            (byte) 0x24, // 7
            (byte) 0x25, // 8
            (byte) 0x26  // 9
    };

    // HID key codes for lowercase letters (a-z: 0x04-0x1D)
    private static final byte[] LETTER_KEY_CODES = {
            0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D
    };

    // Common symbols and their HID codes (with Shift modifier where needed)
    private static final char[] SYMBOLS = {
            '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '=', '+', '[', ']', '\\',
            ';', '\'', ',', '.', '/', '`', '~', '_', '{', '}', '|', ':', '"', '<', '>', '?'
    };
    private static final byte[] SYMBOL_KEY_CODES = {
            0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2E, 0x2F, 0x30, 0x31,
            0x33, 0x34, 0x36, 0x37, 0x38, 0x35, 0x23, 0x2D, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38
    };
    private static final boolean[] SYMBOL_REQUIRES_SHIFT = {
            true, true, true, true, true, true, true, true, true, true, false, false, true, false, false, false,
            false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true
    };

    // Modifier key for Shift (Left Shift: 0x02)
    private static final byte SHIFT_MODIFIER = (byte) 0x02;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUiElements();
        initializeBluetoothAdapter();
        setupBluetoothEnableLauncher();
        requestBluetoothPermissions();

        connectToggleButton.setOnClickListener(v -> toggleConnection());
        sendKeyButton.setOnClickListener(v -> sendWinLCommand());
        unlockButton.setOnClickListener(v -> sendUnlockCommand());
    }

    private void initializeUiElements() {
        connectionStatusTextView = findViewById(R.id.status_text);
        connectToggleButton = findViewById(R.id.connect_button);
        sendKeyButton = findViewById(R.id.send_button);
        unlockButton = findViewById(R.id.unlock_button);
    }

    private void initializeBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showToast(R.string.bluetooth_not_supported);
            finish();
        }
    }

    private void setupBluetoothEnableLauncher() {
        bluetoothEnableLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                enableBluetoothAndRegisterHid();
            } else {
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
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
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
            showToast(R.string.bluetooth_permission_required);
            return false;
        }
        return true;
    }

    private String getDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null ? name : UNKNOWN_DEVICE_NAME;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to get device name", e);
            return UNKNOWN_DEVICE_NAME;
        }
    }

    private void enableBluetoothAndRegisterHid() {
        if (!canPerformBluetoothOperation()) return;
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothEnableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
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
        if (hidDevice == null) {
            showToast(R.string.hid_service_not_ready);
            return;
        }
        if (!canPerformBluetoothOperation()) return;

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.isEmpty()) {
                showToast(R.string.no_paired_devices);
                return;
            }

            ArrayList<String> deviceNames = new ArrayList<>();
            ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
            for (BluetoothDevice device : pairedDevices) {
                String name = getDeviceName(device);
                deviceNames.add(name + " (" + device.getAddress() + ")");
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_device_title)
                    .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> connectToDevice(deviceList.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to access paired devices", e);
            showToast(R.string.permission_denied_paired_devices);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (!canPerformBluetoothOperation()) return;
        String deviceName = getDeviceName(device);
        showToast(getString(R.string.connecting_to, deviceName));
        connectToggleButton.setEnabled(false);
        try {
            hidDevice.connect(device);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to connect to " + deviceName, e);
            showToast(R.string.connection_failed_permission);
            connectToggleButton.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error connecting to " + deviceName, e);
            showToast(R.string.connection_failed_generic);
            connectToggleButton.setEnabled(true);
        }
    }

    private void disconnectDevice() {
        if (!canPerformBluetoothOperation()) return;
        if (hidDevice != null && connectedDevice != null) {
            try {
                hidDevice.disconnect(connectedDevice);
                showToast(R.string.disconnecting);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to disconnect", e);
                showToast(R.string.disconnect_failed_permission);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during disconnect", e);
                showToast(R.string.disconnect_failed_generic);
            }
        }
    }

    private void sendWinLCommand() {
        if (!canPerformBluetoothOperation()) return;
        if (connectedDevice != null && hidDevice != null) {
            try {
                hidDevice.sendReport(connectedDevice, 0, REPORT_WIN_L_PRESS);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                showToast(R.string.win_l_sent);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send Win+L command", e);
                showToast(R.string.send_command_failed_permission);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending Win+L", e);
                showToast(R.string.send_command_failed_generic);
            }
        } else {
            showToast(R.string.not_connected);
        }
    }

    private void sendUnlockCommand() {
        if (!canPerformBluetoothOperation()) return;
        if (connectedDevice != null && hidDevice != null) {
            try {
                // Press and release Space key
                hidDevice.sendReport(connectedDevice, 0, REPORT_SPACE_PRESS);
                Thread.sleep(50); // Small delay to simulate key press
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Thread.sleep(400);

                // Type predefined text (e.g., "Pass123!")
                for (char c : PREDEFINED_TEXT.toCharArray()) {
                    byte[] report = getHidReport(c);
                    hidDevice.sendReport(connectedDevice, 0, report);
                    Thread.sleep(20); // Simulate typing speed
                    hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                    Thread.sleep(20);
                }
                showToast("Unlocked with Space + " + PREDEFINED_TEXT);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to send Unlock command", e);
                showToast(R.string.send_command_failed_permission);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while sending Unlock command", e);
                Thread.currentThread().interrupt();
                showToast(R.string.send_command_failed_generic);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending Unlock command", e);
                showToast(R.string.send_command_failed_generic);
            }
        } else {
            showToast(R.string.not_connected);
        }
    }

    private byte[] getHidReport(char c) {
        byte modifier = 0x00;
        byte keyCode;

        if (c >= '0' && c <= '9') {
            keyCode = DIGIT_KEY_CODES[c - '0'];
        } else if (c >= 'a' && c <= 'z') {
            keyCode = LETTER_KEY_CODES[c - 'a'];
        } else if (c >= 'A' && c <= 'Z') {
            modifier = SHIFT_MODIFIER; // Use Shift for uppercase
            keyCode = LETTER_KEY_CODES[c - 'A'];
        } else {
            // Check symbols
            for (int i = 0; i < SYMBOLS.length; i++) {
                if (c == SYMBOLS[i]) {
                    keyCode = SYMBOL_KEY_CODES[i];
                    if (SYMBOL_REQUIRES_SHIFT[i]) {
                        modifier = SHIFT_MODIFIER;
                    }
                    return new byte[] {modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
                }
            }
            throw new IllegalArgumentException("Unsupported character: " + c);
        }
        return new byte[] {modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    private void registerHidDevice() {
        if (!canPerformBluetoothOperation()) return;
        try {
            bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile != BluetoothProfile.HID_DEVICE) return;
                    hidDevice = (BluetoothHidDevice) proxy;
                    BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                            "My Keyboard", "Virtual Keyboard", "MyCompany", (byte) 0x40, HID_DESCRIPTOR
                    );
                    try {
                        hidDevice.registerApp(sdp, null, null, new MainThreadExecutor(), new HidCallback());
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to register HID app", e);
                        showToast(R.string.hid_registration_failed_permission);
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error registering HID app", e);
                        showToast(R.string.hid_registration_failed_generic);
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null;
                        showToast(R.string.hid_service_disconnected);
                    }
                }
            }, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to initialize HID service", e);
            showToast(R.string.hid_service_init_failed_permission);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing HID service", e);
            showToast(R.string.hid_service_init_failed_generic);
        }
    }

    private class HidCallback extends BluetoothHidDevice.Callback {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            showToast(registered ? R.string.hid_registered_success : R.string.hid_registration_failed);
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            String deviceName = device != null ? getDeviceName(device) : "device";
            Log.d(TAG, "Connection state changed to " + state + " for " + deviceName);
            switch (state) {
                case BluetoothProfile.STATE_CONNECTED:
                    connectedDevice = device;
                    connectionStatusTextView.setText(getString(R.string.connected_to, deviceName));
                    connectToggleButton.setText(R.string.disconnect);
                    connectToggleButton.setEnabled(true);
                    sendKeyButton.setEnabled(true);
                    unlockButton.setEnabled(true); // Enable Unlock button when connected
                    showToast(getString(R.string.connected_to, deviceName));
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    connectedDevice = null;
                    connectionStatusTextView.setText(R.string.not_connected);
                    connectToggleButton.setText(R.string.connect);
                    connectToggleButton.setEnabled(true);
                    sendKeyButton.setEnabled(false);
                    unlockButton.setEnabled(false); // Disable Unlock button when disconnected
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
                enableBluetoothAndRegisterHid();
            } else {
                showToast(R.string.bluetooth_permissions_required);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidDevice != null && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            try {
                hidDevice.unregisterApp();
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to unregister HID app", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during cleanup", e);
            }
        }
    }

    private void showToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}
