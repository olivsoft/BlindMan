package ch.olivsoft.android.blindman

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import ch.olivsoft.android.blindman.BlindManViewModel.Companion.GameState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

class BlindManView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs), Animation.AnimationListener {

    companion object {
        // Constants
        private val LOG_TAG = BlindManView::class.simpleName
        private const val FULL_ALPHA = 0xFF
        private const val DRAG_START_DELAY = 200L
        private const val DRAG_END_DELAY = 100L
    }

    // View model and dependent variables
    // which will be initialized later
    private lateinit var bmViewModel: BlindManViewModel
    private var size = 0

    // Variables
    private var hits = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var fieldWidth = 0
    private var fieldHeight = 0
    private var offWidth = 0f
    private var offHeight = 0f
    private var oSize = 0
    private var player = Rect()
    private var goal = Rect()
    private var border = Rect()
    private var obstacles = HashSet<Obstacle>(100)
    private val random = Random.Default

    // Game motion
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val dragStarter = DragStarter()
    private val dragHandler = DragHandler()

    // Drawing
    private val drawingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawingRect = RectF()
    private val pp = Rect()

    // Animation
    private var swapColors = false

    private var gameState = GameState.IDLE

    // Constructor
    init {
        // Focus for keyboard input
        isFocusable = true
        isFocusableInTouchMode = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        isClickable = true
    }

    // Further initialization with additional safety for preview
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // ViewModel and Effect initialization
        if (isInEditMode) {
            bmViewModel = BlindManViewModel()
        } else {
            // Safer way to find the ViewModelStoreOwner
            val owner = findViewTreeViewModelStoreOwner() ?: (context as ViewModelStoreOwner)
            bmViewModel = ViewModelProvider(owner)[BlindManViewModel::class]
            // Effects need some additional initialization
            Effect.loadDynamicElements(context, this)
        }

        // Lifecycle owner
        val owner = findViewTreeLifecycleOwner() ?: return

