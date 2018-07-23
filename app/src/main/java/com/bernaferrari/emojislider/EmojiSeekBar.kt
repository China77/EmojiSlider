package com.bernaferrari.emojislider

import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.updatePadding
import com.bernaferrari.emojislider.arrowpopupwindow.utils.Util
import com.bernaferrari.emojislider2.EmojiHelper
import com.orhanobut.logger.Logger


class EmojiSeekBar : SeekBar, OnSeekBarChangeListener {


    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : super(context, attrs, defStyleAttr, 0) {


        this.emojiHelper = EmojiHelper(this.context)
        this.splitTrack = false
        this.progressDrawable =
                ContextCompat.getDrawable(this.context, R.drawable.slider_sticker_gradient)
        this.progress = 10

        this.updatePadding(
            left = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal),
            right = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal)
        )

        this.setOnSeekBarChangeListener(this)
        updateThumb("😍")
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : super(context, attrs, 0, 0) {


        this.emojiHelper = com.bernaferrari.emojislider2.EmojiHelper(this.context)
        this.splitTrack = false
        this.progressDrawable =
                ContextCompat.getDrawable(this.context, R.drawable.slider_sticker_gradient)
        this.progress = 10

        this.updatePadding(
            left = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal),
            right = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal)
        )

        this.setOnSeekBarChangeListener(this)
        updateThumb("😍")
    }

    constructor(
        context: Context
    ) : super(context, null, 0, 0) {

        this.emojiHelper = com.bernaferrari.emojislider2.EmojiHelper(this.context)
        this.splitTrack = false
        this.progressDrawable =
                ContextCompat.getDrawable(this.context, R.drawable.slider_sticker_gradient)
        this.progress = 10

        this.updatePadding(
            left = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal),
            right = resources.getDimensionPixelSize(R.dimen.slider_sticker_padding_horizontal)
        )

        this.setOnSeekBarChangeListener(this)
        updateThumb("😍")
    }

    var emojiHelper: com.bernaferrari.emojislider2.EmojiHelper
    private val sliderStickerSlider by lazy { this }

    var sliderParticleSystem: View? = null
        set(value) {
            field = value

            if (value?.background !is com.bernaferrari.emojislider2.EmojiHelper) {
                value?.background = emojiHelper
            } else {
                emojiHelper = value.background as EmojiHelper
            }
        }


    fun setBackgroundView(backgroundView: View, emojiHelper: com.bernaferrari.emojislider2.EmojiHelper? = null) {
        if (emojiHelper != null) {
            this.emojiHelper = emojiHelper
        }

        sliderParticleSystem = backgroundView
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) = emojiHelper.progressStarted("😍")

    override fun onStopTrackingTouch(seekBar: SeekBar) = emojiHelper.onStopTrackingTouch()

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

        if (sliderParticleSystem == null) return

        Logger.d("slider [top]: " + sliderStickerSlider.top + " // slider [paddingTop]: " + sliderStickerSlider.paddingTop + " // slider [bounds top]: " + sliderStickerSlider.thumb.bounds.top)
        Logger.d("thisTop: " + this.top + " thisPaddingTop: " + this.paddingTop + " thumbLeft: " + sliderStickerSlider.thumb.bounds.left + " thumbBounds: " + sliderStickerSlider.thumb.bounds.toShortString())

        val sliderLocation = IntArray(2)
        sliderStickerSlider.getLocationOnScreen(sliderLocation)

        val particleLocation = IntArray(2)
        sliderParticleSystem!!.getLocationOnScreen(particleLocation)

        Logger.d(
            "SLIDER - location [x]: " + sliderLocation[0] + " --- location [y]: " + sliderLocation[1] + "\n" +
                    "PARTICLE - location [x]:" + particleLocation[0] + " --- location [y]: " + particleLocation[1] + "\n" +
                    "thumbLeft: " + sliderStickerSlider.thumb.bounds.left +
                    "\npaddingLeft: " + (sliderLocation[0].toFloat() + sliderStickerSlider.paddingLeft + sliderStickerSlider.thumb.bounds.left - particleLocation[0])
        )

        if (fromUser) {
            this.emojiHelper.onProgressChanged(
                paddingLeft = sliderLocation[0].toFloat() + sliderStickerSlider.paddingLeft + sliderStickerSlider.thumb.bounds.left - particleLocation[0],
                paddingTop = sliderLocation[1].toFloat() + Util.DpToPx(
                    this.context,
                    32f
                ) - particleLocation[1]
            )
            this.emojiHelper.updateProgress(progress.toFloat() / 100.0f)
        }
    }

    private fun updateThumb(emoji: String) {
        sliderStickerSlider.thumb = generateThumb(
            context = this.context,
            text = emoji,
            size = R.dimen.slider_sticker_slider_handle_size
        )
//        emojiHelper.emoji = emoji
    }
}