@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.westnordost.streetcomplete

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.IosNearbyQuestMonitor
import de.westnordost.streetcomplete.ui.common.IncomingFileImportDialog
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIViewController

/** Holds incoming links and private document copies until Compose is ready to consume them. */
class IosAppLinks {
    internal var uri by mutableStateOf<String?>(null)
        private set
    private val importedFiles = mutableStateListOf<String>()
    internal val importedFilePath: String? get() = importedFiles.firstOrNull()
    internal var fileImportError by mutableStateOf(false)
        private set

    fun openUri(uri: String) { this.uri = uri }
    internal fun consume() { uri = null }
    fun openImportedFile(path: String) { importedFiles.add(path) }
    fun notifyFileImportError() { fileImportError = true }
    internal fun consumeFileImportError() { fileImportError = false }

    internal fun consumeImportedFile(path: String) {
        importedFiles.remove(path)
        // Only delete the UUID directory created by the Swift document copier.
        val root = NSTemporaryDirectory().trimEnd('/') + "/SCEEImports/"
        val relative = path.removePrefix(root)
        if (relative == path || relative.split('/').any { it == ".." || it == "." }) return
        val parts = relative.split('/')
        if (parts.size != 2 || !parts[0].matches(Regex("[0-9a-fA-F-]{36}"))) return
        runCatching {
            SystemFileSystem.delete(Path(path))
            SystemFileSystem.delete(Path(root + parts[0]))
        }
    }
}

fun MainViewController(links: IosAppLinks): UIViewController = ComposeUIViewController {
    App(uri = links.uri, onConsumedUri = links::consume,
        platformDialogs = {
            IosNearbyQuestMonitor()
            links.importedFilePath?.let { path ->
                key(path) {
                    IncomingFileImportDialog(
                        file = remember(path) { PlatformFile(path) },
                        onDismissRequest = { links.consumeImportedFile(path) },
                    )
                }
            }
            if (links.fileImportError) AlertDialog(
                onDismissRequest = links::consumeFileImportError,
                text = { Text(stringResource(Res.string.import_error)) },
                confirmButton = { TextButton(links::consumeFileImportError) { Text(stringResource(Res.string.ok)) } },
            )
        },
    )
}
