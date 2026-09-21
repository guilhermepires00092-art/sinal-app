package com.sinal.app

import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F19"))
            setPadding(64, 64, 64, 64)
        }

        val logoBadge = TextView(this).apply {
            text = "🔔 SINAL"
            textSize = 32f
            setTextColor(Color.parseColor("#F43F5E"))
            gravity = Gravity.CENTER
        }

        val titleView = TextView(this).apply {
            text = "MVP v0.1 • Fase 1"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }

        val descriptionView = TextView(this).apply {
            text = "Compilado com sucesso pelo GitHub Actions!\n\nPróximo passo: Rádio BLE."
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        rootLayout.addView(logoBadge)
        rootLayout.addView(titleView)
        rootLayout.addView(descriptionView)

        setContentView(rootLayout)
    }
}
