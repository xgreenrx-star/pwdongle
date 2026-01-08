package com.pwdongle.recorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Global interface for keyboard event handling
 */
interface KeyboardEventListener {
    fun onKeyboardEvent(keyCode: Int, action: Int): Boolean
}

/**
 * Global interface for mouse event handling
 */
interface MouseEventListener {
    fun onMouseMove(dx: Int, dy: Int): Boolean
    fun onMouseButton(button: String, isDown: Boolean): Boolean
    fun onMouseScroll(horizontal: Int, vertical: Int): Boolean
}

/**
 * Main Activity - handles navigation and BLE connection
 */
class MainActivity : AppCompatActivity() {
    
    private var navController: NavController? = null
    private lateinit var bottomNav: BottomNavigationView
    private val originalTitles = mutableMapOf<Int, String>()
    private var keyboardEventListener: KeyboardEventListener? = null
    private var mouseEventListener: MouseEventListener? = null
    // Track last absolute mouse position to compute deltas if RELATIVE axes are unavailable
    private var lastAbsX: Float? = null
    private var lastAbsY: Float? = null
    
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
    
    fun setKeyboardEventListener(listener: KeyboardEventListener?) {
        keyboardEventListener = listener
    }
    
    fun setMouseEventListener(listener: MouseEventListener?) {
        mouseEventListener = listener
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Apply persisted theme mode before inflating views
            try {
                val pm = PreferencesManager(this)
                val mode = runBlocking { pm.getThemeMode() }
                when (mode) {
                    "light" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    )
                    "dark" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    )
                    else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
                }
            } catch (_: Exception) { /* ignore theme init errors */ }

            setContentView(R.layout.activity_main)
            
            bottomNav = findViewById(R.id.bottomNavigation)
            
            // Theme initialized above; Settings can change and recreate activity

            // Check permissions
            checkPermissions()
            
            // Setup navigation
            try {
                setupNavigation()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Navigation setup failed", e)
                android.widget.Toast.makeText(this, "Navigation error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Activity initialization failed", e)
            finish()
        }
    }
    
    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        // Bluetooth permissions (essential for BLE communication)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Older Android versions
            permissionsNeeded.add(Manifest.permission.BLUETOOTH)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // Location permission (required for BLE scanning on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Note: No storage permissions needed - app uses app-scoped external files
        
        val notGranted = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            android.util.Log.i("MainActivity", "Requesting permissions: $notGranted")
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }
    
    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as? NavHostFragment
            
            if (navHostFragment == null) {
                android.util.Log.e("MainActivity", "NavHostFragment not found")
                return
            }
            
            navController = navHostFragment.navController
            
            // Setup bottom navigation
            navController?.let {
                NavigationUI.setupWithNavController(bottomNav, it)
                captureOriginalTitles()
                configureLabelToggle(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "setupNavigation failed", e)
            throw e
        }
    }

    private fun captureOriginalTitles() {
        for (item in bottomNav.menu) {
            originalTitles[item.itemId] = item.title?.toString() ?: ""
        }
    }

    private fun configureLabelToggle(controller: NavController) {
        bottomNav.setOnItemSelectedListener { item ->
            // Restore all labels
            for (entry in originalTitles) {
                bottomNav.menu.findItem(entry.key)?.title = entry.value
            }
            // Hide label for the selected item
            bottomNav.menu.findItem(item.itemId)?.title = ""
            NavigationUI.onNavDestinationSelected(item, controller)
            true
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
            } else {
                // Some permissions denied
                android.widget.Toast.makeText(
                    this,
                    "Some permissions were denied. App may not work correctly.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_exit -> {
                showExitConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showExitConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ ->
                android.widget.Toast.makeText(this, "Exiting...", android.widget.Toast.LENGTH_SHORT).show()
                try {
                    finishAffinity()
                } catch (_: Exception) {
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Intercept keyboard events from USB OTG devices during recording
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Check if event is from external keyboard (not built-in)
        val device = event.device
        if (device != null && 
            device.sources and android.view.InputDevice.SOURCE_KEYBOARD != 0
        ) {
            android.util.Log.d("MainActivity", "Keyboard device detected: ${device.name}, keyCode=${event.keyCode}, action=${event.action}")
            
            // Only send KEY_DOWN events (PWDongle KEY command does press + release)
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                // Try to pass event to fragment first (for local recording)
                if (keyboardEventListener?.onKeyboardEvent(event.keyCode, event.action) == true) {
                    android.util.Log.d("MainActivity", "Event handled by fragment")
                    return true
                }
                
                // Fallback: send directly to PWDongle
                sendKeyToPWDongle(event.keyCode)
                return true
            }
        }
        
        return super.dispatchKeyEvent(event)
    }
    
    /**
     * Intercept mouse/trackpad events from USB OTG devices during recording
     * Called BEFORE the event reaches the view hierarchy
     * Uses AXIS_RELATIVE_X/Y for true delta movements that don't stop at screen edges
     */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent?): Boolean {
        if (event == null) return super.dispatchGenericMotionEvent(event)
        
        // Check if this is from an external pointing device (mouse, trackpad)
        val isMouse = (event.source and android.view.InputDevice.SOURCE_MOUSE) != 0
        val isTrackball = (event.source and android.view.InputDevice.SOURCE_TRACKBALL) != 0
        
        if (isMouse || isTrackball) {
            // Handle scroll events first
            if (event.action == android.view.MotionEvent.ACTION_SCROLL) {
                val v = event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL).roundToInt()
                val h = event.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL).roundToInt()
                if ((v != 0 || h != 0) && mouseEventListener?.onMouseScroll(h, v) == true) {
                    return true
                }
            }

            // Handle button press/release
            if (event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS ||
                event.action == android.view.MotionEvent.ACTION_BUTTON_RELEASE) {
                val isDown = event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS
                val state = event.buttonState
                // Map primary/secondary/middle
                val handled = when {
                    state and android.view.MotionEvent.BUTTON_PRIMARY != 0 -> mouseEventListener?.onMouseButton("left", isDown) == true
                    state and android.view.MotionEvent.BUTTON_SECONDARY != 0 -> mouseEventListener?.onMouseButton("right", isDown) == true
                    state and android.view.MotionEvent.BUTTON_TERTIARY != 0 -> mouseEventListener?.onMouseButton("middle", isDown) == true
                    else -> false
                }
                if (handled) return true
            }

            // Prefer RELATIVE axes for true deltas; some devices report only ABSOLUTE axes
            var dx = event.getAxisValue(android.view.MotionEvent.AXIS_RELATIVE_X).roundToInt()
            var dy = event.getAxisValue(android.view.MotionEvent.AXIS_RELATIVE_Y).roundToInt()

            // Fallback: compute deltas from absolute AXIS_X/Y when RELATIVE axes are zero
            if (dx == 0 && dy == 0) {
                val ax = event.getAxisValue(android.view.MotionEvent.AXIS_X)
                val ay = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
                val prevX = lastAbsX
                val prevY = lastAbsY
                lastAbsX = ax
                lastAbsY = ay
                if (prevX != null && prevY != null) {
                    dx = (ax - prevX).roundToInt()
                    dy = (ay - prevY).roundToInt()
                }
            }

            // Only forward meaningful movement
            if (dx != 0 || dy != 0) {
                if (mouseEventListener?.onMouseMove(dx, dy) == true) {
                    return true  // Consume - don't pass to views
                }
            }
        }
        
        return super.dispatchGenericMotionEvent(event)
    }
    
    /**
     * Also intercept touch/motion at view level to prevent pointer display
     */
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        
        // Consume all mouse events to prevent pointer display
        if (event.source and android.view.InputDevice.SOURCE_MOUSE != 0) {
            return true
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * Send a key press to PWDongle via BLE
     */
    private fun sendKeyToPWDongle(keyCode: Int) {
        val keyName = keyCodeToKeyName(keyCode)
        val bleManager = BLEManager.getInstance(this, {})
        
        android.util.Log.d("MainActivity", "sendKeyToPWDongle: keyCode=$keyCode, keyName=$keyName, isConnected=${bleManager.isConnected()}")
        
        if (bleManager.isConnected()) {
            bleManager.sendCommand("KEY:$keyName")
            android.util.Log.d("MainActivity", "Sent KEY:$keyName to PWDongle")
        } else {
            android.util.Log.w("MainActivity", "PWDongle not connected, key not sent")
        }
    }
    
    /**
     * Convert Android KeyCode to PWDongle-compatible key name
     */
    private fun keyCodeToKeyName(keyCode: Int): String {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_0 -> "0"
            android.view.KeyEvent.KEYCODE_1 -> "1"
            android.view.KeyEvent.KEYCODE_2 -> "2"
            android.view.KeyEvent.KEYCODE_3 -> "3"
            android.view.KeyEvent.KEYCODE_4 -> "4"
            android.view.KeyEvent.KEYCODE_5 -> "5"
            android.view.KeyEvent.KEYCODE_6 -> "6"
            android.view.KeyEvent.KEYCODE_7 -> "7"
            android.view.KeyEvent.KEYCODE_8 -> "8"
            android.view.KeyEvent.KEYCODE_9 -> "9"
            android.view.KeyEvent.KEYCODE_A -> "a"
            android.view.KeyEvent.KEYCODE_B -> "b"
            android.view.KeyEvent.KEYCODE_C -> "c"
            android.view.KeyEvent.KEYCODE_D -> "d"
            android.view.KeyEvent.KEYCODE_E -> "e"
            android.view.KeyEvent.KEYCODE_F -> "f"
            android.view.KeyEvent.KEYCODE_G -> "g"
            android.view.KeyEvent.KEYCODE_H -> "h"
            android.view.KeyEvent.KEYCODE_I -> "i"
            android.view.KeyEvent.KEYCODE_J -> "j"
            android.view.KeyEvent.KEYCODE_K -> "k"
            android.view.KeyEvent.KEYCODE_L -> "l"
            android.view.KeyEvent.KEYCODE_M -> "m"
            android.view.KeyEvent.KEYCODE_N -> "n"
            android.view.KeyEvent.KEYCODE_O -> "o"
            android.view.KeyEvent.KEYCODE_P -> "p"
            android.view.KeyEvent.KEYCODE_Q -> "q"
            android.view.KeyEvent.KEYCODE_R -> "r"
            android.view.KeyEvent.KEYCODE_S -> "s"
            android.view.KeyEvent.KEYCODE_T -> "t"
            android.view.KeyEvent.KEYCODE_U -> "u"
            android.view.KeyEvent.KEYCODE_V -> "v"
            android.view.KeyEvent.KEYCODE_W -> "w"
            android.view.KeyEvent.KEYCODE_X -> "x"
            android.view.KeyEvent.KEYCODE_Y -> "y"
            android.view.KeyEvent.KEYCODE_Z -> "z"
            android.view.KeyEvent.KEYCODE_ENTER -> "enter"
            android.view.KeyEvent.KEYCODE_TAB -> "tab"
            android.view.KeyEvent.KEYCODE_SPACE -> "space"
            android.view.KeyEvent.KEYCODE_DEL -> "backspace"
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
            android.view.KeyEvent.KEYCODE_ESCAPE -> "esc"
            android.view.KeyEvent.KEYCODE_HOME -> "home"
            260 -> "end"  // KEYCODE_END = 260
            android.view.KeyEvent.KEYCODE_PAGE_UP -> "pageup"
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "pagedown"
            android.view.KeyEvent.KEYCODE_DPAD_UP -> "up"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT -> "shift"
            android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> "shift"
            android.view.KeyEvent.KEYCODE_CTRL_LEFT -> "ctrl"
            android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> "ctrl"
            android.view.KeyEvent.KEYCODE_ALT_LEFT -> "alt"
            android.view.KeyEvent.KEYCODE_ALT_RIGHT -> "alt"
            android.view.KeyEvent.KEYCODE_COMMA -> "comma"
            android.view.KeyEvent.KEYCODE_PERIOD -> "period"
            android.view.KeyEvent.KEYCODE_SEMICOLON -> "semicolon"
            android.view.KeyEvent.KEYCODE_SLASH -> "slash"
            android.view.KeyEvent.KEYCODE_APOSTROPHE -> "quote"
            android.view.KeyEvent.KEYCODE_AT -> "at"
            android.view.KeyEvent.KEYCODE_EQUALS -> "equal"
            android.view.KeyEvent.KEYCODE_MINUS -> "minus"
            else -> "key_$keyCode"
        }
    }
}
