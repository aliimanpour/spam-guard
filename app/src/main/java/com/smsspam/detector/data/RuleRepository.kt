package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads and writes [Rule]s, seeding sensible presets on first launch. */
class RuleRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_rules", "rules")

    private val _rules = MutableStateFlow(loadOrDefaults())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private fun loadOrDefaults(): List<Rule> {
        val stored = store.read().map { Rule.fromJson(it) }
        if (stored.isEmpty()) {
            val defaults = defaultRules()
            store.write(defaults.map { it.toJson() })
            return defaults
        }
        return stored
    }

    @Synchronized
    fun save(rules: List<Rule>) {
        _rules.value = rules
        store.write(rules.map { it.toJson() })
    }

    /** Reset to the built-in presets. */
    @Synchronized
    fun resetToDefaults(): List<Rule> {
        val defaults = defaultRules()
        save(defaults)
        return defaults
    }

    companion object {
        /**
         * Preset rules shipped with the app. The opt-out rule for the Persian
         * "لغو11" keyword is enabled by default, as requested.
         */
        fun defaultRules(): List<Rule> = listOf(
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
    }
}
