package de.westnordost.streetcomplete.util.platform

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun String.normalizeToNfd(): String = (this as NSString).decomposedStringWithCanonicalMapping
