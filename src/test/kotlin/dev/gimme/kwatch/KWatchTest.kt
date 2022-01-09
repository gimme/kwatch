package dev.gimme.kwatch

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

internal class KWatchTest {

    @Test
    fun `watch throws IllegalArgumentException if directory does not exist`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false

        assertThrows<IllegalArgumentException> {
            path.watch {}
        }
    }

    @Test
    fun `watch throws IllegalArgumentException if path is not a directory`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns true
        every { Files.isDirectory(path) } returns false

        assertThrows<IllegalArgumentException> {
            path.watch {}
        }
    }

    @Test
    fun `onChange throws IllegalArgumentException if parent is null`() {
        val path = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false
        every { path.toAbsolutePath() } returns path
        every { path.parent } returns null

        assertThrows<IllegalArgumentException> {
            path.onChange {}
        }
    }

    @Test
    fun `onChange throws IllegalArgumentException if parent directory does not exist`() {
        val path = mockk<Path>()
        val parent = mockk<Path>()

        mockkStatic(Files::exists)
        every { Files.exists(path) } returns false
        every { Files.exists(parent) } returns false
        every { path.toAbsolutePath() } returns path
        every { path.parent } returns parent

        assertThrows<IllegalArgumentException> {
            path.onChange {}
        }
    }
}
