package de.westnordost.streetcomplete.util.ktx

import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource


val DrawableResource.name: String get() = drawableResourceToName.getValue(this)

private val drawableResourceToName by lazy {
    Res.allDrawableResources.entries.associate { it.value to it.key }
}
