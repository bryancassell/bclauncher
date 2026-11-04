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
        val byComponent = apps.associateBy { it.component.flattenToString() }
        // distinct(), since the grid throws on a repeated key, and a file restored from backup was written by another
        // version of the app.
        val placed = components.distinct().mapNotNull { byComponent[it] }
        val placedComponents = placed.mapTo(HashSet()) { it.component }
        return placed + apps.filterNot { it.component in placedComponents }
    }
}

/**
 * Stores [AppOrder] as JSON. A file it can't read counts as corrupt, so DataStore's corruption handler can replace it
 * rather than every read failing.
 */
internal object AppOrderSerializer : Serializer<AppOrder> {
    override val defaultValue = AppOrder()

    override suspend fun readFrom(input: InputStream): AppOrder = try {
        Json.decodeFromString<AppOrder>(input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        // decodeFromString throws SerializationException, a subclass, for text that isn't an AppOrder.
        throw CorruptionException("Couldn't read the app order", e)
    }

    override suspend fun writeTo(t: AppOrder, output: OutputStream) {
        output.write(Json.encodeToString(t).encodeToByteArray())
    }
}
