package de.westnordost.streetcomplete.util.locale

/** Keep app/system language priority and the unlocalized fallback when extending a search. */
fun featureSearchLanguages(
    defaultLanguages: List<String?>,
    countryLanguages: List<String>,
    includeCountryLanguages: Boolean,
): List<String?> = if (!includeCountryLanguages) defaultLanguages else
    (defaultLanguages.filterNotNull() + countryLanguages.filter { it.isNotBlank() }).distinct() + null
