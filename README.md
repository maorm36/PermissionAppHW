# Permission Challenge App 🎯

An Android app that turns runtime permissions and device state into a fun real-world challenge.

To **win the challenge**, the user must satisfy **all 5 conditions at the same time**:

1. The device is connected to **Jabra Bluetooth earphones**
2. The device is **connected to a charger and actually charging**
3. The device is **physically located in Petach Tikva**
4. The device has a **contact named `Sarah`**
5. The user says the magic word into the microphone:  
   **`Supercalifragilisticexpialidocious`**

When all 5 conditions are true, the app navigates to the **Prize screen** 🎉

---

## 📋 Features

- ✅ Live status for each of the **5 challenge conditions**
- ✅ Detection of **Jabra Bluetooth** earphones
- ✅ Verification that the phone is **plugged in and charging**
- ✅ **Location check** to ensure the user is in **Petach Tikva**
- ✅ Contacts check for a contact named **Sarah**
- ✅ **Speech recognition** for the magic word
- ✅ App is locked to **portrait orientation** for simplicity
- ✅ Robust **permission handling**:
  - Explanation dialog when the user denies permissions
  - Special handling when the user denies twice or taps **“Don’t ask again”**
  - Redirect to **App Settings** so the user can enable permissions manually

---

## 🧩 Challenge Rules

The challenge is considered **won** only when **all** of the following are true:

1. **Bluetooth Condition**  
   A Jabra Bluetooth device is connected to the phone.

2. **Charging Condition**  
   The device is plugged into a charger **and** is reported as charging.

3. **Location Condition**  
   The device’s current location is inside the city of **Petach Tikva**.

4. **Contacts Condition**  
   The device’s Contacts app contains a contact with the **exact name** `Sarah`.

5. **Magic Word Condition**  
   The user taps the “speak” button and clearly says:  
   **`Supercalifragilisticexpialidocious`**

Once all conditions are satisfied at the same time, **MainActivity** automatically opens **PrizeActivity**.

---

## 🖼 Screens

### MainActivity

- Displays each of the **5 conditions** and whether it currently **passes or fails**
- Provides buttons to:
  - Trigger checks (Bluetooth, charging, location, contacts)
  - Start listening on the microphone for the magic word
- When all checks pass, it navigates to **PrizeActivity**

### PrizeActivity

- Simple **“You won / prize”** screen
- Pressing **Back** returns to `MainActivity`
- Orientation is locked to **portrait**

---

## 🔐 Permissions

The app may request the following runtime permissions (depending on Android version):

- **Location** – to verify the user is in **Petach Tikva**
- **Contacts** – to check for a contact named **Sarah**
- **Microphone / Audio Recording** – to listen for the magic word
- **Bluetooth** (e.g. `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, etc.) – to detect Jabra earphones

### Permission Flow & Edge Cases

- On first launch, the app requests the permissions that are required for the challenge.
- If the user **denies** permissions:
  - An **explanation dialog** appears:
    - Explains why the permissions are needed
    - Offers a **“Try again”** button to re-request permissions
- If the user **denies again** or taps **“Don’t ask again”**:
  - A second dialog appears:
    - Informs the user that some permissions are permanently denied
    - Explains that the challenge may not work without them
    - Provides a button to open **system App Settings** so the user can enable permissions manually
- If permissions are missing, the app **warns** the user, but the **business logic of the challenge remains unchanged**

---

## 🛠 Tech Stack

- **Language:** Kotlin  
- **Build System:** Gradle (with `gradlew` wrapper)  
- **Android Gradle Plugin (AGP):** 8.x  
- **Gradle Version:** 9.x  
- **Compile SDK:** **Android 15 (API 36)**  
- **Target SDK:** API 36  
- **Min SDK:** as configured in `build.gradle.kts` (e.g. 24)  

**Modules / Main Classes:**

- `MainActivity` – UI, challenge logic, and permission handling
- `PrizeActivity` – success / prize screen

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (latest stable recommended)
- **Android SDK** with **API 36** installed
- A real device is needed for:
  - Bluetooth tests (Jabra)
  - Real location (Petach Tikva)
  - Charging state

### Clone & Run

```bash
git clone <your-repo-url>.git

Made with ❤ by Maor Mordo
cd <your-project-folder>
./gradlew assembleDebug
