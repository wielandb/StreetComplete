package de.westnordost.streetcomplete.util.locale

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter

actual class LocalDateFormatter actual constructor(
    locale: Locale?,
    style: DateTimeFormatStyle,
) {
    private val formatter = NSDateFormatter().also {
        it.locale = formatterLocale(locale)
        it.dateStyle = style.toNSDateFormatterStyle()
    }
    private val calendar = NSCalendar(NSCalendarIdentifierGregorian).apply {
        timeZone = formatter.timeZone
    }

    actual fun format(date: LocalDate): String {
        val components = date.toNSDateComponents().apply { timeZone = formatter.timeZone }
        val date = calendar.dateFromComponents(components)
            ?: return ""
        return formatter.stringFromDate(date)
    }
}
