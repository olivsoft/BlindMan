package ch.olivsoft.android.blindman;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.Shape;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BlindManView extends View
        implements AnimationListener, OnGestureListener, OnDoubleTapListener {

    // Constants
    private static final String LOG_TAG = BlindManView.class.getSimpleName();
    private static final int FULL_ALPHA = 0xFF;
    private static final int[] BACKGROUND_ALPHA = new int[]{0, 0x40, 0x80};
    private static final int DRAG_START_DELAY = 200;
    private static final int DRAG_END_DELAY = 300;

    // Game states
    private enum GameState {
        IDLE, SHOW, PLAY, HINT
    }

    private GameState gameState = GameState.IDLE;

    // Variables visible to main activity
    static List<Integer> allowedLives = Arrays.asList(1, 2, 3, 4, 6, 9, 12, 0);
    TextView textView;
    int level = 1;
    int size = 1;
    int background = 1;

    // Non-private internal variables (for efficient inner class access)
    Rect player;

    // Private internal variables
    private int lives = allowedLives.get(2); // 3 lives is the default
    private int hits = 0;
    private int viewWidth;
    private int viewHeight;
    private int fieldWidth;
    private int fieldHeight;
    private int offWidth;
    private int offHeight;
    private int oSize;
    private Rect goal;
    private Rect border;
    private List<Obstacle> obstacles;
    private Random random;

    // Game motion
    private DragStarter dragStarter;
    private DragHandler dragHandler;
    private GestureDetector gestureDetector;

    // For efficiency, objects used in onDraw should be created in advance.
    // We add further such objects from invalidate and makeMove.
    private Paint drawingPaint;
    private RectF drawingRect;
    private Rect dirtyRect;
    private Rect pp;

    // Animation
    private boolean swapColors = false;

    // Get and set only for non-trivial cases
    int getLives() {
        return lives;
    }

    void setLives(int newLives) {
        // Reset in case something went completely wrong
        lives = allowedLives.contains(newLives) ? newLives : allowedLives.get(2);
        if (gameState == GameState.PLAY) {
            if (newLives > 0 && hits >= newLives)
                newGame(0);
            else
                invalidate(player);
        }
    }

    // This constructor is called by the layout mechanism.
    // We use this to initialize a few long-living members.
    public BlindManView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (this.isInEditMode())
            return;

        initGlobals(context);
    }

    // This is also called by the layout mechanism.
    // We use it to initialize all the size-dependent members.
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (this.isInEditMode())
            return;

        // Copy view width and height and initialize game field
        viewWidth = w;
        viewHeight = h;
        initField(0);
    }

    private void initGlobals(Context context) {
        // Some objects are completely independent of size and GameState
        random = new Random();
        drawingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawingRect = new RectF();
        dirtyRect = new Rect();
        pp = new Rect();

        // The drag starter and handler instances can be created here
        dragStarter = new DragStarter(DRAG_START_DELAY);
        dragHandler = new DragHandler(DRAG_END_DELAY);

        // Overriding methods of a SimpleGestureListener would be sufficient
        // but we implement the full interfaces because we think it looks cleaner.
        // Strange: DoubleTap listening works also without explicitly setting it.
        // In our case, setLongPressEnabled and setClickable are required in this
        // combination, there are many articles on why this is the case.
        gestureDetector = new GestureDetector(context, this);
        gestureDetector.setOnDoubleTapListener(this);
        gestureDetector.setIsLongpressEnabled(false);
        this.setClickable(true);

        // Effects need some additional initialization
        Effect.loadDynamicElements(context, this);
    }

    void initField(int newSize) {
        // Check who called
        if (newSize > 0)
            size = newSize;

        // Initialize obstacle density depending on size choice and aspect ratio.
        // The hard-coded factors are tuning parameters, they roughly indicate how
        // many obstacles per size and how many for high aspect ratios are added.
        int d0 = -2 + 12 * size
                + 8 * Math.max(viewWidth, viewHeight) / Math.min(viewWidth, viewHeight);

        // Optimize obstacle size for minimal screen-waste on borders
        int od = d0;
        int r = 2 * d0;
        for (int d = d0 - 2; d <= d0 + 2; d++) {
            if (viewWidth % d + viewHeight % d <= r) {
                r = viewWidth % d + viewHeight % d;
                od = d;
            }
        }
        oSize = Math.max(viewWidth, viewHeight) / od;
        fieldWidth = viewWidth - viewWidth % oSize;
        fieldHeight = viewHeight - viewHeight % oSize;

        // The offsets are stored here and applied in onDraw and invalidate
        offWidth = (viewWidth - fieldWidth) / 2;
        offHeight = (viewHeight - fieldHeight) / 2;

        // Allocate size-dependent objects
        border = new Rect(0, 0, fieldWidth, fieldHeight);
        border.inset(oSize / 2, oSize / 2);
        goal = new Rect(fieldWidth - 3 * oSize, fieldHeight - 3 * oSize, fieldWidth - oSize, fieldHeight - oSize);
        player = new Rect(oSize, oSize, 2 * oSize, 2 * oSize);
        obstacles = new ArrayList<>(fieldWidth * fieldHeight / (oSize * oSize) / 2);

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
        List<Integer> obsLine = new ArrayList<>(numObs);
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
            for (int i : obsLine) {
                obstacles.add(new Obstacle(o ? i : iLine, o ? iLine : i, oSize) {
                    @Override
                    protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
                        // This looks like the most concise and
                        // efficient way to set the obstacle color
                        paint.setColor(ColoredPart.OBSTACLE.color);
                        super.onDraw(shape, canvas, paint);
                    }
                });
            }
        }

        // Now we are in show state
        gameState = GameState.SHOW;
        textView.setText(R.string.mess_start);
        invalidate();
    }

    // Override this for dealing with offset properly
    @Override
    public void invalidate(Rect dirty) {
        // We need a copy of the dirty region because we move it
        dirtyRect.set(dirty);
        dirtyRect.offset(offWidth, offHeight);
        super.invalidate(dirtyRect);
    }

    // This is well prepared
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
            canvas.drawArc(drawingRect, 0, 360 - 360f / lives * hits, true, drawingPaint);

        // Obstacles have their own draw routine. The color
        // is set in the OnDraw override (see above).
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
            if (x > 0)
                pp.offset(oSize, 0);
            else
                pp.offset(-oSize, 0);
        else if (y > 0)
            pp.offset(0, oSize);
        else
            pp.offset(0, -oSize);

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
                    pp.union(player);
                    invalidate(pp);
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
        // First, the affected area is stored, then
        // the move is made, then the area is invalidated.
        dirtyRect.set(player);
        dirtyRect.union(pp);
        player.set(pp);
        invalidate(dirtyRect);

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


    // DoubleTap and Gesture interfaces. Only four methods
    // are effectively used, the five last ones are empty.
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2,
                           float velocityX, float velocityY) {
        Log.d(LOG_TAG, "onFling called");
        if (gameState != GameState.PLAY)
            return false;

        // Make a move
        makeMove(velocityX, velocityY);
        return true;
    }

    public void onShowPress(@NonNull MotionEvent e) {
        Log.d(LOG_TAG, "onShowPress called");
        if (gameState != GameState.PLAY)
            return;

        // Start dragging immediately
        dragStarter.cancelTimer();
        dragHandler.resetPosition(e);
        dragHandler.startDragMode();
        Effect.GRAB.makeEffect(this);
    }

    public boolean onDown(@NonNull MotionEvent e) {
        // onDown must always return true in this usage pattern
        return true;
    }

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

    // Empty implementations
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        return false;
    }

    public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
        return false;
    }

    public boolean onSingleTapUp(@NonNull MotionEvent e) {
        return false;
    }

    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2,
                            float distanceX, float distanceY) {
        return false;
    }

    public void onLongPress(@NonNull MotionEvent e) {
    }


    // Animation interface
    public void onAnimationStart(Animation animation) {
        // Start the cycle with inverted colors
        swapColors = true;
        invalidate(goal);
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
        invalidate(goal);
    }

    public void onAnimationEnd(Animation animation) {
        // Restore colors and redraw again
        swapColors = false;
        invalidate(goal);
    }


    // Drag starting class
    private class DragStarter extends SimpleCountDownTimer {

        DragStarter(long millis) {
            super(millis);
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
        DragHandler(long millis) {
            super(millis);
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
            invalidate(player);
        }

        void stopDragMode() {
            isDragModeActive = false;
            cancelTimer();
            invalidate(player);
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
                    // We did not to consume the event
                    return false;
            }
        }
    }
}
