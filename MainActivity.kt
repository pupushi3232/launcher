package com.minimal.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String, val icon: Drawable)
    private class Tile(val title: String, val query: String?, val glyph: String?, val onTap: (() -> Unit)?)

    // ---- Настройки оформления ----
    private val cBgTop = Color.parseColor("#0A0D12")
    private val cBgBottom = Color.parseColor("#141A23")
    private val cTile = Color.parseColor("#1B212B")
    private val cTilePressed = Color.parseColor("#27303D")
    private val cText = Color.parseColor("#E8EBF0")
    private val cDim = Color.parseColor("#7D8795")
    private val cAccent = Color.parseColor("#FF8A3D")
    private val cLine = Color.parseColor("#9AA5B4")

    /** Высота системной панели климата снизу (dp). Подберите под свой экран. */
    private val bottomSafeDp = 76
    private val drawerBodyDp = 300

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val installed = mutableListOf<AppItem>()
    private lateinit var tilesBox: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var drawerBody: FrameLayout
    private lateinit var handle: HandleView
    private lateinit var drawerAdapter: AppAdapter
    private var drawerOpen = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(cBgTop, cBgBottom))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(36), dp(24), dp(36), dp(bottomSafeDp + 48))
        }

        // Левая колонка: часы, дата, машина
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 84f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(cText)
            includeFontPadding = false
        })
        left.addView(TextClock(this).apply {
            format24Hour = "EEEE, d MMMM"; format12Hour = "EEEE, d MMMM"
            textSize = 18f
            setTextColor(cDim)
        })
        left.addView(CarView(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        content.addView(left, LinearLayout.LayoutParams(0, MATCH, 5f))

        // Правая колонка: плитки
        tilesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(tilesBox, LinearLayout.LayoutParams(0, MATCH, 5f).apply {
            leftMargin = dp(24)
        })
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        // Нижняя скрытая панель: стрелка + список всех приложений
        handle = HandleView(this).apply { setOnClickListener { setDrawer(!drawerOpen) } }
        drawerAdapter = AppAdapter()
        val grid = GridView(this).apply {
            numColumns = 7
            horizontalSpacing = dp(12)
            verticalSpacing = dp(12)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            adapter = drawerAdapter
            setOnItemClickListener { _, _, pos, _ ->
                launch(installed[pos].pkg)
                setDrawer(false)
            }
        }
        val r = dp(26).toFloat()
        drawerBody = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(cTile)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(20), dp(20), dp(12))
            visibility = View.INVISIBLE
            addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(handle, LinearLayout.LayoutParams(MATCH, dp(40)))
            addView(drawerBody, LinearLayout.LayoutParams(MATCH, dp(drawerBodyDp)))
            translationY = dp(drawerBodyDp).toFloat()
        }
        root.addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            leftMargin = dp(24); rightMargin = dp(24); bottomMargin = dp(bottomSafeDp)
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        buildTiles()
    }

    override fun onBackPressed() {
        if (drawerOpen) setDrawer(false)   // иначе лаунчер не закрывается
    }

    private fun setDrawer(open: Boolean) {
        drawerOpen = open
        handle.up = !open
        handle.invalidate()
        if (open) drawerBody.visibility = View.VISIBLE
        panel.animate()
            .translationY(if (open) 0f else dp(drawerBodyDp).toFloat())
            .setDuration(250)
            .withEndAction { if (!drawerOpen) drawerBody.visibility = View.INVISIBLE }
            .start()
    }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        installed.clear()
        installed.addAll(list)
        drawerAdapter.notifyDataSetChanged()
    }

    /** Пока подогрев только открывает климат. Прямое управление добавим после поиска интерфейса. */
    private fun heat(name: String) {
        toast("$name: открываю климат")
        installed.firstOrNull { it.label.contains("Кондиционер", true) }?.let { launch(it.pkg) }
    }

    private fun buildTiles() {
        tilesBox.removeAllViews()
        val tiles = listOf(
            Tile("Подогрев руля", null, "♨") { heat("Подогрев руля") },
            Tile("Подогрев стекла", null, "♨") { heat("Подогрев стекла") },
            Tile("Климат", "Кондиционер", null, null),
            Tile("Радио", "Локальные медиа", null, null),
            Tile("Карта", "Petal", null, null),
            Tile("Телефон", "Телефон", null, null),
            Tile("Панорама 360°", "Панорама", null, null),
            Tile("Автомобиль", "Управление автомобилем", null, null),
            Tile("Настройки", "Настройки системы", null, null)
        )
        for (r in 0 until 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 3) {
                row.addView(makeTile(tiles[r * 3 + c]),
                    LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    })
            }
            tilesBox.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
    }

    private fun makeTile(t: Tile): View {
        val app = t.query?.let { q -> installed.firstOrNull { it.label.contains(q, true) } }
        val tap = t.onTap
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), rounded(cTilePressed, 24))
                addState(intArrayOf(), rounded(cTile, 24))
            }
        }
        if (t.glyph != null) {
            v.addView(TextView(this).apply {
                text = t.glyph; textSize = 40f; setTextColor(cAccent); gravity = Gravity.CENTER
            })
        } else if (app != null) {
            v.addView(ImageView(this).apply { setImageDrawable(app.icon) },
                LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        v.addView(TextView(this).apply {
            text = t.title; textSize = 16f; setTextColor(cText)
            gravity = Gravity.CENTER; setPadding(dp(4), dp(10), dp(4), 0)
        })
        v.setOnClickListener {
            when {
                tap != null -> tap()
                app != null -> launch(app.pkg)
                else -> toast("Приложение не найдено: ${t.title}")
            }
        }
        return v
    }

    // ---- Стрелка-ручка нижней панели ----
    private inner class HandleView(ctx: Context) : View(ctx) {
        var up = true
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = cLine
        }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val half = dp(70).toFloat()
            val rise = dp(6).toFloat()
            val s = if (up) 1f else -1f
            val path = Path().apply {
                moveTo(cx - half, cy + s * rise)
                lineTo(cx, cy - s * rise)
                lineTo(cx + half, cy + s * rise)
            }
            c.drawPath(path, p)
        }
    }

    // ---- Машина ----
    // Своя модель: положите PNG в app/src/main/res/drawable-nodpi/
    //   car.png               - одна картинка
    //   car_0.png ... car_35.png - кадры вращения, машину можно крутить пальцем
    private inner class CarView(ctx: Context) : View(ctx) {
        private val frames = mutableListOf<Drawable>()
        private var idx = 0f
        private var lastX = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            var i = 0
            while (true) {
                val id = resources.getIdentifier("car_$i", "drawable", packageName)
                if (id == 0) break
                frames.add(resources.getDrawable(id, theme))
                i++
            }
            if (frames.isEmpty()) {
                val id = resources.getIdentifier("car", "drawable", packageName)
                if (id != 0) frames.add(resources.getDrawable(id, theme))
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (frames.size < 2) return super.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> lastX = e.x
                MotionEvent.ACTION_MOVE -> {
                    idx += (e.x - lastX) / dp(10)
                    lastX = e.x
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val floorY = height * 0.80f
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx, floorY, width * 0.45f,
                intArrayOf(0x33FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawOval(RectF(cx - width * 0.42f, floorY - dp(14), cx + width * 0.42f, floorY + dp(14)), paint)
            paint.shader = null

            if (frames.isNotEmpty()) {
                val n = frames.size
                val d = frames[((idx.toInt() % n) + n) % n]
                val iw = d.intrinsicWidth.coerceAtLeast(1)
                val ih = d.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(width.toFloat() / iw, height * 0.9f / ih)
                val w = iw * scale
                val h = ih * scale
                d.setBounds((cx - w / 2).toInt(), (floorY - h + dp(8)).toInt(),
                    (cx + w / 2).toInt(), (floorY + dp(8)).toInt())
                d.draw(c)
            } else {
                drawPlaceholder(c, cx, floorY)
            }
        }

        private fun drawPlaceholder(c: Canvas, cx: Float, floorY: Float) {
            val cw = minOf(width * 0.95f, height * 2.4f)
            val ch = cw / 2.6f
            val l = cx - cw / 2
            val t = floorY - ch
            fun px(x: Float) = l + x * cw
            fun py(y: Float) = t + y * ch

            val body = Path().apply {
                moveTo(px(.03f), py(.68f)); lineTo(px(.05f), py(.50f))
                quadTo(px(.06f), py(.44f), px(.14f), py(.42f))
                lineTo(px(.30f), py(.38f))
                quadTo(px(.38f), py(.14f), px(.52f), py(.12f))
                lineTo(px(.68f), py(.12f))
                quadTo(px(.80f), py(.14f), px(.90f), py(.40f))
                lineTo(px(.96f), py(.46f))
                quadTo(px(.98f), py(.50f), px(.97f), py(.68f))
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#2A313D")
            c.drawPath(body, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = cLine
            c.drawPath(body, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#3A4556")
            c.drawPath(Path().apply {
                moveTo(px(.33f), py(.40f)); lineTo(px(.40f), py(.20f))
                lineTo(px(.52f), py(.17f)); lineTo(px(.52f), py(.40f)); close()
            }, paint)
            c.drawPath(Path().apply {
                moveTo(px(.55f), py(.40f)); lineTo(px(.55f), py(.17f))
                lineTo(px(.67f), py(.17f)); lineTo(px(.78f), py(.40f)); close()
            }, paint)

            for (wx in listOf(.20f, .79f)) {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#0A0D12")
                c.drawCircle(px(wx), py(.72f), ch * 0.17f, paint)
                paint.style = Paint.Style.STROKE
                paint.color = cLine
                c.drawCircle(px(wx), py(.72f), ch * 0.11f, paint)
            }

            paint.style = Paint.Style.FILL
            paint.color = cDim
            paint.textSize = dp(13).toFloat()
            paint.textAlign = Paint.Align.CENTER
            c.drawText("Макет. Для своей модели добавьте car.png", cx, floorY + dp(34), paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    // ---- Список приложений в нижней панели ----
    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = installed.size
        override fun getItem(position: Int) = installed[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = installed[position]
            val cell = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = AbsListView.LayoutParams(MATCH, dp(100))
            }
            cell.addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icon) },
                LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(TextView(this@MainActivity).apply {
                text = app.label; textSize = 13f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER; setTextColor(cText)
                setPadding(0, dp(6), 0, 0)
            })
            return cell
        }
    }
}
