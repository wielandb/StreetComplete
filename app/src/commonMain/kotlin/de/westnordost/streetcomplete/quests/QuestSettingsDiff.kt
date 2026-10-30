package de.westnordost.streetcomplete.quests

import io.github.petertrr.diffutils.text.DiffRow.Tag
import io.github.petertrr.diffutils.text.DiffRowGenerator
import io.github.petertrr.diffutils.text.DiffTagGenerator

/** Shows changed filter lines, with deleted and inserted words marked separately. */
internal fun createQuestFilterDiff(defaultText: String, currentText: String): String {
    val generator = DiffRowGenerator(
        showInlineDiffs = true,
        mergeOriginalRevised = true,
        inlineDiffByWord = true,
        ignoreWhiteSpaces = true,
        oldTag = object : DiffTagGenerator {
            override fun generateOpen(tag: Tag) = "<b><i><del>"
            override fun generateClose(tag: Tag) = "</del></i></b>"
        },
        newTag = object : DiffTagGenerator {
            override fun generateOpen(tag: Tag) = "<b><u><ins>"
            override fun generateClose(tag: Tag) = "</ins></u></b>"
        },
    )
    // Split alternatives into words without adding visible spaces to the filter.
    val hairSpace = "\u200a"
    val original = defaultText.replace("|", "$hairSpace|$hairSpace").lines()
    val revised = currentText.replace("|", "$hairSpace|$hairSpace").lines()
    return generator.generateDiffRows(original, revised)
        .filter { it.tag != Tag.EQUAL }
        .joinToString("<br>") { it.oldLine }
}