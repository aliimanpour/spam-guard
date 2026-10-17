package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleAction
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads and writes [Rule]s, seeding sensible presets on first launch. */
class RuleRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_rules", "rules")

    /** Tracks which preset generation the stored list was seeded with. */
    private val metaPrefs = context.getSharedPreferences("spam_guard_meta", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(loadOrDefaults())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    /**
     * Master switch for the built-in presets. The individual preset rules keep
     * their own configuration; this flag is what the single "Spam Protection"
     * row in the Rules screen controls.
     */
    private val _protectionEnabled =
        MutableStateFlow(metaPrefs.getBoolean(KEY_PROTECTION_ENABLED, true))
    val protectionEnabled: StateFlow<Boolean> = _protectionEnabled.asStateFlow()

    /**
     * Rules the matcher should evaluate. Built-in block presets are skipped
     * while Spam Protection is off, but the built-in allow rule (verification
     * codes) stays active so an OTP is never blocked by a custom rule.
     */
    fun matchingRules(): List<Rule> {
        val stored = _rules.value
        if (_protectionEnabled.value) return stored
        return stored.map { rule ->
            if (rule.builtin && rule.action == RuleAction.BLOCK) rule.copy(enabled = false) else rule
        }
    }

    /** Turns the whole built-in preset pack on or off. */
    @Synchronized
    fun setProtectionEnabled(enabled: Boolean) {
        _protectionEnabled.value = enabled
        metaPrefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    /**
     * Replaces the built-in presets with their defaults while keeping the
     * user's custom rules, and re-enables Spam Protection.
     */
    @Synchronized
    fun resetPresetsKeepingCustom(): List<Rule> {
        val custom = _rules.value.filterNot { it.builtin }
        val merged = custom + defaultRules()
        save(merged)
        setProtectionEnabled(true)
        return merged
    }

    private fun loadOrDefaults(): List<Rule> {
        val stored = store.read().map { Rule.fromJson(it) }
        if (stored.isEmpty()) {
            val defaults = defaultRules()
            store.write(defaults.map { it.toJson() })
            metaPrefs.edit().putInt(KEY_PRESET_VERSION, PRESET_VERSION).apply()
            return defaults
        }
        // A preset generation was added since this list was stored: refresh the
        // built-in presets to their latest definitions (keeping the user's
        // enabled/disabled choice) and append any brand-new presets once.
        if (metaPrefs.getInt(KEY_PRESET_VERSION, 0) < PRESET_VERSION) {
            val merged = mergePresets(stored)
            store.write(merged.map { it.toJson() })
            metaPrefs.edit().putInt(KEY_PRESET_VERSION, PRESET_VERSION).apply()
            return merged
        }
        return stored
    }

    @Synchronized
    fun save(rules: List<Rule>) {
        _rules.value = rules
        store.write(rules.map { it.toJson() })
    }

    /** Replaces the whole list (backup import) and guarantees the presets exist. */
    @Synchronized
    fun importRules(rules: List<Rule>) {
        save(mergePresets(rules))
        metaPrefs.edit().putInt(KEY_PRESET_VERSION, PRESET_VERSION).apply()
        setProtectionEnabled(true)
    }

    /**
     * Refreshes built-in presets to their latest shipped definition (keeping the
     * user's on/off choice) and appends every preset (by id) that [rules] does
     * not already have.
     */
    private fun mergePresets(rules: List<Rule>): List<Rule> {
        val defaults = defaultRules()
        val defaultsById = defaults.associateBy { it.id }
        // Drop built-in presets that are no longer shipped, but keep any custom
        // rule the user created (even if it happens to reuse a retired id).
        val kept = rules.filterNot { it.builtin && it.id in RETIRED_PRESET_IDS }
        val known = kept.mapTo(HashSet()) { it.id }
        val refreshed = kept.map { rule ->
            val latest = defaultsById[rule.id]
            if (rule.builtin && latest != null) latest.copy(enabled = rule.enabled) else rule
        }
        return refreshed + defaults.filter { it.id !in known }
    }

    companion object {
        /**
         * Preset rules shipped with the app. The opt-out rule for the Persian
         * "لغو11" keyword is enabled by default, as requested, and the
         * verification-code preset is an allow-rule so one-time codes pass
         * through even when they trip another rule.
         */
        fun defaultRules(): List<Rule> = listOf(
            Rule(
                id = "preset-verification",
                name = "Verification codes (OTP)",
                enabled = true,
                action = RuleAction.ALLOW,
                logic = RuleLogic.ANY,
                builtin = true,
                conditions = listOf(
                    Condition(
                        id = "preset-verification-1",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "(کد|رمز)[\\s\u200c]*(تایید|تاييد|تأیید|تأييد|تائید|تائيد|تاییدیه|" +
                            "ورود|امنیتی|پیامکی|بازیابی|اعتبارسنجی|فعال[\\s\u200c]*سازی|" +
                            "یکبار[\\s\u200c]*مصرف|شش[\\s\u200c]*رقمی)",
                    ),
                    Condition(
                        id = "preset-verification-2",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "(verification|verify|confirm|security|login|log[\\s-]?in|" +
                            "sign[\\s-]?in|activation|auth(entification)?|authorization)[\\s-]+code",
                    ),
                    Condition(
                        id = "preset-verification-3",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "\\b(otp|passcode|one[\\s-]time(\\s(password|code|pin|passcode))?)\\b",
                    ),
                    Condition(
                        id = "preset-verification-4",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "(code|کد|رمز)\\s*[:：-]\\s*[0-9۰-۹٠-٩]{4,8}(?![0-9۰-۹٠-٩])",
                    ),
                    Condition(
                        id = "preset-verification-5",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "your code is|is your code",
                    ),
                ),
            ),
            Rule(
                id = "preset-optout",
                name = "Unsubscribe / opt-out (لغو11)",
                enabled = true,
                logic = RuleLogic.ANY,
                builtin = true,
                conditions = listOf(
                    // لغو + optional space/ZWNJ + a 1–2 digit code, in Latin,
                    // Persian (۰-۹) or Arabic-Indic (٠-٩) digits: لغو11، لغو ۱۱، لغو۱۲.
                    Condition(
                        id = "preset-optout-1",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "لغو[\\s\u200c]*[0-9۰-۹٠-٩]{1,2}",
                    ),
                ),
            ),
            Rule(
                id = "preset-fa-promo",
                name = "Persian promo & scam spam",
                enabled = true,
                logic = RuleLogic.ANY,
                builtin = true,
                conditions = listOf(
                    Condition(id = "preset-fa-promo-1", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "شرط بندی"),
                    Condition(id = "preset-fa-promo-2", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "شرط‌بندی"),
                    Condition(id = "preset-fa-promo-3", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "کازینو"),
                    Condition(id = "preset-fa-promo-4", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "پیش بینی فوتبال"),
                    Condition(id = "preset-fa-promo-5", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "پیش‌بینی فوتبال"),
                    Condition(id = "preset-fa-promo-6", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "وام بدون ضامن"),
                    Condition(id = "preset-fa-promo-7", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "وام بدون چک"),
                    Condition(id = "preset-fa-promo-8", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "وام فوری"),
                    Condition(id = "preset-fa-promo-9", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "سود تضمینی"),
                    Condition(id = "preset-fa-promo-10", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "سرمایه گذاری تضمینی"),
                    Condition(id = "preset-fa-promo-11", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "سرمایه‌گذاری تضمینی"),
                    Condition(id = "preset-fa-promo-12", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "خرید فالوور"),
                    Condition(id = "preset-fa-promo-13", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "فالوور اینستاگرام"),
                    // Advertising / promotion markers extracted from the Persian
                    // promo messages the user reported. A link on its own is never
                    // treated as spam; only these explicit ad phrases (or the
                    // opt-out keyword) trigger a block.
                    Condition(id = "preset-fa-promo-14", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "تخفیف"),
                    Condition(id = "preset-fa-promo-15", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "هدیه"),
                    Condition(id = "preset-fa-promo-16", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "رایگان"),
                    Condition(id = "preset-fa-promo-17", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "کد دستوری"),
                    Condition(id = "preset-fa-promo-18", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "کد معرف"),
                    Condition(id = "preset-fa-promo-19", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "فرصت محدود"),
                    Condition(id = "preset-fa-promo-20", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "فرصت ویژه"),
                    Condition(id = "preset-fa-promo-21", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "اعتبار اقساطی"),
                    Condition(id = "preset-fa-promo-22", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "خرید شارژ"),
                    Condition(id = "preset-fa-promo-23", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "اپلیکیشن همراه من"),
                    Condition(id = "preset-fa-promo-24", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "قرعه\u200cکشی"),
                    Condition(id = "preset-fa-promo-25", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "شانس بردن"),
                    // USSD dial codes (*100*4#، *1000*31#) are a strong ad marker.
                    Condition(
                        id = "preset-fa-promo-26",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "\\*[0-9۰-۹٠-٩]{2,4}(\\*[0-9۰-۹٠-٩]{1,4})+#",
                    ),
                ),
            ),
            Rule(
                id = "preset-prize",
                name = "Prize, lottery & winner spam",
                enabled = true,
                logic = RuleLogic.ANY,
                builtin = true,
                conditions = listOf(
                    Condition(id = "preset-prize-1", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "برنده"),
                    Condition(id = "preset-prize-2", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "جایزه"),
                    Condition(id = "preset-prize-3", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "قرعه کشی"),
                    Condition(id = "preset-prize-4", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "winner"),
                    Condition(id = "preset-prize-5", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "you won"),
                ),
            ),
        )

        /**
         * Built-in presets that shipped before but are no longer offered. They
         * are stripped from stored lists on upgrade — e.g. the old link-only
         * filter, because a link on its own is not spam.
         */
        private val RETIRED_PRESET_IDS = setOf("preset-links")

        /** Bump when a new preset is added so stored lists pick it up on upgrade. */
        const val PRESET_VERSION = 4

        private const val KEY_PRESET_VERSION = "preset_version"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
    }
}
