# Utils

A Bluetooth HID (Human Interface Device) keyboard emulator for Android, designed to lock or unlock a paired PC remotely. The Utils app sends the `Win + L` command to lock your Windows PC or types a user-defined password (stored securely) to unlock it via Bluetooth.

## Features
- **Lock PC**: Sends `Win + L` to lock your Windows PC.
- **Unlock PC**: Sends `Space` followed by a user-defined password to unlock your PC.
- **Secure Password Storage**: Uses `EncryptedSharedPreferences` with AES256 encryption to store the unlock password securely on your Android device.
- **Password Management**: Allows setting a new password or updating it via a "Change Password" option.
- **Bluetooth HID**: Emulates a keyboard over Bluetooth using the Android `BluetoothHidDevice` API (requires API 28+).
- **User-Friendly UI**: Simple interface with connect/disconnect, lock, unlock, and password change buttons.

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
2. **Connect to PC**:
   - Click "Connect" and select your paired PC from the list.
   - Wait for the "Connected to [PC Name]" status.
3. **Lock PC**:
   - Click "Lock (Win+L)" to send `Win + L` to your PC.
   - If no password is set, you’ll be prompted to enter one (stored securely for future unlocks).
4. **Unlock PC**:
   - Click "Unlock" to send `Space` followed by your stored password.
   - If no password is set, enter one when prompted.
5. **Change Password**:
   - Click "Change Password" to update your unlock password at any time.

## Project Structure
- **`app/src/main/java/io/github/ktauchathuranga/utils/MainActivity.java`**:
  - Main logic for Bluetooth HID, UI handling, and encrypted password management.
- **`app/src/main/res/layout/activity_main.xml`**:
  - UI layout with buttons for connect, lock, unlock, and password change.
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
    Verify `androidx.security:security-crypto:1.0.0` is included.

## Troubleshooting
- **Connection Fails**:
  - Ensure your PC is paired and Bluetooth is enabled.
  - Retry connection if HID service isn’t ready (app retries up to 3 times).
- **Password Not Working**:
  - Verify the password matches your PC’s unlock password.
  - Update via "Change Password" if needed.
- **Build Errors**:
  - If errors persist:
    - Check `build.gradle` for `implementation(libs.security.crypto)`.
    - Verify `libs.versions.toml` has `security-crypto`.
    - Run `gradlew cleanBuildCache build --refresh-dependencies`.
    - Invalidate caches in Android Studio (`File > Invalidate Caches / Restart`).

## Security
- **Password Storage**: Encrypted with AES256-GCM using `EncryptedSharedPreferences` and a `MasterKey` from Android’s KeyStore.
- **Best Practices**: Avoid hardcoding passwords; the app prompts for input and stores it securely.

## Contributing
- Fork the repo, make changes, and submit a pull request.
- Report issues or suggest features via GitHub Issues.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgement
- Icon: <a href="https://www.flaticon.com/free-icons/remote-support" title="remote support icons">Remote support icons created by Freepik - Flaticon</a>
