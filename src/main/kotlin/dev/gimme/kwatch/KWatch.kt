@file:JvmName("KWatch")

package dev.gimme.kwatch

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * Listens for changes in the directory represented by this path.
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
 *
 * @return an interface to cancel the listener
 */
fun Path.watch(
    recursive: Boolean = true,
    delayMillis: Long = 50,
    context: CoroutineContext = EmptyCoroutineContext,
    onEvent: (Event) -> Unit,
): Cancellable {
    val watcher = DirectoryWatcher(path = this, recursive = recursive, delayMillis = delayMillis, onEvent = onEvent)

    val scope = CoroutineScope(CoroutineName("kwatch-watcher") + context)

    scope.launch {
        watcher.start()
    }

    return Cancellable { scope.cancel() }
}

/**
 * Listens for changes on the file represented by this path.
 *
 * The [block] gets called every time the content of the file is modified or the file is recreated. The call happens
 * after a delay of [delayMillis] ms to avoid multiple calls in quick succession.
 *
 * The file does not have to exist but is guaranteed to exist when the [block] is called.
 *
 * Throws [IllegalArgumentException] if the parent directory does not exist, and if the parent directory is deleted
 * later, no further file changes will be seen.
 *
 * @return an interface to cancel the listener
 */
@JvmOverloads
fun Path.onChange(
    delayMillis: Long = 50,
    context: CoroutineContext = EmptyCoroutineContext,
    block: () -> Unit,
): Cancellable {
    val parent: Path? = this.absolute().parent
    require(parent != null && parent.exists()) { "Parent directory does not exist" }

    return parent.watch(recursive = false, delayMillis = delayMillis, context = context) { event ->
        if (!exists()) return@watch

        val fileChanged = equalsReal(event.path) &&
                event.actions.any { it in setOf(Event.Action.CREATE, Event.Action.MODIFY) }

        if (fileChanged) {
            block.invoke()
        }
    }
}

/**
 * Returns if the real paths of this and the [other] path are equal.
 */
private fun Path.equalsReal(other: Path) = this.toRealPath() == other.toRealPath()
