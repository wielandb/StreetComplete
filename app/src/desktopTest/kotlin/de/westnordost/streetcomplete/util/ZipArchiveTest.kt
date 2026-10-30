package de.westnordost.streetcomplete.util

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipArchiveTest {
    @Test fun `standard Java ZIP reader reads all streamed entries including Unicode and empty files`(): Unit = runBlocking {
        val photo = ByteArray(200_000) { (it * 31).toByte() }
        val expected = linkedMapOf(
            "notex.gpx" to "<gpx><wpt lat=\"1\" lon=\"2\"/></gpx>".encodeToByteArray(),
            "Bäume.jpg" to photo,
            "empty.txt" to byteArrayOf(),
        )
        val sink = Buffer()
        writeZipArchive(sink, expected.map { (name, bytes) ->
            ArchiveSource(name) { Buffer().apply { write(bytes) } }
        })
        val actual = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(sink.readByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                actual[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) -> assertContentEquals(bytes, actual[name]) }
    }

    @Test fun `source failures propagate and close the archive output`(): Unit = runBlocking {
        var closed = false
        val sink = object : RawSink by Buffer() {
            override fun close() { closed = true }
        }.buffered()
        assertFailsWith<IOException> {
            writeZipArchive(sink, listOf(ArchiveSource("photo.jpg") { throw IOException("Read failed") }))
        }
        assertTrue(closed)
    }

    @Test fun `a final output close failure is reported to the exporter`(): Unit = runBlocking {
        val sink = object : RawSink by Buffer() {
            override fun close() { throw IOException("Close failed") }
        }.buffered()
        assertFailsWith<IOException> {
            writeZipArchive(sink, listOf(ArchiveSource("empty") { Buffer() }))
        }
    }
}