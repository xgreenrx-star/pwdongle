package com.pwdongle.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

private data class KeySpec(
    val label: String,
    val code: String = label,
    val weight: Float = 1f
)

/**
 * On-screen keyboard for text input and key commands
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var onKeyPress: ((String) -> Unit)? = null
    private var onTextInput: ((String) -> Unit)? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
    }
    
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2d2d2d")
    }
    
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * resources.displayMetrics.density
    }
    
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3f7fff")
    }
    
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var shiftActive = false
    private var capsLockActive = false
    private val activeModifiers = mutableSetOf<String>()
    
    private val keyRects = mutableMapOf<KeySpec, RectF>()
    private var selectedKey: KeySpec? = null
    
    // Standard keyboard layout with function keys, symbols, modifiers, arrows, and nav block
    private val keyRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("ESC", code = "esc", weight = 1.1f),
            KeySpec("F1", code = "f1"), KeySpec("F2", code = "f2"), KeySpec("F3", code = "f3"), KeySpec("F4", code = "f4"),
            KeySpec("F5", code = "f5"), KeySpec("F6", code = "f6"), KeySpec("F7", code = "f7"), KeySpec("F8", code = "f8"),
            KeySpec("F9", code = "f9"), KeySpec("F10", code = "f10"), KeySpec("F11", code = "f11"), KeySpec("F12", code = "f12"),
            KeySpec("PRT", code = "print", weight = 1.1f)
        ),
        listOf(
            KeySpec("`"), KeySpec("1"), KeySpec("2"), KeySpec("3"), KeySpec("4"), KeySpec("5"),
            KeySpec("6"), KeySpec("7"), KeySpec("8"), KeySpec("9"), KeySpec("0"), KeySpec("-"), KeySpec("="),
            KeySpec("⌫", code = "backspace", weight = 1.6f)
        ),
        listOf(
            KeySpec("TAB", code = "tab", weight = 1.3f),
            KeySpec("q"), KeySpec("w"), KeySpec("e"), KeySpec("r"), KeySpec("t"),
            KeySpec("y"), KeySpec("u"), KeySpec("i"), KeySpec("o"), KeySpec("p"),
            KeySpec("["), KeySpec("]"), KeySpec("\\")
        ),
        listOf(
            KeySpec("CAPS", code = "capslock", weight = 1.6f),
            KeySpec("a"), KeySpec("s"), KeySpec("d"), KeySpec("f"), KeySpec("g"),
            KeySpec("h"), KeySpec("j"), KeySpec("k"), KeySpec("l"),
            KeySpec(";"), KeySpec("'"),
            KeySpec("ENTER", code = "enter", weight = 1.8f)
        ),
        listOf(
            KeySpec("SHIFT", code = "shift", weight = 1.8f),
            KeySpec("z"), KeySpec("x"), KeySpec("c"), KeySpec("v"), KeySpec("b"),
            KeySpec("n"), KeySpec("m"), KeySpec(","), KeySpec("."), KeySpec("/"),
            KeySpec("SHIFT", code = "shift", weight = 1.8f)
        ),
        listOf(
            KeySpec("CTRL", code = "ctrl", weight = 1.3f),
            KeySpec("ALT", code = "alt", weight = 1.3f),
            KeySpec("GUI", code = "gui", weight = 1.3f),
            KeySpec("SPACE", code = "space", weight = 4.5f),
            KeySpec("ALT", code = "alt", weight = 1.3f),
            KeySpec("MENU", code = "menu", weight = 1.2f),
            KeySpec("CTRL", code = "ctrl", weight = 1.3f)
        ),
        listOf(
            KeySpec("INS", code = "ins"), KeySpec("DEL", code = "del"),
            KeySpec("HOME", code = "home"), KeySpec("END", code = "end"),
            KeySpec("PGU", code = "pgup"), KeySpec("PGD", code = "pgdn"),
            KeySpec("◀", code = "left"), KeySpec("▲", code = "up"), KeySpec("▼", code = "down"), KeySpec("▶", code = "right")
        )
    )

    private val shiftedSymbols = mapOf(
        "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
        "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")",
        "-" to "_", "=" to "+", "[" to "{", "]" to "}", "\\" to "|",
        ";" to ":", "'" to "\"", "," to "<", "." to ">", "/" to "?",
        "`" to "~"
    )

    private val commandKeys = setOf(
        "backspace", "enter", "up", "down", "left", "right", "esc", "tab", "capslock",
        "shift", "ctrl", "alt", "gui", "menu", "ins", "del", "home", "end", "pgup", "pgdn",
        "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12", "print"
    )
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (640 * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(widthMeasureSpec, height)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val density = resources.displayMetrics.density
        val padding = (10 * density).toInt()
        val gap = 8 * density
        val totalWidth = w - padding * 2
        
        keyHeight = (h.toFloat() - padding * 2 - gap * (keyRows.size - 1)) / keyRows.size
        
        keyRects.clear()
        var y = padding.toFloat()
        
        keyRows.forEach { row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val availableWidth = totalWidth - gap * (row.size - 1)
            val unit = availableWidth / totalWeight
            var x = padding.toFloat()
            row.forEach { key ->
                val widthForKey = unit * key.weight
                val rect = RectF(x, y, x + widthForKey, y + keyHeight)
                keyRects[key] = rect
                x += widthForKey + gap
            }
            y += keyHeight + gap
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw keys
        keyRects.forEach { (key, rect) ->
            val isPrimary = key.code.matches(Regex("[a-z0-9]")) || key.code == "space"
            val isActiveModifier = key.code.lowercase() in activeModifiers || (key.code.lowercase() == "shift" && shiftActive) || (key.code.lowercase() == "capslock" && capsLockActive)
            val isSelected = key == selectedKey || isActiveModifier
            
            val drawPaint = when {
                isSelected -> activePaint
                isPrimary -> keyPaint
                else -> {
                    Paint(keyPaint).apply {
                        color = Color.parseColor("#3a3a3a")
                    }
                }
            }
            
            canvas.drawRoundRect(rect, 10f, 10f, drawPaint)
            
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = if (isSelected) Color.parseColor("#7fb2ff") else Color.parseColor("#555555")
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(rect, 10f, 10f, borderPaint)
            
            val displayText = key.label.uppercase()
            val textX = rect.centerX() - (textPaint.measureText(displayText) / 2)
            val textY = rect.centerY() + (textPaint.textSize / 3)
            canvas.drawText(displayText, textX, textY, textPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find touched key
                keyRects.forEach { (key, rect) ->
                    if (rect.contains(x, y)) {
                        selectedKey = key
                        invalidate()
                        return@forEach
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val key = selectedKey
                if (key != null) {
                    val code = key.code.lowercase()
                    when (code) {
                        "shift" -> {
                            shiftActive = !shiftActive
                            if (shiftActive) activeModifiers.add("shift") else activeModifiers.remove("shift")
                        }
                        "capslock" -> capsLockActive = !capsLockActive
                        "ctrl", "alt", "gui", "menu" -> {
                            if (activeModifiers.contains(code)) activeModifiers.remove(code) else activeModifiers.add(code)
                        }
                        else -> {
                            val shifted = shiftActive.xor(capsLockActive)
                            val baseChar = when {
                                code == "space" -> " "
                                code.length == 1 -> if (shifted) key.code.uppercase() else key.code
                                code in shiftedSymbols && shifted -> shiftedSymbols[code] ?: key.code
                                else -> key.code
                            }

                            val modifiers = activeModifiers.toList().sortedWith(
                                compareBy({ it != "ctrl" }, { it != "shift" }, { it != "alt" }, { it })
                            )

                            val isCharLike = code.length == 1 || code in shiftedSymbols || code == "space"
                            if (isCharLike) {
                                if (modifiers.isNotEmpty()) {
                                    val combo = modifiers.joinToString("+") + "+" + baseChar
                                    onKeyPress?.invoke(combo)
                                } else {
                                    onTextInput?.invoke(baseChar)
                                }
                            } else {
                                if (modifiers.isNotEmpty()) {
                                    val combo = modifiers.joinToString("+") + "+" + code
                                    onKeyPress?.invoke(combo)
                                } else {
                                    onKeyPress?.invoke(code)
                                }
                            }

                            if (shiftActive && !capsLockActive) {
                                shiftActive = false
                                activeModifiers.remove("shift")
                            }
                        }
                    }
                }
                selectedKey = null
                invalidate()
            }
            else -> { /* no-op for move/cancel */ }
        }
        
        return true
    }
    
    fun setOnKeyPressListener(listener: (keyName: String) -> Unit) {
        onKeyPress = listener
    }
    
    fun setOnTextInputListener(listener: (text: String) -> Unit) {
        onTextInput = listener
    }
}
