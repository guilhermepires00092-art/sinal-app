package com.sinal.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SINAL_UUID: UUID = UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private lateinit var statusBadge: TextView
    private lateinit var radarPulseRing: FrameLayout
    private lateinit var radarCenterCircle: LinearLayout
    private lateinit var radarStatusText: TextView
    private lateinit var radarDistanceText: TextView
    private lateinit var alertCard: LinearLayout
    private lateinit var btnToggleRadar: Button
    private lateinit var btnDemoMatch: Button
    private var pulseAnimator: ObjectAnimator? = null

    private var isRadarActive = false
    private var demoDistance = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Layout Principal - Tema Noturno Profundo Cinematográfico
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070B"))
            setPadding(40, 56, 40, 40)
        }

        // Topo / Marca S I N A L
        val logoText = TextView(this).apply {
            text = "⚡ S  I  N  A  L"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            setPadding(0, 8, 0, 4)
        }

        val tagLine = TextView(this).apply {
            text = "Sintonize o destino a 10 metros."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        statusBadge = TextView(this).apply {
            text = "● RADAR ADORMECIDO"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0E131F"))
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#1E293B"))
            }
        }

        // Radar Visual Central com Anéis de Pulso
        val radarWrapper = FrameLayout(this).apply {
            val size = 560
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 32, 0, 24)
            }
        }

        // Anel Externo de Pulso (Efeito Onda de Rádio)
        radarPulseRing = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#180D2B"))
                setStroke(3, Color.parseColor("#7C3AED"))
            }
            alpha = 0.3f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Círculo Central do Radar
        radarCenterCircle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val centerSize = 440
            layoutParams = FrameLayout.LayoutParams(centerSize, centerSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0B0F19"))
                setStroke(4, Color.parseColor("#334155"))
            }
        }

        radarStatusText = TextView(this).apply {
            text = "Vazio"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        radarDistanceText = TextView(this).apply {
            text = "Raio de 10m"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }

        radarCenterCircle.addView(radarStatusText)
        radarCenterCircle.addView(radarDistanceText)

        radarWrapper.addView(radarPulseRing)
        radarWrapper.addView(radarCenterCircle)

        // Card de Alerta Dopaminérgico (Quando alguém curte você no local)
        alertCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 28, 32, 28)
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#150A21"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#A855F7"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = params
        }

        val alertTitle = TextView(this).apply {
            text = "💓 ALGUÉM A 3 METROS CURTIU VOCÊ!"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F43F5E"))
            gravity = Gravity.CENTER
        }

        val alertSubtitle = TextView(this).apply {
            text = "Olhe ao redor discretamente na praça agora..."
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 16)
        }

        // Silhueta Misteriosa / Foto Desfocada
        val blurAvatar = TextView(this).apply {
            text = "👤"
            textSize = 42f
            gravity = Gravity.CENTER
            val avatarSize = 130
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                setMargins(0, 0, 0, 16)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2A1245"))
                setStroke(2, Color.parseColor("#E11D48"))
            }
        }

        // Botão de Desbloqueio Impulsivo via Pix (R$ 4,90)
        val btnUnlockPix = Button(this).apply {
            text = "⚡ REVELAR FOTO AGORA (R$ 4,90 PIX)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E11D48"))
            setPadding(20, 16, 20, 16)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 8)
            layoutParams = p
            setOnClickListener {
                Toast.makeText(this@MainActivity, "✨ Chave Pix Copiada! Revelando foto no local...", Toast.LENGTH_LONG).show()
            }
        }

        // Opção do Passe VIP Mensal
        val btnVipPass = TextView(this).apply {
            text = "Ou assine o Passe VIP (R$ 24,90/mês)"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "💎 SINAL VIP: Desbloqueios ilimitados em todo o shopping!", Toast.LENGTH_SHORT).show()
            }
        }

        alertCard.addView(alertTitle)
        alertCard.addView(alertSubtitle)
        alertCard.addView(blurAvatar)
        alertCard.addView(btnUnlockPix)
        alertCard.addView(btnVipPass)

        // Botão Principal de Ativar o Radar
        btnToggleRadar = Button(this).apply {
            text = "SINTONIZAR MEU SINAL"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setPadding(24, 22, 24, 22)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 12) }
            layoutParams = params
            setOnClickListener {
                if (!isRadarActive) startRadar() else stopRadar()
            }
        }

        // Botão de Demonstração / Teste no Shopping
        btnDemoMatch = Button(this).apply {
            text = "⚡ DEMONSTRAR SINAL SE APROXIMANDO"
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(16, 14, 16, 14)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                triggerImpressionDemo()
            }
        }

        root.addView(logoText)
        root.addView(tagLine)
        root.addView(statusBadge)
        root.addView(radarWrapper)
        root.addView(alertCard)
        root.addView(btnToggleRadar)
        root.addView(btnDemoMatch)

        setContentView(root)
    }

    private fun startRadar() {
        isRadarActive = true
        statusBadge.text = "● ESCUTANDO O ESPAÇO AO REDOR"
        statusBadge.setTextColor(Color.parseColor("#10B981"))
        btnToggleRadar.text = "DESLIGAR MEU SINAL"
        btnToggleRadar.setBackgroundColor(Color.parseColor("#1E293B"))
        radarDistanceText.text = "Buscando..."

        startPulseAnimation()

        // Ativa escuta BLE real
        if (bluetoothAdapter?.isEnabled == true) {
            try {
                bleScanner = bluetoothAdapter?.bluetoothLeScanner
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                bleScanner?.startScan(null, settings, object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.let {
                            if (it.rssi > -65) {
                                onSinalFound(approximateMeters(it.rssi))
                            }
                        }
                    }
                })
            } catch (e: SecurityException) {}
        }
    }

    private fun stopRadar() {
        isRadarActive = false
        stopPulseAnimation()
        try {
            bleScanner?.stopScan(object : ScanCallback() {})
        } catch (e: SecurityException) {}

        statusBadge.text = "● RADAR ADORMECIDO"
        statusBadge.setTextColor(Color.parseColor("#475569"))
        btnToggleRadar.text = "SINTONIZAR MEU SINAL"
        btnToggleRadar.setBackgroundColor(Color.parseColor("#7C3AED"))

        radarCenterCircle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0B0F19"))
            setStroke(4, Color.parseColor("#334155"))
        }
        radarStatusText.text = "Vazio"
        radarDistanceText.text = "Raio de 10m"
        alertCard.visibility = View.GONE
        demoDistance = 7
    }

    private fun triggerImpressionDemo() {
        if (!isRadarActive) startRadar()

        demoDistance -= 2
        if (demoDistance < 2) demoDistance = 6

        onSinalFound(demoDistance)
    }

    private fun onSinalFound(meters: Int) {
        runOnUiThread {
            triggerHeartbeatVibration()

            radarStatusText.text = "⚡ PRESENÇA DETECTADA!"
            radarStatusText.setTextColor(Color.parseColor("#F43F5E"))
            radarDistanceText.text = "Aprox. $meters metros"
            radarDistanceText.setTextColor(Color.WHITE)

            // Destaque Rubi no Radar Central
            radarCenterCircle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3B0715"))
                setStroke(6, Color.parseColor("#E11D48"))
            }

            alertCard.visibility = View.VISIBLE
        }
    }

    // Vibração tátil dupla com ritmo de batimento cardíaco (Tum-tum)
    private fun triggerHeartbeatVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 120, 100, 140)
            val amplitudes = intArrayOf(0, 200, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(250)
        }
    }

    private fun startPulseAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0.05f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(radarPulseRing, scaleX, scaleY, alpha).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        radarPulseRing.scaleX = 1.0f
        radarPulseRing.scaleY = 1.0f
        radarPulseRing.alpha = 0.3f
    }

    private fun approximateMeters(rssi: Int): Int {
        return when {
            rssi > -55 -> 2
            rssi > -68 -> 4
            rssi > -78 -> 6
            else -> 8
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
    }
}
