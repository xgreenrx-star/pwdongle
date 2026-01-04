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

/**
 * Main Activity - handles navigation and BLE connection
 */
class MainActivity : AppCompatActivity() {
    
    private var navController: NavController? = null
    private lateinit var bottomNav: BottomNavigationView
    private val originalTitles = mutableMapOf<Int, String>()
    
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            
            bottomNav = findViewById(R.id.bottomNavigation)
            
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
}
