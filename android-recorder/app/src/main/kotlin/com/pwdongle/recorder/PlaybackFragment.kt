package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Playback fragment - play recorded macros
 */
class PlaybackFragment : Fragment() {
    
    private lateinit var macroNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var speedSpinner: Spinner
    private lateinit var macroContentView: ScrollView
    private lateinit var contentText: TextView
    
    private var bleManager: BLEManager? = null
    private var macroPlayer: MacroPlayer? = null
    
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
        playButton = view.findViewById(R.id.playButton)
        stopButton = view.findViewById(R.id.stopButton)
        speedSpinner = view.findViewById(R.id.speedSpinner)
        macroContentView = view.findViewById(R.id.macroContentView)
        contentText = view.findViewById(R.id.contentText)
        
        try {
            bleManager = BLEManager(requireContext()) { status ->
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
    }
    
    private fun playMacro() {
        if (currentMacroContent == null || isPlaying) return
        
        isPlaying = true
        playButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.text = "Playing..."
        
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
            speedMultiplier = speedMultiplier
        )
        
        viewLifecycleOwner.lifecycleScope.launch {
            macroPlayer?.playMacro(currentMacroContent!!)
            isPlaying = false
            playButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Playback complete"
        }
    }
    
    private fun stopPlayback() {
        macroPlayer?.stop()
        isPlaying = false
        playButton.isEnabled = true
        stopButton.isEnabled = false
        statusText.text = "Playback stopped"
    }
}
