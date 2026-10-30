package de.westnordost.streetcomplete.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.Sink
import kotlinx.io.Source
import no.synth.kmpzip.kotlinx.ZipOutputStream
import no.synth.kmpzip.zip.ZipEntry

/** A lazily opened file; its source is closed after the archive entry is written. */
data class ArchiveSource(val name: String, val open: suspend () -> Source)

/** Streams files into a ZIP archive and closes [output] before returning successfully. */
suspend fun writeZipArchive(output: Sink, entries: List<ArchiveSource>) {
    require(entries.map { it.name }.toSet().size == entries.size) { "Duplicate archive entry names" }
    val buffer = ByteArray(64 * 1024)
    ZipOutputStream(output).use { zip ->
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            zip.putNextEntry(ZipEntry(entry.name))
            entry.open().use { source ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.readAtMostTo(buffer)
                    if (count == -1) break
                    check(count > 0) { "Archive source made no progress" }
                    zip.write(buffer, 0, count)
                }
            }
            zip.closeEntry()
        }
    }
}