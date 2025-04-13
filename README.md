# Utils

A Bluetooth HID (Human Interface Device) keyboard emulator for Android, designed to control a paired Windows PC remotely. The Utils app sends the `Win + L` command to lock your PC, types a user-defined password to unlock it, wakes the PC, syncs clipboard content, sends custom text with an optional `Enter` key, or executes power commands (shutdown, restart, sleep, hibernate) via Bluetooth.

## Features
- **Lock PC**: Sends `Win + L` to lock your Windows PC.
- **Unlock PC**: Sends `Space` followed by a user-defined password to unlock your PC.
- **Wake PC**: Sends `Space` to wake your PC from sleep or lock screen.
- **Send Password**: Sends only the stored password without additional keys (useful for specific unlock scenarios).
- **Clipboard Sync**: Sends text (e.g., Zoom links, URLs, or any text) entered in a text field to your PC, making it easy to share content.
- **Text + Enter**: Sends user-entered text from a text field to the PC, appending an `Enter` key at the end. If the text field is empty, it sends only the `Enter` key.
- **Power Commands**:
    - **Shutdown**: Executes `shutdown /s /f /t 0` to shut down the PC.
    - **Restart**: Executes `shutdown /r /f /t 0` to restart the PC.

[//]: # (    - **Sleep**: Executes `rundll32.exe powrprof.dll,SetSuspendState 0,1,0` to put the PC to sleep.)

[//]: # (    - **Hibernate**: Executes `shutdown /h` to hibernate the PC.)
- **Secure Password Storage**: Uses `EncryptedSharedPreferences` with AES256 encryption to store the unlock password securely on your Android device.
- **Password Management**: Allows setting a new password or updating it via a "Change Password" option.
- **Speed Profiles**: Prompts for a typing speed (Slow, Medium, Fast) on the first multi-key command after connecting, then uses that speed until disconnected.
- **Bluetooth HID**: Emulates a keyboard over Bluetooth using the Android `BluetoothHidDevice` API (requires API 28+).
- **User-Friendly UI**: Simple interface with buttons for connect/disconnect, lock, unlock, wake, send password, clipboard sync, text + enter, power commands, and password change, plus a text field for text input.

## Requirements
- **Android Device**: Running Android 9.0 (API 28) or higher.
- **PC**: Windows PC with Bluetooth support (tested on Windows 10/11).
- **Permissions**: Bluetooth permissions (`BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`).

## Setup

### Prerequisites
- Android Studio (2023.1.1 or later recommended).
- Gradle 8.9 with Android Gradle Plugin (AGP) 8.9.0.
- A Bluetooth-enabled Android device and PC.

### Installation
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/ktauchathuranga/utils-android-app.git
   cd utils-android-app
   ```
2. **Open in Android Studio**:
    - Open the project in Android Studio.
    - Sync the project with Gradle files (`File > Sync Project with Gradle Files`).

3. **Build and Run**:
    - Connect your Android device via USB.
    - Build and run the app (`Run > Run 'app'`).

### Pairing Your Devices
1. **On Your PC**:
    - Go to **Settings > Bluetooth & devices > Add a device**.
    - Make your PC discoverable and pair it with your Android device.
    - Select "Bluetooth" and wait for your Android device to appear, then pair it.

2. **On Your Android Device**:
    - Open the Bluetooth settings.
    - Scan for devices, find your PC, and pair it.
    - Ensure pairing is successful (you may need to accept a prompt on both devices).

## Usage
1. **Launch the App**:
    - Grant Bluetooth permissions when prompted.

2. **Connecting for the First Time**:
    - If connecting normally doesn’t work, follow these steps:
        1. Pair your Android device and PC via Bluetooth settings on both devices as described in **Pairing Your Devices**.
        2. On your PC, remove your Android device from the list of paired devices (**Settings > Bluetooth & devices > Devices**, select your device, and click "Remove device").
        3. Open the Utils app on your Android device.
        4. Click "Connect" and select your PC from the list of paired devices.
        5. When prompted to pair, accept the pairing request on both devices.
        6. The app should now connect successfully, showing "Connected to [PC Name]".
    - After the first successful connection, subsequent connections should work normally by clicking "Connect" in the app.

3. **Connect to PC**:
    - Click "Connect" and select your paired PC from the list.
    - Wait for the "Connected to [PC Name]" status.
    - On the first multi-key command (e.g., unlock, clipboard sync, power commands), choose a typing speed (Slow, Medium, Fast). This speed will be used for all commands until you disconnect.

4. **Lock PC**:
    - Click "Lock (Win+L)" to send `Win + L` to your PC.
    - If no password is set, you’ll be prompted to enter one (stored securely for future unlocks).

5. **Unlock PC**:
    - Click "Unlock" to send `Space` followed by your stored password.
    - If no password is set, enter one when prompted.

6. **Wake PC**:
    - Click "Wake" to send `Space` and wake your PC from sleep or lock screen.

7. **Send Password**:
    - Click "Send Password" to send only the stored password (useful for manual unlock scenarios).

8. **Clipboard Sync**:
    - Enter text (e.g., a Zoom link, URL, or note) in the text field.
    - Click "Send Text" to type the text on your PC.

9. **Text + Enter**:
    - Enter text in the provided text field (e.g., a command or message).
    - Click "Text + Enter" to send the text followed by an `Enter` key to your PC.
    - If the text field is empty, clicking "Text + Enter" sends only the `Enter` key.

10. **Power Commands**:
    - Click "Shutdown" to shut down your PC.
    - Click "Restart" to restart your PC.
    - Click "Sleep" to put your PC to sleep (if enabled).
    - Click "Hibernate" to hibernate your PC (if enabled).

11. **Change Password**:
    - Click "Change Password" to update your unlock password at any time.

## Project Structure
- **`app/src/main/java/io/github/ktauchathuranga/utils/MainActivity.java`**:
    - Main logic for Bluetooth HID, UI handling, encrypted password management, clipboard sync, text + enter, power commands, and speed profile management.
- **`app/src/main/res/layout/activity_main.xml`**:
    - UI layout with buttons for connect, lock, unlock, wake, send password, clipboard sync, text + enter, power commands, and password change, plus a text field for text input.
- **`app/build.gradle`**:
    - Dependencies and build configuration.
- **`gradle/libs.versions.toml`**:
    - Version catalog for managing library versions.

## Dependencies
- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.activity:activity:1.10.1`
- `androidx.constraintlayout:constraintlayout:2.2.1`
- `androidx.security:security-crypto:1.0.0` (for encrypted storage)
- Test dependencies: `junit`, `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`

## Building the Project
- **Gradle Configuration**:
    - `compileSdk`: 35
    - `minSdk`: 28
    - `targetSdk`: 35
    - Java 11 compatibility
- **Sync Issues**:
    - If build errors occur, ensure all dependencies are resolved:
      ```bash
      gradlew app:dependencies
      ```
        - Verify `androidx.security:security-crypto:1.0.0` is included.

## Troubleshooting
- **Connection Fails**:
    - Ensure your PC is paired and Bluetooth is enabled.
    - If connection issues persist, try the first-time connection steps in **Usage > Connecting for the First Time**:
        - Pair devices, remove your phone from the PC’s Bluetooth list, then reconnect via the app and accept the pairing prompt.
    - Retry connection if HID service isn’t ready (app retries up to 3 times).
- **Password Not Working**:
    - Verify the password matches your PC’s unlock password.
    - Update via "Change Password" if needed.
- **Clipboard Sync Issues**:
    - Ensure text is entered in the text field before clicking "Send Text."
    - Verify Bluetooth connection is active.
- **Text + Enter Issues**:
    - Ensure the Bluetooth connection is active before sending text.
    - If no text is sent, verify the text field content or check if the `Enter` key was sent when the field was empty.
- **Power Commands Not Working**:
    - Ensure the PC is connected and the correct speed profile is selected.
    - Verify the PC supports the command (e.g., sleep or hibernate may be disabled).
- **Build Errors**:
    - If errors persist:
        - Check `build.gradle` for `implementation(libs.security.crypto)`.
        - Verify `libs.versions.toml` has `security-crypto`.
        - Run `gradlew cleanBuildCache build --refresh-dependencies`.
        - Invalidate caches in Android Studio (`File > Invalidate Caches / Restart`).

## Security
- **Password Storage**: Encrypted with AES256-GCM using `EncryptedSharedPreferences` and a `MasterKey` from Android’s KeyStore.
- **Clipboard Sync**: No sensitive data is stored; only the entered text is sent over Bluetooth and cleared after transmission.
- **Text + Enter**: Text entered in the field is not stored; it’s sent directly over Bluetooth and cleared after transmission.
- **Best Practices**: Avoid hardcoding passwords; the app prompts for input and stores it securely.

## Contributing
- Fork the repo, make changes, and submit a pull request.
- Report issues or suggest features via GitHub Issues.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgement
- Icon: <a href="https://www.flaticon.com/free-icons/remote-support" title="remote support icons">Remote support icons created by Freepik - Flaticon</a>
