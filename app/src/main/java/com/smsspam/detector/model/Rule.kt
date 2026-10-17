package com.smsspam.detector.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Which part of an SMS a condition is tested against. */
enum class SmsField(val label: String) {
    BODY("Message body"),
    SENDER("Sender"),
    ANY("Sender + body"),
}

/** What happens when a rule matches. */
enum class RuleAction(val label: String) {
    /** Matching messages are treated as spam and moved to quarantine. */
    BLOCK("Block"),

    /** Matching messages are treated as safe and are never blocked (e.g. verification codes). */
    ALLOW("Allow"),
}

/** How a condition compares its pattern with the target text. */
enum class MatchType(val label: String) {
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
    EQUALS("equals"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with"),
    REGEX("matches regex"),
    NOT_REGEX("does not match regex"),
}

/** A single test inside a rule. */
data class Condition(
    val id: String = UUID.randomUUID().toString(),
    val field: SmsField = SmsField.BODY,
    val type: MatchType = MatchType.CONTAINS,
    val pattern: String = "",
    val ignoreCase: Boolean = true,
) {
    val isRegex: Boolean get() = type == MatchType.REGEX || type == MatchType.NOT_REGEX

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("field", field.name)
        put("type", type.name)
        put("pattern", pattern)
        put("ignoreCase", ignoreCase)
    }

    companion object {
        fun fromJson(o: JSONObject): Condition = Condition(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            field = enumOrDefault(o.optString("field"), SmsField.BODY),
            type = enumOrDefault(o.optString("type"), MatchType.CONTAINS),
            pattern = o.optString("pattern"),
            ignoreCase = o.optBoolean("ignoreCase", true),
        )

        private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}

/** How multiple conditions in a rule are combined (AND / OR). */
enum class RuleLogic(val label: String, val symbol: String) {
    ANY("Match ANY condition", "OR"),
    ALL("Match ALL conditions", "AND"),
}

/** A detection rule: a name plus one or more conditions, with a block or allow action. */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val action: RuleAction = RuleAction.BLOCK,
    val logic: RuleLogic = RuleLogic.ANY,
    val conditions: List<Condition> = emptyList(),
    val builtin: Boolean = false,
) {
    val summary: String
        get() = buildString {
            append("${conditions.size} condition${if (conditions.size == 1) "" else "s"} · ${logic.symbol}")
            if (action == RuleAction.ALLOW) append(" · ${action.label}")
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("action", action.name)
        put("logic", logic.name)
        put("builtin", builtin)
        put("conditions", JSONArray().apply { conditions.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Rule = Rule(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            action = enumValues<RuleAction>().firstOrNull { it.name == o.optString("action") }
                ?: RuleAction.BLOCK,
            logic = enumValues<RuleLogic>().firstOrNull { it.name == o.optString("logic") } ?: RuleLogic.ANY,
            builtin = o.optBoolean("builtin", false),
            conditions = o.optJSONArray("conditions")?.let { arr ->
                (0 until arr.length()).map { Condition.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
        )
    }
}
