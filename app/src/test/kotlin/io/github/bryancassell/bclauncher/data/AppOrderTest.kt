package io.github.bryancassell.bclauncher.data

import androidx.datastore.core.CorruptionException
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOrderTest {
    private val apple = fakeApp("Apple")
    private val netflix = fakeApp("Netflix")
    private val youTube = fakeApp("YouTube")

    private fun orderOf(vararg apps: InstalledApp) =
        AppOrder(apps.map { it.component.flattenToString() })

    @Test
    fun anEmptyOrderLeavesTheAppsAlphabetical() {
        val arranged = AppOrder().arrange(listOf(apple, netflix, youTube))

        assertEquals(listOf(apple, netflix, youTube), arranged)
    }

    @Test
    fun putsTheAppsInTheSavedOrder() {
        val arranged = orderOf(youTube, apple, netflix).arrange(listOf(apple, netflix, youTube))

        assertEquals(listOf(youTube, apple, netflix), arranged)
    }

    @Test
    fun putsAppsTheOrderDoesNotNameAtTheEndAlphabetically() {
        val disney = fakeApp("Disney")
        val zattoo = fakeApp("Zattoo")

        val arranged = orderOf(youTube, netflix).arrange(listOf(disney, netflix, youTube, zattoo))

        assertEquals(listOf(youTube, netflix, disney, zattoo), arranged)
    }

    @Test
    fun skipsAppsThatAreNotInstalledButKeepsTheirPlaces() {
        val order = orderOf(youTube, apple, netflix)

        val withoutApple = order.arrange(listOf(netflix, youTube))
        val withAppleBack = order.arrange(listOf(apple, netflix, youTube))

        assertEquals(listOf(youTube, netflix), withoutApple)
        assertEquals(listOf(youTube, apple, netflix), withAppleBack)
    }

    @Test
    fun showsAnAppOnceWhenTheOrderNamesItTwice() {
        val order =
            AppOrder(orderOf(youTube, apple).components + youTube.component.flattenToString())

        val arranged = order.arrange(listOf(apple, youTube))

        assertEquals(listOf(youTube, apple), arranged)
    }

    @Test
    fun aRenamedActivityTakesThePlaceOfItsPackagesOldOne() {
        val renamedYouTube = fakeApp("YouTube", activityName = "com.example.youtube.TvActivity")

        val arranged = orderOf(youTube, apple).arrange(listOf(apple, netflix, renamedYouTube))

        assertEquals(listOf(renamedYouTube, apple, netflix), arranged)
    }

    @Test
    fun aPackageWithTwoActivitiesLeavesThemAtTheEnd() {
        val youTubeKids =
            fakeApp("YouTube Kids", packageName = "com.example.youtube", activityName = "Kids")
        val youTubeTv =
            fakeApp("YouTube TV", packageName = "com.example.youtube", activityName = "Tv")

        val arranged = orderOf(youTube, apple).arrange(listOf(apple, youTubeKids, youTubeTv))

        assertEquals(listOf(apple, youTubeKids, youTubeTv), arranged)
    }

    @Test
    fun anActivityTheOrderNamesKeepsItsOwnPlace() {
        val renamedYouTube = fakeApp("YouTube", activityName = "com.example.youtube.TvActivity")

        val arranged = orderOf(
            youTube,
            apple,
            renamedYouTube,
        ).arrange(listOf(apple, renamedYouTube))

        assertEquals(listOf(apple, renamedYouTube), arranged)
    }

    @Test
    fun theSerializerIgnoresKeysItDoesNotKnow() = runTest {
        val text = """{"components":["${youTube.component.flattenToString()}"],"hidden":[]}"""

        val read = AppOrderSerializer.readFrom(ByteArrayInputStream(text.encodeToByteArray()))

        assertEquals(orderOf(youTube), read)
    }

    @Test
    fun theSerializerReadsBackWhatItWrote() = runTest {
        val order = orderOf(youTube, apple)
        val output = ByteArrayOutputStream()

        AppOrderSerializer.writeTo(order, output)
        val read = AppOrderSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals(order, read)
    }

    @Test
    fun theSerializerCallsTextThatIsNotAnOrderCorrupt() {
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                AppOrderSerializer.readFrom(ByteArrayInputStream("not json".encodeToByteArray()))
            }
        }
    }
}
