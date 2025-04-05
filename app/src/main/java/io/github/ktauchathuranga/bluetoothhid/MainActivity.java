package io.github.ktauchathuranga.bluetoothhid;

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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private TextView statusText;
    private Button connectButton;
    private Button sendButton;

    // HID descriptor for a keyboard
    private static final byte[] HID_DESCRIPTOR = {
            (byte)0x05, (byte)0x01, // USAGE_PAGE (Generic Desktop)
            (byte)0x09, (byte)0x06, // USAGE (Keyboard)
            (byte)0xa1, (byte)0x01, // COLLECTION (Application)
            (byte)0x05, (byte)0x07, // USAGE_PAGE (Keyboard)
            (byte)0x19, (byte)0xe0, // USAGE_MINIMUM (Keyboard LeftControl)
            (byte)0x29, (byte)0xe7, // USAGE_MAXIMUM (Keyboard Right GUI)
            (byte)0x15, (byte)0x00, // LOGICAL_MINIMUM (0)
            (byte)0x25, (byte)0x01, // LOGICAL_MAXIMUM (1)
            (byte)0x75, (byte)0x01, // REPORT_SIZE (1)
            (byte)0x95, (byte)0x08, // REPORT_COUNT (8)
            (byte)0x81, (byte)0x02, // INPUT (Data,Var,Abs) - Modifier byte
            (byte)0x95, (byte)0x01, // REPORT_COUNT (1)
            (byte)0x75, (byte)0x08, // REPORT_SIZE (8)
            (byte)0x81, (byte)0x01, // INPUT (Cnst,Ary,Abs) - Reserved byte
            (byte)0x95, (byte)0x05, // REPORT_COUNT (5)
            (byte)0x75, (byte)0x01, // REPORT_SIZE (1)
            (byte)0x05, (byte)0x08, // USAGE_PAGE (LEDs)
            (byte)0x19, (byte)0x01, // USAGE_MINIMUM (Num Lock)
            (byte)0x29, (byte)0x05, // USAGE_MAXIMUM (Kana)
            (byte)0x91, (byte)0x02, // OUTPUT (Data,Var,Abs) - LED report
            (byte)0x95, (byte)0x01, // REPORT_COUNT (1)
            (byte)0x75, (byte)0x03, // REPORT_SIZE (3)
            (byte)0x91, (byte)0x01, // OUTPUT (Cnst,Ary,Abs) - LED padding
            (byte)0x95, (byte)0x06, // REPORT_COUNT (6)
            (byte)0x75, (byte)0x08, //REPORT_SIZE (8)
            (byte)0x15, (byte)0x00, // LOGICAL_MINIMUM (0)
            (byte)0x25, (byte)0x65, // LOGICAL_MAXIMUM (101)
            (byte)0x05, (byte)0x07, // USAGE_PAGE (Keyboard)
            (byte)0x19, (byte)0x00, // USAGE_MINIMUM (Reserved)
            (byte)0x29, (byte)0x65, // USAGE_MAXIMUM (Keyboard Application)
            (byte)0x81, (byte)0x00, // INPUT (Data,Ary,Abs) - Key array
            (byte)0xc0              // END_COLLECTION
    };

    // HID report for Win+L press: Left GUI (0x08) + L (0x0F)
    private static final byte[] REPORT_PRESS = {
            (byte)0x08, (byte)0x00, (byte)0x0F, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
    };

    // HID report for key release
    private static final byte[] REPORT_RELEASE = {
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        statusText = findViewById(R.id.status_text);
        connectButton = findViewById(R.id.connect_button);
        sendButton = findViewById(R.id.send_button);

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Enable Bluetooth if not already enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkPermissionsAndRegisterHid();
        }

        // Connect/Disconnect button logic
        connectButton.setOnClickListener(v -> {
            if (connectedDevice == null) {
                showDeviceSelectionDialog();
            } else {
                if (hidDevice != null) {
                    hidDevice.disconnect(connectedDevice);
                    Toast.makeText(this, "Disconnecting", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Send Win+L button logic
        sendButton.setOnClickListener(v -> {
            if (connectedDevice != null && hidDevice != null) {
                hidDevice.sendReport(connectedDevice, 0, REPORT_PRESS);
                hidDevice.sendReport(connectedDevice, 0, REPORT_RELEASE);
                Toast.makeText(this, "Win+L sent", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Not connected to a device", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeviceSelectionDialog() {
        if (hidDevice == null) {
            Toast.makeText(this, "HID service not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired devices found. Pair a device first.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);

        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName() + " (" + device.getAddress() + ")");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a Device");
        builder.setItems(deviceNames.toArray(new String[0]), (dialog, which) -> {
            BluetoothDevice selectedDevice = deviceList.get(which);
            Toast.makeText(this, "Connecting to " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            hidDevice.connect(selectedDevice);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void checkPermissionsAndRegisterHid() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            registerHidDevice();
        }
    }

    private void registerHidDevice() {
        bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = (BluetoothHidDevice) proxy;
                    BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                            "My Keyboard", "Virtual Keyboard", "MyCompany", (byte)0x40, HID_DESCRIPTOR
                    );
                    hidDevice.registerApp(sdp, null, null, new MainThreadExecutor(), new BluetoothHidDevice.Callback() {
                        @Override
                        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                            runOnUiThread(() -> {
                                if (registered) {
                                    Toast.makeText(MainActivity.this, "HID registered successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "HID registration failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onConnectionStateChanged(BluetoothDevice device, int state) {
                            runOnUiThread(() -> {
                                switch (state) {
                                    case BluetoothProfile.STATE_CONNECTED:
                                        connectedDevice = device;
                                        statusText.setText("Connected to " + device.getName());
                                        connectButton.setText("Disconnect");
                                        sendButton.setEnabled(true);
                                        Toast.makeText(MainActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                                        break;
                                    case BluetoothProfile.STATE_DISCONNECTED:
                                        connectedDevice = null;
                                        statusText.setText("Not connected");
                                        connectButton.setText("Connect");
                                        sendButton.setEnabled(false);
                                        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                                        break;
                                    case BluetoothProfile.STATE_CONNECTING:
                                        statusText.setText("Connecting...");
                                        break;
                                    case BluetoothProfile.STATE_DISCONNECTING:
                                        statusText.setText("Disconnecting...");
                                        break;
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null;
                }
            }
        }, BluetoothProfile.HID_DEVICE);
    }

    // Executor to run callbacks on the main thread
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
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            registerHidDevice();
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            checkPermissionsAndRegisterHid();
        } else {
            Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidDevice != null) {
            hidDevice.unregisterApp();
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
        }
    }
}