package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch

/**
 * Playback fragment - play recorded macros
 */
class PlaybackFragment : Fragment() {
    
    private lateinit var macroNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var backButton: Button
    private lateinit var speedSpinner: Spinner
    private lateinit var macroContentView: ScrollView
    private lateinit var contentText: TextView
    
    private var bleManager: BLEManager? = null
    private var macroPlayer: MacroPlayer? = null
    private val macroValidator = MacroValidator()
    
    private var currentMacroName: String? = null
    private var currentMacroContent: String? = null
    private var isPlaying = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeUI(view)
        setupListeners()
        
        // Load macro from arguments
        arguments?.let { bundle ->
            currentMacroName = bundle.getString("macro_name", "Unknown")
            currentMacroContent = bundle.getString("macro_content", "")
            macroNameText.text = currentMacroName
            contentText.text = currentMacroContent
        }
    }
    
    private fun initializeUI(view: View) {
        macroNameText = view.findViewById(R.id.macroNameText)
        statusText = view.findViewById(R.id.statusText)
        progressBar = view.findViewById(R.id.progressBar)
        progressText = view.findViewById(R.id.progressText)
        playButton = view.findViewById(R.id.playButton)
        stopButton = view.findViewById(R.id.stopButton)
        backButton = view.findViewById(R.id.backButton)
        speedSpinner = view.findViewById(R.id.speedSpinner)
        macroContentView = view.findViewById(R.id.macroContentView)
        contentText = view.findViewById(R.id.contentText)
        
        // Use singleton BLE manager (shared instance)
        try {
            bleManager = BLEManager.getInstance(requireContext()) { status ->
                try {
                    requireActivity().runOnUiThread {
                        statusText.text = status
                    }
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            statusText.text = "BLE unavailable"
        }
        
        // Setup speed spinner
        val speeds = arrayOf("0.5x", "1x", "1.5x", "2x", "3x")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speeds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speedSpinner.adapter = adapter
        speedSpinner.setSelection(1) // Default 1x
    }
    
    private fun setupListeners() {
        playButton.setOnClickListener { playMacro() }
        stopButton.setOnClickListener { stopPlayback() }
        backButton.setOnClickListener { findNavController().popBackStack() }
    }
    
    private fun playMacro() {
        if (currentMacroContent == null || isPlaying) return
        
        // Validate macro before playback
        val validationResult = macroValidator.validate(currentMacroContent!!)
        
        if (!validationResult.isValid) {
            // Show validation errors
            val errorMsg = "Validation failed:\n" + validationResult.errors.joinToString("\n")
            statusText.text = errorMsg
            
            // Show dialog with option to play anyway
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Macro Validation Failed")
                .setMessage(errorMsg + "\n\nPlay anyway?")
                .setPositiveButton("Play Anyway") { _, _ -> startPlayback(validationResult) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        
        // Show validation summary with warnings
        if (validationResult.warnings.isNotEmpty() || validationResult.hasLongDelays) {
            val warningMsg = buildString {
                append("Macro: ${validationResult.commandCount} commands\n")
                append("Duration: ~${macroValidator.formatDuration(validationResult.estimatedDurationMs)}\n")
                if (validationResult.warnings.isNotEmpty()) {
                    append("\nWarnings:\n")
                    append(validationResult.warnings.take(5).joinToString("\n"))
                    if (validationResult.warnings.size > 5) {
                        append("\n...and ${validationResult.warnings.size - 5} more")
                    }
                }
            }
            
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Ready to Play")
                .setMessage(warningMsg)
                .setPositiveButton("Play") { _, _ -> startPlayback(validationResult) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // No warnings, show brief confirmation
            val infoMsg = "${validationResult.commandCount} commands, ~${macroValidator.formatDuration(validationResult.estimatedDurationMs)}"
            Toast.makeText(requireContext(), infoMsg, Toast.LENGTH_SHORT).show()
            startPlayback(validationResult)
        }
    }
    
    private fun startPlayback(validationResult: MacroValidator.ValidationResult) {
        isPlaying = true
        playButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.text = "Playing ${validationResult.commandCount} commands..."
        
        // Show progress indicators
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.visibility = View.VISIBLE
        progressText.text = "Starting..."
        
        // Get speed multiplier
        val speedMultiplier = speedSpinner.selectedItem
            ?.toString()
            ?.replace("x", "")
            ?.toFloatOrNull()
            ?: 1f
        
        macroPlayer = MacroPlayer(
            onKeyCommand = { key ->
                viewLifecycleOwner.lifecycleScope.launch {
                    bleManager?.sendCommand("KEY:$key")
                }
            },
            onMouseCommand = { args ->
                viewLifecycleOwner.lifecycleScope.launch {
                    bleManager?.sendCommand("MOUSE:$args")
                }
            },
            onTypeCommand = { text ->
                viewLifecycleOwner.lifecycleScope.launch {
                    bleManager?.sendCommand("TYPE:$text")
                }
            },
            onStatusChange = { status ->
                requireActivity().runOnUiThread {
                    statusText.text = status
                }
            },
            onProgressUpdate = { current, total, elapsedMs, estimatedTotalMs ->
                requireActivity().runOnUiThread {
                    val percentage = (current * 100 / total)
                    progressBar.progress = percentage
                    
                    val elapsedSec = elapsedMs / 1000
                    val estimatedSec = estimatedTotalMs / 1000
                    val remainingSec = estimatedSec - elapsedSec
                    
                    progressText.text = "$current/$total commands • ${elapsedSec}s / ${estimatedSec}s (~${remainingSec}s left)"
                }
            },
            speedMultiplier = speedMultiplier
        )
        
        viewLifecycleOwner.lifecycleScope.launch {
            macroPlayer?.playMacro(currentMacroContent!!)
            isPlaying = false
            playButton.isEnabled = true
            stopButton.isEnabled = false
            
            // Hide progress indicators
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            
            // Auto-navigate back to file manager after 1.5 seconds
            kotlinx.coroutines.delay(1500)
            try {
                // Only navigate if the view is still attached
                if (isAdded && view != null) {
                    findNavController().popBackStack()
                }
            } catch (e: Exception) {
                android.util.Log.w("PlaybackFragment", "Navigation failed: ${e.message}")
            }
        }
    }
    
    private fun stopPlayback() {
        macroPlayer?.stop()
        isPlaying = false
        playButton.isEnabled = true
        stopButton.isEnabled = false
        statusText.text = "Playback stopped"
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
    }
}
