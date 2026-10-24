package app.achievements

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Slide-in achievement card drawn as a system overlay, so it shows over games.
object Popup {
    private val h = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<() -> Unit>()
    private var busy = false

    fun show(ctx: Context, p: Prefs, name: String, desc: String, game: String, icon: Bitmap?) {
        val side = p.popupSide
        val secs = p.popupSecs
        h.post {
            queue.add { run(ctx, side, secs, name, desc, game, icon) }
            if (!busy) next()
        }
    }

    private fun next() {
        val t = queue.removeFirstOrNull()
        if (t == null) { busy = false; return }
        busy = true
        try { t() } catch (e: Exception) { next() }
    }

    private fun run(ctx: Context, side: String, secs: Int, name: String, desc: String, game: String, icon: Bitmap?) {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        fun tv(t: String, sp: Float, color: Long, bold: Boolean) = TextView(ctx).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color.toInt())
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply { setColor(0xFF282B30.toInt()); cornerRadius = dp(14).toFloat() }
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0xFF32363C.toInt()); cornerRadius = dp(8).toFloat() }
            clipToOutline = true
            if (icon != null) setImageBitmap(icon)
        }
        card.addView(img, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv("Achievement unlocked", 11f, 0xFF8E949CL, false))
        col.addView(tv(name, 15f, 0xFFE8EAEDL, true).apply { maxLines = 2 })
        if (desc.isNotBlank()) col.addView(tv(desc, 12f, 0xFF8E949CL, false).apply { maxLines = 2 })
        card.addView(col)

        val maxW = minOf(ctx.resources.displayMetrics.widthPixels - dp(32), dp(340))
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = card.measuredWidth
        val hh = card.measuredHeight
        val lp = WindowManager.LayoutParams(
            w, hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        val sbId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val sb = if (sbId > 0) ctx.resources.getDimensionPixelSize(sbId) else dp(24)
        val vertical = side == "top" || side == "bottom"
        lp.gravity = when (side) {
            "bottom" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            "left" -> Gravity.TOP or Gravity.START
            "right" -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val shown = if (side == "top") dp(16) + sb else dp(20)
        val hidden = -(if (vertical) hh else w) - dp(8)
        fun place(v: Int) {
            if (vertical) lp.y = v else lp.x = v
            try { wm.updateViewLayout(card, lp) } catch (e: Exception) { }
        }
        if (vertical) lp.y = hidden else { lp.x = hidden; lp.y = sb + dp(16) }
        wm.addView(card, lp)

        fun move(from: Int, to: Int, ms: Long, end: () -> Unit) {
            ValueAnimator.ofInt(from, to).apply {
                duration = ms
                interpolator = DecelerateInterpolator()
                addUpdateListener { place(it.animatedValue as Int) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { end() }
                })
                start()
            }
        }
        move(hidden, shown, 350) {
            h.postDelayed({
                move(shown, hidden, 300) {
                    try { wm.removeView(card) } catch (e: Exception) { }
                    next()
                }
            }, secs * 1000L)
        }
    }
}
