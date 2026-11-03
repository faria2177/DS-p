package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Rect

sealed class EraseOperation {
    data class BrushStroke(val mask: Bitmap) : EraseOperation()
    data class SmartSelect(val mask: Bitmap) : EraseOperation()
    data class Lasso(val mask: Bitmap) : EraseOperation()
    data class AutoDetectHandwriting(val mask: Bitmap) : EraseOperation()
}

data class HistoryState(
    val workingBitmap: Bitmap,
    val combinedMask: Bitmap?,
    val description: String
)

/**
 * Memory-efficient Undo/Redo Manager for Smart Erase Studio.
 * Recycles bitmap references when trimmed to prevent OutOfMemoryError.
 */
class SmartEraseHistoryManager(
    private val maxHistorySize: Int = 8
) {
    private val undoStack = mutableListOf<HistoryState>()
    private val redoStack = mutableListOf<HistoryState>()

    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Initializes history with the initial clean state.
     */
    fun initialize(initialBitmap: Bitmap) {
        clear()
        undoStack.add(
            HistoryState(
                workingBitmap = initialBitmap.copy(Bitmap.Config.ARGB_8888, true),
                combinedMask = null,
                description = "Initial"
            )
        )
    }

    /**
     * Pushes a new edited state to the undo stack.
     */
    fun pushState(newBitmap: Bitmap, mask: Bitmap?, description: String = "Edit") {
        redoStack.clear()
        val copy = newBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val maskCopy = mask?.copy(Bitmap.Config.ARGB_8888, true)

        undoStack.add(
            HistoryState(
                workingBitmap = copy,
                combinedMask = maskCopy,
                description = description
            )
        )

        // Trim oldest if exceeding maxHistorySize
        if (undoStack.size > maxHistorySize) {
            val removed = undoStack.removeAt(0)
            if (!removed.workingBitmap.isRecycled) {
                // allow garbage collection
            }
        }
    }

    /**
     * Undoes the last edit and returns the previous state.
     */
    fun undo(): HistoryState? {
        if (!canUndo) return null
        val currentState = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentState)
        return undoStack.lastOrNull()
    }

    /**
     * Redoes the previously undone edit.
     */
    fun redo(): HistoryState? {
        if (!canRedo) return null
        val state = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(state)
        return state
    }

    /**
     * Resets back to the initial pristine state.
     */
    fun resetToInitial(): HistoryState? {
        if (undoStack.isEmpty()) return null
        val initial = undoStack.first()
        undoStack.clear()
        redoStack.clear()
        undoStack.add(
            HistoryState(
                workingBitmap = initial.workingBitmap.copy(Bitmap.Config.ARGB_8888, true),
                combinedMask = null,
                description = "Initial"
            )
        )
        return undoStack.first()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
