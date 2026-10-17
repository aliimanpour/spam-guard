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

    private fun loadOrDefaults(): List<Rule> {
        val stored = store.read().map { Rule.fromJson(it) }
        if (stored.isEmpty()) {
            val defaults = defaultRules()
            store.write(defaults.map { it.toJson() })
            metaPrefs.edit().putInt(KEY_PRESET_VERSION, PRESET_VERSION).apply()
            return defaults
        }
        // A preset generation was added since this list was stored: append the
        // presets it is missing once (a deleted preset stays deleted afterwards).
        if (metaPrefs.getInt(KEY_PRESET_VERSION, 0) < PRESET_VERSION) {
            val merged = mergePresets(stored)
            if (merged.size > stored.size) store.write(merged.map { it.toJson() })
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
    }

    /** Reset to the built-in presets. */
    @Synchronized
    fun resetToDefaults(): List<Rule> {
        val defaults = defaultRules()
        save(defaults)
        return defaults
    }

    /** Appends every built-in preset (by id) that [rules] does not already have. */
    private fun mergePresets(rules: List<Rule>): List<Rule> {
        val known = rules.mapTo(HashSet()) { it.id }
        return rules + defaultRules().filter { it.id !in known }
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
                    Condition(id = "preset-optout-1", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "لغو11"),
                    Condition(id = "preset-optout-2", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "لغو 11"),
                    Condition(id = "preset-optout-3", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "لغو12"),
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
            Rule(
                id = "preset-links",
                name = "Messages that contain links",
                enabled = false,
                logic = RuleLogic.ANY,
                builtin = true,
                conditions = listOf(
                    Condition(
                        id = "preset-links-1",
                        field = SmsField.BODY,
                        type = MatchType.REGEX,
                        pattern = "https?://\\S+",
                    ),
                    Condition(id = "preset-links-2", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "bit.ly"),
                    Condition(id = "preset-links-3", field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "tinyurl.com"),
                ),
            ),
        )

        /** Bump when a new preset is added so stored lists pick it up on upgrade. */
        const val PRESET_VERSION = 2

        private const val KEY_PRESET_VERSION = "preset_version"
    }
}
