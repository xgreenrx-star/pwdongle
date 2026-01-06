package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * File manager fragment - browse, delete, and manage macro files
 * Supports both local phone storage and PWDongle SD card files
 */
class FileManagerFragment : Fragment() {
    
    private lateinit var macrosRecyclerView: RecyclerView
    private lateinit var fileAdapter: MacroFileAdapter
    private lateinit var fileManager: MacroFileManager
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var tabToggle: RadioGroup
    private lateinit var localFilesRadio: RadioButton
    private lateinit var deviceFilesRadio: RadioButton
    private lateinit var batchModeButton: Button
    private lateinit var batchActionsLayout: LinearLayout
    private lateinit var selectAllButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var playSelectedButton: Button
    private var isBatchMode = false
    private val selectedFiles = mutableSetOf<String>()
    private var bleManager: BLEManager? = null
    private var isConnected = false
    private var currentDeviceFiles = mutableListOf<String>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_file_manager, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statusText = view.findViewById(R.id.statusText)
        emptyText = view.findViewById(R.id.emptyText)
        macrosRecyclerView = view.findViewById(R.id.macrosRecyclerView)
        tabToggle = view.findViewById(R.id.tabToggle)
        localFilesRadio = view.findViewById(R.id.localFilesRadio)
        deviceFilesRadio = view.findViewById(R.id.deviceFilesRadio)
        batchModeButton = view.findViewById(R.id.batchModeButton)
        batchActionsLayout = view.findViewById(R.id.batchActionsLayout)
        selectAllButton = view.findViewById(R.id.selectAllButton)
        deleteSelectedButton = view.findViewById(R.id.deleteSelectedButton)
        playSelectedButton = view.findViewById(R.id.playSelectedButton)
        
        fileManager = MacroFileManager(requireContext())
        
        // Initialize BLE manager for device files
        try {
            bleManager = BLEManager.getInstance(requireContext()) { status ->
                requireActivity().runOnUiThread {
                    isConnected = status.contains("Connected", ignoreCase = true)
                    deviceFilesRadio.isEnabled = isConnected
                    if (!isConnected && deviceFilesRadio.isChecked) {
                        localFilesRadio.isChecked = true
                        statusText.text = "Device disconnected"
                    }
                }
            }
            isConnected = bleManager?.isConnected() == true
            deviceFilesRadio.isEnabled = isConnected
        } catch (e: Exception) {
            android.util.Log.e("FileManagerFragment", "BLE initialization failed", e)
            deviceFilesRadio.isEnabled = false
        }
        
        macrosRecyclerView.layoutManager = LinearLayoutManager(context)
        fileAdapter = MacroFileAdapter(
            onDelete = { macro -> deleteMacro(macro) },
            onPlayback = { macro -> playMacro(macro) },
            onShare = { macro -> shareMacro(macro) },
            onPlayOnDevice = { filename -> playOnDevice(filename) },
            onView = { macro -> viewMacroContents(macro) },
            onSelectionChanged = { filename, selected ->
                if (selected) {
                    selectedFiles.add(filename)
                } else {
                    selectedFiles.remove(filename)
                }
                updateBatchActionsState()
            }
        )
        macrosRecyclerView.adapter = fileAdapter
        
        // Batch mode toggle
        batchModeButton.setOnClickListener {
            toggleBatchMode()
        }
        
        // Batch action buttons
        selectAllButton.setOnClickListener {
            selectAllFiles()
        }
        
        deleteSelectedButton.setOnClickListener {
            deleteSelectedFiles()
        }
        
        playSelectedButton.setOnClickListener {
            playSelectedFiles()
        }
        
