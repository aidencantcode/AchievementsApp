package app.achievements

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

// Slide-in achievement card drawn as a system overlay, so it shows over games.
// Styles: solid, glass (window blur of whatever is behind it) and liquid (glass + specular edge + moving sheen).
object Popup {
    private val h = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<() -> Unit>()
    private var busy = false

    private class Cfg(
        val side: String, val secs: Int, val radius: Int, val blur: Int,
        val opacity: Int, val scale: Int, val style: String, val font: File?
    )

    private class Sheen(ctx: Context) : View(ctx) {
        var t = -1f
        private val paint = Paint()
        override fun onDraw(c: Canvas) {
            if (t < 0f || t > 1f) return
            val w = width.toFloat()
            val x = -w * 0.4f + t * w * 1.8f
            paint.shader = LinearGradient(x, 0f, x + w * 0.35f, height * 0.6f, intArrayOf(0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, height.toFloat(), paint)
        }
    }

    fun show(ctx: Context, p: Prefs, name: String, desc: String, game: String, icon: Bitmap?) {
        val app = ctx.applicationContext
        Sfx.init(app)
        val font = File(app.filesDir, "popup_font").takeIf { it.exists() }
        val cfg = Cfg(p.popupSide, p.popupSecs, p.popupRadius, p.popupBlur, p.popupOpacity, p.popupScale, p.popupStyle, font)
        h.post {
            queue.add { run(app, cfg, name, desc, game, icon) }
            if (!busy) next()
        }
    }

    private fun next() {
        val t = queue.removeFirstOrNull()
        if (t == null) { busy = false; return }
        busy = true
        try { t() } catch (e: Exception) { next() }
    }

    private fun run(ctx: Context, cfg: Cfg, name: String, desc: String, game: String, icon: Bitmap?) {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density * cfg.scale / 100f
        fun dp(x: Int) = (x * d).toInt()
        val base = cfg.font?.let { try { Typeface.createFromFile(it) } catch (e: Exception) { null } } ?: Typeface.DEFAULT
        fun tv(t: String, sp: Float, color: Long, bold: Boolean) = TextView(ctx).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * d); setTextColor(color.toInt())
            typeface = if (bold) Typeface.create(base, Typeface.BOLD) else base
        }
        val r = dp(cfg.radius).toFloat()
        val solid = cfg.style == "solid"
        val blurOk = !solid && cfg.blur > 0 && Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled
        var a = (cfg.opacity * 255 / 100).coerceIn(30, 255)
        if (!solid && !blurOk && a < 215) a = 215 // no blur available: keep text readable
        val bg: android.graphics.drawable.Drawable = when (cfg.style) {
            "solid" -> GradientDrawable().apply { setColor(0xFF1F252D.toInt()); cornerRadius = r }
            "glass" -> GradientDrawable().apply {
                setColor(android.graphics.Color.argb(a, 16, 22, 30)); cornerRadius = r
                setStroke(maxOf(1, dp(1)), android.graphics.Color.argb(80, 255, 255, 255))
            }
            else -> {
                val i = maxOf(1, dp(1))
                val border = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(android.graphics.Color.argb(200, 255, 255, 255), android.graphics.Color.argb(30, 120, 190, 255), android.graphics.Color.argb(120, 255, 255, 255))).apply { cornerRadius = r }
                val fill = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(android.graphics.Color.argb(minOf(255, a + 25), 44, 58, 80), android.graphics.Color.argb(a, 14, 20, 30))).apply { cornerRadius = maxOf(0f, r - i) }
                val spec = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(android.graphics.Color.argb(70, 255, 255, 255), 0x00FFFFFF)).apply { cornerRadius = maxOf(0f, r - i) }
                LayerDrawable(arrayOf(border, fill, spec)).apply { setLayerInset(1, i, i, i, i); setLayerInset(2, i, i, i, i) }
            }
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(18), dp(12))
            background = bg
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0xFF38414D.toInt()); cornerRadius = dp(8).toFloat() }
            clipToOutline = true
            if (icon != null) setImageBitmap(icon)
        }
        card.addView(img, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv("ACHIEVEMENT UNLOCKED", 10f, 0xFF6CC4FFL, true).apply { letterSpacing = 0.08f })
        col.addView(tv(name, 15f, 0xFFF2F5F8L, true).apply { maxLines = 2 })
        if (desc.isNotBlank()) col.addView(tv(desc, 12f, 0xFFB4BEC9L, false).apply { maxLines = 2 })
        card.addView(col)
        val sheen = Sheen(ctx)
        val root = FrameLayout(ctx).apply {
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            if (cfg.style == "liquid") addView(sheen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) }
            }
            clipToOutline = true
        }
        val maxW = minOf(ctx.resources.displayMetrics.widthPixels - dp(32), dp(360))
        root.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val w = root.measuredWidth
        val hh = root.measuredHeight
        val lp = WindowManager.LayoutParams(
            w, hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (blurOk) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = dp(cfg.blur)
        }
        val sbId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val sb = if (sbId > 0) ctx.resources.getDimensionPixelSize(sbId) else dp(24)
        val vertical = cfg.side == "top" || cfg.side == "bottom"
        lp.gravity = when (cfg.side) {
            "bottom" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            "left" -> Gravity.TOP or Gravity.START
            "right" -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val shown = if (cfg.side == "top") dp(16) + sb else dp(20)
        val hidden = -(if (vertical) hh else w) - dp(8)
        fun place(v: Int) {
            if (vertical) lp.y = v else lp.x = v
            try { wm.updateViewLayout(root, lp) } catch (e: Exception) { }
        }
        if (vertical) lp.y = hidden else { lp.x = hidden; lp.y = sb + dp(16) }
        wm.addView(root, lp)
        Sfx.play("unlock")

        fun move(from: Int, to: Int, ms: Long, end: () -> Unit) {
            ValueAnimator.ofInt(from, to).apply {
                duration = ms
                interpolator = DecelerateInterpolator()
                addUpdateListener { place(it.animatedValue as Int) }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { end() } })
                start()
            }
        }
        if (cfg.style == "liquid") ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100; startDelay = 250
            addUpdateListener { sheen.t = it.animatedValue as Float; sheen.invalidate() }
            start()
        }
        move(hidden, shown, 380) {
            h.postDelayed({
                move(shown, hidden, 300) {
                    try { wm.removeView(root) } catch (e: Exception) { }
                    next()
                }
            }, cfg.secs * 1000L)
        }
    }
}
