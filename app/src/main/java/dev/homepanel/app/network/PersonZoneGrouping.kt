package dev.homepanel.app.network

import java.util.Locale

/**
 * A Home Assistant person belongs to exactly one reported zone/state.
 * Non-person trackers are deliberately excluded from presence UI.
 */
data class PersonZoneGroup(
    val zoneName: String,
    val persons: List<PersonLocation>,
    val category: Category
) {
    enum class Category { HOME, ZONE, OUTSIDE, UNKNOWN }

    val displayText: String
        get() = "${persons.joinToString(", ") { it.friendlyName }} ($zoneName)"
}

fun isHomeAssistantPerson(location: PersonLocation): Boolean =
    location.entityType == "person" && location.entityId.startsWith("person.")

fun personZoneName(person: PersonLocation, zones: List<HomeZoneLocation>): String {
    val state = person.state.trim()
    val home = zones.firstOrNull { it.entityId.equals("zone.home", ignoreCase = true) }
    if (state.equals("home", ignoreCase = true) || state.equals("zone.home", ignoreCase = true)) {
        return home?.friendlyName?.takeIf { it.isNotBlank() } ?: "Home"
    }
    if (state.isBlank() || state.equals("unknown", ignoreCase = true) ||
        state.equals("unavailable", ignoreCase = true)) return "Unknown"
    if (state.equals("not_home", ignoreCase = true)) return "Outside zones"

    val matchingZone = zones.firstOrNull { zone ->
        zone.friendlyName.equals(state, ignoreCase = true) ||
            zone.entityId.removePrefix("zone.").equals(state, ignoreCase = true) ||
            zone.entityId.equals(state, ignoreCase = true)
    }
    return matchingZone?.friendlyName?.takeIf { it.isNotBlank() } ?: state
}

/**
 * Show everyone, not just persons whose state is "home". Order: home, named
 * zones, outside, unknown. Zone names come from Home Assistant zone.* entities.
 */
fun HouseSummary.groupPeopleByZone(): List<PersonZoneGroup> {
    val eligible = persons.filter(::isHomeAssistantPerson)
    val buckets = eligible.groupBy { person ->
        val state = person.state.trim()
        val category = when {
            state.equals("home", true) || state.equals("zone.home", true) ->
                PersonZoneGroup.Category.HOME
            state.equals("not_home", true) ->
                PersonZoneGroup.Category.OUTSIDE
            state.isBlank() || state.equals("unknown", true) || state.equals("unavailable", true) ->
                PersonZoneGroup.Category.UNKNOWN
            else -> PersonZoneGroup.Category.ZONE
        }
        val name = personZoneName(person, zones)
        // Normalize different casing in states so they merge into one group.
        category to name.trim().lowercase(Locale.ROOT)
    }
    return buckets.map { (key, people) ->
        val sample = people.first()
        PersonZoneGroup(
            zoneName = personZoneName(sample, zones),
            persons = people.sortedBy { it.friendlyName.lowercase(Locale.ROOT) },
            category = key.first
        )
    }.sortedWith(compareBy(
        { when (it.category) {
            PersonZoneGroup.Category.HOME -> 0
            PersonZoneGroup.Category.ZONE -> 1
            PersonZoneGroup.Category.OUTSIDE -> 2
            PersonZoneGroup.Category.UNKNOWN -> 3
        } },
        { it.zoneName.lowercase(Locale.ROOT) }
    ))
}
