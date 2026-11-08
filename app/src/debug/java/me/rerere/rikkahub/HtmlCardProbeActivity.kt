package me.rerere.rikkahub

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/** Debug-only host for the HTML card WebView probe. Not in release builds. */
class HtmlCardProbeActivity : ComponentActivity() {
    lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
    }
}
