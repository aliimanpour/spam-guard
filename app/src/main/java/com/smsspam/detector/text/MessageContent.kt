package com.smsspam.detector.text

/**
 * Pure text analysis shared by the message UI and the notification builder.
 *
 * It locates the parts of an SMS body the user interacts with directly — links
 * (opened in the browser), payment card and SHEBA (IBAN) numbers, phone numbers
 * and one-time-password codes (copied to the clipboard) — while tolerating the
 * Persian (`۰-۹`) and Arabic-Indic (`٠-٩`) digit forms people receive.
 *
 * Nothing here touches the Android framework, so it stays trivially testable
 * with plain JUnit.
 */
object MessageContent {

    /** How a [Token] should be presented to the user. */
    enum class Kind { LINK, CARD, SHEBA, PHONE, OTP }

    /**
     * A non-overlapping region of a message body: [start] inclusive, [end]
     * exclusive. [value] is the normalized payload — an absolute URL for
     * links, bare digits for cards and phones, `IR`-prefixed digits for SHEBA
     * numbers, and the bare code for OTPs.
     */
    data class Token(
        val start: Int,
        val end: Int,
        val kind: Kind,
        val value: String,
    )

    /** Latin digits followed by the Persian and Arabic-Indic variants. */
    private const val DIGITS = "0-9\u06F0-\u06F9\u0660-\u0669"

    private const val CARD_LENGTH = 16

    /** Inclusive digit counts that look like a phone number (not a card). */
    private const val PHONE_MIN = 10
    private const val PHONE_MAX = 13

    /** Characters that may separate the digit groups of a number. */
    private val separators = charArrayOf(' ', '\u00A0', '-', '.', '_')

    /** Punctuation that frequently trails a link but is not part of it. */
    private val linkTrailing = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    private val urlRegex = Regex(
        "(?i)\\b(?:(?:https?://|www\\.)[^\\s<>\"'`{}|^\\[\\]]+" +
            "|[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9-]+)*\\." +
            "(?:com|ir|org|net|io|me|co|app|dev|site|online|shop|xyz|info|biz|edu|gov)" +
            "(?:/[^\\s<>\"'`{}|^\\[\\]]*)?)",
    )

    private val shebaRegex = Regex("(?i)IR(?:[ -]?[$DIGITS]){24}")

    private val digitRunRegex = Regex("[$DIGITS]+")

    /**
     * Words that signal a one-time-password message. An OTP is only extracted
     * when one of these is present, so ordinary numbers are never mistaken for
     * a code.
     */
    private val otpKeywords = listOf(
        "otp", "code", "pin", "passcode", "password", "verification", "verify",
        "token", "one time", "one-time",
        "کد", "رمز", "تایید", "تأیید", "ورود", "فعالسازی", "فعال سازی",
        "احراز", "یکبار", "یک بار",
    )

    /** Latinizes Persian and Arabic-Indic digits, leaving other characters alone. */
    fun normalizeDigits(text: String): String = buildString(text.length) {
        text.forEach { char ->
            append(
                when (char) {
                    in '\u06F0'..'\u06F9' -> '0' + (char - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (char - '\u0660')
                    else -> char
                },
            )
        }
    }

    /** Every interactive token in [body], ordered and free of overlaps. */
    fun tokens(body: String): List<Token> {
        if (body.isEmpty()) return emptyList()
        return resolve(
            findLinks(body) + findSheba(body) + findNumbers(body) + findOtp(body),
        )
    }

    /** The first URL in [body], already absolute (a scheme is added when missing). */
    fun firstUrl(body: String): String? =
        tokens(body).firstOrNull { it.kind == Kind.LINK }?.value

    /** The first card or SHEBA number in [body], or null. */
    fun firstAccount(body: String): Token? =
        tokens(body).firstOrNull { it.kind == Kind.CARD || it.kind == Kind.SHEBA }

    /**
     * A one-time-password code in [body], or null when the message does not look
     * like an OTP. Prefers the usual 5-6 digit codes, then any 4-8 digit run,
     * and never returns digits that are part of a longer number (a phone number,
     * for example).
     */
    fun otpCode(body: String): String? =
        tokens(body).firstOrNull { it.kind == Kind.OTP }?.value

    /** Finds links, trimming sentence punctuation and adding a scheme when needed. */
    private fun findLinks(body: String): List<Token> {
        val result = ArrayList<Token>()
        for (match in urlRegex.findAll(body)) {
            var raw = match.value
            while (raw.isNotEmpty() && raw.last() in linkTrailing) raw = raw.dropLast(1)
            if (raw.isBlank()) continue
            val start = match.range.first
            val value = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                raw
            } else {
                "https://$raw"
            }
            result += Token(start, start + raw.length, Kind.LINK, value)
        }
        return result
    }

