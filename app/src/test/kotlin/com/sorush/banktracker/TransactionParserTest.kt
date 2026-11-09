package com.sorush.banktracker

import com.sorush.banktracker.model.TransactionType
import com.sorush.banktracker.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {

    @Test
    fun testResalatDeposit() {
        val sms = "10.14423005.1\n+26,000,000\n08/12 20:21\nمانده: 26,529,867"
        val parsed = TransactionParser.parse("ResalatBank", sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.DEPOSIT, parsed?.type)
        assertEquals(2600000L, parsed?.amountToman)
        assertEquals(26000000L, parsed?.amountRial)
    }

    @Test
    fun testResalatWithdrawal() {
        val sms = "10.14423005.1\n-26,000,000\n08/14 00:23\nمانده: 529,867"
        val parsed = TransactionParser.parse("ResalatBank", sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals(2600000L, parsed?.amountToman)
    }

    @Test
    fun testSystemNoticeIgnored() {
        val sms = "مشتری گرامی؛\nبه‌منظور ارتقای سطح خدمات بانکی، ممکن است موقتا سرویس‌دهی برخی از سامانه‌های این بانک با اختلال مواجه شود."
        val parsed = TransactionParser.parse("ResalatBank", sms)
        assertNull(parsed)
    }

    @Test
    fun testPasargadWithdrawals() {
        val sms1 = "777.888.27870097.1\n-870,000\n08/18 02:56\nمانده: 217,458,760"
        val parsed1 = TransactionParser.parse("B.Pasargad", sms1)
        assertNotNull(parsed1)
        assertEquals(TransactionType.EXPENSE, parsed1?.type)
        assertEquals(87000L, parsed1?.amountToman)

        val sms2 = "777.888.27870097.1\n-300,000\n08/18 03:00\nمانده: 217,158,760"
        val parsed2 = TransactionParser.parse("B.Pasargad", sms2)
        assertNotNull(parsed2)
        assertEquals(30000L, parsed2?.amountToman)

        val sms3 = "777.888.27870097.1\n-1,700,000\n08/18 13:03\nمانده: 364,258,760"
        val parsed3 = TransactionParser.parse("B.Pasargad", sms3)
        assertNotNull(parsed3)
        assertEquals(170000L, parsed3?.amountToman)
    }
}
