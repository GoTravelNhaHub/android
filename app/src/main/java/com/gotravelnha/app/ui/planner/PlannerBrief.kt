package com.gotravelnha.app.ui.planner

import com.gotravelnha.app.data.AgentBrief
import com.gotravelnha.app.data.AgentUpdates
import com.gotravelnha.app.data.BriefTraveller
import com.gotravelnha.app.data.PartyMember

enum class BriefField { Destination, Dates, Ages }

/** One field the agent changed, shown under its reply */
sealed interface BriefChange {
    data class Destination(val value: String) : BriefChange
    data class Dates(val start: String, val end: String) : BriefChange
    data class Travellers(val ages: List<Int>) : BriefChange
    data class Budget(val value: Double) : BriefChange
    data class Pace(val value: String) : BriefChange
    data class Interests(val value: String) : BriefChange
}

/** What the planner knows about the trip. Filled by the agent and by the details sheet. */
data class PlannerBrief(
    val destination: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val party: List<PartyMember> = emptyList(),
    val budget: Double? = null,
    val pace: String = "balanced",
    val interests: String = "",
) {
    val adults get() = party.count { it.age >= ADULT_AGE }
    val children get() = party.filter { it.age < ADULT_AGE }

    /** What the itinerary call still needs */
    val missing: List<BriefField>
        get() = buildList {
            if (destination.trim().length < 2) add(BriefField.Destination)
            if (startDate == null || endDate == null) add(BriefField.Dates)
            if (party.isEmpty()) add(BriefField.Ages)
        }

    fun withAdults(count: Int): PlannerBrief {
        val adultsNow = party.filter { it.age >= ADULT_AGE }
        return copy(party = adultsNow.take(count) + List((count - adultsNow.size).coerceAtLeast(0)) { PartyMember(age = DEFAULT_ADULT_AGE) } + children)
    }

    fun withChild(age: Int) = copy(party = party + PartyMember(age = age))

    fun withoutChild(index: Int) = copy(party = party.filter { it.age >= ADULT_AGE } + children.filterIndexed { i, _ -> i != index })

    /** Applies the agent's updates and lists what actually changed. */
    fun applying(updates: AgentUpdates): Pair<PlannerBrief, List<BriefChange>> {
        var next = this
        val changes = mutableListOf<BriefChange>()
        updates.destination?.takeIf { it != destination }?.let {
            next = next.copy(destination = it)
            changes += BriefChange.Destination(it)
        }
        val start = updates.startDate ?: startDate
        val end = (updates.endDate ?: endDate)?.takeIf { start == null || it >= start }
        if (start != startDate || end != endDate) {
            next = next.copy(startDate = start, endDate = end)
            if (start != null && end != null) changes += BriefChange.Dates(start, end)
        }
        updates.travellers?.takeIf { it.isNotEmpty() }?.let { people ->
            next = next.copy(party = people.map { PartyMember(name = it.label, age = it.age) })
            changes += BriefChange.Travellers(people.map { it.age })
        }
        updates.budget?.takeIf { it != budget }?.let {
            next = next.copy(budget = it)
            changes += BriefChange.Budget(it)
        }
        updates.pace?.takeIf { it != pace }?.let {
            next = next.copy(pace = it)
            changes += BriefChange.Pace(it)
        }
        updates.interests?.takeIf { it != interests }?.let {
            next = next.copy(interests = it)
            changes += BriefChange.Interests(it)
        }
        return next to changes
    }

    fun toAgentBrief() = AgentBrief(
        destination = destination.trim().ifBlank { null },
        startDate = startDate,
        endDate = endDate,
        travellers = party.map { BriefTraveller(it.age) },
        budget = budget,
        pace = pace,
        interests = interests.trim().ifBlank { null },
    )

    private companion object {
        const val ADULT_AGE = 18
        const val DEFAULT_ADULT_AGE = 35
    }
}
