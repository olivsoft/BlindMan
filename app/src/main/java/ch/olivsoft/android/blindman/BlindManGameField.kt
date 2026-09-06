package ch.olivsoft.android.blindman

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.times
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toRect
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.olivsoft.android.blindman.BlindManViewModel.Companion.GameState
import ch.olivsoft.android.blindman.BlindManViewModel.Companion.OBSTACLE_ROWS
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

// Constants
private const val LOG_TAG = "BlindManGameField"

// Game field
@Composable
fun BlindManGameField(
    modifier: Modifier = Modifier
) {
    // Simple variables
    val random = Random.Default
    val iMode = LocalInspectionMode.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Painting
    val obstacles = remember { mutableStateListOf<Obstacle>() }
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var hits by remember { mutableIntStateOf(0) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    var fieldHeight by remember { mutableIntStateOf(0) }
    var fieldOffset by remember { mutableStateOf(Offset.Zero) }
    var fieldUnit by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf(IntRect.Zero) }
    var goal by remember { mutableStateOf(IntRect.Zero) }
    var border by remember { mutableStateOf(IntRect.Zero) }
    var fillPlayer by remember { mutableStateOf(true) }
    var firstTap by remember { mutableStateOf(true) }

    // Animation states
    val crashAlpha = remember { Animatable(1f) }
    var swapColors by remember { mutableStateOf(false) }
    var swapJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    // View model
    val bmViewModel: BlindManViewModel = viewModel()
    val size by bmViewModel.sizeData.observeAsState(bmViewModel.size)
    val level by bmViewModel.levelData.observeAsState(bmViewModel.level)
    val background by bmViewModel.backgroundData.observeAsState(bmViewModel.background)
    val lives by bmViewModel.livesData.observeAsState(bmViewModel.lives)
    val invalidateCounter by bmViewModel.invalidateCounterData.observeAsState(0)

    // Colors
    var fieldColor by remember { mutableIntStateOf(ColoredPart.FIELD.color) }
    var playerColor by remember { mutableIntStateOf(ColoredPart.PLAYER.color) }
    var goalColor by remember { mutableIntStateOf(ColoredPart.GOAL.color) }
    var obstacleColor by remember { mutableIntStateOf(ColoredPart.OBSTACLE.color) }

    // Canvas
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // New game trigger (not from view model)
    var newGameCounter by remember { mutableIntStateOf(0) }

    // Text in preview mode
    val textMeasurer = rememberTextMeasurer()

    // Key events
    val focusRequester = remember { FocusRequester() }

    // Helpers for message text
    fun setMessage(context: Context, id: Int) {
        bmViewModel.messageText = context.resources.getString(id)
    }

    fun setHitsMessage(context: Context, goalReached: Boolean = false) {
        var msg = context.resources.getString(R.string.mess_hits) + " $hits"
        if (goalReached)
            msg += ". " + context.resources.getString(R.string.mess_goal)
        bmViewModel.messageText = msg
    }

    // Functions for field creation and game setup
    fun initField(): Boolean {
        // Preview check
        if (canvasSize == IntSize.Zero)
            return false

        // Check for allowed sizes
        val safeSize = OBSTACLE_ROWS[size.coerceIn(1, OBSTACLE_ROWS.size) - 1]
        fieldUnit = max(canvasSize.width, canvasSize.height) / (2 * safeSize + 7)
        if (fieldUnit <= 0) {
            Log.e("GameField", "Calculation error: field unit is 0")
            return false
        }

        fieldWidth = canvasSize.width - canvasSize.width % fieldUnit
        fieldHeight = canvasSize.height - canvasSize.height % fieldUnit
        if (fieldWidth * fieldHeight <= 0) {
            Log.e("GameField", "Calculation error: field size is 0")
            return false
        }
        fieldOffset = Offset(
            (canvasSize.width - fieldWidth) / 2f,
            (canvasSize.height - fieldHeight) / 2f
        )

        border = IntRect(0, 0, fieldWidth, fieldHeight)
            .deflate(fieldUnit / 2)
        goal = IntRect(
            fieldWidth - 3 * fieldUnit, fieldHeight - 3 * fieldUnit,
            fieldWidth - fieldUnit, fieldHeight - fieldUnit
        )

        Log.d(LOG_TAG, "Field initialized")
        return true
    }

    fun newGame(): Boolean {
        // Stop animations if still running
        coroutineScope.launch {
            crashAlpha.snapTo(1f)
            swapJob?.cancel()
            swapColors = false
        }

        // New game: player goes (back) to initial position,
        // hits are cleared, obstacles recreated.
        player = IntRect(
            fieldUnit, fieldUnit,
            2 * fieldUnit, 2 * fieldUnit
        )
        hits = 0

        // Determine the orientation and count the available space for obstacles.
        // Last obstacle line is 5 units before goal-side end of canvas.
        // Across fieldWidth reserves 1 unit for each border.
        val orientation = (fieldHeight > fieldWidth)
        val lastAcross = (if (orientation) fieldHeight else fieldWidth) / fieldUnit - 5
        val widthAcross = (if (orientation) fieldWidth else fieldHeight) / fieldUnit - 2
        if (widthAcross < 1 || lastAcross <= 3) {
            Log.e("GameField", "Calculation error: Obstacles have no space")
            return false
        }

        // The number of obstacles per line is based on the level.
        // A HashSet eliminates duplicates. Every second line
        // across contains randomly placed obstacles.
        obstacles.clear()
        val numObs = (0.3 * widthAcross * level).toInt()
        val obsLine = HashSet<Int>(numObs)
        for (iLine in 3..lastAcross step 2) {
            obsLine.clear()
            repeat(numObs) {
                // Random number between and including 1 and widthAcross
                obsLine.add(1 + random.nextInt(widthAcross))
            }
            // For the easiest level some more border obstacles are needed
            if (level == 1) {
                if (random.nextFloat() < 0.2) obsLine.add(1)
                if (random.nextFloat() >= 0.8) obsLine.add(widthAcross)
            }
            // Now, the real obstacles are created
            for (i in obsLine) {
                obstacles.add(
                    Obstacle(
                        if (orientation) i else iLine,
                        if (orientation) iLine else i,
                        fieldUnit
                    )
                )
            }
        }

        // Ready for the game!
        firstTap = true
        gameState = GameState.SHOW
        setMessage(context, R.string.mess_start)
        Log.d(LOG_TAG, "New game ready")
        return true
    }

    // Move function
    fun makeMove(dx: Int, dy: Int) {
        // Move a copy of the player
        val pp = player.translate(
            dx * fieldUnit,
            dy * fieldUnit
        )

        // Stop at border
        if (!border.contains(pp.topLeft) || !border.contains(pp.bottomRight))
            return

        // Check if an obstacle was hit and what that means
        val oh = obstacles.find { it.overlaps(pp) }
        oh?.apply {
            if (!isHit()) {
                setHit()
                hits++
                setHitsMessage(context)
                if (bmViewModel.isHapticFeedbackEnabled)
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                // This is ok because hits >= 1
                if (lives !in 1..hits) {
                    // Simple hit
                    coroutineScope.launch {
                        if (bmViewModel.isSoundEffectsEnabled)
                            Effect.HIT.makeSoundEffect()
                    }
                } else {
                    // Crash
                    gameState = GameState.IDLE
                    setMessage(context, R.string.mess_over)
                    coroutineScope.launch {
                        if (bmViewModel.isSoundEffectsEnabled)
                            Effect.OVER.makeSoundEffect()
                        if (bmViewModel.isHapticFeedbackEnabled)
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        crashAlpha.run {
                            val durationMs = 100
                            animateTo(
                                targetValue = 0.7f,
                                animationSpec = repeatable(
                                    // Odd number, return to 1f follows below
                                    iterations = 5,
                                    animation = tween(
                                        durationMillis = durationMs
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = durationMs
                                )
                            )
                        }
                    }
                }
            }
            // No move after all
            return
        }

        // Make move and if lucky reach the goal!
        player = pp.copy()
        if (player.overlaps(goal)) {
            gameState = GameState.IDLE
            setHitsMessage(context = context, true)
            fillPlayer = true
            // Start goal animation (swap colors)
            swapJob = coroutineScope.launch {
                if (bmViewModel.isSoundEffectsEnabled)
                    Effect.GOAL.makeSoundEffect()
                // Even number please...
                repeat(10) {
                    swapColors = !swapColors
                    delay(50.milliseconds)
                }
            }
        }
    }

    // Key events
    fun handleKey(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown)
            return false

        when (gameState) {
            GameState.SHOW,
            GameState.HINT -> gameState = GameState.PLAY

            GameState.IDLE -> newGame()

            else -> {
                when (keyEvent.key) {
                    Key.DirectionRight,
                    Key.NumPadDirectionRight,
                    Key.NumPad6 -> makeMove(1, 0)

                    Key.DirectionLeft,
                    Key.NumPadDirectionLeft,
                    Key.NumPad4 -> makeMove(-1, 0)

                    Key.DirectionUp,
                    Key.NumPadDirectionUp,
                    Key.NumPad8 -> makeMove(0, -1)

                    Key.DirectionDown,
                    Key.NumPadDirectionDown,
                    Key.NumPad2 -> makeMove(0, 1)

                    else -> return false
                }
            }
        }
        return true
    }

    // Full field and game setup for initial start and relevant changes
    LaunchedEffect(canvasSize, level, size) {
        if (canvasSize == IntSize.Zero
            || !initField()
            || !newGame()
        )
            Log.d(LOG_TAG, "Launch incomplete")
    }

    // New game if asked for
    LaunchedEffect(newGameCounter) {
        if (newGameCounter > 0)
            newGame()
    }

    // New game if lives reduced
    LaunchedEffect(lives) {
        if (gameState == GameState.PLAY
            && lives > 0
            && hits >= lives
        )
            newGame()
    }

    // Redraw if called for
    LaunchedEffect(invalidateCounter) {
        if (invalidateCounter > 0) {
            fieldColor = ColoredPart.FIELD.color
            playerColor = ColoredPart.PLAYER.color
            goalColor = ColoredPart.GOAL.color
            obstacleColor = ColoredPart.OBSTACLE.color
        }
    }

    // Request focus for key events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                if (it != IntSize.Zero) {
                    Log.d(LOG_TAG, "Canvas size: $it")
                    canvasSize = it
                }
            }
            .clipToBounds()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                Log.d(LOG_TAG, "${it.key} / ${it.type}")
                handleKey(it)
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        when (gameState) {
                            GameState.IDLE -> newGameCounter++
                            GameState.PLAY -> {}
                            else -> {
                                gameState = GameState.PLAY
                                if (firstTap) {
                                    firstTap = false
                                    obstacles.forEach { it.setHidden() }
                                    setHitsMessage(context)
                                } else
                                    fillPlayer = false
                            }
                        }
                    },
                    onTap = { fillPlayer = true },
                    onDoubleTap = {
                        if (gameState == GameState.PLAY)
                            gameState = GameState.HINT
                        fillPlayer = true
                    }
                )
            }
            .pointerInput(Unit) {
                // Local variables are doing perfectly fine here
                var d = Offset.Zero
                val dMinSquared = 0.65f * 0.65f * fieldUnit * fieldUnit
                detectDragGestures(
                    onDragStart = {
                        d = Offset.Zero
                        fillPlayer = false
                        if (bmViewModel.isHapticFeedbackEnabled)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { fillPlayer = true },
                    onDragCancel = { fillPlayer = true }
                ) { _, dragAmount ->
                    // Only relevant during play
                    if (gameState != GameState.PLAY)
                        return@detectDragGestures

                    // Wait until drag goes far enough
                    d += dragAmount
                    if (d.getDistanceSquared() < dMinSquared)
                        return@detectDragGestures

                    // Move!
                    if (d.x.absoluteValue > d.y.absoluteValue)
                        makeMove(d.x.sign.toInt(), 0)
                    else
                        makeMove(0, d.y.sign.toInt())

                    // Reset drag measurement
                    d = Offset.Zero
                }
            }
    ) {
        // onDraw
        if (canvasSize == IntSize.Zero || fieldUnit <= 0)
            return@Canvas

        translate(fieldOffset.x, fieldOffset.y) {
            val alpha = crashAlpha.value
            val backgroundAlpha =
                BlindManViewModel.BACKGROUND_ALPHA[background] / 256f
            val fieldColorCurrent = Color(fieldColor)
            val playerColorCurrent = Color(playerColor)
            val goalColorCurrent = Color(goalColor)

            // Field
            val b = border.toRect()
            drawRect(
                color = fieldColorCurrent,
                alpha = backgroundAlpha * alpha,
                size = b.size,
                topLeft = b.topLeft
            )
            drawRect(
                color = fieldColorCurrent,
                alpha = alpha,
                style = Stroke(width = 0.5f * fieldUnit),
                size = b.size,
                topLeft = b.topLeft
            )

            // Goal
            val g = goal.toRect()
            drawRoundRect(
                color =
                    if (swapColors) playerColorCurrent
                    else goalColorCurrent,
                alpha = alpha,
                size = g.size,
                topLeft = g.topLeft,
                cornerRadius = CornerRadius(0.075f * g.width)
            )

            // Obstacles
            for (o in obstacles) {
                when (gameState) {
                    GameState.SHOW, GameState.HINT -> o.setVisible()
                    GameState.PLAY, GameState.IDLE -> o.setVisibleIfHit()
                }
                o.draw(drawContext.canvas.nativeCanvas, obstacleColor)
            }

            // Player
            val p = player.toRect()
            val color =
                if (swapColors) goalColorCurrent
                else playerColorCurrent
            val dr = if (fillPlayer) 0f else 0.1f * fieldUnit
            val pSize = fieldUnit - 2 * dr
            val style = if (fillPlayer) Fill else Stroke(2 * dr)
            if (lives == 0 || hits == 0) {
                drawCircle(
                    color = color,
                    alpha = alpha,
                    style = style,
                    center = p.center,
                    radius = pSize / 2
                )
            } else {
                // Division is safe here because lives != 0
                val sweepAngle = 360 - 360f / lives * hits
                val topLeft = p.topLeft + Offset(dr, dr)
                val size = Size(pSize, pSize)
                drawArc(
                    color = color,
                    alpha = alpha,
                    style = style,
                    topLeft = topLeft,
                    size = size,
                    startAngle = 0f,
                    sweepAngle = sweepAngle,
                    useCenter = true
                )
            }

            // Inspection...
            if (iMode) {
                val text = buildString {
                    appendLine("Field: $canvasSize")
                    appendLine("Obstacles: ${obstacles.size}")
                    append("Field unit: $fieldUnit")
                }
                val textStyle = TextStyle(
                    fontSize = fieldUnit.toSp(),
                    color = Color.Black
                )
                val textLayoutResult = textMeasurer.measure(text, textStyle)
                val backgroundSize = 1.1f * textLayoutResult.size.toSize()
                // NOTE: "center" does not change with translate!
                val c = center - fieldOffset
                drawRect(
                    size = backgroundSize,
                    color = Color.White,
                    topLeft = c - backgroundSize.center
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = c - textLayoutResult.size.center.toOffset()
                )
            }
        }
    }
}

@Preview
@Composable
fun GameFieldPreview() {
    val bmViewModel: BlindManViewModel = viewModel()
    bmViewModel.size = 2
    bmViewModel.level = 2
    BlindManTheme {
        // This is needed for applying the color scheme.
        // Canvas alone does not do this as it is transparent.
        // In the full layout, Scaffold takes care of this.
        Surface {
            BlindManGameField()
        }
    }
}
