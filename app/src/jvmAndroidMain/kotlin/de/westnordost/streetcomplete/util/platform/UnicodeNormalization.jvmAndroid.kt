package de.westnordost.streetcomplete.util.platform

import java.text.Normalizer

actual fun String.normalizeToNfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
