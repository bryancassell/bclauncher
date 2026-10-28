package io.github.bryancassell.bclauncher.ui.home

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawablePainterTest {
    private val drawable = RecordingDrawable()

    private fun Painter.drawAt(size: Size, layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        // A Canvas without a bitmap, since the test checks what the painter tells the drawable, not the pixels.
        val canvas = androidx.compose.ui.graphics.Canvas(Canvas())
        CanvasDrawScope().draw(Density(1f), layoutDirection, canvas, size) { draw(size) }
    }

    @Test
    fun drawsAtTheSizeItIsGiven() {
        DrawablePainter(drawable).drawAt(Size(16f, 9f))

        assertEquals(16, drawable.bounds.width())
        assertEquals(9, drawable.bounds.height())
    }

    @Test
    fun drawsInTheLayoutDirection() {
        val painter = DrawablePainter(drawable)

        painter.drawAt(Size(16f, 9f), LayoutDirection.Rtl)
        assertEquals(View.LAYOUT_DIRECTION_RTL, drawable.drawnLayoutDirection)

        painter.drawAt(Size(16f, 9f), LayoutDirection.Ltr)
        assertEquals(View.LAYOUT_DIRECTION_LTR, drawable.drawnLayoutDirection)
    }

    @Test
    fun hasNoSizeOfItsOwnWhenTheDrawableHasNone() {
        assertEquals(Size.Unspecified, DrawablePainter(ColorDrawable()).intrinsicSize)
    }

    /** Records the layout direction it was last drawn in, which decides whether an autoMirrored drawable is mirrored. */
    private class RecordingDrawable : ColorDrawable() {
        var drawnLayoutDirection = -1

        override fun draw(canvas: Canvas) {
            drawnLayoutDirection = layoutDirection
            super.draw(canvas)
        }
    }
}
