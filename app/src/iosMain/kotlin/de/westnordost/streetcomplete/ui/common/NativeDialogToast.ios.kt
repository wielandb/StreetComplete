@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIView
import kotlin.time.Duration

@Composable
actual fun NativeDialogToast(text: String, duration: Duration, onDismissRequest: () -> Unit) {
    val controller = LocalUIViewController.current
    val onDismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(controller, text, duration) {
        // Attach to the window so the message is also visible above a presented native dialog.
        val host: UIView = controller.view.window ?: controller.view
        val hostWidth = host.bounds.useContents { size.width }
        val hostHeight = host.bounds.useContents { size.height }
        val bottomInset = host.safeAreaInsets.useContents { bottom }
        val maximumWidth = (hostWidth - 48.0).coerceIn(64.0, 600.0)
        val label = UILabel().apply {
            this.text = text
            textColor = UIColor.whiteColor
            font = UIFont.systemFontOfSize(14.0)
            numberOfLines = 0
            textAlignment = NSTextAlignmentCenter
        }
        val labelSize = label.sizeThatFits(CGSizeMake(maximumWidth - 32.0, hostHeight))
        val toastWidth = labelSize.useContents { width } + 32.0
        val toastHeight = labelSize.useContents { height } + 24.0
        val message = UIView(frame = CGRectMake(
            (hostWidth - toastWidth) / 2.0,
            (hostHeight - bottomInset - toastHeight - 48.0).coerceAtLeast(0.0),
            toastWidth,
            toastHeight,
        )).apply {
            backgroundColor = UIColor.blackColor.colorWithAlphaComponent(0.85)
            layer.cornerRadius = 12.0
            clipsToBounds = true
            setUserInteractionEnabled(false)
        }
        label.setFrame(CGRectMake(16.0, 12.0, toastWidth - 32.0, toastHeight - 24.0))
        message.addSubview(label)
        host.addSubview(message)
        try {
            delay(duration)
            onDismiss()
        } finally {
            message.removeFromSuperview()
        }
    }
}
