package com.pwdongle.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * On-screen touchpad emulator for mouse control
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var onMouseMove: ((Int, Int) -> Unit)? = null
    private var onMouseClick: ((String) -> Unit)? = null
    private var onMouseScroll: ((Int) -> Unit)? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f
    }
    
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
    }
    
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
    }
    
    private var lastX = 0f
    private var lastY = 0f
    private var isTracking = false
    
    // Button regions
    private lateinit var leftButtonRect: RectF
    private lateinit var rightButtonRect: RectF
    private lateinit var middleButtonRect: RectF
    private lateinit var scrollUpRect: RectF
    private lateinit var scrollDownRect: RectF
    
    private var leftButtonPressed = false
    private var rightButtonPressed = false
    private var middleButtonPressed = false
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val buttonHeight = 50f
        val padding = 10f
        
        // Button layout at bottom
        val buttonY = h - buttonHeight - padding
        val buttonWidth = (w - padding * 4) / 3
        
        leftButtonRect = RectF(padding, buttonY, padding + buttonWidth, buttonY + buttonHeight)
        rightButtonRect = RectF(padding * 2 + buttonWidth, buttonY, padding * 2 + buttonWidth * 2, buttonY + buttonHeight)
        middleButtonRect = RectF(padding * 3 + buttonWidth * 2, buttonY, w - padding, buttonY + buttonHeight)
        
        // Scroll indicators on right
        scrollUpRect = RectF(w - 60f, padding, w - padding, padding + 40f)
        scrollDownRect = RectF(w - 60f, h - padding - 40f, w - padding, h - padding)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Touchpad background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Instructions
        canvas.drawText("Slide to move mouse | Tap buttons below", 20f, 30f, textPaint)
        
        // Draw buttons
        drawButton(canvas, leftButtonRect, "LEFT", leftButtonPressed)
        drawButton(canvas, rightButtonRect, "RIGHT", rightButtonPressed)
        drawButton(canvas, middleButtonRect, "MIDDLE", middleButtonPressed)
        
        // Draw scroll indicators
        drawScrollButton(canvas, scrollUpRect, "↑")
        drawScrollButton(canvas, scrollDownRect, "↓")
    }
    
    private fun drawButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        val paint = if (pressed) activePaint else buttonPaint
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        
        // Draw border
        paint.style = Paint.Style.STROKE
        paint.color = if (pressed) Color.CYAN else Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL
        
        // Draw text
        val textX = rect.centerX() - (textPaint.measureText(label) / 2)
        val textY = rect.centerY() + 5f
        canvas.drawText(label, textX, textY, textPaint)
    }
    
    private fun drawScrollButton(canvas: Canvas, rect: RectF, label: String) {
        buttonPaint.style = Paint.Style.STROKE
        buttonPaint.color = Color.LTGRAY
        buttonPaint.strokeWidth = 2f
        canvas.drawRoundRect(rect, 8f, 8f, buttonPaint)
        buttonPaint.style = Paint.Style.FILL
        
        val textX = rect.centerX() - (textPaint.measureText(label) / 2)
        val textY = rect.centerY() + 5f
        canvas.drawText(label, textX, textY, textPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTracking = true
                lastX = x
                lastY = y
                
                // Check button presses
                when {
                    leftButtonRect.contains(x, y) -> {
                        leftButtonPressed = true
                        onMouseClick?.invoke("left")
                        invalidate()
                    }
                    rightButtonRect.contains(x, y) -> {
                        rightButtonPressed = true
                        onMouseClick?.invoke("right")
                        invalidate()
                    }
                    middleButtonRect.contains(x, y) -> {
                        middleButtonPressed = true
                        onMouseClick?.invoke("middle")
                        invalidate()
                    }
                    scrollUpRect.contains(x, y) -> {
                        onMouseScroll?.invoke(3)
                    }
                    scrollDownRect.contains(x, y) -> {
                        onMouseScroll?.invoke(-3)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTracking && !leftButtonRect.contains(x, y) && 
                    !rightButtonRect.contains(x, y) && !middleButtonRect.contains(x, y)) {
                    
                    val dx = (x - lastX).toInt()
                    val dy = (y - lastY).toInt()
                    
                    if (dx != 0 || dy != 0) {
                        onMouseMove?.invoke(dx, dy)
                        lastX = x
                        lastY = y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                isTracking = false
                leftButtonPressed = false
                rightButtonPressed = false
                middleButtonPressed = false
                invalidate()
            }
        }
        
        return true
    }
    
    fun setOnMouseMoveListener(listener: (dx: Int, dy: Int) -> Unit) {
        onMouseMove = listener
    }
    
    fun setOnMouseClickListener(listener: (button: String) -> Unit) {
        onMouseClick = listener
    }
    
    fun setOnMouseScrollListener(listener: (amount: Int) -> Unit) {
        onMouseScroll = listener
    }
}
