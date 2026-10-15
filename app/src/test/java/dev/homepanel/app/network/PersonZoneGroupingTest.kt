package dev.homepanel.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonZoneGroupingTest {
    private val zones = listOf(
        HomeZoneLocation("zone.home", "Casa", 51.0, 4.0),
        HomeZoneLocation("zone.instituto", "Instituto", 51.1, 4.1)
    )

    @Test
    fun groupsPeopleByHomeAssistantZoneAndExcludesTrackers() {
        val summary = HouseSummary(
            zones = zones,
            persons = listOf(
                PersonLocation("person.fina", "Fina", "home", null, null),
                PersonLocation("person.alex", "Alex", "home", null, null),
                PersonLocation("person.alixer", "Alixer", "Instituto", null, null),
                PersonLocation("device_tracker.bike", "eBike", "home", 51.0, 4.0, entityType = "device_tracker")
            )
        )
        assertEquals(listOf("Alex, Fina (Casa)", "Alixer (Instituto)"),
            summary.groupPeopleByZone().map { it.displayText })
    }

    @Test
    fun supportsZoneEntityIdCaseInsensitivityAndUnknownStates() {
        val summary = HouseSummary(
            zones = zones,
            persons = listOf(
                PersonLocation("person.b", "B", "zone.instituto", null, null),
                PersonLocation("person.a", "A", "INSTITUTO", null, null),
                PersonLocation("person.c", "C", "not_home", null, null),
                PersonLocation("person.d", "D", "unknown", null, null),
                PersonLocation("person.e", "E", "unavailable", null, null),
                PersonLocation("person.out", "Out", "Other place", null, null)
            )
        )
        assertEquals(listOf(
            "A, B (Instituto)", "Out (Other place)",
            "C (Outside zones)", "D, E (Unknown)"
        ), summary.groupPeopleByZone().map { it.displayText })
    }

    @Test
    fun mapPersonFilterRejectsNonPersonEntityIds() {
        assertEquals(false, isHomeAssistantPerson(PersonLocation("zone.home", "Casa", "home", 1.0, 2.0)))
        assertEquals(false, isHomeAssistantPerson(PersonLocation("device_tracker.bike", "Bike", "home", 1.0, 2.0)))
        assertEquals(true, isHomeAssistantPerson(PersonLocation("person.alex", "Alex", "home", 1.0, 2.0)))
    }
}
