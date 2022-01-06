package dev.gimme.kwatch

import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

// TODO: API (documentation/naming)
// TODO: Document: cancelled on thread close

/**
 * Watches for changes in the directory representing this path.
 *
 * [onEvent] gets called every time there is an event. The call happens after a delay of [delayMillis] ms and collects
 * all events within that time window into one. If set to 0, all events get sent separately.
 *
 * Throws [IllegalArgumentException] if this path is not an existing directory, and if the directory is deleted later,
 * no further events will be sent.
 *
 * If [recursive] is set to true, events in subdirectories are also included (and their subdirectories etc.).
 *
 * Uses [java.nio.file.WatchService] under the hood to catch the events.
 */
suspend fun Path.watch(
    recursive: Boolean = true,
    delayMillis: Long = 50,
    onEvent: suspend (Event) -> Unit,
): Unit = KWatcher(path = this, recursive = recursive, delayMillis = delayMillis, onEvent = onEvent).run { start() }

/**
 * Watches for changes on the file representing this path.
 *
 * The [block] gets called every time the content of the file is modified or the file is recreated. The call happens
 * after a delay of [delayMillis] ms to avoid multiple calls in quick succession.
 *
 * The file does not have to exist but is guaranteed to exist when the [block] is called.
 *
 * Throws [IllegalArgumentException] if the parent directory does not exist, and if the parent directory is deleted
 * later, no further file changes will be seen.
 *
 * [lastModified] can be used to decide if the [block] should be called once at the start. There are three options:
 *
 * | Value    | Block called at start |
 * | -------- | --------------------- |
 * | Default  | Never                 |
 * | null     | Always                |
 * | Provided | If modified since     |
 */
suspend fun Path.onChange(
    lastModified: FileTime? = lastModifiedOrNull,
    delayMillis: Long = 50,
    block: suspend () -> Unit,
) {
    val parent: Path? = this.absolute().parent
    require(parent != null && parent.exists()) { "Parent directory does not exist" }

    parent.watch(recursive = false, delayMillis = delayMillis) { event ->
        if (!exists()) return@watch

        val fileChanged = event.path.equalsAbsolute(this) &&
                event.actions.any { it in setOf(Event.Action.CREATE, Event.Action.MODIFY) }
        val modifiedBeforeInit = { event.initialization && this.isModifiedSince(lastModified) }

        if (fileChanged || modifiedBeforeInit()) {
            block.invoke()
        }
    }
}

/**
 * Returns if the absolute paths of this and the [other] path are equal.
 */
private fun Path.equalsAbsolute(other: Path) = this.absolute() == other.absolute()

/**
 * Returns if the file located by this path has been modified since [lastModified], or if it doesn't exist,
 * returns true if [lastModified] is null.
 */
private fun Path.isModifiedSince(lastModified: FileTime?) = lastModifiedOrNull != lastModified

/**
 * Returns the last modified time of the file located by this path if it exists.
 */
private val Path.lastModifiedOrNull: FileTime? get() = if (exists()) getLastModifiedTime() else null
