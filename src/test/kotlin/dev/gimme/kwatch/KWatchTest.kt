package dev.gimme.kwatch

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class KWatchTest {

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `watch throws IllegalArgumentException if directory does not exist`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false

        assertThrows<IllegalArgumentException> {
            runBlocking {
                path.watch {}
            }
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `watch throws IllegalArgumentException if path is not a directory`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns true
        every { Files.isDirectory(path) } returns false

        assertThrows<IllegalArgumentException> {
            runBlocking {
                path.watch {}
            }
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `onChange throws IllegalArgumentException if parent is null`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false
        every { path.toAbsolutePath() } returns path
        every { path.parent } returns null

        assertThrows<IllegalArgumentException> {
            runBlocking {
                path.onChange {}
            }
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `onChange throws IllegalArgumentException if parent directory does not exist`() {
        val path = mockk<Path>()
        val parent = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false
        every { Files.exists(parent) } returns false
        every { path.toAbsolutePath() } returns path
        every { path.parent } returns parent

        assertThrows<IllegalArgumentException> {
            runBlocking {
                path.onChange {}
            }
        }
    }
}