        // Observe LiveData. All are called at first run.
        // Therefore, we include some protection.
        bmViewModel.isHapticFeedbackEnabledData.observe(owner) {
            isHapticFeedbackEnabled = it
        }
        bmViewModel.isSoundEffectsEnabledData.observe(owner) {
            isSoundEffectsEnabled = it
        }
        bmViewModel.invalidateCounterData.observe(owner) {
            if (it > 0) invalidate()
        }
        bmViewModel.livesData.observe(owner) {
            if (gameState == GameState.PLAY) {
                if ((it > 0) && (hits >= it)) newGame()
                else invalidate()
            }
        }
        bmViewModel.sizeData.observe(owner) {
            size = it
            if (viewWidth * viewHeight > 0)
                initField(it)
        }
        bmViewModel.levelData.observe(owner) {
            if (oSize > 0)
                newGame()
        }
    }

    // This is called by the layout mechanism
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // Store width and height
        viewWidth = w
        viewHeight = h
        if (w * h > 0)
            initField(0)
    }

    // Here, size-dependent members are set
    private fun initField(newSize: Int) {
        if (newSize > 0)
            size = min(newSize, BlindManViewModel.OBSTACLE_ROWS.size)

        // Obstacle and field sizes
        oSize = max(viewWidth, viewHeight) /
                (2 * BlindManViewModel.OBSTACLE_ROWS[size - 1] + 7)
        fieldWidth = viewWidth - viewWidth % oSize
        fieldHeight = viewHeight - viewHeight % oSize

        // The offsets are stored here and applied in onDraw
        offWidth = (viewWidth - fieldWidth) / 2f
        offHeight = (viewHeight - fieldHeight) / 2f

        // Allocate size-dependent objects
        border.set(0, 0, fieldWidth, fieldHeight)
        border.inset(oSize / 2, oSize / 2)
        goal.set(
            fieldWidth - 3 * oSize, fieldHeight - 3 * oSize,
            fieldWidth - oSize, fieldHeight - oSize
        )
        player.set(oSize, oSize, 2 * oSize, 2 * oSize)

        // Set the game state to idle and put a message onto the text view
        gameState = GameState.IDLE
        bmViewModel.messageText = resources.getString(R.string.mess_show)
        Log.d(LOG_TAG, "Field initialized")

        // Start a new game
        newGame()
    }

    private fun newGame() {
        // Extra protection for compose preview
        if (oSize <= 0)
            return

        // Player goes (back) to initial position, hits are cleared, obstacles recreated.
        player.offsetTo(oSize, oSize)
        hits = 0
        obstacles.clear()

        // Determine the orientation and count the available space for obstacles.
        // Last obstacle line is 5 units before goal-side end of canvas.
        // Across fieldWidth reserves 1 unit for each border.
        val portrait = (fieldHeight > fieldWidth)
        val lastAcross = (if (portrait) fieldHeight else fieldWidth) / oSize - 5
        val widthAcross = (if (portrait) fieldWidth else fieldHeight) / oSize - 2

        // The number of obstacles per line is based on the level.
        // A HashSet eliminates duplicates. Every second line
        // across contains randomly placed obstacles.
        val numObs = (0.3 * widthAcross * bmViewModel.level).toInt()
        val obsLine = HashSet<Int>(numObs)
        var iLine = 3
        while (iLine <= lastAcross) {
            obsLine.clear()
            repeat(numObs) {
                // Random number between and including 1 and widthAcross
                obsLine.add(1 + random.nextInt(widthAcross))
            }
            // For the easiest level some more border obstacles are needed
            if (bmViewModel.level == 1) {
                if (random.nextFloat() < 0.2) obsLine.add(1)
                if (random.nextFloat() >= 0.8) obsLine.add(widthAcross)
            }
            // Now, the real obstacles are created
            for (i in obsLine) {
                val ix = if (portrait) i else iLine
                val iy = if (portrait) iLine else i
                obstacles.add(Obstacle(ix, iy, oSize))
            }
            iLine += 2
        }

        // Now we are in show state
        gameState = GameState.SHOW
        bmViewModel.messageText = resources.getString(R.string.mess_start)
        invalidate()
    }

    // No time-consuming stuff here
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Translate used space into center
        canvas.translate(offWidth, offHeight)

        with(drawingPaint) {
            // Field background
            color = ColoredPart.FIELD.color
            alpha = BlindManViewModel.BACKGROUND_ALPHA[bmViewModel.background]
            style = Paint.Style.FILL
            canvas.drawRect(border, this)

            // Field border with reduced thickness
            alpha = FULL_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = oSize / 2f
            canvas.drawRect(border, this)

            // Goal fully filled
            style = Paint.Style.FILL
            color = if (swapColors) ColoredPart.PLAYER.color else ColoredPart.GOAL.color
            canvas.drawRect(goal, this)

            // Player is drawn after goal because it may be on top
            drawingRect.set(player)
            color = if (swapColors) ColoredPart.GOAL.color else ColoredPart.PLAYER.color
            if (dragHandler.isDragModeActive) {
                // In drag mode the player looks like a ring
                val w = 0.1f * oSize
                drawingRect.inset(w, w)
                style = Paint.Style.STROKE
                strokeWidth = 2 * w
            }
            if (bmViewModel.lives == 0)
                canvas.drawOval(drawingRect, this)
            else
                canvas.drawArc(
                    drawingRect, 0f,
                    360 - 360f / bmViewModel.lives * hits,
                    true, this
                )
        }

        // Obstacles have their own onDraw method
        obstacles.forEach { it.draw(canvas) }
    }

    // Move logic
    private fun makeMove(x: Float, y: Float) {
        if (gameState != GameState.PLAY)
            return

        // Try the move with a copy of the player
        pp.set(player)
        if (abs(x) > abs(y))
            pp.offset(sign(x).toInt() * oSize, 0)
        else
            pp.offset(0, sign(y).toInt() * oSize)

        // Check for borders
        if (pp.left <= 0 ||
            pp.top <= 0 ||
            pp.left >= fieldWidth - oSize ||
            pp.top >= fieldHeight - oSize
        )
            return

        // Check for an obstacle
        obstacles.find { it.intersects(pp) }?.let {
            if (!it.isHit()) {
                // Bad luck, we hit a hidden obstacle!
                // Invalidate the area of the now visible
                // obstacle and the player that lost one life.
                it.setHit()
                hits++
                invalidate()
                if (bmViewModel.lives == 0 || hits < bmViewModel.lives) {
                    // Go on
                    bmViewModel.messageText = resources.getString(R.string.mess_hits) + " $hits"
                    Effect.HIT.makeEffect(this)
                } else {
                    // Game over
                    dragHandler.stopDragMode()
                    gameState = GameState.IDLE
                    bmViewModel.messageText = resources.getString(R.string.mess_over)
                    Effect.OVER.makeEffect(this)
                }
            }
            // In any case, this move has no future
            return
        }

        // All is fine, the move can be performed.
        player.set(pp)
        invalidate()

        // GOAL reached?
        if (Rect.intersects(player, goal)) {
            dragHandler.stopDragMode()
            gameState = GameState.IDLE
            bmViewModel.messageText += ". " + resources.getText(R.string.mess_goal)
            Effect.GOAL.makeEffect(this)
        }
    }

    // Touch event handling including required override
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // We start with the play state
        if (gameState == GameState.PLAY) {
            // First we check for a placed or lifted finger
            // and stop the drag starter if it was running
            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_DOWN
            )
                dragStarter.cancelTimer()

            // Main logic: If the dragHandler consumes the event
            // we are done. If not, it is given to the gestureDetector.
            // If this returns false again we try to detect a
            // motion event that may lead to a drag start.
            // Here we exceptionally use (non-forcing!) boolean operators
            // instead of if-sequences because of better readability.
            return (dragHandler.doDrag(event) ||
                    gestureDetector.onTouchEvent(event) ||
                    dragStarter.startTimer(event))
        }

        // In all other states we only look at a down event
        if (event.action != MotionEvent.ACTION_DOWN)
            return true

        when (gameState) {
            GameState.IDLE -> {
                newGame()
                return true
            }

            GameState.SHOW -> {
                obstacles.forEach { it.setHidden() }
                gameState = GameState.PLAY
                bmViewModel.messageText = resources.getString(R.string.mess_hits) + " 0"
                invalidate()
                // Start to move
                return gestureDetector.onTouchEvent(event)
            }

            GameState.HINT -> {
                obstacles.forEach { it.setVisibleIfHit() }
                gameState = GameState.PLAY
                invalidate()
                // Start to move again
                return gestureDetector.onTouchEvent(event)
            }

            else -> {
                Log.e(LOG_TAG, "Unknown game state")
                return performClick()
            }
        }
    }

    override fun performClick(): Boolean {
        return super.performClick() && hasOnClickListeners()
    }

    // DoubleTap and Gesture implementations. Only four methods
    // are needed. Therefore, we extend the simple helper class.
    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // onDown must always return true in this usage pattern
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d(LOG_TAG, "onDoubleTap called")

            obstacles.forEach { it.setVisible() }
            // Cancel drag mode (if active)
            dragHandler.stopDragMode()
            gameState = GameState.HINT
            invalidate()

            return true
        }

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
            dragHandler.setReferencePosition(e)
            dragHandler.startDragMode()
            Effect.GRAB.makeEffect(this@BlindManView)
        }
    }

    // Drag starting class
    private inner class DragStarter : SimpleCountDownTimer(DRAG_START_DELAY) {
        fun startTimer(e: MotionEvent): Boolean {
            // Decide if a move on the screen should start the dragging mode
            val doStart = !isTimerRunning
                    && !dragHandler.isDragModeActive
                    && e.action == MotionEvent.ACTION_MOVE
            if (doStart) {
                dragHandler.setReferencePosition(e)
                startTimer()
                Log.d(LOG_TAG, "DragStarter started")
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
        // Public variable
        var isDragModeActive: Boolean = false

        // Private variables
        private var refP = PointF(0f, 0f)

        // Override
        override fun onTimerElapsed() {
            if (isDragModeActive)
                stopDragMode()
        }

        // Public methods
        fun setReferencePosition(e: MotionEvent) {
            refP.set(e.x, e.y)
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
            // Make sure we are in the right mode
            if (!isDragModeActive)
                return false

            // Check if we have to cancel the timer and reset the drag position
            if (isTimerRunning) {
                cancelTimer()
                setReferencePosition(e)
            }

            when (e.action) {
                MotionEvent.ACTION_UP -> {
                    Log.d(LOG_TAG, "doDrag.ACTION_UP called")
                    // Now we start the timer for ending the drag mode
                    startTimer()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dX = e.x - refP.x
                    val dY = e.y - refP.y
                    // Motion amplification gives a good user experience
                    if (hypot(dX, dY) > 0.65f * oSize) {
                        makeMove(dX, dY)
                        setReferencePosition(e)
                    }
                    return true
                }

                else -> return false
            }
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

    // Key events
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN)
            return false

        when (gameState) {
            GameState.IDLE -> newGame()

            GameState.SHOW -> {
                obstacles.forEach { it.setHidden() }
                gameState = GameState.PLAY
                bmViewModel.messageText = resources.getString(R.string.mess_hits) + " 0"
                invalidate()
            }

            GameState.HINT -> {
                obstacles.forEach { it.setVisibleIfHit() }
                gameState = GameState.PLAY
                invalidate()
            }

            else -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_NUMPAD_6,
                    KeyEvent.KEYCODE_SOFT_RIGHT -> makeMove(1f, 0f)

                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_NUMPAD_4,
                    KeyEvent.KEYCODE_SOFT_LEFT -> makeMove(-1f, 0f)

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_NUMPAD_8,
                    KeyEvent.KEYCODE_PAGE_UP -> makeMove(0f, -1f)

                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_NUMPAD_2,
                    KeyEvent.KEYCODE_PAGE_DOWN -> makeMove(0f, 1f)

                    else -> return super.onKeyDown(keyCode, event)
                }
            }
        }
        return true
    }
}
