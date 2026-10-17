package com.smsspam.detector.engine

import com.smsspam.detector.data.RuleRepository
import com.smsspam.detector.engine.RuleEngine.matches
import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleAction
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun `default opt-out rule flags Persian لغو11`() {
        val rules = RuleRepository.defaultRules()
        val match = RuleEngine.firstMatch("+989120000000", "برای لغو11 عدد ۱۱ را ارسال کنید", rules)
        assertEquals("preset-optout", match?.id)
    }

    @Test
    fun `opt-out rule matches Persian and Arabic digit codes`() {
        val rules = RuleRepository.defaultRules()
        assertEquals("preset-optout", RuleEngine.firstMatch("", "برای لغو ۱۱ پیامک کنید", rules)?.id)
        assertEquals("preset-optout", RuleEngine.firstMatch("", "لغو۱۲", rules)?.id)
        assertEquals("preset-optout", RuleEngine.firstMatch("", "لغو 12", rules)?.id)
        assertEquals("preset-optout", RuleEngine.firstMatch("", "لغو ١١", rules)?.id)
    }

    @Test
    fun `conservative Persian promo preset blocks scam keywords`() {
        val rules = RuleRepository.defaultRules()
        assertEquals("preset-fa-promo", RuleEngine.firstMatch("", "تیم پیش بینی فوتبال با سود تضمینی", rules)?.id)
        assertEquals("preset-fa-promo", RuleEngine.firstMatch("", "وام بدون ضامن فوری", rules)?.id)
        assertEquals("preset-fa-promo", RuleEngine.firstMatch("", "خرید فالوور اینستاگرام ارزان", rules)?.id)
    }

    @Test
    fun `ordinary Persian messages are not promoted to spam`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("", "سلام، حالت چطوره؟ قرار جلسه فردا ساعت ۵", rules))
        assertNull(RuleEngine.firstMatch("", "فاکتور خرید شما آماده است", rules))
    }

    @Test
    fun `a link on its own is not spam`() {
        // Regression: the old preset-links filter treated any URL as spam.
        val rules = RuleRepository.defaultRules()
        assertTrue(rules.none { it.id == "preset-links" })
        assertNull(RuleEngine.firstMatch("", "اینو ببین https://example.com/abc", rules))
        assertNull(RuleEngine.firstMatch("", "لطفا از این لینک استفاده کن bit.ly/abc", rules))
        assertNull(RuleEngine.firstMatch("", "my.asiatech.ir را در مرورگر باز کن", rules))
    }

    @Test
    fun `reported Persian promo messages are detected`() {
        val rules = RuleRepository.defaultRules()
        val samples = listOf(
            "تا 100 گیگ اینترنت هدیه شبانت 🎁 کد دستوری اینترنت همراهی: *100*621#",
            "هدیه ویژه همراه اول برای شما 15 گیگابایت اینترنت سه روزه رایگان *1000*2864#",
            "مشترک گرامی آسیاتک فرصت ویژه تمدید سرویس با تخفیف استثنایی my.asiatech.ir",
            "🎉 ۵۰ درصد تخفیف! فقط تا جمعه در کالا0098 kala0098.com",
            "کاربر عزیز با ۲۰ میلیون تومان اعتبار اقساطی دیجی‌پی i.jabama.me/geRyjwZB",
            "تبریک! ۱۰ سوت طلا هدیه با ثبت نام و اولین خرید jzbe.ir/17oor6",
            "📱با هر ۱۰ هزار تومان خرید شارژ از همراه من، شانس بردن گوشی پرچمدار",
            "ایمپلنت هر6واحد👈🏻1واحد رایگان اقساط0٪سود",
            "هر ۱۰ هزار تومان خرید، ۱ امتیاز قرعه\u200cکشی کمک‌هزینه لپ‌تاپ",
            "خرید پُروپیمون آخرهفته، با سوپرمارکت تپسی تا ۵۰٪ تخفیف بیشتر",
        )
        samples.forEach { body ->
            assertNotNull("expected spam: $body", RuleEngine.firstMatch("", body, rules))
        }
    }

    @Test
    fun `plain greeting is not spam`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("+989120000000", "سلام، حالت چطوره؟", rules))
    }

    @Test
    fun `AND logic requires all conditions`() {
        val rule = Rule(
            name = "and",
            logic = RuleLogic.ALL,
            conditions = listOf(
                Condition(field = SmsField.SENDER, type = MatchType.CONTAINS, pattern = "bank"),
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "verify"),
            ),
        )
        assertTrue(rule.matches("Bank", "Please verify your account"))
        assertFalse(rule.matches("Bank", "Please check your account"))
    }

    @Test
    fun `OR logic needs only one condition`() {
        val rule = Rule(
            name = "or",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "winner"),
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "prize"),
            ),
        )
        assertTrue(rule.matches("", "You are a WINNER"))
        assertFalse(rule.matches("", "Just a normal note"))
    }

    @Test
    fun `regex condition matches`() {
        val rule = Rule(
            name = "regex",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.BODY, type = MatchType.REGEX, pattern = "code\\s*\\d{4}"),
            ),
        )
        assertTrue(rule.matches("", "Your code 4821 expires soon"))
        assertFalse(rule.matches("", "Your code expires soon"))
    }

    @Test
    fun `invalid regex is reported and never matches`() {
        val condition = Condition(field = SmsField.BODY, type = MatchType.REGEX, pattern = "(unclosed")
        assertTrue(RuleEngine.hasInvalidRegex(condition))
        assertFalse(condition.matches("", "anything"))
    }

    @Test
    fun `sender conditions use the sender field`() {
        val rule = Rule(
            name = "sender",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.SENDER, type = MatchType.EQUALS, pattern = "AD-BANK"),
            ),
        )
        assertTrue(rule.matches("ad-bank", "hello"))
        assertFalse(rule.matches("+98912", "hello"))
    }

    @Test
    fun `disabled rules are ignored`() {
        val rules = RuleRepository.defaultRules().map { it.copy(enabled = false) }
        assertNull(RuleEngine.firstMatch("", "لغو11", rules))
    }

    // --- Verification-code (OTP) preset: an allow rule ----------------------

    @Test
    fun `verification preset lets OTP through even with لغو11`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("+989120000000", "بانک ملت\nکد تایید شما: 123456\nلغو11", rules))
        assertNull(RuleEngine.firstMatch("sender", "Your verification code is 482913 لغو11", rules))
    }

    @Test
    fun `verification preset recognizes persian digits ZWNJ and code separators`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("", "کد فعال‌سازی شما ۱۲۳۴۵۶ لغو11", rules))
        assertNull(RuleEngine.firstMatch("", "Telegram code: 12345", rules))
        assertNull(RuleEngine.firstMatch("", "رمز ورود: ۸۱۳۴", rules))
        assertNull(RuleEngine.firstMatch("", "otp: 1234", rules))
    }

    @Test
    fun `ordinary texts are not verification messages`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("", "سلام، حالت چطوره؟", rules))
        assertFalse(
            RuleRepository.defaultRules()
                .first { it.action == RuleAction.ALLOW }
                .matches("", "با کد تخفیف خرید کن"),
        )
    }

    @Test
    fun `disabling the verification preset re-blocks OTP-shaped spam`() {
        val rules = RuleRepository.defaultRules().map {
            if (it.id == "preset-verification") it.copy(enabled = false) else it
        }
        assertEquals("preset-optout", RuleEngine.firstMatch("", "کد تایید شما: 123456 لغو11", rules)?.id)
    }

    @Test
    fun `allow rule beats a block rule regardless of order`() {
        val rules = listOf(
            Rule(name = "block-all", conditions = listOf(Condition(type = MatchType.CONTAINS, pattern = "a"))),
            Rule(
                name = "allow",
                action = RuleAction.ALLOW,
                conditions = listOf(Condition(type = MatchType.CONTAINS, pattern = "b")),
            ),
        )
        assertNull(RuleEngine.firstMatch("", "ab", rules))
        assertEquals("block-all", RuleEngine.firstMatch("", "a", rules)?.name)
    }

    @Test
    fun `promo with a discount code is still blocked`() {
        // "کد تخفیف" is not a verification phrase, so the OTP preset must not
        // swallow real spam.
        val rules = RuleRepository.defaultRules()
        val match = RuleEngine.firstMatch("", "قرعه کشی! با کد تخفیف ثبت نام کن لغو11", rules)
        assertEquals("preset-optout", match?.id)
    }
}
