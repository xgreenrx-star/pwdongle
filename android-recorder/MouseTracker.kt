package com.pwdongle.recorder

/**
 * Mouse Position Tracker
 * 
 * Tracks absolute mouse position for recording
 */
class MouseTracker {
    var x: Int = 0
        private set
    
    var y: Int = 0
        private set
    
    fun updatePosition(newX: Int, newY: Int) {
        x = newX
        y = newY
    }
    
    fun reset() {
        x = 0
        y = 0
    }
    
    fun isAtOrigin(tolerance: Int = 50): Boolean {
        return x <= tolerance && y <= tolerance
    }
}
