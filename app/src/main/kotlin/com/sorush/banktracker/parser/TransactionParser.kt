package com.sorush.banktracker.parser

import com.sorush.banktracker.model.TransactionType
import java.util.regex.Pattern

data class ParsedTransaction(
    val amountToman: Long,
    val amountRial: Long,
    val type: TransactionType,
    val bankName: String,
    val rawMessage: String
)

object TransactionParser {

    // Matches signed amounts like +26,000,000 or -870,000 or -300000
    // Negative lookbehind ensures it's not part of an account number or float (like .1)
    private val SIGNED_AMOUNT_PATTERN = Pattern.compile(
        "(?:^|[\\r\\n\\s])([+\\-])\\s*([\\d,،]{3,})",
        Pattern.MULTILINE
    )

    fun normalizeDigits(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '۰', '٠' -> builder.append('0')
                '۱', '١' -> builder.append('1')
                '۲', '٢' -> builder.append('2')
                '۳', '٣' -> builder.append('3')
                '۴', '٤' -> builder.append('4')
                '۵', '٥' -> builder.append('5')
                '۶', '٦' -> builder.append('6')
                '۷', '٧' -> builder.append('7')
                '۸', '٨' -> builder.append('8')
                '۹', '٩' -> builder.append('9')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    fun parse(sender: String, messageBody: String): ParsedTransaction? {
        val normalized = normalizeDigits(messageBody)
        val matcher = SIGNED_AMOUNT_PATTERN.matcher(normalized)

        if (!matcher.find()) {
            return null
        }

        val sign = matcher.group(1) ?: return null
        val rawNumberStr = matcher.group(2)
            ?.replace(",", "")
            ?.replace("،", "")
            ?.trim() ?: return null

        val amountRial = rawNumberStr.toLongOrNull() ?: return null
        if (amountRial <= 0) return null

        val amountToman = amountRial / 10L
        val type = if (sign == "+") TransactionType.DEPOSIT else TransactionType.EXPENSE
        val bankName = detectBankName(sender, messageBody)

        return ParsedTransaction(
            amountToman = amountToman,
            amountRial = amountRial,
            type = type,
            bankName = bankName,
            rawMessage = messageBody
        )
    }

    fun detectBankName(sender: String, body: String): String {
        val lowerSender = sender.lowercase()
        val lowerBody = body.lowercase()

        return when {
            lowerSender.contains("resalat") || lowerBody.contains("رسالت") -> "بانک قرض‌الحسنه رسالت"
            lowerSender.contains("pasargad") || lowerBody.contains("پاسارگاد") -> "بانک پاسارگاد"
            lowerSender.contains("mellat") || lowerBody.contains("ملت") -> "بانک ملت"
            lowerSender.contains("melli") || lowerBody.contains("ملی") -> "بانک ملی ایران"
            lowerSender.contains("saderat") || lowerBody.contains("صادرات") -> "بانک صادرات"
            lowerSender.contains("tejarat") || lowerBody.contains("تجارت") -> "بانک تجارت"
            lowerSender.contains("saman") || lowerBody.contains("سامان") -> "بانک سامان"
            lowerSender.contains("blubank") || lowerSender.contains("blu") || lowerBody.contains("بلو") -> "بلوبانک"
            lowerSender.contains("shahr") || lowerBody.contains("شهر") -> "بانک شهر"
            lowerSender.contains("sepah") || lowerBody.contains("سپه") -> "بانک سپه"
            sender.isNotBlank() -> sender
            else -> "تراکنش بانکی"
        }
    }
}
