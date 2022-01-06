package dev.gimme.kwatch

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.jimfs.WatchServiceConfiguration
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.writeText

internal class KWatchIntegrationTest {

    private lateinit var fileSystem: FileSystem
    private lateinit var directory: Path
    private lateinit var file: Path
    private lateinit var nestedDirectory: Path
    private lateinit var nestedFile: Path

    private val pollRateMillis = 10L

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
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun `Is interruptible`() = runBlocking {
        val job = launch {
            directory.watch {}
        }

        yield()
        delay(100)

        job.cancel()
    }

    @Test
    fun `Watches changes in directory including nested directories`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)

        val job = launch {
            directory.watch(recursive = true, delayMillis = 0, onEvent = onEvent)
        }

        yield()
        delay(10)
        verify { onEvent(Event(directory.absolute(), setOf(Event.Action.INIT))) }

        file.writeText("Lorem")
        delay(50)
        verify { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.writeText("Lorem")
        delay(50)
        verify { onEvent(Event(nestedFile.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.deleteExisting()
        delay(50)
        verify {
            onEvent(Event(nestedFile.absolute(), setOf(Event.Action.DELETE)))
            onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.MODIFY)))
        }

        nestedDirectory.deleteExisting()
        delay(50)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.DELETE))) }

        job.cancel()

        confirmVerified(onEvent)
    }

    @Test
    fun `Watches changes in directory excluding nested directories`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)

        val job = launch {
            directory.watch(recursive = false, delayMillis = 0, onEvent = onEvent)
        }

        yield()
        delay(10)
        verify { onEvent(Event(directory.absolute(), setOf(Event.Action.INIT))) }

        file.writeText("Lorem")
        delay(50)
        verify { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        nestedFile.writeText("Lorem")
        delay(50)

        nestedFile.deleteExisting()
        delay(50)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.MODIFY))) }

        nestedDirectory.deleteExisting()
        delay(50)
        verify { onEvent(Event(nestedDirectory.absolute(), setOf(Event.Action.DELETE))) }

        job.cancel()

        confirmVerified(onEvent)
    }

    @Test
    fun `Handles folder structure modifications`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)
        val newDir = directory.resolve("newDir")
        val newFile = directory.resolve("newFile.txt")

        val job = launch {
            directory.watch(recursive = true, delayMillis = 0, onEvent = onEvent)
        }

        yield()
        delay(10)
        verify { onEvent(Event(directory.absolute(), setOf(Event.Action.INIT))) }

        newDir.createDirectory()
        delay(50)
        verify { onEvent(Event(newDir.absolute(), setOf(Event.Action.CREATE))) }

        newFile.createFile()
        delay(50)
        verify { onEvent(Event(newFile.absolute(), setOf(Event.Action.CREATE))) }

        newFile.writeText("Lorem")
        delay(50)
        verify { onEvent(Event(newFile.absolute(), setOf(Event.Action.MODIFY))) }

        job.cancel()

        confirmVerified(onEvent)
    }

    @Test
    fun `Pools together events in quick succession`() = runBlocking {
        val onEvent: (Event) -> Unit = mockk(relaxed = true)
        val delayMillis = 50L

        val job = launch {
            directory.watch(recursive = true, delayMillis = delayMillis, onEvent = onEvent)
        }

        yield()
        delay(10)
        verify { onEvent(Event(directory.absolute(), setOf(Event.Action.INIT))) }

        file.writeText("Lorem")
        delay(100)

        verify(exactly = 1) { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        file.writeText("ipsum")
        delay(10)
        file.writeText("dolor")
        delay(100)

        verify(exactly = 2) { onEvent(Event(file.absolute(), setOf(Event.Action.MODIFY))) }

        job.cancel()

        confirmVerified(onEvent)
    }

    @Test
    fun `Listens to single file changes`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        val job = launch {
            file.onChange(block = onChange)
        }

        yield()
        delay(10)

        file.writeText("Lorem")
        delay(100)
        verify(exactly = 1) { onChange() }

        job.cancel()

        confirmVerified(onChange)
    }

    @Test
    fun `Does not fire event when file does not exist`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        val job = launch {
            nestedFile.onChange(block = onChange)
        }

        yield()
        delay(10)

        nestedFile.deleteExisting()
        delay(100)

        nestedDirectory.deleteExisting()
        delay(100)

        job.cancel()

        verify(exactly = 0) { onChange() }
        confirmVerified(onChange)
    }

    @Test
    fun `File does not have to exist when watching it`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        val file = directory.resolve("file2308.txt")

        val job = launch {
            file.onChange(block = onChange)
        }

        yield()
        delay(10)

        file.createFile()
        delay(100)
        verify(exactly = 1) { onChange() }

        file.writeText("Lorem")
        delay(100)
        verify(exactly = 2) { onChange() }

        job.cancel()

        confirmVerified(onChange)
    }

    @Test
    fun `Pools together changes in quick succession`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)
        val delayMillis = 50L

        val job = launch {
            file.onChange(delayMillis = delayMillis, block = onChange)
        }

        yield()

        file.writeText("Lorem")
        delay(100)

        verify(exactly = 1) { onChange() }

        file.writeText("ipsum")
        delay(10)
        file.writeText("dolor")
        delay(100)

        verify(exactly = 2) { onChange() }

        job.cancel()

        confirmVerified(onChange)
    }

    @Test
    fun `Catches file changes during initialization`() = runBlocking {
        val onChange: () -> Unit = mockk(relaxed = true)

        val lastModified = file.getLastModifiedTime()

        file.writeText("Lorem")

        val job = launch {
            file.onChange(lastModified = lastModified, block = onChange)
        }

        yield()
        verify(exactly = 1) { onChange() }

        job.cancel()

        confirmVerified(onChange)
    }
}
