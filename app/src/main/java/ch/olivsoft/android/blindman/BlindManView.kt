package ch.olivsoft.android.blindman

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.widget.TextView
import ch.olivsoft.android.blindman.Effect.Companion.loadDynamicElements
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BlindManView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs), Animation.AnimationListener {

    companion object {
        // Constants
        val ALLOWED_LIVES: List<Int> = listOf(1, 2, 3, 4, 6, 9, 12, 0)
        private val LOG_TAG = BlindManView::class.simpleName
        private const val FULL_ALPHA = 0xFF
        private val BACKGROUND_ALPHA = intArrayOf(0, 0x40, 0x80)
        private val OBSTACLE_ROWS = intArrayOf(8, 11, 15)
        private const val DRAG_START_DELAY = 200L
        private const val DRAG_END_DELAY = 300L
    }

    // Variables visible to main activity
    lateinit var textView: TextView
    var size: Int = 1
    var level: Int = 1
    var background: Int = 1

    // 3 lives is the default
    var lives = ALLOWED_LIVES[2]
        set(value) {
            // Reset in case something went completely wrong
            field = if (ALLOWED_LIVES.contains(value)) value else ALLOWED_LIVES[2]
            if (gameState == GameState.PLAY) {
                if (field in 1..hits) newGame(0)
                else invalidate()
            }
        }

    // Private internal variables
    private var hits = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var fieldWidth = 0
    private var fieldHeight = 0
    private var offWidth = 0f
    private var offHeight = 0f
    private var oSize = 0
    private lateinit var player: Rect
    private lateinit var goal: Rect
    private lateinit var border: Rect
    private lateinit var obstacles: HashSet<Obstacle>
    private val random = Random.Default

    // Game motion
    private val dragStarter: DragStarter
    private val dragHandler: DragHandler
    private val gestureDetector: GestureDetector

    // For efficiency, objects used in onDraw should be created in advance.
    // We add further such objects from makeMove.
    private val drawingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawingRect = RectF()
    private val pp = Rect()

    // Animation
    private var swapColors = false

    // Game states
    private enum class GameState {
        IDLE, SHOW, PLAY, HINT
    }

    private var gameState = GameState.IDLE

    // This constructor is called by the layout mechanism.
    // Size-independent members can be initialized here.
    init {
        dragStarter = DragStarter()
        dragHandler = DragHandler()

        // Overriding methods of a SimpleGestureListener is sufficient.
        // Strange: DoubleTap listening works also without explicitly setting it.
        // In our case, setLongPressEnabled and setClickable are required in this
        // combination, there are many articles on why this is the case.
        val gestureListener = GestureListener()
        gestureDetector = GestureDetector(context, gestureListener)
        gestureDetector.setOnDoubleTapListener(gestureListener)
        gestureDetector.setIsLongpressEnabled(false)
        this.isClickable = true

        // Effects need some additional initialization
        loadDynamicElements(context, this)
        Log.d(LOG_TAG, "View created")
    }

    // This is also called by the layout mechanism.
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // Store width and height
        viewWidth = w
        viewHeight = h
        if (!this.isInEditMode)
            initField(0)
    }

    // Here, size-dependent members are set.
    fun initField(newSize: Int) {
        if (newSize > 0)
            size = min(newSize, OBSTACLE_ROWS.size)

        // Obstacle and field sizes
        oSize = max(viewWidth, viewHeight) / (2 * OBSTACLE_ROWS[size - 1] + 7)
        fieldWidth = viewWidth - viewWidth % oSize
        fieldHeight = viewHeight - viewHeight % oSize

        // The offsets are stored here and applied in onDraw
        offWidth = (viewWidth - fieldWidth) / 2f
        offHeight = (viewHeight - fieldHeight) / 2f

        // Allocate size-dependent objects
        border = Rect(0, 0, fieldWidth, fieldHeight)
        border.inset(oSize / 2, oSize / 2)
        goal = Rect(
            fieldWidth - 3 * oSize, fieldHeight - 3 * oSize,
            fieldWidth - oSize, fieldHeight - oSize
        )
        player = Rect(oSize, oSize, 2 * oSize, 2 * oSize)
        obstacles = HashSet(fieldWidth * fieldHeight / (oSize * oSize) / 2)

        // Set the game state to idle and put a message onto the text view
        gameState = GameState.IDLE
        textView.setText(R.string.mess_show)
        Log.d(LOG_TAG, "Field initialized")
    }

    fun newGame(newLevel: Int) {
        // Check who called
        if (newLevel > 0) level = newLevel

        // Player goes (back) to initial position, hits are cleared, obstacles recreated.
        player.offsetTo(oSize, oSize)
        hits = 0
        obstacles.clear()

        // Determine the orientation and count the available space for obstacles.
        // Last obstacle line is 5 units before goal-side end of canvas.
        // Across fieldWidth reserves 1 unit for each border.
        val o = (fieldHeight > fieldWidth)
        val lastAcross = (if (o) fieldHeight else fieldWidth) / oSize - 5
        val widthAcross = (if (o) fieldWidth else fieldHeight) / oSize - 2

        // The number of obstacles per line is based on the level.
        // A HashSet eliminates duplicates. Every second line
        // across contains randomly placed obstacles.
        val numObs = (0.3 * widthAcross * level).toInt()
        val obsLine = HashSet<Int>(numObs)
        var iLine = 3
        while (iLine <= lastAcross) {
            obsLine.clear()
            for (n in 1..numObs) {
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
                val obstacle = Obstacle(if (o) i else iLine, if (o) iLine else i, oSize)
                obstacles.add(obstacle)
            }
            iLine += 2
        }

        // Now we are in show state
        gameState = GameState.SHOW
        textView.setText(R.string.mess_start)
        invalidate()
    }

    // No time-consuming stuff here
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (this.isInEditMode)
            return

        // Translate used space into center
        canvas.translate(offWidth, offHeight)

        // Field background
        drawingPaint.color = ColoredPart.FIELD.color
        drawingPaint.alpha = BACKGROUND_ALPHA[background]
        drawingPaint.style = Paint.Style.FILL
        canvas.drawRect(border, drawingPaint)

        // Field border with reduced thickness
        drawingPaint.alpha = FULL_ALPHA
        drawingPaint.style = Paint.Style.STROKE
        drawingPaint.strokeWidth = oSize / 2f
        canvas.drawRect(border, drawingPaint)

        // Goal fully filled
        drawingPaint.style = Paint.Style.FILL
        drawingPaint.color = if (swapColors) ColoredPart.PLAYER.color else ColoredPart.GOAL.color
        canvas.drawRect(goal, drawingPaint)

        // Player is drawn after goal because it may be on top
        drawingRect.set(player)
        drawingPaint.color =
            if (swapColors) ColoredPart.GOAL.color
            else ColoredPart.PLAYER.color
        if (dragHandler.isDragModeActive) {
            // In drag mode the player looks like a ring
            val w = 0.1f * oSize
            drawingRect.inset(w, w)
            drawingPaint.style = Paint.Style.STROKE
            drawingPaint.strokeWidth = 2 * w
        }
        if (lives == 0)
            canvas.drawOval(drawingRect, drawingPaint)
        else
            canvas.drawArc(
                drawingRect, 0f,
                360 - 360f / lives * hits,
                true, drawingPaint
            )

        // Obstacles have their own onDraw method
        for (o in obstacles)
            o.draw(canvas)
    }

    // Move logic
    fun makeMove(x: Float, y: Float) {
        if (gameState != GameState.PLAY) return

        // Try the move with a copy of the player
        pp.set(player)
        if (abs(x) > abs(y))
            pp.offset(if (x > 0) oSize else -oSize, 0)
        else
            pp.offset(0, if (y > 0) oSize else -oSize)

        // Check for borders
        if (pp.left <= 0 ||
            pp.top <= 0 ||
            pp.left >= fieldWidth - oSize ||
            pp.top >= fieldHeight - oSize
        )
            return

        // Check for an obstacle
        for (o in obstacles) {
            if (o.intersects(pp)) {
                if (!o.isHit()) {
                    // Bad luck, we hit a hidden obstacle!
                    // Invalidate the area of the now visible
                    // obstacle and the player that lost one life.
                    o.setHit()
                    hits++
                    invalidate()
                    if (lives == 0 || hits < lives) {
                        // Go on
                        textView.setText(R.string.mess_hits)
                        textView.append(" $hits")
                        Effect.HIT.makeEffect(this)
                    } else {
                        // Game over
                        dragHandler.stopDragMode()
                        gameState = GameState.IDLE
                        textView.setText(R.string.mess_over)
                        Effect.OVER.makeEffect(this)
                    }
                }
                // In any case, this move has no future
                return
            }
        }

        // All is fine, the move can be performed.
        player.set(pp)
        invalidate()

        // GOAL reached?
        if (Rect.intersects(player, goal)) {
            dragHandler.stopDragMode()
            gameState = GameState.IDLE
            textView.append(". " + resources.getText(R.string.mess_goal))
            Effect.GOAL.makeEffect(this)
        }
    }

    // Touch event handling
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First we consider the play state
        if (gameState == GameState.PLAY) {
            // First we check for a placed or lifted finger
            // and stop the drag starter if it was running
            if (event.action == MotionEvent.ACTION_UP
                || event.action == MotionEvent.ACTION_DOWN
            )
                dragStarter.cancelTimer()

            // Main logic: If the dragHandler consumes the event
            // we are done. If not, it is given to the gestureDetector.
            // If this returns false again we try to detect a
            // motion event that may lead to a drag start.
            // Here we exceptionally use (non-forcing!) boolean operators
            // instead of if-sequences because of better readability.
            return (dragHandler.isDragModeActive && dragHandler.doDrag(event))
                    || gestureDetector.onTouchEvent(event)
                    || dragStarter.startTimer(event)
        }

        // In all other states we only look at a down event
        if (event.action != MotionEvent.ACTION_DOWN)
            return true

        // This switch clause uses return statements in each case.
        // So we do not need break statements or a final return.
        when (gameState) {
            GameState.IDLE -> {
                newGame(0)
                return true
            }

            GameState.SHOW -> {
                for (o in obstacles)
                    o.setHidden()
                gameState = GameState.PLAY
                textView.setText(R.string.mess_hits)
                textView.append(" 0")
                invalidate()
                // Start to move
                return gestureDetector.onTouchEvent(event)
            }

            GameState.HINT -> {
                for (o in obstacles)
                    o.setVisibleIfHit()
                gameState = GameState.PLAY
                invalidate()
                // Start to move again
                return gestureDetector.onTouchEvent(event)
            }

            else -> return false
        }
    }

    // DoubleTap and Gesture implementations. Only four methods
    // are needed, therefore we extend the simple helper class.
    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            Log.d(LOG_TAG, "onFling called")
            if (gameState != GameState.PLAY)
                return false

            // Make a move
            makeMove(velocityX, velocityY)
            return true
        }

        override fun onShowPress(e: MotionEvent) {
            Log.d(LOG_TAG, "onShowPress called")
            if (gameState != GameState.PLAY)
                return

            // Start dragging immediately
            dragStarter.cancelTimer()
            dragHandler.resetPosition(e)
            dragHandler.startDragMode()
            Effect.GRAB.makeEffect(this@BlindManView)
        }

        override fun onDown(e: MotionEvent): Boolean {
            // onDown must always return true in this usage pattern
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d(LOG_TAG, "onDoubleTap called")

            for (o in obstacles)
                o.setVisible()
            // Cancel drag mode (if active)
            dragHandler.stopDragMode()
            gameState = GameState.HINT
            invalidate()

            return true
        }
    }

    // Animation interface
    override fun onAnimationStart(animation: Animation) {
        // Start the cycle with inverted colors
        swapColors = true
        invalidate()
    }

    override fun onAnimationRepeat(animation: Animation) {
        if (gameState != GameState.IDLE) {
            // Someone was quick in tapping on the screen. According to
            // the documentation, onAnimationEnd is called after cancel,
            // so we don't have to do it. reset, however, must be
            // called manually. Note: cancel only exists since FROYO.
            animation.cancel()
            animation.reset()
            return
        }
        swapColors = !swapColors
        invalidate()
    }

    override fun onAnimationEnd(animation: Animation) {
        // Restore colors and redraw again
        swapColors = false
        invalidate()
    }

    // Drag starting class
    private inner class DragStarter : SimpleCountDownTimer(DRAG_START_DELAY) {
        fun startTimer(e: MotionEvent): Boolean {
            // This is a very well guarded routine: Make sure a motion in
            // non-dragging mode only starts the timer and later dragging
            // if a number of conditions are fulfilled.
            val doStart = !isTimerRunning
                    && !dragHandler.isDragModeActive
                    && e.action == MotionEvent.ACTION_MOVE
            if (doStart) {
                dragHandler.resetPosition(e)
                startTimer()
                Log.d(LOG_TAG, "DragStarter STARTED")
            }
            return doStart
        }

        override fun onTimerElapsed() {
            if (gameState == GameState.PLAY && !dragHandler.isDragModeActive) {
                dragHandler.startDragMode()
                Log.d(LOG_TAG, "Dragging started by DragStarter")
            }
        }
    }

    // Drag handling class
    private inner class DragHandler : SimpleCountDownTimer(DRAG_END_DELAY) {
        // Public access for efficiency
        var isDragModeActive: Boolean = false

        // Private variables
        private var oldX = 0f
        private var oldY = 0f

        override fun onTimerElapsed() {
            if (isDragModeActive)
                stopDragMode()
        }

        // Public methods
        fun resetPosition(e: MotionEvent) {
            oldX = e.x
            oldY = e.y
        }

        fun startDragMode() {
            isDragModeActive = true
            invalidate()
        }

        fun stopDragMode() {
            isDragModeActive = false
            cancelTimer()
            invalidate()
        }

        fun doDrag(e: MotionEvent): Boolean {
            // Check if we have to cancel the timer and reset the drag position
            if (isTimerRunning) {
                cancelTimer()
                resetPosition(e)
            }

            when (e.action) {
                MotionEvent.ACTION_UP -> {
                    Log.d(LOG_TAG, "doDrag.ACTION_UP called")
                    // Now we start the timer for ending the drag mode
                    startTimer()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dX = e.x - oldX
                    val dY = e.y - oldY
                    // Motion amplification gives a good user experience
                    if (dX * dX + dY * dY > 0.4f * oSize * oSize) {
                        makeMove(dX, dY)
                        oldX += dX
                        oldY += dY
                    }
                    return true
                }

                else -> return false
            }
        }
    }
}
