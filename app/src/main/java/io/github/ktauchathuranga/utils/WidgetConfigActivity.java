package io.github.ktauchathuranga.utils;

import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Set;

public class WidgetConfigActivity extends AppCompatActivity {

    private static final String TAG = "WidgetConfig";
    private static final String PREFS_NAME = "UtilsPrefs";
    private static final String KEY_WIDGET_DEVICE_ADDRESS = "widget_device_address_";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private EncryptedSharedPreferences encryptedPrefs;
    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Log.d(TAG, "WidgetConfigActivity started");
        setContentView(R.layout.activity_widget_config);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            Log.d(TAG, "Permission result: BLUETOOTH_CONNECT granted=" + isGranted);
            if (isGranted) {
                setupDeviceList();
            } else {
                showToast(R.string.bluetooth_permission_required);
                finish();
            }
        });

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        Log.d(TAG, "Received widget ID: " + appWidgetId);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid widget ID");
            showToast("Invalid widget ID");
            finish();
            return;
        }

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
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage(), e);
            showToast("Failed to initialize secure storage");
            finish();
            return;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            showToast(R.string.bluetooth_not_supported);
            finish();
            return;
        }
        Log.d(TAG, "Bluetooth adapter initialized");

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            setupDeviceList();
        } else {
            Log.d(TAG, "Requesting BLUETOOTH_CONNECT permission");
            permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

    private void setupDeviceList() {
        Log.d(TAG, "Setting up device list for widget ID: " + appWidgetId);
        Spinner deviceSpinner = findViewById(R.id.device_spinner);
        Button saveButton = findViewById(R.id.save_button);
        Button cancelButton = findViewById(R.id.cancel_button);

        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.isEmpty()) {
                Log.w(TAG, "No paired devices found");
                showToast(R.string.no_paired_devices);
                finish();
                return;
            }
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName() != null ? device.getName() : "Unknown Device";
                deviceNames.add(name + " (" + device.getAddress() + ")");
                deviceList.add(device);
            }
            Log.d(TAG, "Found " + deviceList.size() + " paired devices");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException accessing paired devices: " + e.getMessage(), e);
            showToast(R.string.bluetooth_permission_required);
            finish();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> {
            int position = deviceSpinner.getSelectedItemPosition();
            Log.d(TAG, "Save button clicked, selected position: " + position);
            if (position >= 0 && position < deviceList.size()) {
                BluetoothDevice selectedDevice = deviceList.get(position);
                try {
                    String key = KEY_WIDGET_DEVICE_ADDRESS + appWidgetId;
                    encryptedPrefs.edit().putString(key, selectedDevice.getAddress()).apply();
                    Log.d(TAG, "Saved device address: " + selectedDevice.getAddress() + " for widget ID: " + appWidgetId);

                    Intent resultValue = new Intent();
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    setResult(RESULT_OK, resultValue);
                    Log.d(TAG, "Returning RESULT_OK for widget ID: " + appWidgetId);

                    // Start BluetoothHidService to initialize HID
                    startService(new Intent(this, BluetoothHidService.class));

                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save device address: " + e.getMessage(), e);
                    showToast("Failed to save device");
                }
            } else {
                Log.w(TAG, "No device selected");
                showToast("Please select a device");
            }
        });

        cancelButton.setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked");
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WidgetConfigActivity destroyed for widget ID: " + appWidgetId);
    }
}