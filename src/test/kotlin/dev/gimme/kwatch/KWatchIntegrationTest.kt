package dev.gimme.kwatch

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.jimfs.WatchServiceConfiguration
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

internal class KWatchIntegrationTest {

    private lateinit var fileSystem: FileSystem
    private lateinit var directory: Path
    private lateinit var file: Path
    private lateinit var nestedDirectory: Path
    private lateinit var nestedFile: Path

    private val pollRateMillis = 10L
    private val fileDelay = 100L

    @BeforeEach
    fun setUp() {
        fileSystem = Jimfs.newFileSystem(
            Configuration.unix()
                .toBuilder()
                .setWatchServiceConfiguration(
                    WatchServiceConfiguration.polling(pollRateMillis, TimeUnit.MILLISECONDS)
                )
                .build()
        )

        directory = fileSystem.getPath("directory")
        directory.createDirectory()

        file = directory.resolve("file.txt")
        file.createFile()

        nestedDirectory = directory.resolve("nestedDirectory")
        nestedDirectory.createDirectory()

        nestedFile = nestedDirectory.resolve("nestedFile.txt")
        nestedFile.createFile()
    }

    @Test
    fun `Catches events immediately after starting listener`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)

        directory.watch(delayMillis = 0, onEvent = onEvent)

        verify(exactly = 0) { onEvent(any()) }

        file.writeText("Lorem")
        delay(fileDelay)

        verify(exactly = 1) { onEvent(any()) }
    }

    @Test
    fun `Listener is cancellable`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)
        val listener = directory.watch(onEvent = onEvent)

        file.writeText("Lorem")
        delay(fileDelay)
        verify(exactly = 1) { onEvent(any()) }

        listener.cancel()

        file.writeText("ipsum")
        delay(fileDelay)
        confirmVerified(onEvent)
    }

    @Test
    fun `Watches changes in directory including nested directories`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)

        directory.watch(recursive = true, delayMillis = 0, onEvent = onEvent)

        file.writeText("Lorem")
        delay(fileDelay)
        verify { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.writeText("Lorem")
        delay(fileDelay)
        verify { onEvent(Event(nestedFile.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.deleteExisting()
        delay(fileDelay)
        verify {
            onEvent(Event(nestedFile.absolute(), setOf(Event.Action.DELETE)))
            onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.MODIFY)))
        }

        nestedDirectory.deleteExisting()
        delay(fileDelay)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.DELETE))) }

        confirmVerified(onEvent)
    }

    @Test
    fun `Watches changes in directory excluding nested directories`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)

        directory.watch(recursive = false, delayMillis = 0, onEvent = onEvent)

        file.writeText("Lorem")
        delay(fileDelay)
        verify { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.writeText("Lorem")
        delay(fileDelay)

        nestedFile.deleteExisting()
        delay(fileDelay)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.MODIFY))) }

        nestedDirectory.deleteExisting()
        delay(fileDelay)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.DELETE))) }

        confirmVerified(onEvent)
    }

    @Test
    fun `Handles folder structure modifications`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)
        val newDir = directory.resolve("newDir")
        val newFile = directory.resolve("newFile.txt")

        directory.watch(recursive = true, delayMillis = 0, onEvent = onEvent)

        newDir.createDirectory()
        delay(fileDelay)
        verify { onEvent(Event(newDir.absolute(), setOf(Event.Action.CREATE))) }

        newFile.createFile()
        delay(fileDelay)
        verify { onEvent(Event(newFile.absolute(), setOf(Event.Action.CREATE))) }

        newFile.writeText("Lorem")
        delay(fileDelay)
        verify { onEvent(Event(newFile.absolute(), setOf(Event.Action.MODIFY))) }

        confirmVerified(onEvent)
    }

    @Test
    fun `Pools together events in quick succession`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)
        val delayMillis = 100L

        directory.watch(recursive = true, delayMillis = delayMillis, onEvent = onEvent)

        file.writeText("Lorem")
        delay(200)

        verify(exactly = 1) { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        file.writeText("ipsum")
        delay(10)
        file.deleteExisting()
        delay(100)

        verify(exactly = 1) { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY, Event.Action.DELETE))) }

        confirmVerified(onEvent)
    }

    @Test
    fun `Listens to single file changes`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        file.onChange(block = onChange)

        file.writeText("Lorem")
        delay(fileDelay)
        verify(exactly = 1) { onChange() }
    }

    @Test
    fun `Does not fire event when file does not exist`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        nestedFile.onChange(block = onChange)

        nestedFile.deleteExisting()
        delay(fileDelay)

        nestedDirectory.deleteExisting()
        delay(fileDelay)

        verify(exactly = 0) { onChange() }
    }

    @Test
    fun `File does not have to exist when watching it`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        val file = directory.resolve("file2308.txt")

        file.onChange(delayMillis = 0, block = onChange)

        file.createFile()
        delay(fileDelay)
        verify(exactly = 1) { onChange() }

        file.writeText("Lorem")
        delay(fileDelay)
        verify(exactly = 2) { onChange() }
    }

    @Test
    fun `Pools together file changes in quick succession`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)
        val delayMillis = 50L

        file.onChange(delayMillis = delayMillis, block = onChange)

        file.writeText("Lorem")
        delay(100)

        verify(exactly = 1) { onChange() }

        file.writeText("ipsum")
        delay(10)
        file.writeText("dolor")
        delay(100)

        verify(exactly = 2) { onChange() }
    }
}
