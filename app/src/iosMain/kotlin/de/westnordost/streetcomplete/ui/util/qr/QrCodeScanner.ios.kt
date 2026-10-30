@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.ui.util.qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.*
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume
import kotlinx.cinterop.ObjCAction

@Composable
actual fun rememberQrCodeScanner(onResult: (QrScanResult) -> Unit): QrCodeScanner {
    val presenter = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val cancel = stringResource(Res.string.cancel)
    val scanner = remember(presenter, cancel) {
        IosQrCodeScanner(presenter, cancel) { currentOnResult(it) }
    }
    DisposableEffect(scanner) { onDispose { scanner.dispose() } }
    return remember(scanner, scope) {
        QrCodeScanner {
            scope.launch {
                val permitted = suspendCancellableCoroutine { continuation ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        if (continuation.isActive) continuation.resume(granted)
                    }
                }
                if (permitted) scanner.scan() else currentOnResult(QrScanResult.PermissionDenied)
            }
        }
    }
}

private class IosQrCodeScanner(
    private val presenter: UIViewController,
    private val cancelTitle: String,
    private val onResult: (QrScanResult) -> Unit,
) {
    private var controller: QrScannerViewController? = null

    fun scan() {
        if (controller != null) return
        val scanner = QrScannerViewController(cancelTitle) { result ->
            controller = null
            onResult(result)
        }
        controller = scanner
        var top = presenter
        while (top.presentedViewController != null) top = top.presentedViewController!!
        val navigation = UINavigationController(rootViewController = scanner)
        navigation.modalPresentationStyle = UIModalPresentationFullScreen
        top.presentViewController(navigation, animated = true, completion = null)
    }

    fun dispose() {
        controller?.finish(QrScanResult.Cancelled)
        controller = null
    }
}

/** Keeps the capture session on one serial queue and UI/result callbacks on the main queue. */
private class QrScannerViewController(
    private val cancelTitle: String,
    private val onResult: (QrScanResult) -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private val session = AVCaptureSession()
    private val captureQueue = dispatch_queue_create("scee.qr.capture", null)
    private val preview = AVCaptureVideoPreviewLayer(session = session)
    private var finished = false
    private var configured = false
    // AVFoundation holds a weak delegate reference, so retain it for the whole session.
    private val metadataDelegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection,
        ) {
            val text = didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstOrNull { it.type == AVMetadataObjectTypeQRCode }?.stringValue
            if (!text.isNullOrBlank()) finish(QrScanResult.Success(text))
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title = cancelTitle,
            style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
            target = this,
            action = NSSelectorFromString("cancelScan"),
        )
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(preview)
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        if (configured || finished) return
        configured = true
        dispatch_async(captureQueue) {
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
            val output = AVCaptureMetadataOutput()
            if (input == null || !session.canAddInput(input)) {
                fail()
                return@dispatch_async
            }
            session.beginConfiguration()
            session.addInput(input)
            if (!session.canAddOutput(output)) {
                session.commitConfiguration()
                fail()
                return@dispatch_async
            }
            session.addOutput(output)
            if (AVMetadataObjectTypeQRCode !in output.availableMetadataObjectTypes) {
                session.commitConfiguration()
                fail()
                return@dispatch_async
            }
            output.setMetadataObjectsDelegate(metadataDelegate, dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            session.commitConfiguration()
            session.startRunning()
        }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
        preview.connection?.videoOrientation = when (view.window?.windowScene?.interfaceOrientation) {
            UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
            UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
            UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        finish(QrScanResult.Cancelled)
    }

    @ObjCAction
    fun cancelScan() { finish(QrScanResult.Cancelled) }

    private fun fail() {
        dispatch_async(dispatch_get_main_queue()) { finish(QrScanResult.CameraUnavailable) }
    }

    fun finish(result: QrScanResult) {
        if (finished) return
        finished = true
        dispatch_async(captureQueue) { session.stopRunning() }
        navigationController?.dismissViewControllerAnimated(true) { onResult(result) }
    }
}
