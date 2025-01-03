package ch.olivsoft.android.blindman;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class BlindManView extends View implements AnimationListener {

    // Constants
    private static final String LOG_TAG = BlindManView.class.getSimpleName();
    private static final int FULL_ALPHA = 0xFF;
    private static final int[] BACKGROUND_ALPHA = {0, 0x40, 0x80};
    private static final int[] OBSTACLE_ROWS = {8, 11, 15};
    private static final int DRAG_START_DELAY = 200;
    private static final int DRAG_END_DELAY = 300;

    // Variables visible to main activity
    TextView textView;
    static final List<Integer> ALLOWED_LIVES = List.of(1, 2, 3, 4, 6, 9, 12, 0);
    int size = 1;
    int level = 1;
    int background = 1;

    // Private internal variables
    private int lives = ALLOWED_LIVES.get(2); // 3 lives is the default
    private int hits = 0;
    private int viewWidth;
    private int viewHeight;
    private int fieldWidth;
    private int fieldHeight;
    private int offWidth;
    private int offHeight;
    private int oSize;
    private Rect player;
    private Rect goal;
    private Rect border;
    private HashSet<Obstacle> obstacles;
    private Random random;

    // Game motion
    private DragStarter dragStarter;
    private DragHandler dragHandler;
    private GestureDetector gestureDetector;

    // For efficiency, objects used in onDraw should be created in advance.
    // We add further such objects from makeMove.
    private Paint drawingPaint;
    private RectF drawingRect;
    private Rect pp;

    // Animation
    private boolean swapColors = false;

    // Game states
    private enum GameState {
        IDLE, SHOW, PLAY, HINT
    }

    private GameState gameState = GameState.IDLE;

    // Get and set only for non-trivial cases
    int getLives() {
        return lives;
    }

    void setLives(int newLives) {
        // Reset in case something went completely wrong
        lives = ALLOWED_LIVES.contains(newLives) ? newLives : ALLOWED_LIVES.get(2);
        if (gameState == GameState.PLAY) {
            if (newLives > 0 && hits >= newLives)
                newGame(0);
            else
                invalidate();
        }
    }

    // This constructor is called by the layout mechanism.
    // Size-independent members can be initialized here.
    public BlindManView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (this.isInEditMode())
            return;

        random = new Random();
        pp = new Rect();
        drawingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawingRect = new RectF();
        dragStarter = new DragStarter();
        dragHandler = new DragHandler();

        // Overriding methods of a SimpleGestureListener is sufficient.
        // Strange: DoubleTap listening works also without explicitly setting it.
        // In our case, setLongPressEnabled and setClickable are required in this
        // combination, there are many articles on why this is the case.
        GestureListener gestureListener = new GestureListener();
        gestureDetector = new GestureDetector(context, gestureListener);
        gestureDetector.setOnDoubleTapListener(gestureListener);
        gestureDetector.setIsLongpressEnabled(false);
        this.setClickable(true);

        // Effects need some additional initialization
        Effect.loadDynamicElements(context, this);
    }

    // This is also called by the layout mechanism.
    // Here, size-dependent members are initialized.
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (this.isInEditMode())
            return;

        // Copy view width and height and initialize game field
        viewWidth = w;
        viewHeight = h;
        initField(0);
    }

    void initField(int newSize) {
        if (newSize > 0)
            size = Math.min(newSize, OBSTACLE_ROWS.length);

        // Obstacle and field sizes
        oSize = Math.max(viewWidth, viewHeight) / (2 * OBSTACLE_ROWS[size - 1] + 7);
        fieldWidth = viewWidth - viewWidth % oSize;
        fieldHeight = viewHeight - viewHeight % oSize;

        // The offsets are stored here and applied in onDraw
        offWidth = (viewWidth - fieldWidth) / 2;
        offHeight = (viewHeight - fieldHeight) / 2;

        // Allocate size-dependent objects
        border = new Rect(0, 0, fieldWidth, fieldHeight);
        border.inset(oSize / 2, oSize / 2);
        goal = new Rect(fieldWidth - 3 * oSize, fieldHeight - 3 * oSize,
                fieldWidth - oSize, fieldHeight - oSize);
        player = new Rect(oSize, oSize, 2 * oSize, 2 * oSize);
        obstacles = new HashSet<>(fieldWidth * fieldHeight / (oSize * oSize) / 2);

        // Set the game state to idle and put a message onto the text view
        gameState = GameState.IDLE;
        textView.setText(R.string.mess_show);
    }

    void newGame(int newLevel) {
        // Check who called
        if (newLevel > 0)
            level = newLevel;

        // Player goes (back) to initial position, hits are cleared.
        player.offsetTo(oSize, oSize);
        hits = 0;

        // Determine the orientation and count the available space for obstacles.
        // Last obstacle line is 5 units before goal-side end of canvas.
        // Across fieldWidth reserves 1 unit for each border.
        boolean o = (fieldHeight > fieldWidth);
        int lastAcross = (o ? fieldHeight : fieldWidth) / oSize - 5;
        int widthAcross = (o ? fieldWidth : fieldHeight) / oSize - 2;

        // Number of obstacles in each across line
        int numObs = (int) (0.3 * widthAcross * level);

        obstacles.clear();
        // A HashSet eliminates duplicates
        HashSet<Integer> obsLine = new HashSet<>(numObs);
        // Every second line across contains obstacles
        for (int iLine = 3; iLine <= lastAcross; iLine += 2) {
            obsLine.clear();
            for (int n = 1; n <= numObs; n++) {
                // Random number between and including 1 and widthAcross
                obsLine.add(1 + random.nextInt(widthAcross));
            }
            // For the easiest level some more border obstacles are needed
            if (level == 1) {
                if (random.nextFloat() < 0.2)
                    obsLine.add(1);
                if (random.nextFloat() >= 0.8)
                    obsLine.add(widthAcross);
            }
            // Now, the real obstacles are created
            for (int i : obsLine)
                obstacles.add(new Obstacle(o ? i : iLine, o ? iLine : i, oSize));
        }

        // Now we are in show state
        gameState = GameState.SHOW;
        textView.setText(R.string.mess_start);
        invalidate();
    }

    // No time-consuming stuff here
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (this.isInEditMode())
            return;

        // Translate used space into center
        canvas.translate(offWidth, offHeight);

        // Field background
        drawingPaint.setColor(ColoredPart.FIELD.color);
        drawingPaint.setAlpha(BACKGROUND_ALPHA[background]);
        drawingPaint.setStyle(Style.FILL);
        canvas.drawRect(border, drawingPaint);

        // Field border with reduced thickness
        drawingPaint.setAlpha(FULL_ALPHA);
        drawingPaint.setStyle(Style.STROKE);
        drawingPaint.setStrokeWidth(oSize / 2f);
        canvas.drawRect(border, drawingPaint);

        // Goal fully filled
        drawingPaint.setStyle(Style.FILL);
        drawingPaint.setColor(swapColors ? ColoredPart.PLAYER.color : ColoredPart.GOAL.color);
        canvas.drawRect(goal, drawingPaint);

        // Player is drawn after goal because it may be on top
        drawingRect.set(player);
        drawingPaint.setColor(swapColors ? ColoredPart.GOAL.color : ColoredPart.PLAYER.color);
        if (dragHandler.isDragModeActive) {
            // In drag mode the player looks like a ring
            float w = 0.1f * oSize;
            drawingRect.inset(w, w);
            drawingPaint.setStyle(Style.STROKE);
            drawingPaint.setStrokeWidth(2 * w);
        }
        if (lives == 0)
            canvas.drawOval(drawingRect, drawingPaint);
        else
            canvas.drawArc(drawingRect, 0, 360 - 360f / lives * hits,
                    true, drawingPaint);

        // Obstacles have their own onDraw method
        for (Obstacle o : obstacles)
            o.draw(canvas);
    }

    // Move logic
    void makeMove(float x, float y) {
        if (gameState != GameState.PLAY)
            return;

        // Try the move with a copy of the player
        pp.set(player);
        if (Math.abs(x) > Math.abs(y))
            pp.offset(x > 0 ? oSize : -oSize, 0);
        else
            pp.offset(0, y > 0 ? oSize : -oSize);

        // Check for borders
        if (pp.left <= 0 || pp.top <= 0
                || pp.left >= fieldWidth - oSize || pp.top >= fieldHeight - oSize)
            return;

        // Check for an obstacle
        for (Obstacle o : obstacles) {
            if (o.intersects(pp)) {
                if (!o.isHit()) {
                    // Bad luck, we hit a hidden obstacle!
                    // Invalidate the area of the now visible
                    // obstacle and the player that lost one life.
                    o.setHit();
                    hits++;
                    invalidate();
                    if (lives == 0 || hits < lives) {
                        // Go on
                        textView.setText(R.string.mess_hits);
                        textView.append(" " + hits);
                        Effect.HIT.makeEffect(this);
                    } else {
                        // Game over
                        dragHandler.stopDragMode();
                        gameState = GameState.IDLE;
                        textView.setText(R.string.mess_over);
                        Effect.OVER.makeEffect(this);
                    }
                }
                // In any case, this move has no future
                return;
            }
        }

        // All is fine, the move can be performed.
        player.set(pp);
        invalidate();

        // GOAL reached?
        if (Rect.intersects(player, goal)) {
            dragHandler.stopDragMode();
            gameState = GameState.IDLE;
            textView.append(". " + getResources().getText(R.string.mess_goal));
            Effect.GOAL.makeEffect(this);
        }
    }

    // Touch event handling
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int eventAction = event.getAction();

        // First we consider the play state
        if (gameState == GameState.PLAY) {
            // First we check for a placed or lifted finger
            // and stop the drag starter if it was running
            if (eventAction == MotionEvent.ACTION_UP
                    || eventAction == MotionEvent.ACTION_DOWN)
                dragStarter.cancelTimer();

            // Main logic: If the dragHandler consumes the event
            // we are done. If not, it is given to the gestureDetector.
            // If this returns false again we try to detect a
            // motion event that may lead to a drag start.
            // Here we exceptionally use (non-forcing!) boolean operators
            // instead of if-sequences because of better readability.
            return (dragHandler.isDragModeActive && dragHandler.doDrag(event))
                    || gestureDetector.onTouchEvent(event)
                    || dragStarter.startTimer(event);
        }

        // In all other states we only look at a down event
        if (eventAction != MotionEvent.ACTION_DOWN)
            return true;

        // This switch clause uses return statements in each case.
        // So we do not need break statements or a final return.
        switch (gameState) {
            case IDLE:
                newGame(0);
                return true;

            case SHOW:
                for (Obstacle o : obstacles)
                    o.setHidden();
                gameState = GameState.PLAY;
                textView.setText(R.string.mess_hits);
                textView.append(" 0");
                invalidate();
                // Start to move
                return gestureDetector.onTouchEvent(event);

            case HINT:
                for (Obstacle o : obstacles)
                    o.setVisibleIfHit();
                gameState = GameState.PLAY;
                invalidate();
                // Start to move again
                return gestureDetector.onTouchEvent(event);

            default:
                return false;
        }
    }

    // DoubleTap and Gesture implementations. Only four methods
    // are needed, therefore we extend the simple helper class.
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                               float velocityX, float velocityY) {
            Log.d(LOG_TAG, "onFling called");
            if (gameState != GameState.PLAY)
                return false;

            // Make a move
            makeMove(velocityX, velocityY);
            return true;
        }

        @Override
        public void onShowPress(@NonNull MotionEvent e) {
            Log.d(LOG_TAG, "onShowPress called");
            if (gameState != GameState.PLAY)
                return;

            // Start dragging immediately
            dragStarter.cancelTimer();
            dragHandler.resetPosition(e);
            dragHandler.startDragMode();
            Effect.GRAB.makeEffect(BlindManView.this);
        }

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            // onDown must always return true in this usage pattern
            return true;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            Log.d(LOG_TAG, "onDoubleTap called");

            for (Obstacle o : obstacles)
                o.setVisible();
            // Cancel drag mode (if active)
            dragHandler.stopDragMode();
            gameState = GameState.HINT;
            invalidate();

            return true;
        }
    }

    // Animation interface
    public void onAnimationStart(Animation animation) {
        // Start the cycle with inverted colors
        swapColors = true;
        invalidate();
    }

    public void onAnimationRepeat(Animation animation) {
        if (gameState != GameState.IDLE) {
            // Someone was quick in tapping on the screen. According to
            // the documentation, onAnimationEnd is called after cancel,
            // so we don't have to do it. reset, however, must be
            // called manually. Note: cancel only exists since FROYO.
            animation.cancel();
            animation.reset();
            return;
        }
        swapColors = !swapColors;
        invalidate();
    }

    public void onAnimationEnd(Animation animation) {
        // Restore colors and redraw again
        swapColors = false;
        invalidate();
    }

    // Drag starting class
    private class DragStarter extends SimpleCountDownTimer {

        DragStarter() {
            super(DRAG_START_DELAY);
        }

        boolean startTimer(MotionEvent e) {
            // This is a very well guarded routine: Make sure a motion in
            // non-dragging mode only starts the timer and later dragging
            // if a number of conditions are fulfilled.
            boolean doStart = !isTimerRunning()
                    && !dragHandler.isDragModeActive
                    && e.getAction() == MotionEvent.ACTION_MOVE;
            if (doStart) {
                dragHandler.resetPosition(e);
                startTimer();
                Log.d(LOG_TAG, "DragStarter STARTED");
            }
            return doStart;
        }

        @Override
        public void onTimerElapsed() {
            if (gameState == GameState.PLAY && !dragHandler.isDragModeActive) {
                dragHandler.startDragMode();
                Log.d(LOG_TAG, "Dragging started by DragStarter");
            }
        }
    }

    // Drag handling class
    private class DragHandler extends SimpleCountDownTimer {

        // Public access for efficiency
        boolean isDragModeActive = false;

        // Private variables
        private float oldX;
        private float oldY;

        // Constructor
        DragHandler() {
            super(DRAG_END_DELAY);
        }

        @Override
        public void onTimerElapsed() {
            if (isDragModeActive)
                stopDragMode();
        }

        // Public methods
        void resetPosition(MotionEvent e) {
            oldX = e.getX();
            oldY = e.getY();
        }

        void startDragMode() {
            isDragModeActive = true;
            invalidate();
        }

        void stopDragMode() {
            isDragModeActive = false;
            cancelTimer();
            invalidate();
        }

        boolean doDrag(MotionEvent e) {
            // Check if we have to cancel the timer and reset the drag position
            if (isTimerRunning()) {
                cancelTimer();
                resetPosition(e);
            }

            switch (e.getAction()) {
                case MotionEvent.ACTION_UP:
                    Log.d(LOG_TAG, "doDrag.ACTION_UP called");
                    // Now we start the timer for ending the drag mode
                    startTimer();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dX = e.getX() - oldX;
                    float dY = e.getY() - oldY;
                    // Motion amplification gives a good user experience
                    if (dX * dX + dY * dY > 0.4f * oSize * oSize) {
                        makeMove(dX, dY);
                        oldX += dX;
                        oldY += dY;
                    }
                    return true;

                default:
                    // We did not consume the event
                    return false;
            }
        }
    }
}
