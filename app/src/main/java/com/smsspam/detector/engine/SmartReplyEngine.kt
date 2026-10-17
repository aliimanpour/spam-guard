package com.smsspam.detector.engine

/**
 * On-device, offline reply suggestions. Given the text of the last message the
 * user received it returns a short list of ready-to-send replies, chosen with
 * simple keyword/intent rules — no model, no network, nothing leaves the phone.
 *
 * The templates are Persian-first (the primary audience) with a few bilingual
 * fallbacks, so a Persian SMS gets Persian suggestions and an English one gets
 * English ones.
 */
object SmartReplyEngine {

    /**
     * Up to [limit] suggestions for [incoming], best match first. Returns an
     * empty list for messages that should never be answered automatically
     * (one-time codes, OTPs), so we never nudge the user to reply to spam.
     */
    fun suggestions(incoming: String, limit: Int = 3): List<String> {
        val text = incoming.trim()
        if (text.isEmpty()) return emptyList()
        if (looksLikeOtp(text)) return emptyList()

        val lower = text.lowercase()
        val persian = isPersian(text)
        val ranked = LinkedHashSet<String>()

        fun addAll(replies: List<String>) = ranked.addAll(replies)

        when {
            containsAny(lower, PERSIAN_QUESTION_TIME) || containsAny(lower, ENGLISH_QUESTION_TIME) ->
                addAll(if (persian) TIME_REPLIES_FA else TIME_REPLIES_EN)
            containsAny(lower, PERSIAN_QUESTION_PLACE) || containsAny(lower, ENGLISH_QUESTION_PLACE) ->
                addAll(if (persian) PLACE_REPLIES_FA else PLACE_REPLIES_EN)
            containsAny(lower, PERSIAN_MONEY) || containsAny(lower, ENGLISH_MONEY) ->
                addAll(if (persian) MONEY_REPLIES_FA else MONEY_REPLIES_EN)
            containsAny(lower, PERSIAN_CALL) || containsAny(lower, ENGLISH_CALL) ->
                addAll(if (persian) CALL_REPLIES_FA else CALL_REPLIES_EN)
            containsAny(lower, PERSIAN_ORDER) || containsAny(lower, ENGLISH_ORDER) ->
                addAll(if (persian) ORDER_REPLIES_FA else ORDER_REPLIES_EN)
            containsAny(lower, PERSIAN_INVITE) || containsAny(lower, ENGLISH_INVITE) ->
                addAll(if (persian) INVITE_REPLIES_FA else INVITE_REPLIES_EN)
            containsAny(lower, PERSIAN_THANKS) || containsAny(lower, ENGLISH_THANKS) ->
                addAll(if (persian) THANKS_REPLIES_FA else THANKS_REPLIES_EN)
            containsAny(lower, PERSIAN_GREETING) || containsAny(lower, ENGLISH_GREETING) ->
                addAll(if (persian) GREETING_REPLIES_FA else GREETING_REPLIES_EN)
        }

        // A question with no specific intent still gets a yes/no/answer set.
        if (ranked.isEmpty() && isQuestion(text)) {
            addAll(if (persian) GENERIC_QUESTION_REPLIES_FA else GENERIC_QUESTION_REPLIES_EN)
        }

        addAll(if (persian) FALLBACK_REPLIES_FA else FALLBACK_REPLIES_EN)
        return ranked.take(limit)
    }

