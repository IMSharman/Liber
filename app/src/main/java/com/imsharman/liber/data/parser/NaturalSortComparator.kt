package com.imsharman.liber.data.parser

/**
 * Natural alphanumeric comparator so comic archive entries sort as:
 * `Page 1.jpg`, `Page 2.jpg`, `Page 10.jpg` instead of `Page 1.jpg`, `Page 10.jpg`, `Page 2.jpg`.
 */
object NaturalSortComparator : Comparator<String> {
    override fun compare(a: String?, b: String?): Int {
        if (a === b) return 0
        if (a == null) return -1
        if (b == null) return 1

        var ia = 0
        var ib = 0
        val na = a.length
        val nb = b.length

        while (ia < na && ib < nb) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                var startA = ia
                while (ia < na && a[ia].isDigit()) ia++
                var startB = ib
                while (ib < nb && b[ib].isDigit()) ib++

                val trimmedA = a.substring(startA, ia).trimStart('0')
                val trimmedB = b.substring(startB, ib).trimStart('0')

                if (trimmedA.length != trimmedB.length) {
                    return trimmedA.length.compareTo(trimmedB.length)
                }
                val cmp = trimmedA.compareTo(trimmedB)
                if (cmp != 0) return cmp
                // Tie-break by original digit run length (fewer leading zeros first)
                val lenDiff = (ia - startA).compareTo(ib - startB)
                if (lenDiff != 0) return lenDiff
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }
        return (na - ia).compareTo(nb - ib)
    }
}
