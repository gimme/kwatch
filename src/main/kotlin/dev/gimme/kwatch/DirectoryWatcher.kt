package dev.gimme.kwatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Watches for changes in the directory represented by the [path]. Starts watching for events immediately, and after
 * [start] has been called, [onEvent] continuously gets called for each event seen by this watcher.
 */
internal class DirectoryWatcher(
    path: Path,
    /**
     * If events from subdirectories (and their subdirectories etc.) should be included.
     */
    private val recursive: Boolean = true,
    /**
     * Delay in milliseconds after getting an event to wait for more events before sending them. This is useful to avoid
     * having [onEvent] called multiple times in quick succession when multiple events are spawned from "one change".
     */
    private val delayMillis: Long = 50,
    /**
     * The function to be called on each event.
     */
    private val onEvent: suspend (Event) -> Unit,
) {

    init {
        require(path.exists()) { "Path does not exist" }
        require(path.isDirectory()) { "Path is not a directory" }
    }

    private val path: Path = path.absolute()

    private val watchService: WatchService = path.fileSystem.newWatchService()
    private val watchKeys = mutableListOf<WatchKey>()

    init {
        updateWatchKeys()
    }

    /**
     * Starts calling [onEvent] based on events seen by this watcher.
     */
    suspend fun start() = withContext(EmptyCoroutineContext) {
        coroutineContext.job.invokeOnCompletion { watchService.close() }

        var structureModified = false

        while (true) {
            if (recursive && structureModified) {
                updateWatchKeys()
                structureModified = false
            }

            val key = take()
            val eventDir = key.watchable() as Path

            delay(delayMillis)

            key.pollEvents()
                .groupBy({ eventDir.resolve(it.context() as Path) }) { it.kind() }
                .mapNotNull { (eventPath, kinds) ->
                    val eventActions: Set<Event.Action> = kinds.map { kind ->
                        when (kind) {
                            ENTRY_CREATE -> Event.Action.CREATE
                            ENTRY_DELETE -> Event.Action.DELETE
                            ENTRY_MODIFY -> Event.Action.MODIFY
                            else -> return@mapNotNull null
                        }
                    }.toSet()
                    Event(path = eventPath, actions = eventActions)
                }
                .forEach { event: Event ->
                    onEvent(event)

                    if (event.modifiedStructure) {
                        structureModified = true
                    }
                }

            key.reset()
        }
    }

    private suspend fun take(): WatchKey = runInterruptible(Dispatchers.IO) {
        watchService.take()
    }

    /**
     * Updates the watch key registrations to match the current folder structure of the watched [path].
     */
    private fun updateWatchKeys() {
        val paths = mutableSetOf<Path>()

        if (recursive) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    paths.add(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            paths.add(path)
        }

        val currentWatchPaths = watchKeys.map { key -> key.watchable() }.toSet()
        val oldKeys = watchKeys.filter { it.watchable() !in paths }.onEach { it.cancel() }
        val newKeys = paths.filter { it !in currentWatchPaths }.map { it.register() }

        watchKeys.removeAll(oldKeys)
        watchKeys.addAll(newKeys)
    }

    /**
     * Registers this path with the [watchService] and returns the generated watch key.
     */
    private fun Path.register() = register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
}
