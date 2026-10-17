package com.smsspam.detector.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.smsspam.detector.text.MessageContent

/**
 * Renders a message body with its links and numbers made interactive: URLs open
 * in the browser, phone numbers open the dialer via [onCallNumber], and card /
 * SHEBA numbers and OTP codes copy via [onCopyAccount]. Everything else is
 * rendered as plain text.
 */
@Composable
fun MessageText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onCopyAccount: (String) -> Unit = {},
    onCallNumber: (String) -> Unit = {},
) {
    // Keep the latest callbacks without rebuilding the annotated string: the
    // remembered State instances are stable across recompositions.
    val copyState = rememberUpdatedState(onCopyAccount)
    val callState = rememberUpdatedState(onCallNumber)
    val annotated = remember(text, style, color, linkColor) {
        buildMessageText(
            text = text,
            linkColor = linkColor,
            onCopy = { value -> copyState.value(value) },
            onCall = { value -> callState.value(value) },
        )
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
    )
}

/** Builds the annotated message: links open, phones dial, card/SHEBA/OTP copy. */
private fun buildMessageText(
    text: String,
    linkColor: Color,
    onCopy: (String) -> Unit,
    onCall: (String) -> Unit,
): AnnotatedString {
    val tokens = MessageContent.tokens(text)
    if (tokens.isEmpty()) return AnnotatedString(text)

    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        var cursor = 0
        tokens.forEach { token ->
            if (token.start > cursor) append(text.substring(cursor, token.start))
            val raw = text.substring(token.start, token.end)
            when (token.kind) {
                MessageContent.Kind.LINK -> withLink(
                    LinkAnnotation.Url(url = token.value, styles = linkStyles),
                ) {
                    append(raw)
                }

                MessageContent.Kind.PHONE -> withLink(
                    LinkAnnotation.Clickable(
                        tag = token.kind.name,
                        styles = linkStyles,
                        linkInteractionListener = object : LinkInteractionListener {
                            override fun onClick(link: LinkAnnotation) {
                                onCall(token.value)
                            }
                        },
                    ),
                ) {
                    append(raw)
                }

                MessageContent.Kind.CARD,
                MessageContent.Kind.SHEBA,
                MessageContent.Kind.OTP,
                -> withLink(
                    LinkAnnotation.Clickable(
                        tag = token.kind.name,
                        styles = linkStyles,
                        linkInteractionListener = object : LinkInteractionListener {
                            override fun onClick(link: LinkAnnotation) {
                                onCopy(token.value)
                            }
                        },
                    ),
                ) {
                    append(raw)
                }
            }
            cursor = token.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
