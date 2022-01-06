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

internal class KWatcher(
    path: Path,
    private val recursive: Boolean = true,
    /**
     * Avoids multiple events being fired in quick succession by collecting them during a period.
     * TODO: https://stackoverflow.com/questions/16777869/java-7-watchservice-ignoring-multiple-occurrences-of-the-same-event
     */
    private val delayMillis: Long = 50,
    private val onEvent: suspend (Event) -> Unit,
) {

    init {
        require(path.exists()) { "Path does not exist" }
        require(path.isDirectory()) { "Path is not a directory" }
    }

    private val path: Path = path.absolute()

    private val watchService: WatchService = path.fileSystem.newWatchService()
    private val watchKeys = mutableListOf<WatchKey>()

    /**
     * Starts watching TODO
     */
    suspend fun start() = withContext(EmptyCoroutineContext) {
        coroutineContext.job.invokeOnCompletion { watchService.close() }
        updateWatchKeys()

        onEvent(Event(path, setOf(Event.Action.INIT)))

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
    private suspend fun updateWatchKeys() {
        watchKeys.forEach { it.cancel() }
        watchKeys.clear()

        if (recursive) runInterruptible(Dispatchers.IO) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    watchKeys += dir.register()
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            watchKeys += path.register()
        }
    }

    /**
     * Registers this path with the [watchService].
     */
    private fun Path.register() = register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
}
