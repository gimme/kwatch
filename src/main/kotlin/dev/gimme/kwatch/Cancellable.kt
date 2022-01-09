package dev.gimme.kwatch

/**
 * Represents a cancellable task.
 */
fun interface Cancellable {

    /**
     * Cancels this task.
     */
    fun cancel()
}
