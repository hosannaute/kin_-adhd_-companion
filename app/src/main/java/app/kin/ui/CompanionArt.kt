package app.kin.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kin.domain.Evolution
import kotlin.math.sin

enum class CreatureMood { IDLE, SLEEPING, THINKING, CELEBRATING, WAVING }

@Composable
fun CompanionArt(stage: Evolution, reducedMotion: Boolean, modifier: Modifier = Modifier,
                 mood: CreatureMood = CreatureMood.IDLE) {
    val motion = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "Gentle breathing")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "Breath")
        value
    }
    Canvas(modifier.semantics { contentDescription = "KIN companion, ${stage.label}, ${mood.name.lowercase()}" }) {
        val unit = minOf(size.width / 240, size.height / 230)
        translate((size.width - 240 * unit) / 2, (size.height - 230 * unit) / 2) {
            scale(unit, unit, Offset.Zero) {
                drawOval(Color(0xFF243845).copy(alpha = .16f), Offset(50f, 197f), Size(150f, 15f))
                val breathing = sin(motion * Math.PI * 2).toFloat() * 2
                translate(0f, -breathing) {
                    if (stage == Evolution.EGG) {
                        drawOval(Brush.linearGradient(listOf(Color(0xFFF7EDD6), Mint, Color(0xFFAFA4DF))), Offset(68f, 46f), Size(110f, 150f))
                        drawPath(Path().apply { moveTo(103f, 87f); lineTo(128f, 112f); lineTo(112f, 142f); lineTo(88f, 116f); close() }, Color.White.copy(alpha=.65f))
                        drawCircle(Violet.copy(alpha=.5f), 9f, Offset(146f, 153f))
                    } else {
                        val fur = when (stage) { Evolution.BABY -> Color(0xFFE6E7E8); Evolution.CURIOUS -> Color(0xFFCCE5DF)
                            Evolution.YOUNG -> Color(0xFFC0DFE7); Evolution.MATURE -> Color(0xFFC6C1E8); else -> Color(0xFFD2C7F0) }
                        drawPath(Path().apply { moveTo(168f, 159f); cubicTo(223f, 164f, 225f, 112f, 208f, 120f); cubicTo(209f, 140f, 179f, 127f, 162f, 150f); close() }, fur)
                        if (stage.ordinal >= Evolution.YOUNG.ordinal) {
                            drawPath(Path().apply { moveTo(154f, 149f); lineTo(216f, 95f); lineTo(203f, 143f); lineTo(225f, 158f); close() }, Color(0xFF8C9CCD))
                            drawPath(Path().apply { moveTo(77f, 145f); lineTo(28f, 103f); lineTo(33f, 145f); lineTo(18f, 155f); close() }, Color(0xFF8C9CCD))
                        }
                        drawOval(fur, Offset(77f, 122f), Size(92f, 77f))
                        drawOval(Color(0xFFFAF0DC), Offset(99f, 146f), Size(48f, 47f))
                        fun ear(left: Boolean) {
                            val path = Path().apply { moveTo(80f, 101f); cubicTo(39f, 80f, 20f, 43f, 27f, 35f); cubicTo(66f, 44f, 89f, 57f, 102f, 86f); close() }
                            if (left) drawPath(path, fur) else scale(-1f, 1f, Offset(120f, 0f)) { drawPath(path, fur) }
                            val inner = Path().apply { moveTo(77f, 82f); lineTo(40f, 48f); lineTo(92f, 81f); close() }
                            if (left) drawPath(inner, Color(0xFFD9AAC2)) else scale(-1f, 1f, Offset(120f, 0f)) { drawPath(inner, Color(0xFFD9AAC2)) }
                        }
                        ear(true); ear(false)
                        drawOval(fur, Offset(55f, 66f), Size(132f, 103f))
                        drawOval(Color(0xFFFFF5E6), Offset(66f, 83f), Size(110f, 77f))
                        val asleep = mood == CreatureMood.SLEEPING || (!reducedMotion && motion > .92f)
                        for (x in listOf(89f, 151f)) {
                            if (asleep) drawArc(Ink, 10f, 160f, false, Offset(x - 12, 113f), Size(24f, 12f), style = Stroke(3f))
                            else {
                                drawOval(Ink, Offset(x - 12, 104f), Size(24f, 31f))
                                drawOval(Color(0xFF658FA4), Offset(x - 9, 115f), Size(18f, 18f))
                                drawCircle(Ink, 7f, Offset(x, 118f))
                                drawCircle(Color.White, 4.5f, Offset(x - 4, 110f))
                                drawCircle(Color.White, 2f, Offset(x + 5, 122f))
                            }
                        }
                        drawOval(Color(0xFFE8B9BA).copy(alpha=.6f), Offset(69f, 133f), Size(20f, 9f))
                        drawOval(Color(0xFFE8B9BA).copy(alpha=.6f), Offset(151f, 133f), Size(20f, 9f))
                        drawCircle(Ink, 2.5f, Offset(120f, 135f))
                        drawArc(Ink, 15f, 150f, false, Offset(111f, 137f), Size(18f, 9f), style = Stroke(1.5f))
                        drawPath(Path().apply { moveTo(120f, 76f); lineTo(129f, 88f); lineTo(120f, 100f); lineTo(111f, 88f); close() }, Mint)
                        drawPath(Path().apply { moveTo(120f, 81f); lineTo(124f, 88f); lineTo(120f, 94f) }, Color.White, style = Stroke(2f))
                        drawOval(fur, Offset(78f, 185f), Size(32f, 17f))
                        drawOval(fur, Offset(139f, 185f), Size(32f, 17f))
                        val waving = mood == CreatureMood.WAVING || mood == CreatureMood.CELEBRATING
                        rotate(if (waving) -45f + breathing * 4 else 12f, Offset(164f, 153f)) {
                            drawOval(fur, Offset(151f, 143f), Size(22f, 35f))
                        }
                        if (stage == Evolution.EVOLVED) {
                            drawPath(Path().apply { moveTo(96f, 73f); lineTo(103f, 39f); lineTo(115f, 70f); close() }, Color(0xFFEACD91))
                            drawPath(Path().apply { moveTo(132f, 70f); lineTo(144f, 39f); lineTo(149f, 74f); close() }, Color(0xFFEACD91))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorldLandscape(modifier: Modifier = Modifier, night: Boolean = false, unlocked: Int = 1) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(if (night) listOf(Color(0xFF192B46), Color(0xFF557781))
            else listOf(Color(0xFFE0DFF5), Color(0xFFF3ECD9), Color(0xFFDEEEE6))))
        drawCircle(if (night) Color(0xFFFAEAC5) else Color(0xFFFFE9B2), w*.08f, Offset(w*.78f,h*.19f))
        fun mountain(x: Float, y: Float, width: Float, color: Color) {
            drawPath(Path().apply {
                moveTo(x-width/2,h*.78f)
                cubicTo(x-width*.3f,y+h*.14f,x-width*.17f,y,x,y)
                cubicTo(x+width*.17f,y,x+width*.3f,y+h*.14f,x+width/2,h*.78f)
                close()
            }, color)
        }
        mountain(w*.67f,h*.22f,w*.67f,Color(0xFF94AEB9)); mountain(w*.88f,h*.37f,w*.6f,Color(0xFF7E9FAB))
        drawOval(Color(0xFF88ABA0), Offset(-w*.4f,h*.54f), Size(w*1.5f,h*.65f))
        drawOval(if(night) Color(0xFF294D50) else Color(0xFF6C9380), Offset(w*.35f,h*.59f), Size(w,h*.6f))
        drawOval(if(night) Color(0xFF446478) else Color(0xFF9ACDD2), Offset(w*.41f,h*.7f), Size(w*.7f,h*.3f))
        drawOval(if(night) Color(0xFF264942) else Color(0xFF527B59), Offset(-w*.3f,h*.77f), Size(w*1.2f,h*.5f))
        // One layered tree frames the companion, without high-cost bitmaps or particles.
        drawPath(Path().apply { moveTo(0f,0f); lineTo(w*.2f,0f); cubicTo(w*.1f,h*.4f,w*.28f,h*.48f,w*.13f,h); lineTo(0f,h); close() }, Color(0xFF655643))
        for (i in 0..6) drawCircle(if(night) Color(0xFF274C45) else Color(0xFF547344), w*.17f,
            Offset(w*i*.09f,h*.035f + (i%2)*h*.06f))
        if (unlocked >= 1) {
            drawRoundRect(Color(0xFFE6CDA4), Offset(w*.6f,h*.55f), Size(w*.19f,h*.17f), androidx.compose.ui.geometry.CornerRadius(7f))
            drawPath(Path().apply { moveTo(w*.56f,h*.56f); lineTo(w*.69f,h*.43f); lineTo(w*.83f,h*.56f); close() }, Color(0xFF80718A))
            drawRoundRect(Color(0xFF695653), Offset(w*.68f,h*.63f), Size(w*.05f,h*.09f), androidx.compose.ui.geometry.CornerRadius(6f))
            drawRect(Color(0xFFF6DF9A), Offset(w*.62f,h*.59f), Size(w*.04f,h*.04f))
        }
        if (unlocked >= 2) for(i in 0..13) {
            val x = w*(.2f+i*.04f); val y=h*(.87f+(i%3)*.035f)
            drawLine(Color(0xFF365F48), Offset(x,y),Offset(x,y-h*.035f),2f)
            drawCircle(if(i%2==0) Color(0xFFE5B5D0) else Color(0xFFF4D695),w*.009f,Offset(x,y-h*.035f))
        }
    }
}
