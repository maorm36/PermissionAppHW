package com.example.permissionapphw

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvChallengeStatus: TextView
    private lateinit var tvCondition1: TextView
    private lateinit var tvCondition2: TextView
    private lateinit var tvCondition3: TextView
    private lateinit var tvCondition4: TextView
    private lateinit var tvCondition5: TextView

    private lateinit var btnCheckAll: Button
    private lateinit var btnSpeak: Button

    // Track condition state
    private var hasJabra = false
    private var isCharging = false
    private var inPetahTikva = false
    private var hasSarahContact = false
    private var saidMagicWord = false

    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val REQUEST_PERMISSIONS = 123

        // Rough bounding box around Petah Tikva (based on ~32.0833N, 34.8833E) :contentReference[oaicite:1]{index=1}
        private const val PTA_LAT_MIN = 32.05
        private const val PTA_LAT_MAX = 32.12
        private const val PTA_LON_MIN = 34.85
        private const val PTA_LON_MAX = 34.92
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvChallengeStatus = findViewById(R.id.tvChallengeStatus)
        tvCondition1 = findViewById(R.id.tvCondition1)
        tvCondition2 = findViewById(R.id.tvCondition2)
        tvCondition3 = findViewById(R.id.tvCondition3)
        tvCondition4 = findViewById(R.id.tvCondition4)
        tvCondition5 = findViewById(R.id.tvCondition5)

        btnCheckAll = findViewById(R.id.btnCheckAll)
        btnSpeak = findViewById(R.id.btnSpeak)

        // Speech recognizer result handler
        speechLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val textResults =
                        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?: arrayListOf()
                    val phrase = "supercalifragilisticexpialidocious"
                    saidMagicWord = textResults.any {
                        it.contains(phrase, ignoreCase = true)
                    }
                    tvCondition5.text = "5. Magic word: heard a word"
                } else {
                    saidMagicWord = false
                    tvCondition5.text = "5. Magic word: cancelled / error"
                    updateOverallStatus()
                }
            }

        // Ask for runtime permissions on first launch
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions(),
                REQUEST_PERMISSIONS
            )
        }

        btnCheckAll.setOnClickListener {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Grant permissions first", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    requiredPermissions(),
                    REQUEST_PERMISSIONS
                )
            } else {
                checkAllConditions()
            }
        }

        btnSpeak.setOnClickListener {
            if (!hasAudioPermission()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_PERMISSIONS
                )
            } else {
                startListeningForMagicWord()
            }
        }
    }

    // ----- Permissions helpers -----

    private fun requiredPermissions(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base += Manifest.permission.BLUETOOTH_CONNECT
        }
        return base.toTypedArray()
    }

    private fun allPermissionsGranted(): Boolean =
        requiredPermissions().all { perm ->
            ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasContactsPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // no runtime permission before Android 12
        }

    private fun hasAudioPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return
        if (permissions.isEmpty()) return

        val denied = mutableListOf<String>()
        val granted = mutableListOf<String>()

        permissions.forEachIndexed { index, perm ->
            val result = grantResults.getOrNull(index) ?: PackageManager.PERMISSION_DENIED
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted += perm
            } else {
                denied += perm
            }
        }

        if (denied.isEmpty()) {
            Toast.makeText(
                this,
                "All permissions granted, you can play the challenge!",
                Toast.LENGTH_SHORT
            ).show()
            // Business logic stays the same: user can now press the buttons as before
            return
        }

        // Some permissions are still denied
        val permanentlyDenied = denied.filterNot { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
        }

        if (permanentlyDenied.isNotEmpty()) {
            // User either denied twice or checked "Don't ask again"
            showPermissionSettingsDialog()
        } else {
            // Normal denial (first time): show explanation + option to retry
            showPermissionRationaleDialog()
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions needed")
            .setMessage(
                "This app needs Location, Contacts, Microphone and Bluetooth permissions " +
                        "to check all 5 challenge conditions. Without them, some checks cannot run."
            )
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ ->
                // Ask again only for the required permissions
                ActivityCompat.requestPermissions(
                    this,
                    requiredPermissions(),
                    REQUEST_PERMISSIONS
                )
            }
            .setNegativeButton("No thanks") { _, _ ->
                Toast.makeText(
                    this,
                    "You can enable permissions later from Settings\n but the challenge will not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable permissions in Settings")
            .setMessage(
                "You chose not to be asked again for some permissions.\n\n" +
                        "To let the challenge work, open the app settings and enable " +
                        "Location, Contacts, Microphone and Bluetooth permissions manually."
            )
            .setCancelable(false)
            .setPositiveButton("Open settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    this,
                    "Challenge will not work without these permissions.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    // ----- Main checks -----

    private fun checkAllConditions() {
        checkBluetoothCondition()
        checkChargingCondition()
        checkContactsCondition()
        checkLocationCondition() // async; will update UI when done

        tvCondition5.text = if (saidMagicWord) {
            "5. Magic word: heard ✅"
        } else {
            "5. Magic word: it wasn't the magic word"
        }

        updateOverallStatus()
    }

    // 1. Connected to Jabra Bluetooth earphones (heuristic)
    @SuppressLint("MissingPermission")
    private fun checkBluetoothCondition() {
        if (!hasBluetoothConnectPermission()) {
            tvCondition1.text = "1. Jabra Bluetooth: permission missing"
            hasJabra = false
            updateOverallStatus()
            return
        }

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            tvCondition1.text = "1. Jabra Bluetooth: Bluetooth disabled"
            hasJabra = false
            updateOverallStatus()
            return
        }

        val bonded = adapter.bondedDevices ?: emptySet()
        val hasJabraBonded = bonded.any {
            it.name?.contains("jabra", ignoreCase = true) == true
        }

        val audioManager = getSystemService(AudioManager::class.java)
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        val hasBtOutput = outputs.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }

        // Simple rule: at least one bonded Jabra + audio currently routed to Bluetooth
        hasJabra = hasJabraBonded && hasBtOutput

        tvCondition1.text = if (hasJabra) {
            "1. Jabra Bluetooth: connected ✅"
        } else if (!hasJabraBonded) {
            "1. Jabra Bluetooth: no bonded Jabra device"
        } else {
            "1. Jabra Bluetooth: Jabra paired but not active audio output"
        }

        updateOverallStatus()
    }

    // 2. Connected to charger AND actually charging
    private fun checkChargingCondition() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)

        if (batteryStatus == null) {
            isCharging = false
            tvCondition2.text = "2. Charging: unknown (no battery broadcast)"
            updateOverallStatus()
            return
        }

        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val chargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val plugged =
            chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                    chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
                    chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        isCharging = chargingNow && plugged

        tvCondition2.text = if (isCharging) {
            "2. Charging: charger connected & receiving current ✅"
        } else {
            "2. Charging: Not charging right now"
        }

        updateOverallStatus()
    }

    // 3. Device located inside Petah Tikva bounding box
    @SuppressLint("MissingPermission")
    private fun checkLocationCondition() {
        if (!hasLocationPermission()) {
            inPetahTikva = false
            tvCondition3.text = "3. Location: permission missing"
            updateOverallStatus()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    inPetahTikva = false
                    tvCondition3.text = "3. Location: unknown (no last location)"
                } else {
                    inPetahTikva = isInPetahTikva(location)
                    tvCondition3.text = if (inPetahTikva) {
                        "3. Location: inside Petah Tikva ✅\n" +
                                "lat=${location.latitude}, lon=${location.longitude}"
                    } else {
                        "3. Location: NOT in Petah Tikva\n" +
                                "lat=${location.latitude}, lon=${location.longitude}"
                    }
                }
                updateOverallStatus()
            }
            .addOnFailureListener { ex ->
                inPetahTikva = false
                tvCondition3.text = "3. Location: error: ${ex.message}"
                updateOverallStatus()
            }
    }

    private fun isInPetahTikva(location: Location): Boolean {
        return location.latitude in PTA_LAT_MIN..PTA_LAT_MAX &&
                location.longitude in PTA_LON_MIN..PTA_LON_MAX
    }

    // 4. A contact named "Sarah" exists
    private fun checkContactsCondition() {
        if (!hasContactsPermission()) {
            hasSarahContact = false
            tvCondition4.text = "4. Contact 'Sarah': permission missing"
            updateOverallStatus()
            return
        }

        hasSarahContact = hasContactNamedSarah()
        tvCondition4.text = if (hasSarahContact) {
            "4. Contact 'Sarah': found ✅"
        } else {
            "4. Contact 'Sarah': Not found"
        }

        updateOverallStatus()
    }

    private fun hasContactNamedSarah(): Boolean {
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val args = arrayOf("%sarah%")

        contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val nameIndex =
                cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: ""
                if (name.contains("sarah", ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    // 5. User says Supercalifragilisticexpialidocious into the mic
    private fun startListeningForMagicWord() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toLanguageTag())
        }
        speechLauncher.launch(intent)
    }

    // ----- Final: decide win/lose -----
    private fun updateOverallStatus() {
        val allOk = hasJabra && isCharging && inPetahTikva && hasSarahContact && saidMagicWord

        // instead of this, open up a new activity with: "Challenge status: YOU WIN 🎉"
        tvChallengeStatus.text =
            if (allOk) "Challenge status: completed" else "Challenge status: Not completed yet"

        if (allOk) {
            val intent = Intent(this, PrizeActivity::class.java)
            startActivity(intent)
        }
    }
}