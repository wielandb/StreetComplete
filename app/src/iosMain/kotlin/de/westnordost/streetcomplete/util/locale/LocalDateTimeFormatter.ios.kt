package de.westnordost.streetcomplete.util.locale

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSDateComponents
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter

actual class LocalDateTimeFormatter actual constructor(
    locale: Locale?,
    timeZone: TimeZone,
    dateStyle: DateTimeFormatStyle,
    timeStyle: DateTimeFormatStyle,
) {
    private val formatter = NSDateFormatter().also {
        it.locale = formatterLocale(locale)
        it.dateStyle = dateStyle.toNSDateFormatterStyle()
        it.timeStyle = timeStyle.toNSDateFormatterStyle()
        it.timeZone = timeZone.toNSTimeZone()
    }
    // Local fields belong to the requested zone, independently of the device's calendar/zone.
    private val calendar = NSCalendar(NSCalendarIdentifierGregorian).apply {
        this.timeZone = formatter.timeZone
    }

    actual fun format(dateTime: LocalDateTime): String {
        val components = dateTime.toNSDateComponents().apply { this.timeZone = formatter.timeZone }
        val date = calendar.dateFromComponents(components)
            ?: return ""
        return formatter.stringFromDate(date)
    }
}
