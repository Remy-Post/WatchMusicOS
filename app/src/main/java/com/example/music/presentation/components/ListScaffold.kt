package com.example.music.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.music.presentation.theme.WatchColors

/** Gives list items the standard Wear morphing-at-the-edges transformation. */
class ListTransform(val spec: TransformationSpec) {
    fun TransformingLazyColumnItemScope.surface(): SurfaceTransformation = SurfaceTransformation(spec)

    fun Modifier.morph(scope: TransformingLazyColumnItemScope): Modifier = transformedHeight(scope, spec)
}

/**
 * Scrollable round-screen list with the system scroll indicator, rotary scrolling and an
 * optional edge button hugging the bottom of the screen.
 */
@Composable
fun ListScaffold(
    edgeButton: (@Composable () -> Unit)? = null,
    content: TransformingLazyColumnScope.(ListTransform) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val transform = ListTransform(rememberTransformationSpec())
    if (edgeButton != null) {
        ScreenScaffold(scrollState = state, edgeButton = { edgeButton() }) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding) { content(transform) }
        }
    } else {
        ScreenScaffold(scrollState = state) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding) { content(transform) }
        }
    }
}

/** Screen title with an optional leading glyph. */
fun TransformingLazyColumnScope.header(
    transform: ListTransform,
    title: String,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Color.Unspecified,
) {
    item(key = "header:$title") {
        with(transform) {
            ListHeader(
                modifier = Modifier.fillMaxWidth().morph(this@item),
                transformation = surface(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.primary else iconTint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Small section label inside a list ("Today", "This week", …). */
fun TransformingLazyColumnScope.sectionLabel(transform: ListTransform, key: String, text: String) {
    item(key = "section:$key") {
        with(transform) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().morph(this@item),
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Centered secondary text for empty states and hints. */
fun TransformingLazyColumnScope.hint(transform: ListTransform, key: String, text: String) {
    item(key = "hint:$key") {
        with(transform) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = WatchColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().morph(this@item),
            )
        }
    }
}
