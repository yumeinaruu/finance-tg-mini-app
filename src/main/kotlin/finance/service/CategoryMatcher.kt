package finance.service

import finance.domain.Category

/** Finds a category for an operation note by aliases and category names. */
object CategoryMatcher {

    fun normalize(word: String): String =
        word.lowercase().replace('ё', 'е').trim { !it.isLetterOrDigit() }

    fun words(note: String?): List<String> =
        note.orEmpty().split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }

    fun match(note: String?, categories: List<Category>, aliases: Map<String, Long>): Category? {
        val words = words(note)
        if (words.isEmpty()) return null
        val byId = categories.associateBy { it.id }
        val keys: Map<String, Category> =
            aliases.mapNotNull { (alias, id) -> byId[id]?.let { alias to it } }.toMap() +
                categories.associateBy { normalize(it.name) }

        words.firstNotNullOfOrNull { keys[it] }?.let { return it }
        keys[words.joinToString(" ")]?.let { return it }
        return words.firstNotNullOfOrNull { word -> keys.entries.firstOrNull { similar(word, it.key) }?.value }
    }

    /** Tolerates word endings: "продуктов" ~ "продукты", "кофейня" ~ "кофе". */
    private fun similar(word: String, key: String): Boolean {
        if (key.length >= 4 && word.startsWith(key)) return true
        if (word.length < 5 || key.length < 5) return false
        return word.commonPrefixWith(key).length >= minOf(word.length, key.length) - 2
    }
}