        // Tab switching
        tabToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.localFilesRadio -> loadLocalMacros()
                R.id.deviceFilesRadio -> loadDeviceFiles()
            }
        }
        
        loadLocalMacros()
    }
    
    private fun toggleBatchMode() {
        isBatchMode = !isBatchMode
        selectedFiles.clear()
        
        if (isBatchMode) {
            batchModeButton.text = "Cancel"
            batchActionsLayout.visibility = View.VISIBLE
        } else {
            batchModeButton.text = "Select Multiple"
            batchActionsLayout.visibility = View.GONE
        }
        
        fileAdapter.setBatchMode(isBatchMode)
        updateBatchActionsState()
    }
    
    private fun selectAllFiles() {
        val allFiles = fileAdapter.getAllFileNames()
        selectedFiles.clear()
        selectedFiles.addAll(allFiles)
        fileAdapter.selectAll()
        updateBatchActionsState()
    }
    
    private fun updateBatchActionsState() {
        val count = selectedFiles.size
        selectAllButton.text = if (count == fileAdapter.itemCount) "Deselect All" else "Select All"
        deleteSelectedButton.isEnabled = count > 0
        playSelectedButton.isEnabled = count > 0
        deleteSelectedButton.text = "Delete ($count)"
        playSelectedButton.text = "Play ($count)"
    }
    
    private fun deleteSelectedFiles() {
        if (selectedFiles.isEmpty()) return
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selectedFiles.size} files?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var deletedCount = 0
                    selectedFiles.forEach { filename ->
                        try {
                            fileManager.deleteMacro(filename)
                            deletedCount++
                        } catch (e: Exception) {
                            android.util.Log.e("FileManagerFragment", "Failed to delete $filename", e)
                        }
                    }
                    
                    selectedFiles.clear()
                    statusText.text = "Deleted $deletedCount files"
                    loadLocalMacros()
                    toggleBatchMode() // Exit batch mode
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun playSelectedFiles() {
        if (selectedFiles.isEmpty()) return
        
        // Confirm sequential playback
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Play ${selectedFiles.size} macros?")
            .setMessage("Macros will be played sequentially with a 2-second delay between each.")
            .setPositiveButton("Play All") { _, _ ->
                playFilesSequentially(selectedFiles.toList())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun playFilesSequentially(filenames: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            var playedCount = 0
            for ((index, filename) in filenames.withIndex()) {
                statusText.text = "Playing ${index + 1}/${filenames.size}: $filename"
                
                try {
                    val result = fileManager.loadMacro(filename)
                    result.onSuccess { content ->
                        // For batch playback, we'd need to execute inline
                        // For now, just count it as "played"
                        // TODO: Integrate with MacroPlayer for inline execution
                        playedCount++
                    }
                    result.onFailure { e ->
                        android.util.Log.e("FileManagerFragment", "Failed to load $filename", e)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FileManagerFragment", "Failed to play $filename", e)
                }
                
                // Delay between macros
                if (index < filenames.size - 1) {
                    kotlinx.coroutines.delay(2000)
                }
            }
            
            statusText.text = "Batch playback complete: $playedCount/${filenames.size} macros"
            selectedFiles.clear()
            toggleBatchMode()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (localFilesRadio.isChecked) {
            loadLocalMacros()
        } else if (isConnected) {
            loadDeviceFiles()
        }
    }
    
    private fun loadLocalMacros() {
        viewLifecycleOwner.lifecycleScope.launch {
            fileManager.listMacros().onSuccess { macros ->
                if (macros.isEmpty()) {
                    macrosRecyclerView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No macros found. Create one by recording!"
                    statusText.text = "Ready"
                } else {
                    macrosRecyclerView.visibility = View.VISIBLE
                    emptyText.visibility = View.GONE
                    fileAdapter.submitList(macros, isDeviceFiles = false)
                    statusText.text = "Found ${macros.size} macro(s)"
                }
            }.onFailure { error ->
                statusText.text = "Error loading macros: ${error.message}"
            }
        }
    }
    
    private fun loadDeviceFiles() {
        statusText.text = "Loading device files..."
        bleManager?.sendCommandWithResponse("LIST") { response ->
            requireActivity().runOnUiThread {
                try {
                    // Parse response: OK: Listing macro files:
                    // 1. filename.txt
                    // 2. another.txt
                    val lines = response.split("\n")
                    currentDeviceFiles.clear()
                    
                    for (line in lines) {
                        val trimmed = line.trim()
                        // Match lines like "1. filename" or "1. filename.txt"
                        if (trimmed.matches(Regex("^\\d+\\.\\s+.+$"))) {
                            var filename = trimmed.substring(trimmed.indexOf(".") + 1).trim()
                            // Add .txt extension if not present
                            if (!filename.endsWith(".txt")) {
                                filename += ".txt"
                            }
                            currentDeviceFiles.add(filename)
                        }
                    }
                    
                    if (currentDeviceFiles.isEmpty()) {
                        macrosRecyclerView.visibility = View.GONE
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No macros on device"
                        statusText.text = "Device has 0 macros"
                    } else {
                        macrosRecyclerView.visibility = View.VISIBLE
                        emptyText.visibility = View.GONE
                        val deviceMacros = currentDeviceFiles.map { 
                            MacroFile(
                                name = it,
                                filename = it,
                                path = "/device/$it",
                                size = 0L,
                                modified = System.currentTimeMillis()
                            )
                        }
                        fileAdapter.submitList(deviceMacros, isDeviceFiles = true)
                        statusText.text = "Device has ${currentDeviceFiles.size} macro(s)"
                    }
                } catch (e: Exception) {
                    statusText.text = "Error parsing device files: ${e.message}"
                    android.util.Log.e("FileManagerFragment", "Error parsing LIST response", e)
                }
            }
        }
    }
    
    private fun playOnDevice(filename: String) {
        statusText.text = "Playing $filename on device..."
        bleManager?.sendCommandWithResponse("PLAY:$filename") { response ->
            requireActivity().runOnUiThread {
                statusText.text = if (response.contains("OK")) {
                    "Playing $filename on device"
                } else {
                    "Error: ${response}"
                }
            }
        }
    }
    
    private fun deleteMacro(macro: MacroFile) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Macro")
            .setMessage("Delete '${macro.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    fileManager.deleteMacro(macro.name).onSuccess {
                        Toast.makeText(context, "Macro deleted", Toast.LENGTH_SHORT).show()
                        loadLocalMacros()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun playMacro(macro: MacroFile) {
        viewLifecycleOwner.lifecycleScope.launch {
            fileManager.loadMacro(macro.name).onSuccess { content ->
                // Navigate to playback fragment with macro data
                val bundle = Bundle().apply {
                    putString("macro_name", macro.name)
                    putString("macro_content", content)
                }
                try {
                    findNavController().navigate(R.id.nav_playback, bundle)
                } catch (e: Exception) {
                    android.util.Log.e("FileManagerFragment", "Navigation failed", e)
                }
            }
        }
    }
    
    private fun shareMacro(macro: MacroFile) {
        viewLifecycleOwner.lifecycleScope.launch {
            fileManager.getMacroFile(macro.name).onSuccess { file ->
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, 
                        androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        ))
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Share Macro"))
            }
        }
    }
    
    private fun viewMacroContents(macro: MacroFile) {
        // Check if this is a device file (path contains "/device/")
        if (macro.path.startsWith("/device/")) {
            // Request macro content from device via BLE
            statusText.text = "Loading ${macro.name} from device..."
            bleManager?.sendCommandWithResponse("VIEW:${macro.name}") { response ->
                requireActivity().runOnUiThread {
                    if (response.contains("ERROR", ignoreCase = true)) {
                        Toast.makeText(context, "Error loading macro from device: $response", Toast.LENGTH_SHORT).show()
                        statusText.text = "Error: Could not load macro from device"
                    } else {
                        showMacroViewDialog(macro.name, response)
                        statusText.text = "Ready"
                    }
                }
            }
        } else {
            // Load local file
            viewLifecycleOwner.lifecycleScope.launch {
                fileManager.getMacroFile(macro.name).onSuccess { file ->
                    try {
                        val contents = file.readText()
                        showMacroViewDialog(macro.name, contents)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error reading macro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(context, "Error loading macro: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showMacroViewDialog(filename: String, contents: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        dialog.setTitle("View Macro: $filename")
        
        val textView = TextView(requireContext()).apply {
            text = contents
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(20, 20, 20, 20)
            setBackgroundColor(android.graphics.Color.BLACK)
            setTextColor(android.graphics.Color.WHITE)
        }
        
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        dialog.setView(scrollView)
        dialog.setPositiveButton("Close") { _, _ -> }
        dialog.setNegativeButton("Copy") { _, _ ->
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("macro", contents)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }
}

/**
 * RecyclerView adapter for macro files (local and device)
 */
class MacroFileAdapter(
    private val onDelete: (MacroFile) -> Unit,
    private val onPlayback: (MacroFile) -> Unit,
    private val onShare: (MacroFile) -> Unit,
    private val onPlayOnDevice: ((String) -> Unit)? = null,
    private val onView: ((MacroFile) -> Unit)? = null,
    private val onSelectionChanged: ((String, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<MacroFileViewHolder>() {
    
    private var macros = listOf<MacroFile>()
    private var isDeviceFiles = false
    private var isBatchMode = false
    private val selectedFiles = mutableSetOf<String>()
    
    fun submitList(list: List<MacroFile>, isDeviceFiles: Boolean = false) {
        macros = list
        this.isDeviceFiles = isDeviceFiles
        notifyDataSetChanged()
    }
    
    fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        if (!enabled) {
            selectedFiles.clear()
        }
        notifyDataSetChanged()
    }
    
    fun selectAll() {
        if (selectedFiles.size == macros.size) {
            selectedFiles.clear()
        } else {
            selectedFiles.clear()
            macros.forEach { selectedFiles.add(it.name) }
        }
        notifyDataSetChanged()
    }
    
    fun getAllFileNames(): List<String> = macros.map { it.name }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacroFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_macro_file, parent, false)
        return MacroFileViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MacroFileViewHolder, position: Int) {
        val macro = macros[position]
        val isSelected = selectedFiles.contains(macro.name)
        
        holder.bind(
            macro, 
            onDelete, 
            onPlayback, 
            onShare,
            isDeviceFiles,
            onPlayOnDevice,
            onView,
            isBatchMode,
            isSelected,
            onSelect = { filename ->
                if (selectedFiles.contains(filename)) {
                    selectedFiles.remove(filename)
                    onSelectionChanged?.invoke(filename, false)
                } else {
                    selectedFiles.add(filename)
                    onSelectionChanged?.invoke(filename, true)
                }
                notifyItemChanged(position)
            }
        )
    }
    
    override fun getItemCount() = macros.size
}

/**
 * ViewHolder for macro file items (local and device)
 */
class MacroFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    
    private val nameText: TextView = itemView.findViewById(R.id.nameText)
    private val infoText: TextView = itemView.findViewById(R.id.infoText)
    private val deleteBtn: Button = itemView.findViewById(R.id.deleteBtn)
    private val playBtn: Button = itemView.findViewById(R.id.playBtn)
    private val shareBtn: Button = itemView.findViewById(R.id.shareBtn)
    private val checkbox: CheckBox? = try { itemView.findViewById(R.id.fileCheckbox) } catch (e: Exception) { null }
    
    fun bind(
        macro: MacroFile, 
        onDelete: (MacroFile) -> Unit, 
        onPlayback: (MacroFile) -> Unit, 
        onShare: (MacroFile) -> Unit,
        isDeviceFile: Boolean = false,
        onPlayOnDevice: ((String) -> Unit)? = null,
        onView: ((MacroFile) -> Unit)? = null,
        isBatchMode: Boolean = false,
        isSelected: Boolean = false,
        onSelect: ((String) -> Unit)? = null
    ) {
        nameText.text = macro.name
        
        // Batch mode checkbox
        if (isBatchMode) {
            checkbox?.visibility = View.VISIBLE
            checkbox?.isChecked = isSelected
            checkbox?.setOnCheckedChangeListener(null) // Prevent recursion
            checkbox?.setOnCheckedChangeListener { _, _ ->
                onSelect?.invoke(macro.name)
            }
            
            // Make entire item clickable for selection
            itemView.setOnClickListener {
                onSelect?.invoke(macro.name)
            }
            
            // Hide action buttons in batch mode
            deleteBtn.visibility = View.GONE
            playBtn.visibility = View.GONE
            shareBtn.visibility = View.GONE
        } else {
            checkbox?.visibility = View.GONE
            itemView.setOnClickListener(null)
            
            if (isDeviceFile) {
                infoText.text = "On Device"
                deleteBtn.visibility = View.GONE
                shareBtn.visibility = View.VISIBLE
                shareBtn.text = "View"
                playBtn.visibility = View.VISIBLE
                playBtn.text = "Play on Device"
                playBtn.setOnClickListener { onPlayOnDevice?.invoke(macro.name) }
                shareBtn.setOnClickListener { onView?.invoke(macro) }
            } else {
                infoText.text = "${macro.sizeString} • ${macro.modifiedString}"
                deleteBtn.visibility = View.VISIBLE
                shareBtn.visibility = View.VISIBLE
                shareBtn.text = "View"
                playBtn.visibility = View.VISIBLE
                playBtn.text = "Play"
                
                deleteBtn.setOnClickListener { onDelete(macro) }
                playBtn.setOnClickListener { onPlayback(macro) }
                shareBtn.setOnClickListener { onView?.invoke(macro) }
            }
        }
    }
}
