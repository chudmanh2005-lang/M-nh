package com.example.autocall5

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Auto Gọi 5\\n\\nQuy trình: Gọi → Gọi người nhận → thêm 5 → gọi → 12 giây → tắt → quay lại → vuốt đơn tiếp."
            textSize = 18f
        }

        val settingsButton = Button(this).apply {
            text = "Mở Trợ năng"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val startButton = Button(this).apply {
            text = "BẬT TỰ ĐỘNG"
            setOnClickListener { AutoCallAccessibilityService.running = true }
        }

        val stopButton = Button(this).apply {
            text = "DỪNG"
            setOnClickListener { AutoCallAccessibilityService.running = false }
        }

        layout.addView(title)
        layout.addView(settingsButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }
}