    /** True when at least one Arabic/Persian letter is present. */
    fun isPersian(text: String): Boolean = text.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF }

    /** A question mark in either script, or an interrogative Persian word. */
    private fun isQuestion(text: String): Boolean =
        text.contains('?') || text.contains('؟') || containsAny(text.lowercase(), PERSIAN_QUESTION_WORDS)

    /** One-time / verification codes should not be answered. */
    private fun looksLikeOtp(text: String): Boolean {
        val lower = text.lowercase()
        if (containsAny(lower, OTP_MARKERS)) return true
        // A short message that is mostly a 4-8 digit code.
        val digits = text.count { it.isDigit() }
        return digits in 4..8 && digits * 100 / text.length.coerceAtLeast(1) > 40
    }

    private fun containsAny(text: String, needles: List<String>): Boolean =
        needles.any { it.isNotBlank() && text.contains(it) }

    // --- Persian intents -----------------------------------------------------
    private val PERSIAN_GREETING = listOf("سلام", "درود", "صبح بخیر", "شب بخیر", "وقت بخیر")
    private val PERSIAN_THANKS = listOf("ممنون", "مرسی", "سپاس", "دستت درد نکنه", "لطف")
    private val PERSIAN_QUESTION_TIME = listOf("چه ساعتی", "ساعت چند", "کی می", "چه موقع", "کِی")
    private val PERSIAN_QUESTION_PLACE = listOf("کجا", "آدرس", "نشانی", "کجایی")
    private val PERSIAN_MONEY = listOf("پول", "کارت به کارت", "واریز", "مبلغ", "تومان", "ریال", "قیمت", "حساب")
    private val PERSIAN_CALL = listOf("تماس", "زنگ بزن", "تلفن")
    private val PERSIAN_ORDER = listOf("سفارش", "ارسال شد", "تحویل", "مرسوله", "رهگیری", "پست")
    private val PERSIAN_INVITE = listOf("میای", "می آیی", "بیا", "دعوت", "میایم")
    private val PERSIAN_QUESTION_WORDS = listOf("آیا", "میشه", "میتونی", "هستی", "داری", "میای", "چطور")

    // --- English intents -----------------------------------------------------
    private val ENGLISH_GREETING = listOf("hi", "hello", "hey", "good morning", "good evening")
    private val ENGLISH_THANKS = listOf("thanks", "thank you", "thx", "appreciate")
    private val ENGLISH_QUESTION_TIME = listOf("what time", "when", "how soon")
    private val ENGLISH_QUESTION_PLACE = listOf("where", "address", "location")
    private val ENGLISH_MONEY = listOf("price", "cost", "pay", "payment", "amount", "invoice")
    private val ENGLISH_CALL = listOf("call me", "phone me", "give me a call")
    private val ENGLISH_ORDER = listOf("order", "delivery", "tracking", "shipped", "parcel")
    private val ENGLISH_INVITE = listOf("coming", "join", "are you free", "meet")

    private val OTP_MARKERS = listOf(
        "کد تایید", "کد ورود", "رمز یکبار", "کد یکبار", "verification code", "one-time", "otp", "your code",
    )

    // --- Reply templates -----------------------------------------------------
    private val GREETING_REPLIES_FA = listOf("سلام، وقت بخیر", "سلام، در خدمتم", "سلام، خوبی؟")
    private val THANKS_REPLIES_FA = listOf("خواهش میکنم", "قابلی نداشت", "نوش جان")
    private val TIME_REPLIES_FA = listOf("الان میام", "نیم ساعت دیگه", "بهتون خبر میدم")
    private val PLACE_REPLIES_FA = listOf("آدرس رو میفرستم", "همینجا هستم", "کجایی؟")
    private val MONEY_REPLIES_FA = listOf("واریز کردم", "باشه، واریز میکنم", "چقدر؟")
    private val CALL_REPLIES_FA = listOf("تماس میگیرم", "الان نمیتونم، بعداً تماس میگیرم", "باشه")
    private val ORDER_REPLIES_FA = listOf("ممنون، پیگیری میکنم", "چه زمانی میرسه؟", "دریافت شد")
    private val INVITE_REPLIES_FA = listOf("بله میام", "متأسفانه نمیتونم", "چه ساعتی؟")
    private val GENERIC_QUESTION_REPLIES_FA = listOf("بله", "نه", "باشه")
    private val FALLBACK_REPLIES_FA = listOf("چشم", "باشه، ممنون", "حتماً")

    private val GREETING_REPLIES_EN = listOf("Hi!", "Hello, how are you?", "Hey, what's up?")
    private val THANKS_REPLIES_EN = listOf("You're welcome", "No problem", "Anytime")
    private val TIME_REPLIES_EN = listOf("On my way", "In 30 minutes", "I'll let you know")
    private val PLACE_REPLIES_EN = listOf("I'll send the address", "I'm here", "Where are you?")
    private val MONEY_REPLIES_EN = listOf("I've paid", "I'll pay now", "How much?")
    private val CALL_REPLIES_EN = listOf("I'll call you", "Can't talk now, I'll call later", "Sure")
    private val ORDER_REPLIES_EN = listOf("Thanks, I'll track it", "When will it arrive?", "Received")
    private val INVITE_REPLIES_EN = listOf("Yes, I'll come", "Sorry, I can't", "What time?")
    private val GENERIC_QUESTION_REPLIES_EN = listOf("Yes", "No", "Okay")
    private val FALLBACK_REPLIES_EN = listOf("Okay", "Thanks", "Sure")
}
