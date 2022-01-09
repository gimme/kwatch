package dev.gimme.kwatch

import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Represents an event happening to a file/directory.
 */
data class Event(
    /**
     * The path to the file/directory that this event happened to.
     */
    val path: Path,
    /**
     * The actions included in this event.
     */
    val actions: Set<Action>,
) {

    /**
     * A type of action done to a file/directory.
     */
    enum class Action {
        CREATE,
        DELETE,
        MODIFY,
    }

    /**
     * If the event resulted in a folder structure modification.
     */
    internal val modifiedStructure =
        path.isDirectory() && actions.any { it in setOf(Action.CREATE, Action.DELETE) }
}
