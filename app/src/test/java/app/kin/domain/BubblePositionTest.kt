package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class BubblePositionTest {
    @Test fun docksToPhysicalEdges() { assertEquals(0, BubblePosition.x(false,1080,100)); assertEquals(980, BubblePosition.x(true,1080,100)) }
    @Test fun nearestEdgeUsesTheWholeIcon() { assertFalse(BubblePosition.nearestRight(100,1080,100)); assertTrue(BubblePosition.nearestRight(900,1080,100)) }
    @Test fun fractionsClampOffScreenDragging() { assertEquals(0f,BubblePosition.fraction(-80,800,100,30),0f); assertEquals(1f,BubblePosition.fraction(900,800,100,30),0f) }
    @Test fun positionSurvivesChangingScreenDimensions() {
        val old=BubblePosition.y(.7f,1920,100,32)
        val fraction=BubblePosition.fraction(old,1920,100,32)
        val rotated=BubblePosition.y(fraction,1080,100,32)
        assertTrue(rotated in 32..948); assertEquals(.7f,BubblePosition.fraction(rotated,1080,100,32),.002f)
    }
    @Test fun expandedWindowStaysInBoundsAtTheBottom() { assertEquals(108,BubblePosition.y(1f,640,500,32)) }
    @Test fun tinyScreensNeverProduceNegativeCoordinates() { assertEquals(0,BubblePosition.x(true,60,100)); assertEquals(0,BubblePosition.y(1f,60,100,32)); assertEquals(0f,BubblePosition.fraction(80,60,100,32),0f) }
}
