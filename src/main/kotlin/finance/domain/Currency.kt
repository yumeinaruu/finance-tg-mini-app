package finance.domain

enum class Currency(
    val aliases: Set<String>,
    /** How many units are shown in rate listings, e.g. "100 RUB". */
    val rateUnit: Int = 1,
) {
    BYN(setOf("byn", "р", "руб", "рублей")),
    USD(setOf("usd", "$", "долл", "дол", "доллар", "долларов", "бакс", "баксов")),
    EUR(setOf("eur", "€", "евро")),
    RUB(setOf("rub", "rur", "₽", "рос"), rateUnit = 100),
    ;

    val code: String get() = name

    companion object {
        val BASE = BYN

        private val byAlias: Map<String, Currency> =
            entries.flatMap { currency -> (currency.aliases + currency.code.lowercase()).map { it to currency } }.toMap()

        fun fromAlias(token: String): Currency? = byAlias[token.lowercase()]
    }
}