    /** Finds `IR` + 24 digits, however the groups are separated. */
    private fun findSheba(body: String): List<Token> {
        val result = ArrayList<Token>()
        for (match in shebaRegex.findAll(body)) {
            val digits = normalizeDigits(match.value).filter { it.isDigit() }
            if (digits.length == 24) {
                result += Token(match.range.first, match.range.last + 1, Kind.SHEBA, "IR$digits")
            }
        }
        return result
    }

    /**
     * Finds card and phone numbers. A candidate is a maximal run of digits and
     * separators that starts and ends with a digit; its normalized length then
     * decides the kind — exactly [CARD_LENGTH] digits is a card, a [PHONE_MIN]
     * to [PHONE_MAX] digit run is a phone. Anything else (accounts, IDs) is
     * left alone.
     */
    private fun findNumbers(body: String): List<Token> {
        val result = ArrayList<Token>()
        var index = 0
        while (index < body.length) {
            if (!body[index].isMessageDigit()) {
                index++
                continue
            }
            var cursor = index
            var lastDigit = index
            while (cursor < body.length &&
                (body[cursor].isMessageDigit() || body[cursor] in separators)
            ) {
                if (body[cursor].isMessageDigit()) lastDigit = cursor
                cursor++
            }
            val end = lastDigit + 1
            val digits = normalizeDigits(body.substring(index, end)).filter { it.isDigit() }
            val kind = when (digits.length) {
                CARD_LENGTH -> Kind.CARD
                in PHONE_MIN..PHONE_MAX -> Kind.PHONE
                else -> null
            }
            if (kind != null) result += Token(index, end, kind, digits)
            index = end
        }
        return result
    }

    /**
     * Finds the one-time-password code, if the body reads like an OTP message.
     * Prefers the usual 5-6 digit codes, then any 4-8 digit run.
     */
    private fun findOtp(body: String): List<Token> {
        val lower = body.lowercase()
        if (otpKeywords.none { lower.contains(it) }) return emptyList()
        val runs = digitRunRegex.findAll(body).toList()
        val match = runs.firstOrNull { normalizeDigits(it.value).length in 5..6 }
            ?: runs.firstOrNull { normalizeDigits(it.value).length in 4..8 }
            ?: return emptyList()
        return listOf(
            Token(
                start = match.range.first,
                end = match.range.last + 1,
                kind = Kind.OTP,
                value = normalizeDigits(match.value),
            ),
        )
    }

    /** Drops overlapping tokens, preferring links over SHEBA over cards over phones over OTP. */
    private fun resolve(tokens: List<Token>): List<Token> {
        val sorted = tokens.sortedWith(compareBy({ it.start }, { priority(it.kind) }))
        val result = ArrayList<Token>(sorted.size)
        var lastEnd = -1
        for (token in sorted) {
            if (token.start >= lastEnd) {
                result += token
                lastEnd = token.end
            }
        }
        return result
    }

    private fun priority(kind: Kind): Int = when (kind) {
        Kind.LINK -> 0
        Kind.SHEBA -> 1
        Kind.CARD -> 2
        Kind.PHONE -> 3
        Kind.OTP -> 4
    }

    private fun Char.isMessageDigit(): Boolean =
        this in '0'..'9' || this in '\u06F0'..'\u06F9' || this in '\u0660'..'\u0669'
}
