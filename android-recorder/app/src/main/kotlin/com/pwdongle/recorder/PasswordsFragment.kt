package com.pwdongle.recorder

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private data class PasswordEntry(var name: String, var password: String)

class PasswordsFragment : Fragment() {
    private val tag = "PasswordsFragment"

    private lateinit var loginCodeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var newNameInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var passwordList: RecyclerView
    private lateinit var fetchButton: Button
    private lateinit var saveButton: Button
    private lateinit var addButton: Button

    private var bleManager: BLEManager? = null
    private val entries = mutableListOf<PasswordEntry>()
    private lateinit var adapter: PasswordAdapter
    private var autoFetched = false
    private var isBusy = false
    private var isUnlocked = false
    private var verifiedPin: String? = null  // Cache verified PIN to skip re-verification
    private var pinDialog: AlertDialog? = null  // Track PIN dialog to properly dismiss

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_passwords, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        initRecycler()
        initBle()
        setupActions()

        // Try to use cached PIN/passwords for instant unlock without dialog
        if (verifiedPin != null) {
            // Already verified in this session; load passwords with cached PIN
            loginCodeInput.setText(verifiedPin)
            isUnlocked = true
            view?.alpha = 1f
            maybeAutoFetchOnConnect()
        } else {
            // No session PIN cache; always show PIN dialog for security
            showPinEntryDialog()
        }
    }
    
    private fun showPinEntryDialog() {
        // Hide main content until unlocked
        view?.alpha = 0f
        
        val pinInput = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
            keyListener = DigitsKeyListener.getInstance("0123456789")
            filters = arrayOf(InputFilter.LengthFilter(4))
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Password Manager")
            .setMessage("Enter device PIN to access passwords")
            .setView(pinInput)
            .setCancelable(false)
            .setPositiveButton("Unlock", null)
            .setNegativeButton("Cancel") { _, _ ->
                // Close the fragment/tab
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
            .create()
        
        pinDialog = dialog  // Store reference for proper dismissal
        
        dialog.setOnShowListener {
            val unlockButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            unlockButton.setOnClickListener {
                val enteredPin = pinInput.text.toString()
                if (enteredPin.length != 4) {
                    toast("PIN must be 4 digits")
                    return@setOnClickListener
                }
                
                if (!ensureConnected(quiet = true)) {
                    toast("Not connected to device")
                    parentFragmentManager.beginTransaction().remove(this).commit()
                    dialog.dismiss()
                    return@setOnClickListener
                }
                
                unlockButton.isEnabled = false
                unlockButton.text = "Verifying..."
                
                bleManager?.sendCommandWithResponse("RETRIEVEPW") { firstResponse ->
                    requireActivity().runOnUiThread {
                        if (!firstResponse.contains("OK", ignoreCase = true)) {
                            toast("Device error: $firstResponse")
                            unlockButton.isEnabled = true
                            unlockButton.text = "Unlock"
                            return@runOnUiThread
                        }
                        
                        // Small delay to ensure device processed RETRIEVEPW and callback is ready
                        requireActivity().window.decorView.postDelayed({
                            bleManager?.sendCommandWithResponse(enteredPin) { authResponse ->
                                requireActivity().runOnUiThread {
                                    if (authResponse.contains("OK", ignoreCase = true)) {
                                        isUnlocked = true
                                        verifiedPin = enteredPin  // Cache PIN to skip re-verification
                                        loginCodeInput.setText(enteredPin)
                                        view?.alpha = 1f
                                        unlockButton.isEnabled = true  // Reset button state
                                        unlockButton.text = "Unlock"
                                        // Dismiss the stored dialog reference
                                        if (pinDialog?.isShowing == true) {
                                            pinDialog?.dismiss()
                                            pinDialog = null
                                        }
                                        toast("Unlocked")
                                        // Delay fetch to let dialog fully dismiss before starting background load
                                        view?.postDelayed({
                                            maybeAutoFetchOnConnect()
                                        }, 300)
                                    } else {
                                        toast("Incorrect PIN")
                                        unlockButton.isEnabled = true
                                        unlockButton.text = "Unlock"
                                        pinInput.setText("")
                                    }
                                }
                            }
                        }, 20)
                    }
                }
            }
        }
        
        dialog.show()
    }

    private fun bindViews(root: View) {
        loginCodeInput = root.findViewById(R.id.loginCodeInput)
        statusText = root.findViewById(R.id.statusText)
        emptyText = root.findViewById(R.id.emptyText)
        newNameInput = root.findViewById(R.id.newNameInput)
        newPasswordInput = root.findViewById(R.id.newPasswordInput)
        passwordList = root.findViewById(R.id.passwordList)
        fetchButton = root.findViewById(R.id.fetchButton)
        saveButton = root.findViewById(R.id.saveButton)
        addButton = root.findViewById(R.id.addButton)

        // Restrict code input to 4 digits
        loginCodeInput.keyListener = DigitsKeyListener.getInstance("0123456789")
        loginCodeInput.filters = arrayOf(InputFilter.LengthFilter(4))
        // Default to the firmware's factory code so auto-fetch works without typing
        if (loginCodeInput.text.isNullOrBlank()) {
            loginCodeInput.setText("1122")
        }
    }

    private fun initRecycler() {
        adapter = PasswordAdapter(entries,
            onType = { entry -> sendTypeCommand(entry) },
            onRemove = { index -> removeEntry(index) }
        )
        passwordList.layoutManager = LinearLayoutManager(requireContext())
        passwordList.adapter = adapter
        updateEmptyState()
    }

    private fun initBle() {
        bleManager = BLEManager.getInstance(requireContext()) { status ->
            try {
                requireActivity().runOnUiThread {
                    statusText.text = status
                    if (!autoFetched && status.contains("Connected", ignoreCase = true)) {
                        // Pull existing passwords once when already connected
                        autoFetched = true
                        fetchPasswords(showToasts = false, preferCache = true)
                    }
                }
            } catch (_: Exception) {
                // Ignore if fragment is detached
            }
        }
    }

    private fun setupActions() {
        fetchButton.setOnClickListener { fetchPasswords(showToasts = true) }
        saveButton.setOnClickListener { promptChangeCode() }
        addButton.setOnClickListener { addEntryFromInputs() }
    }

    private fun fetchPasswords(showToasts: Boolean = true, preferCache: Boolean = false) {
        if (isBusy) {
            toast("Please wait for current operation to finish")
            return
        }
        val code = ensureLoginCode() ?: return

        if (preferCache && tryLoadCacheForPin(code)) {
            // Trigger a live refresh in the background if we are already connected
            if (bleManager?.isConnected() == true) {
                view?.post { fetchPasswords(showToasts = false, preferCache = false) }
            }
            return
        }

        if (!ensureConnected(quiet = !showToasts)) return

        Log.d(tag, "fetchPasswords: sending RETRIEVEPW")
        setBusy(true)
        statusText.text = "Requesting passwords..."

        bleManager?.sendCommandWithResponse("RETRIEVEPW") { firstResponse ->
            requireActivity().runOnUiThread outer@{
                Log.d(tag, "RETRIEVEPW response: $firstResponse")
                if (!firstResponse.contains("OK", ignoreCase = true)) {
                    statusText.text = firstResponse
                    setBusy(false)
                    return@outer
                }
                Log.d(tag, "Sending login code for RETRIEVEPW")
                // Add small delay to ensure callback is set before device responds
                requireActivity().window.decorView.postDelayed({
                    bleManager?.sendCommandWithResponse(code) { authResponse ->
                        requireActivity().runOnUiThread {
                            Log.d(tag, "RETRIEVEPW auth response: $authResponse")
                            handleRetrieveResponse(authResponse, code, fromCache = false)
                            setBusy(false)
                        }
                    }
                }, 10)
            }
        }
    }

    private fun maybeAutoFetchOnConnect() {
        // If already connected when fragment opens, fetch immediately
        if (!autoFetched && bleManager?.isConnected() == true) {
            Log.d(tag, "Already connected on open; auto-fetching passwords")
            autoFetched = true
            fetchPasswords(showToasts = false, preferCache = true)
        }
    }

    private fun handleRetrieveResponse(response: String, pinUsed: String, fromCache: Boolean) {
        Log.d(tag, "handleRetrieveResponse raw: $response")
        val parsed = parseCsvResponse(response)
        statusText.text = when {
            parsed.isNotEmpty() && !fromCache -> "Retrieved ${parsed.size} password(s)"
            parsed.isNotEmpty() && fromCache -> "Loaded cached passwords"
            else -> response
        }
        entries.clear()
        entries.addAll(parsed.take(MAX_DEVICES))
        adapter.notifyDataSetChanged()
        updateEmptyState()

        if (!fromCache) {
            bleManager?.updateCachedPin(pinUsed)
            bleManager?.updateCachedPasswords(response)
        }
    }

    private fun pushPasswords() {
        if (!ensureConnected()) return
        if (isBusy) {
            toast("Please wait for current operation to finish")
            return
        }
        val code = ensureLoginCode() ?: return
        
        // Handle empty list - send single space to clear device storage
        val payload = if (entries.isEmpty()) {
            " "  // Device needs at least something to parse
        } else {
            entriesToCsv()
        }
        
        Log.d(tag, "pushPasswords payload: $payload")
        setBusy(true)
        statusText.text = "Sending passwords..."

        try {
            bleManager?.sendCommandWithResponse("PWUPDATE") { first ->
                requireActivity().runOnUiThread {
                    try {
                        if (!first.contains("OK", ignoreCase = true)) {
                            statusText.text = first
                            setBusy(false)
                            return@runOnUiThread
                        }
                        // Delay to allow callback to register
                        requireActivity().window.decorView.postDelayed({
                            bleManager?.sendCommandWithResponse(code) { auth ->
                                requireActivity().runOnUiThread {
                                    try {
                                        if (!auth.contains("OK", ignoreCase = true)) {
                                            statusText.text = auth
                                            setBusy(false)
                                            return@runOnUiThread
                                        }
                                        // Delay to allow callback to register
                                        requireActivity().window.decorView.postDelayed({
                                            bleManager?.sendCommandWithResponse(payload) { finalResp ->
                                                requireActivity().runOnUiThread {
                                                    statusText.text = finalResp
                                                    if (finalResp.contains("OK", ignoreCase = true)) {
                                                        bleManager?.updateCachedPin(code)
                                                        bleManager?.updateCachedPasswords(payload)
                                                    }
                                                    setBusy(false)
                                                }
                                            }
                                        }, 10)
                                    } catch (e: Exception) {
                                        Log.e(tag, "Error in auth response", e)
                                        statusText.text = "Error: ${e.message}"
                                        setBusy(false)
                                    }
                                }
                            }
                        }, 10)
                    } catch (e: Exception) {
                        Log.e(tag, "Error in PWUPDATE response", e)
                        statusText.text = "Error: ${e.message}"
                        setBusy(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending PWUPDATE", e)
            statusText.text = "Error: ${e.message}"
            setBusy(false)
        }
    }

    private fun promptChangeCode() {
        val currentCode = ensureLoginCode() ?: return

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "New 4-digit code"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Change login code")
            .setMessage("Enter new 4-digit code")
            .setView(input)
            .setPositiveButton("Change") { _, _ ->
                val newCode = input.text.toString().trim()
                if (newCode.length != 4) {
                    toast("New code must be 4 digits")
                    return@setPositiveButton
                }
                changeLoginCode(currentCode, newCode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changeLoginCode(oldCode: String, newCode: String) {
        if (!ensureConnected()) return
        if (isBusy) {
            toast("Please wait for current operation to finish")
            return
        }
        setBusy(true)
        statusText.text = "Changing code..."

        bleManager?.sendCommandWithResponse("CHANGELOGIN") { first ->
            requireActivity().runOnUiThread outer@{
                if (!first.contains("OK", ignoreCase = true)) {
                    statusText.text = first
                    setBusy(false)
                    return@outer
                }
                // Delay to allow callback to register
                        requireActivity().window.decorView.postDelayed({
                            bleManager?.sendCommandWithResponse(oldCode) { auth ->
                                requireActivity().runOnUiThread {
                                    if (!auth.contains("OK", ignoreCase = true)) {
                                        statusText.text = auth
                                        setBusy(false)
                                        return@runOnUiThread
                                    }
                                    // Delay to allow callback to register
                                    requireActivity().window.decorView.postDelayed({
                                        bleManager?.sendCommandWithResponse(newCode) { finalResp ->
                                            requireActivity().runOnUiThread {
                                                statusText.text = finalResp
                                                if (finalResp.contains("OK", ignoreCase = true)) {
                                                    loginCodeInput.setText(newCode)
                                                }
                                                setBusy(false)
                                            }
                                        }
                                    }, 10)
                                }
                            }
                        }, 10)
            }
        }
    }

    private fun addEntryFromInputs() {
        val name = newNameInput.text.toString().trim()
        val password = newPasswordInput.text.toString()
        if (name.isEmpty() || password.isEmpty()) {
            toast("Enter a system name and password")
            return
        }
        if (name.contains(",")) {
            toast("System name cannot contain commas (,)")
            return
        }
        if (password.contains(",")) {
            toast("Password cannot contain commas (,)")
            return
        }
        if (entries.size >= MAX_DEVICES) {
            toast("Maximum of $MAX_DEVICES entries")
            return
        }
        entries.add(PasswordEntry(name, password))
        adapter.notifyItemInserted(entries.size - 1)
        updateEmptyState()
        newNameInput.text?.clear()
        newPasswordInput.text?.clear()
        
        // Automatically push to device after adding
        pushPasswords()
    }

    private fun removeEntry(index: Int) {
        if (index in entries.indices) {
            entries.removeAt(index)
            adapter.notifyItemRemoved(index)
            updateEmptyState()
            
            // Automatically push to device after removing
            pushPasswords()
        }
    }

    private fun sendTypeCommand(entry: PasswordEntry) {
        if (!ensureConnected()) return
        if (entry.password.isEmpty()) {
            toast("Password is empty")
            return
        }
        bleManager?.sendCommand("TYPE:${entry.password}")
        toast("Sent TYPE for ${entry.name}")
    }

    private fun parseCsvResponse(response: String): List<PasswordEntry> {
        Log.d(tag, "parseCsvResponse input: '$response'")
        val result = mutableListOf<PasswordEntry>()

        // Remove ALL status messages - use regex to catch all "OK: ..." and "ERR..." patterns
        var cleaned = response
            .replace(Regex("OK:\\s*[^\\n]*"), "")  // Remove any "OK: ..." message
            .replace(Regex("ERR[^\\n]*"), "")      // Remove any "ERR..." message
            .replace(Regex("ERROR[^\\n]*"), "")    // Remove any "ERROR..." message
            .trim()

        // Split by newline first (each password is name,password\n)
        val lines = cleaned.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        // Each line is "Name,Password"
        for (line in lines) {
            val parts = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val name = parts[0]
                val password = parts[1]
                result.add(PasswordEntry(name, password))
            } else if (parts.size == 1) {
                // Only name, no password
                result.add(PasswordEntry(parts[0], ""))
            }
        }
        
        Log.d(tag, "parseCsvResponse output: ${result.size} entries")
        result.forEachIndexed { i, entry ->
            Log.d(tag, "  Entry $i: name='${entry.name}' password='${entry.password}'")
        }
        return result
    }

    private fun entriesToCsv(): String {
        val csv = entries.joinToString(separator = ",") { "${it.name},${it.password}" }
        Log.d(tag, "entriesToCsv: '$csv'")
        Log.d(tag, "entriesToCsv length: ${csv.length} bytes")
        return csv
    }

    private fun ensureLoginCode(): String? {
        val code = loginCodeInput.text.toString().trim()
        return if (code.length == 4) {
            code
        } else {
            statusText.text = "Enter a 4-digit code"
            toast("Enter a 4-digit code")
            null
        }
    }
    
    private fun setBusy(busy: Boolean) {
        isBusy = busy
        fetchButton.isEnabled = !busy
        addButton.isEnabled = !busy
        saveButton.isEnabled = !busy
        adapter.setButtonsEnabled(!busy)
    }

    private fun ensureConnected(quiet: Boolean = false): Boolean {
        val connected = bleManager?.isConnected() == true
        if (!connected && !quiet) {
            toast("Not connected to PWDongle")
        }
        return connected
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadCachedPasswordsIfAvailable(): Boolean {
        val cachedPin = bleManager?.cachedPin ?: return false
        val cachedData = bleManager?.cachedPasswords ?: return false
        isUnlocked = true
        verifiedPin = cachedPin  // Also cache PIN locally for session
        loginCodeInput.setText(cachedPin)
        view?.alpha = 1f
        handleRetrieveResponse(cachedData, cachedPin, fromCache = true)
        return true
    }

    private fun tryLoadCacheForPin(pin: String): Boolean {
        val cachedPin = bleManager?.cachedPin
        val cachedData = bleManager?.cachedPasswords
        val matches = cachedPin == pin && cachedData != null
        if (matches) {
            isUnlocked = true
            view?.alpha = 1f
            handleRetrieveResponse(cachedData!!, pin, fromCache = true)
        }
        return matches
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_DEVICES = 10
    }
}

private class PasswordAdapter(
    private val items: MutableList<PasswordEntry>,
    private val onType: (PasswordEntry) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder>() {

    private var buttonsEnabled = true
    
    fun setButtonsEnabled(enabled: Boolean) {
        buttonsEnabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PasswordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_password_entry, parent, false)
        return PasswordViewHolder(view)
    }

    override fun onBindViewHolder(holder: PasswordViewHolder, position: Int) {
        val entry = items[position]
        holder.bind(entry)
        
        holder.typeButton.isEnabled = buttonsEnabled
        holder.removeButton.isEnabled = buttonsEnabled

        holder.typeButton.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) {
                onType(items[idx])
            }
        }

        holder.removeButton.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) {
                val entryName = items[idx].name
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Remove Password")
                    .setMessage("Remove '$entryName'?")
                    .setPositiveButton("Remove") { _, _ ->
                        onRemove(idx)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        holder.bindWatchers(
            onNameChanged = { newName ->
                val idx = holder.bindingAdapterPosition
                if (idx != RecyclerView.NO_POSITION) {
                    items[idx].name = newName
                }
            },
            onPasswordChanged = { newPass ->
                val idx = holder.bindingAdapterPosition
                if (idx != RecyclerView.NO_POSITION) {
                    items[idx].password = newPass
                }
            }
        )
    }

    override fun getItemCount(): Int = items.size

    class PasswordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameInput: EditText = itemView.findViewById(R.id.nameInput)
        val passwordInput: EditText = itemView.findViewById(R.id.passwordInput)
        val typeButton: Button = itemView.findViewById(R.id.typeButton)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)

        private var nameWatcher: TextWatcher? = null
        private var passwordWatcher: TextWatcher? = null
        private var isPasswordVisible = false
        private var longPressStartTime = 0L

        init {
            // Long-press listener for password visibility toggle
            passwordInput.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        longPressStartTime = System.currentTimeMillis()
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val pressDuration = System.currentTimeMillis() - longPressStartTime
                        if (pressDuration > 500) {  // 500ms long-press threshold
                            togglePasswordVisibility()
                            return@setOnTouchListener true
                        }
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        longPressStartTime = 0L
                    }
                }
                false
            }
        }

        private fun togglePasswordVisibility() {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // Show password
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                // Hide password
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        fun bind(entry: PasswordEntry) {
            nameWatcher?.let { nameInput.removeTextChangedListener(it) }
            passwordWatcher?.let { passwordInput.removeTextChangedListener(it) }

            nameInput.setText(entry.name)
            passwordInput.setText(entry.password)
            isPasswordVisible = false
            passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        fun bindWatchers(onNameChanged: (String) -> Unit, onPasswordChanged: (String) -> Unit) {
            nameWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    onNameChanged(s?.toString() ?: "")
                }
            }
            passwordWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    onPasswordChanged(s?.toString() ?: "")
                }
            }
            nameWatcher?.let { nameInput.addTextChangedListener(it) }
            passwordWatcher?.let { passwordInput.addTextChangedListener(it) }
        }
    }
}

private open class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {}
}
