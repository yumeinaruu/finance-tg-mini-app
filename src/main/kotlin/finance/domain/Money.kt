package finance.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    fun toMinor(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()

    fun toBase(amountMinor: Long, rate: BigDecimal): Long =
        BigDecimal.valueOf(amountMinor).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** 173520 -> "1 735.20" (sign is not included). */
    fun format(minor: Long): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }
        return DecimalFormat("#,##0.00", symbols).format(BigDecimal.valueOf(minor).movePointLeft(2).abs())
    }
}
