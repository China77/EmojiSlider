package com.bernaferrari.emojislider

import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.bernaferrari.emojislider.EmojiSlider.Size.NORMAL
import com.bernaferrari.emojislider.EmojiSlider.Size.SMALL
import com.cpiz.android.bubbleview.BubbleStyle
import com.cpiz.android.bubbleview.BubbleTextView
import com.facebook.rebound.*
import kotlin.math.roundToInt


class EmojiSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    size: Size = Size.NORMAL
) : View(context, attrs, defStyleAttr) {

    /**
     * Sizes that can be used.
     * @see NORMAL
     * @see SMALL
     */
    enum class Size(val value: Int) {
        /**
         * Default size - 56dp.
         */
        NORMAL(56),

        /**
         * Small size - 40dp.
         */
        SMALL(40)
    }

    private companion object {
        const val BAR_CORNER_RADIUS = 2
        const val BAR_INNER_HORIZONTAL_OFFSET = 0

        const val SLIDER_WIDTH = 4
        const val SLIDER_HEIGHT = 1

        const val TOP_CIRCLE_DIAMETER = 1
        const val BOTTOM_CIRCLE_DIAMETER = 25.0f
        const val TOUCH_CIRCLE_DIAMETER = 1
        const val LABEL_CIRCLE_DIAMETER = 10

        const val ANIMATION_DURATION = 400
        const val TOP_SPREAD_FACTOR = 0.4f
        const val BOTTOM_START_SPREAD_FACTOR = 0.25f
        const val BOTTOM_END_SPREAD_FACTOR = 0.1f
        const val METABALL_HANDLER_FACTOR = 2.4f
        const val METABALL_MAX_DISTANCE = 15.0f
        const val METABALL_RISE_DISTANCE = 1.1f

        const val COLOR_BAR = 0xff6168e7.toInt()
        const val COLOR_LABEL = Color.WHITE
        const val COLOR_LABEL_TEXT = Color.BLACK
        const val COLOR_BAR_TEXT = Color.WHITE

        const val INITIAL_POSITION = 0.5f
    }

    private val barHeight: Float

    private val desiredWidth: Int
    private val desiredHeight: Int

    private val topCircleDiameter: Float = 0f
    private val bottomCircleDiameter: Float = 0f
    private val touchRectDiameter: Float = 0f
    private val labelRectDiameter: Float = 0f

    private val metaballMaxDistance: Float = 0f
    private val metaballRiseDistance: Float = 0f

    private val barVerticalOffset: Float = 0f
    private val barCornerRadius: Float
    private val barInnerOffset: Float

    private val pathMetaball = Path()

    private val paintBar: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLabel: Paint
    private val paintText: Paint

    private var maxMovement = 0f

    var thumbAllowReselection: Boolean = true

    private val barPaddingTop: Int =
        context.resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_top_without_question)
    private val barPaddingBottom: Int =
        context.resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_bottom_without_question)


    /**
     * Duration of "bubble" rise in milliseconds.
     */
    var pressedFinalValue = 0.0

    /**
     * Color of slider.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var colorBar: Int
        get() = paintBar.color
        set(value) {
            paintBar.color = value
        }

    /**
     * Color of circle "bubble" inside bar.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var colorBubble: Int
        get() = paintLabel.color
        set(value) {
            paintLabel.color = value
        }

    /**
     * Initial position of "bubble" in range form `0.0` to `1.0`.
     */
    var progress = INITIAL_POSITION
        set(value) {
            field = value.limitToRange()

            sliderBar.percentProgress = field
            sliderBar.invalidateSelf()
            invalidate()
        }

    /**
     * Current position tracker. Receive current position, in range from `0.0f` to `1.0f`.
     */
    var positionListener: ((Float) -> Unit)? = null

    /**
     * Called on slider touch.
     */
    var beginTrackingListener: (() -> Unit)? = null

    /**
     * Called when slider is released.
     */
    var endTrackingListener: (() -> Unit)? = null


    class State : BaseSavedState {
        companion object {
            @JvmField
            @Suppress("unused")
            val CREATOR = object : Parcelable.Creator<State> {
                override fun createFromParcel(parcel: Parcel): State = State(parcel)
                override fun newArray(size: Int): Array<State?> = arrayOfNulls(size)
            }
        }

        val position: Float
        val colorLabel: Int
        val colorBar: Int
        val pressedFinalValue: Double

        constructor(
            superState: Parcelable,
            position: Float,
            colorLabel: Int,
            colorBar: Int,
            pressedFinalValue: Double
        ) : super(superState) {
            this.position = position
            this.colorLabel = colorLabel
            this.colorBar = colorBar
            this.pressedFinalValue = pressedFinalValue
        }

        private constructor(parcel: Parcel) : super(parcel) {
            this.position = parcel.readFloat()
            this.colorLabel = parcel.readInt()
            this.colorBar = parcel.readInt()
            this.pressedFinalValue = parcel.readDouble()
        }

        override fun writeToParcel(parcel: Parcel, i: Int) {
            parcel.writeFloat(position)
            parcel.writeInt(colorLabel)
            parcel.writeInt(colorBar)
            parcel.writeDouble(pressedFinalValue)
        }

        override fun describeContents(): Int = 0
    }

    /**
     * Additional constructor that can be used to create FluidSlider programmatically.
     * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
     * @param size Size of FluidSlider.
     * @see Size
     */
    constructor(context: Context, size: Size) : this(context, null, 0, size)

    override fun onSaveInstanceState(): Parcelable {
        return State(
            super.onSaveInstanceState(),
            progress,
            colorBubble, colorBar, pressedFinalValue
        )
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        super.onRestoreInstanceState(state)
        if (state is State) {
            progress = state.position

            colorBubble = state.colorLabel
            colorBar = state.colorBar

            pressedFinalValue = state.pressedFinalValue
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSizeAndState(desiredWidth, widthMeasureSpec, 0)
        val h = resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        println("sliderBoundsContains3 " + sliderBar.trackHeight)

        this.sliderBar.setBounds(
            left + sliderHorizontalPadding,
            h / 2 - sliderBar.intrinsicHeight / 2,
            right - sliderHorizontalPadding,
            h / 2 + sliderBar.intrinsicHeight / 2
        )

        println("sliderBoundsContains2: " + sliderBar.bounds.toShortString() + " iheight: " + sliderBar.intrinsicHeight + " h: " + h / 2)
    }

    //////////////////////////////////////////
    // Spring Methods from Facebook's Rebound
    //////////////////////////////////////////

    private val mSpringSystem = SpringSystem.create()
    private val mSpringListener = object : SimpleSpringListener() {
        override fun onSpringUpdate(spring: Spring?) {
            invalidate()
        }
    }

    private val mThumbSpring = mSpringSystem.createSpring()
        .origamiConfig(3.0, 5.0)
        .setCurrentValue(1.0)
        .setEndValue(1.0)
        .setOvershootClampingEnabled(true)

    private val mAverageSpring: Spring = mSpringSystem.createSpring()
        .origamiConfig(40.0, 7.0)
        .setCurrentValue(0.0)

    private val mAverageProfilePicture: Spring = mSpringSystem.createSpring()
        .origamiConfig(40.0, 7.0)
        .setCurrentValue(0.0)

    //////////////////////////////////////////
    // Variables
    //////////////////////////////////////////

    lateinit var thumbDrawable: Drawable
    val sliderBar: DrawableBars = DrawableBars()
    val drawableProfileImage: DrawableProfilePicture = DrawableProfilePicture(context)

    var isThumbAllowedToScrollEverywhere = true
    var sliderHorizontalPadding: Int = 0

    var isTouchDisabled = false
    internal var colorStart: Int = 0
    internal var colorEnd: Int = 0

    var averagePercentValue = 0f
    var averageShouldShow: Boolean = true
    var isThumbSelected = false

    val drawableAverageCircle: DrawableAverageCircle by lazy {
        DrawableAverageCircle(context).apply {
            val voteAverageHandleSize =
                context.resources.getDimensionPixelSize(R.dimen.slider_sticker_slider_vote_average_handle_size)
            this.radius = voteAverageHandleSize.toFloat() / 2.0f
            this.invalidateSelf()
        }
    }


    fun valueWasSelected() {
        if (thumbAllowReselection) return

        mAverageSpring.endValue = 1.0
        mThumbSpring.endValue = 0.0
        isTouchDisabled = true
        drawableProfileImage.show()

        showAveragePopup()
        invalidate()
    }


    fun reset() {
        mAverageSpring.endValue = 0.0
        mThumbSpring.endValue = 1.0
        isTouchDisabled = false
        drawableProfileImage.hide()

        showAveragePopup()
        invalidate()
    }

    fun showAveragePopup() {

        val finalPosition = SpringUtil.mapValueFromRangeToRange(
            (this.averagePercentValue * sliderBar.bounds.width()).toDouble(),
            0.0,
            sliderBar.bounds.width().toDouble(),
            -(sliderBar.bounds.width() / 2).toDouble(),
            (sliderBar.bounds.width() / 2).toDouble()
        )

        showPopupWindow(finalPosition.roundToInt())
    }


    //////////////////////////////////////////
    // Update methods
    //////////////////////////////////////////

    private fun Float.limitToRange() = Math.max(Math.min(this, 1f), 0f)

    //////////////////////////////////////////
    // Lifecycle methods
    //////////////////////////////////////////

    fun startAnimation() {
        mThumbSpring.addListener(mSpringListener)
        mAverageSpring.addListener(mSpringListener)
    }

    fun stopAnimation() {
        mThumbSpring.removeListener(mSpringListener)
        mAverageSpring.removeListener(mSpringListener)
    }

    fun setHandleSize(size: Int) {
        drawableProfileImage.sizeHandle = size.toFloat()
        val circleHandleC5190I = drawableProfileImage.drawableAverageHandle
        circleHandleC5190I.radius = drawableProfileImage.sizeHandle / 2.0f
        circleHandleC5190I.invalidateSelf()
    }

    override fun scheduleDrawable(drawable: Drawable, runnable: Runnable, j: Long) = Unit
    override fun unscheduleDrawable(drawable: Drawable, runnable: Runnable) = Unit
    override fun invalidateDrawable(drawable: Drawable) = invalidate()

    //////////////////////////////////////////
    // Draw
    //////////////////////////////////////////

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val x = width
        val y = height
        val radius = 100
        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawPaint(paint)
        // Use Color.parseColor to define HTML colors
        paint.color = Color.parseColor("#CD5C5C")
        canvas.drawCircle(x / 2f, y / 2f, radius.toFloat(), paint)

        this.sliderBar.draw(canvas)
        if (this.averageShouldShow) drawAverage(canvas)
        drawThumb(canvas)
        if (this.averageShouldShow) drawProfilePicture(canvas)
    }

    private fun drawThumb(canvas: Canvas) {

        val widthPosition = progress * sliderBar.bounds.width()
        val thumbScale = mThumbSpring.currentValue.toFloat()

        canvas.save()
        canvas.translate(sliderBar.bounds.left.toFloat(), sliderBar.bounds.top.toFloat())
        canvas.scale(thumbScale, thumbScale, widthPosition, 0f)

        thumbDrawable.updateDrawableBounds(widthPosition.roundToInt())
        thumbDrawable.draw(canvas)

        canvas.restore()
    }

    private fun drawProfilePicture(canvas: Canvas) {

        val widthPosition = progress * sliderBar.bounds.width()
        val height: Float = sliderBar.bounds.height() / 2f

        canvas.save()
        canvas.translate(sliderBar.bounds.left.toFloat(), sliderBar.bounds.top.toFloat())
        canvas.scale(1f, 1f, widthPosition, height)

        this.drawableProfileImage.updateDrawableBounds(widthPosition.roundToInt())
        this.drawableProfileImage.draw(canvas)

        canvas.restore()
    }

    private fun drawAverage(canvas: Canvas) {
        this.drawableAverageCircle.outerColor = getCorrectColor(
            this.colorStart,
            this.colorEnd,
            this.averagePercentValue
        )

        this.drawableAverageCircle.invalidateSelf()

        val scale = this.mAverageSpring.currentValue.toFloat()

        val widthPosition = this.averagePercentValue * sliderBar.bounds.width()
        val heightPosition = (sliderBar.bounds.height() / 2).toFloat()

        canvas.save()
        canvas.translate(sliderBar.bounds.left.toFloat(), sliderBar.bounds.top.toFloat())
        canvas.scale(scale, scale, widthPosition, heightPosition)

        this.drawableAverageCircle.updateDrawableBounds(widthPosition.roundToInt())
        this.drawableAverageCircle.draw(canvas)

        canvas.restore()
    }

    private fun Drawable.updateDrawableBounds(widthPosition: Int) {

        val customIntrinsicWidth = this.intrinsicWidth / 2
        val customIntrinsicHeight = this.intrinsicHeight / 2
        val heightPosition = sliderBar.bounds.height() / 2

        this.setBounds(
            widthPosition - customIntrinsicWidth,
            heightPosition - customIntrinsicHeight,
            widthPosition + customIntrinsicWidth,
            heightPosition + customIntrinsicHeight
        )
    }

    //////////////////////////////////////////
    // Other
    //////////////////////////////////////////

    private fun Spring.origamiConfig(tension: Double, friction: Double): Spring =
        this.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(tension, friction))

    fun showPopupWindow(finalPosition: Int) {
        val rootView =
            LayoutInflater.from(context).inflate(R.layout.bubble, null) as BubbleTextView
        val window = BernardoPopupWindow(rootView, rootView)
        window.xPadding = finalPosition
        window.setCancelOnTouch(true)
        window.setCancelOnTouchOutside(true)
        window.setCancelOnLater(2500)
        window.showArrowTo(this@EmojiSlider, BubbleStyle.ArrowDirection.Up)
    }

    var flyingEmoji = FlyingEmoji(context)

    var emoji = "😍"
        set(value) {
            field = value
            updateThumb(field)
        }

    var sliderParticleSystem: View? = null
        set(value) {
            field = value

            if (value?.background !is FlyingEmoji) {
                value?.background = flyingEmoji
            } else {
                flyingEmoji = value.background as FlyingEmoji
            }

            println("RAWR1: $value")
        }

    //////////////////////////////////////////
    // Initialization
    //////////////////////////////////////////

    init {

        paintBar.style = Paint.Style.FILL

        paintLabel = Paint(Paint.ANTI_ALIAS_FLAG)
        paintLabel.style = Paint.Style.FILL

        paintText = Paint(Paint.ANTI_ALIAS_FLAG)

        val density = context.resources.displayMetrics.density

        if (attrs != null) {
//            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.FluidSlider, defStyleAttr, 0)
            try {
//                colorBar = a.getColor(R.styleable.FluidSlider_bar_color, COLOR_BAR)
//                colorBubble = a.getColor(R.styleable.FluidSlider_bubble_color, COLOR_LABEL)
//                colorBarText = a.getColor(R.styleable.FluidSlider_bar_text_color, COLOR_BAR_TEXT)
//                colorBubbleText = a.getColor(R.styleable.FluidSlider_bubble_text_color, COLOR_LABEL_TEXT)
//
//                position = max(0f, min(1f, a.getFloat(R.styleable.FluidSlider_initial_position, INITIAL_POSITION)))
//                textSize = a.getDimension(R.styleable.FluidSlider_text_size, TEXT_SIZE * density)
//                duration = abs(a.getInteger(R.styleable.FluidSlider_duration, ANIMATION_DURATION)).toLong()
//
//                a.getString(R.styleable.FluidSlider_start_text)?.also { startText = it }
//                a.getString(R.styleable.FluidSlider_end_text)?.also { endText = it }
//
//                val defaultBarHeight = if (a.getInteger(R.styleable.FluidSlider_size, 1) == 1) Size.NORMAL.value else Size.SMALL.value
//                barHeight = defaultBarHeight * density
            } finally {
//                a.recycle()
            }
        } else {
            colorBar = COLOR_BAR
            colorBubble = COLOR_LABEL
//            barHeight = size.value * density
        }

        barHeight = size.value * density
        desiredWidth = (barHeight * SLIDER_WIDTH).toInt()
        desiredHeight =
                (density * 8 + context.resources.getDimension(R.dimen.slider_sticker_slider_handle_size)).roundToInt()
//
//        topCircleDiameter = barHeight * TOP_CIRCLE_DIAMETER
//        bottomCircleDiameter = barHeight * BOTTOM_CIRCLE_DIAMETER
//        touchRectDiameter = barHeight * TOUCH_CIRCLE_DIAMETER
//        labelRectDiameter = barHeight - LABEL_CIRCLE_DIAMETER * density
//
//        metaballMaxDistance = barHeight * METABALL_MAX_DISTANCE
//        metaballRiseDistance = barHeight * METABALL_RISE_DISTANCE
//
//        barVerticalOffset = barHeight * BAR_VERTICAL_OFFSET
        barCornerRadius = BAR_CORNER_RADIUS * density
        barInnerOffset = BAR_INNER_HORIZONTAL_OFFSET * density





        startAnimation()

        this.drawableProfileImage.callback = this
        this.drawableAverageCircle.callback = this
        this.sliderBar.callback = this

        setHandleSize(context.resources.getDimensionPixelSize(R.dimen.slider_sticker_slider_handle_size))
        sliderBar.totalHeight =
                context.resources.getDimensionPixelSize(R.dimen.slider_sticker_slider_height)
        sliderBar.setTrackHeight(context.resources.getDimension(R.dimen.slider_sticker_slider_track_height))
        sliderBar.invalidateSelf()


        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.EmojiSlider)

            try {
                array.getProgress().let {
                    progress = if (it in 0f..1f) {
                        it
                    } else if (it > 1 && it < 100) {
                        it / 100f
                    } else if (it < 0) {
                        0f
                    } else {
                        INITIAL_POSITION
                    }
                }

                sliderBar.trackColor.color = array.getSliderTrackColor()
                sliderBar.colorStart = array.getProgressGradientStart()
                sliderBar.colorEnd = array.getProgressGradientEnd()

                colorStart = array.getProgressGradientStart()
                colorEnd = array.getProgressGradientEnd()

                sliderHorizontalPadding = array.getHorizontalPadding()
                isThumbAllowedToScrollEverywhere = array.getThumbAllowScrollAnywhere()
                thumbAllowReselection = array.getAllowReselection()
                isTouchDisabled = array.getIsTouchDisabled()
                averagePercentValue = array.getAverageProgress()

                if (array.getEmojiGravity() == 0) {
                    flyingEmoji.direction = FlyingEmoji.Direction.UP
                } else {
                    flyingEmoji.direction = FlyingEmoji.Direction.DOWN
                }

                this.emoji = array.getEmoji()

                invalidateAll()

            } finally {
                array.recycle()
            }
        } else {
            colorBar = COLOR_BAR
            colorBubble = COLOR_LABEL

            colorStart = ContextCompat.getColor(context, R.color.slider_gradient_start)
            colorEnd = ContextCompat.getColor(context, R.color.slider_gradient_end)

        }
    }


    //////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////

    fun setGradientBackground(color: Int): EmojiSlider {
        sliderBar.trackColor.color = color
        return this
    }

    fun setGradientColorStart(color: Int): EmojiSlider {
        colorStart = color
        return this
    }

    fun setGradientColorEnd(color: Int): EmojiSlider {
        colorEnd = color
        return this
    }

    fun setHorizontalPadding(padding: Int): EmojiSlider {
        sliderHorizontalPadding = padding
        return this
    }

    fun setAllowScrollEverywhere(isAllowed: Boolean): EmojiSlider {
        isThumbAllowedToScrollEverywhere = isAllowed
        return this
    }

    fun setThumbAllowReselection(isAllowed: Boolean): EmojiSlider {
        thumbAllowReselection = isAllowed
        return this
    }

    fun setAverageProgress(progress: Int): EmojiSlider {
        averagePercentValue = progress / 100f
        return this
    }

    fun setFlyingEmojiDirection(direction: FlyingEmoji.Direction): EmojiSlider {
        flyingEmoji.direction = direction
        return this
    }

    fun invalidateAll() {
        drawableProfileImage.invalidateSelf()
        drawableAverageCircle.invalidateSelf()
        sliderBar.invalidateSelf()
        thumbDrawable.invalidateSelf()
        invalidate()
    }

    //////////////////////////////////////////
    // PRIVATE GET METHODS
    //////////////////////////////////////////

    private fun TypedArray.getProgress(): Float =
        this.getFloat(R.styleable.EmojiSlider_progress, INITIAL_POSITION)


    private fun TypedArray.getProgressGradientStart(): Int {
        return this.getColor(
            R.styleable.EmojiSlider_bar_gradient_start,
            ContextCompat.getColor(context, R.color.slider_gradient_start)
        )
    }

    private fun TypedArray.getProgressGradientEnd(): Int {
        return this.getColor(
            R.styleable.EmojiSlider_bar_gradient_end,
            ContextCompat.getColor(context, R.color.slider_gradient_end)
        )
    }

    private fun TypedArray.getSliderTrackColor(): Int {
        return this.getColor(
            R.styleable.EmojiSlider_bar_background_color,
            ContextCompat.getColor(context, R.color.slider_gradient_background)
        )
    }

    private fun TypedArray.getHorizontalPadding(): Int {
        return this.getInt(
            R.styleable.EmojiSlider_horizontal_padding,
            context.resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal)
        )
    }

    private fun TypedArray.getEmoji(): String =
        this.getString(R.styleable.EmojiSlider_emoji) ?: emoji

    private fun TypedArray.getEmojiGravity(): Int =
        this.getInt(R.styleable.EmojiSlider_flying_gravity, 0)

    private fun TypedArray.getThumbAllowScrollAnywhere(): Boolean =
        this.getBoolean(R.styleable.EmojiSlider_thumb_allow_scroll_anywhere, true)

    private fun TypedArray.getAllowReselection(): Boolean =
        this.getBoolean(R.styleable.EmojiSlider_allow_reselection, true)

    private fun TypedArray.getAverageProgress(): Float =
        this.getFloat(R.styleable.EmojiSlider_average_progress, 0.5f)

    private fun TypedArray.getIsTouchDisabled(): Boolean =
        this.getBoolean(R.styleable.EmojiSlider_is_touch_disabled, false)


    //////////////////////////////////////////
    // OTHER METHODS
    //////////////////////////////////////////

    fun progressChanged(progress: Float) {
        println("RAWR1 NOT + " + sliderParticleSystem)
        if (sliderParticleSystem == null) return
        println("RAWR1 NOT NULL")

        val sliderLocation = IntArray(2)
        getLocationOnScreen(sliderLocation)

        val particleLocation = IntArray(2)
        sliderParticleSystem!!.getLocationOnScreen(particleLocation)

        println("RAWR1 SLIDER - location [x]: " + sliderLocation[0] + " --- location [y]: " + sliderLocation[1])
        println("RAWR1 PARTICLE - location [x]:" + particleLocation[0] + " --- location [y]: " + particleLocation[1])
        println(
            "RAWR1 PaddingTop: " + (sliderLocation[1].toFloat() + DpToPx(
                context,
                32f
            ) - particleLocation[1])
        )

        flyingEmoji.onProgressChanged(
            paddingLeft = sliderLocation[0].toFloat() + sliderBar.bounds.left + thumbDrawable.bounds.centerX() - particleLocation[0],
            paddingTop = sliderLocation[1].toFloat() + DpToPx(context, 32f) - particleLocation[1]
        )

        flyingEmoji.updateProgress(progress)
    }

    private fun updateThumb(emoji: String) {
        thumbDrawable = textToDrawable(
            context = this.context,
            text = emoji,
            size = R.dimen.slider_sticker_slider_handle_size
        )
        thumbDrawable.callback = this
    }


    //////////////////////////////////////////
    // Touch Event
    //////////////////////////////////////////

    /**
     * Handles thumbDrawable selection and movement. Notifies listener callback on certain events.
     */
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        super.onTouchEvent(motionEvent)

        if (isTouchDisabled) return false

        val x = motionEvent.x.toInt() - sliderBar.bounds.left
        val y = motionEvent.y.toInt() - sliderBar.bounds.top

        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {

                println(
                    "sliderBoundsContains: " + sliderBar.bounds.toShortString() + " --- x: " + x + " y: " + y + " mX: " + motionEvent.x + " mY: " + motionEvent.y + " contains1: " + sliderBar.bounds.containsXY(
                        motionEvent
                    ) + " contains2: " + sliderBar.bounds.contains(x, y)
                )

                if (thumbDrawable.bounds.contains(x, y) ||
                    (isThumbAllowedToScrollEverywhere
                            && sliderBar.bounds.containsXY(motionEvent))
                ) {
                    isThumbSelected = true
                    flyingEmoji.progressStarted(emoji)
                    mThumbSpring.endValue = 0.9
                    beginTrackingListener?.invoke()
                }
            }

            MotionEvent.ACTION_UP -> {
                onCancelTouch()
                performClick()
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> onCancelTouch()

            MotionEvent.ACTION_MOVE -> {
                if (isThumbSelected) {
                    progress = x / sliderBar.bounds.width().toFloat()
                    progressChanged(progress)
                    positionListener?.invoke(progress)
                    println("moving.. " + "x: " + x + " width: " + sliderBar.bounds.width() + " equals: " + progress)
                }
            }
        }
        return true
    }

    private fun onCancelTouch() {
        mThumbSpring.endValue = 1.0

        if (isThumbSelected) {
            valueWasSelected()
            flyingEmoji.onStopTrackingTouch()
            endTrackingListener?.invoke()
        }
        isThumbSelected = false
    }

    private fun Rect.containsXY(motionEvent: MotionEvent): Boolean =
        this.contains(motionEvent.x.toInt(), motionEvent.y.toInt())


    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
