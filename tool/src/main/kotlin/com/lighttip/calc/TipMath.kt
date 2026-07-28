package com.lighttip.calc

import kotlin.math.abs
import kotlin.math.roundToLong

/** "$1,234.56". Everything in this tool is cents, so this is the only place decimals appear. */
fun Long.asMoney(): String {
    val negative = this < 0
    val cents = abs(this)
    val whole = cents / 100
    val frac = cents % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-$" else "$") + grouped + "." + frac.toString().padStart(2, '0')
}

fun Double.toCents(): Long = (this * 100.0).roundToLong()

/** Tip is taken on the pre-tax amount, the usual US convention. */
fun tipCentsFor(baseCents: Long, percent: Int): Long =
    (baseCents * percent / 100.0).roundToLong()

/**
 * Split [totalCents] across [weights] proportionally, using largest-remainder so the
 * parts always add back up to exactly [totalCents] — no lost or invented pennies.
 */
fun allocateProportionally(totalCents: Long, weights: List<Long>): List<Long> {
    val weightSum = weights.sum()
    if (weights.isEmpty()) return emptyList()
    if (weightSum <= 0L) return evenlyDivide(totalCents, weights.size)

    val exact = weights.map { totalCents.toDouble() * it / weightSum }
    val floors = exact.map { kotlin.math.floor(it).toLong() }.toMutableList()
    var remaining = totalCents - floors.sum()
    val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
    var i = 0
    while (remaining > 0 && order.isNotEmpty()) {
        floors[order[i % order.size]] += 1
        remaining -= 1
        i += 1
    }
    return floors
}

/** Equal split, with the leftover pennies handed to the first people in the list. */
fun evenlyDivide(totalCents: Long, parts: Int): List<Long> {
    if (parts <= 0) return emptyList()
    val base = totalCents / parts
    val extra = totalCents % parts
    return (0 until parts).map { base + if (it < extra) 1L else 0L }
}

data class PersonShare(
    val person: PersonEntity,
    val itemsCents: Long,
    val taxCents: Long,
    val tipCents: Long,
) {
    val totalCents: Long get() = itemsCents + taxCents + tipCents
}

data class SplitResult(
    val shares: List<PersonShare>,
    val unassignedCents: Long,
    val unassignedCount: Int,
    val itemsSubtotalCents: Long,
    val taxCents: Long,
    val tipCents: Long,
) {
    val grandTotalCents: Long get() = itemsSubtotalCents + taxCents + tipCents
    val allAssigned: Boolean get() = unassignedCount == 0
}

/**
 * Work out what each person owes.
 *
 * An item shared by k people is divided k ways (pennies to the earliest assignee).
 * Tax and tip are then spread across people in proportion to what they actually ate,
 * which is the fair reading of "proportional" — order the lobster, carry the tax on it.
 * Items nobody claimed are excluded from everyone's share and surfaced separately.
 */
fun computeSplit(
    people: List<PersonEntity>,
    items: List<ItemEntity>,
    assignments: List<AssignmentEntity>,
    taxCents: Long,
    tipPercent: Int,
): SplitResult {
    val byItem: Map<String, List<String>> = assignments
        .groupBy { it.itemId }
        .mapValues { (_, rows) -> rows.map { it.personId } }

    val personIndex = people.withIndex().associate { (i, p) -> p.id to i }
    val itemsPerPerson = LongArray(people.size)
    var unassignedCents = 0L
    var unassignedCount = 0
    var assignedSubtotal = 0L

    for (item in items) {
        val claimants = byItem[item.id].orEmpty()
            .filter { personIndex.containsKey(it) }
            .sortedBy { personIndex[it] }
        if (claimants.isEmpty()) {
            unassignedCents += item.priceCents
            unassignedCount += 1
            continue
        }
        assignedSubtotal += item.priceCents
        val parts = evenlyDivide(item.priceCents, claimants.size)
        claimants.forEachIndexed { i, personId ->
            itemsPerPerson[personIndex.getValue(personId)] += parts[i]
        }
    }

    // Tax and tip only apply to the part of the bill somebody has claimed.
    val claimedFraction = if (items.sumOf { it.priceCents } > 0L) {
        assignedSubtotal.toDouble() / items.sumOf { it.priceCents }
    } else {
        0.0
    }
    val effectiveTax = (taxCents * claimedFraction).roundToLong()
    val effectiveTip = tipCentsFor(assignedSubtotal, tipPercent)

    val weights = itemsPerPerson.toList()
    val taxParts = allocateProportionally(effectiveTax, weights)
    val tipParts = allocateProportionally(effectiveTip, weights)

    val shares = people.mapIndexed { i, person ->
        PersonShare(
            person = person,
            itemsCents = itemsPerPerson[i],
            taxCents = taxParts.getOrElse(i) { 0L },
            tipCents = tipParts.getOrElse(i) { 0L },
        )
    }

    return SplitResult(
        shares = shares,
        unassignedCents = unassignedCents,
        unassignedCount = unassignedCount,
        itemsSubtotalCents = assignedSubtotal,
        taxCents = effectiveTax,
        tipCents = effectiveTip,
    )
}

/** Two-letter tag used to mark who is on an item without eating a whole row. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
