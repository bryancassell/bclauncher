package io.github.bryancassell.bclauncher.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The order the user put the home screen's apps in (ARCHITECTURE.md, Stored data). Empty until they move one. */
@Serializable
data class AppOrder(
    /** Each app's [android.content.ComponentName.flattenToString], first tile first. */
    val components: List<String> = emptyList(),
) {
    /**
     * [apps], given in alphabetical order, put in this order. Apps it doesn't name, such as one installed since the
     * user last moved an app, go after the rest, still alphabetical. Until the user moves one, that's all of them.
     *
     * Apps it names that aren't installed are skipped but stay in it until the order is next saved. After a restore
     * from backup, Play can reinstall apps after BC Launcher has opened, and each still goes back to its place (#8).
     */
    fun arrange(apps: List<InstalledApp>): List<InstalledApp> {
        if (components.isEmpty()) return apps
        // The apps not placed yet, still alphabetical. Each is removed as it's placed, so an app named twice shows once:
        // the grid throws on a repeated key, and a file restored from backup was written by another version of the app.
        val rest = apps.associateByTo(LinkedHashMap()) { it.component.flattenToString() }
        val renamed = renamedActivities(apps)
        val placed = components.mapNotNull { name ->
            rest.remove(name) ?: renamed[name.substringBefore('/')]?.let(rest::remove)
        }
        return placed + rest.values
    }

    /**
     * By package, the component of each package's only TV activity, when this order doesn't name it. It takes the place
     * of an activity of the package that the order names but isn't installed, as after an update renames the activity
     * or a restore to a TV whose copy of the app uses another one (#8). In a package with more than one TV activity,
     * there's no telling which one replaced it, so they go after the rest.
     */
    private fun renamedActivities(apps: List<InstalledApp>): Map<String, String> {
        val named = components.toSet()
        return apps.groupBy { it.component.packageName }
            .mapNotNull { (packageName, activities) ->
                val only = activities.singleOrNull()?.component?.flattenToString()
                if (only == null || only in named) null else packageName to only
            }
            .toMap()
    }
}

/**
 * Stores [AppOrder] as JSON. A file it can't read counts as corrupt, so DataStore's corruption handler can replace it
 * rather than every read failing.
 */
internal object AppOrderSerializer : Serializer<AppOrder> {
    // A key this version doesn't know, written by a later one, would otherwise make the file corrupt, and the handler
    // would replace the user's order with an empty one. A newer version can come back down when a release is rolled
    // back under a higher version code.
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = AppOrder()

    override suspend fun readFrom(input: InputStream): AppOrder = try {
        json.decodeFromString<AppOrder>(input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        // decodeFromString throws SerializationException, a subclass, for text that isn't an AppOrder.
        throw CorruptionException("Couldn't read the app order", e)
    }

    override suspend fun writeTo(t: AppOrder, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
