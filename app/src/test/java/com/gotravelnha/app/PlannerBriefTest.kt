package com.gotravelnha.app

import com.gotravelnha.app.data.AgentTraveller
import com.gotravelnha.app.data.AgentUpdates
import com.gotravelnha.app.data.PartyMember
import com.gotravelnha.app.ui.planner.BriefChange
import com.gotravelnha.app.ui.planner.BriefField
import com.gotravelnha.app.ui.planner.PlannerBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The same cases run on iOS (PlannerBriefTests.swift)
class PlannerBriefTest {
    @Test
    fun emptyBriefNeedsDestinationDatesAndAges() {
        assertEquals(listOf(BriefField.Destination, BriefField.Dates, BriefField.Ages), PlannerBrief().missing)
    }

    @Test
    fun completeBriefNeedsNothing() {
        val brief = PlannerBrief(destination = "Tokyo", startDate = "2027-01-02", endDate = "2027-01-05", party = listOf(PartyMember(age = 35)))
        assertTrue(brief.missing.isEmpty())
    }

    @Test
    fun updatesAreAppliedAndListed() {
        val (brief, changes) = PlannerBrief().applying(
            AgentUpdates(destination = "Tokyo", startDate = "2027-01-02", endDate = "2027-01-05", travellers = listOf(AgentTraveller(35), AgentTraveller(6, "Mia")), budget = 2000.0, pace = "relaxed", interests = "trains"),
        )
        assertEquals("Tokyo", brief.destination)
        assertEquals(listOf(PartyMember(age = 35), PartyMember(name = "Mia", age = 6)), brief.party)
        assertEquals(
            listOf(
                BriefChange.Destination("Tokyo"),
                BriefChange.Dates("2027-01-02", "2027-01-05"),
                BriefChange.Travellers(listOf(35, 6)),
                BriefChange.Budget(2000.0),
                BriefChange.Pace("relaxed"),
                BriefChange.Interests("trains"),
            ),
            changes,
        )
    }

    @Test
    fun unchangedValuesAreNotListed() {
        val before = PlannerBrief(destination = "Tokyo", startDate = "2027-01-02", endDate = "2027-01-05", pace = "packed")
        val (brief, changes) = before.applying(AgentUpdates(destination = "Tokyo", startDate = "2027-01-02", pace = "packed"))
        assertEquals(before, brief)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun newStartAfterEndClearsTheEnd() {
        val (brief, changes) = PlannerBrief(startDate = "2027-01-02", endDate = "2027-01-05").applying(AgentUpdates(startDate = "2027-02-01"))
        assertEquals("2027-02-01", brief.startDate)
        assertNull(brief.endDate)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun adultCountKeepsChildren() {
        val brief = PlannerBrief(party = listOf(PartyMember(age = 40), PartyMember(age = 6))).withAdults(2)
        assertEquals(2, brief.adults)
        assertEquals(listOf(6), brief.children.map { it.age })
        assertEquals(1, brief.withAdults(1).adults)
    }

    @Test
    fun removingAChildKeepsEveryoneElse() {
        val brief = PlannerBrief(party = listOf(PartyMember(age = 40), PartyMember(age = 6), PartyMember(age = 9))).withoutChild(0)
        assertEquals(listOf(40, 9), brief.party.map { it.age })
    }
}
