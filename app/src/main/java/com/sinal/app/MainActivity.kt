package com.sinal.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.nio.charset.StandardCharsets
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SINAL_UUID: UUID = UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private lateinit var statusBadge: TextView
    private lateinit var radarCircle: LinearLayout
    private lateinit var radarDistanceText: TextView
    private lateinit var radarUserText: TextView
    private lateinit var btnToggleRadar: Button
    private lateinit var btnSimulate: Button
    private lateinit var nameInput: EditText
    private lateinit var premiumAlertCard: LinearLayout

    private var isRadarActive = false
    private var simulatedMeters = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Layout Raiz - Tema Escuro Tecnológico
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#080C14"))
            setPadding(48, 56, 48, 48)
        }

        val appLogo = TextView(this).apply {
            text = "⚡ S I N A L"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }

        statusBadge = TextView(this).apply {
            text = "● RADAR DESLIGADO"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        // Campo para Nome do Usuário
        nameInput = EditText(this).apply {
            hint = "Seu Nome ou Apelido"
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#131B2E"))
            setPadding(32, 24, 32, 24)
            setText("Lucas")
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
            layoutParams = params
        }

        // Radar Central Interativo
        radarCircle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            val size = 520
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 16, 0, 32)
            }
        }

        radarUserText = TextView(this).apply {
            text = "Sintonize seu Radar"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        radarDistanceText = TextView(this).apply {
            text = "Raio de 10m"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        radarCircle.addView(radarUserText)
        radarCircle.addView(radarDistanceText)

        // Card de Alerta de Sinal / Notificação de Curtida (Regra Black Mirror / Proteção)
        premiumAlertCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1B4B"))
            setPadding(32, 24, 32, 24)
            visibility = LinearLayout.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            layoutParams = params
        }

        val alertTitle = TextView(this).apply {
            text = "🔥 Alguém a 3m curtiu você!"
            textSize = 15f
            setTextColor(Color.parseColor("#A5B4FC"))
        }

        val btnUnlock = Button(this).apply {
            text = "DESBLOQUEAR FOTO DE QUEM TE CURTIU (PREMIUM)"
            setBackgroundColor(Color.parseColor("#6366F1"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "✨ Recurso Premium: Revelando perfil...", Toast.LENGTH_LONG).show()
            }
        }
        premiumAlertCard.addView(alertTitle)
        premiumAlertCard.addView(btnUnlock)

        // Botão Principal de Ligar o Radar
        btnToggleRadar = Button(this).apply {
            text = "ATIVAR SINAL NO LOCAL"
            setBackgroundColor(Color.parseColor("#E11D48"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
            layoutParams = params
            setOnClickListener {
                if (!isRadarActive) startRadar() else stopRadar()
            }
        }

        // Botão de Simulação (Para testar no seu aparelho sozinho na empresa)
        btnSimulate = Button(this).apply {
            text = "⚡ SIMULAR ALGUÉM CURTINDO VOCÊ (-2m)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                simulatePresence()
            }
        }

        root.addView(appLogo)
        root.addView(statusBadge)
        root.addView(nameInput)
        root.addView(radarCircle)
        root.addView(premiumAlertCard)
        root.addView(btnToggleRadar)
        root.addView(btnSimulate)

        setContentView(root)
    }

    private fun startRadar() {
        isRadarActive = true
        statusBadge.text = "● RADAR ATIVO • ESCUTANDO AMBIENTE"
        statusBadge.setTextColor(Color.parseColor("#10B981"))
        btnToggleRadar.text = "DESATIVAR RADAR"
        btnToggleRadar.setBackgroundColor(Color.parseColor("#334155"))
        radarDistanceText.text = "Escaneando..."

        val userName = nameInput.text.toString().trim().ifEmpty { "Anônimo" }
        val userBytes = userName.toByteArray(StandardCharsets.UTF_8).take(12).toByteArray()

        if (bluetoothAdapter?.isEnabled == true) {
            try {
                bleScanner = bluetoothAdapter?.bluetoothLeScanner
                bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

                // Transmissão de Beacon BLE ultraleve
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                val data = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SINAL_UUID))
                    .addServiceData(ParcelUuid(SINAL_UUID), userBytes)
                    .setIncludeDeviceName(false)
                    .build()

                bleAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {})

                // Escuta Passiva
                val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SINAL_UUID)).build()
                val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

                bleScanner?.startScan(listOf(scanFilter), scanSettings, object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.let {
                            val serviceData = it.scanRecord?.getServiceData(ParcelUuid(SINAL_UUID))
                            val detectedName = if (serviceData != null) String(serviceData, StandardCharsets.UTF_8) else "Usuário SINAL"
                            onSignalDetected(detectedName, it.rssi)
                        }
                    }
                })
            } catch (e: SecurityException) {
                // Silencioso
            }
        }
    }

    private fun stopRadar() {
        isRadarActive = false
        statusBadge.text = "● RADAR DESLIGADO"
        statusBadge.setTextColor(Color.parseColor("#64748B"))
        btnToggleRadar.text = "ATIVAR SINAL NO LOCAL"
        btnToggleRadar.setBackgroundColor(Color.parseColor("#E11D48"))
        radarCircle.setBackgroundColor(Color.parseColor("#0F172A"))
        radarUserText.text = "Sintonize seu Radar"
        radarDistanceText.text = "Raio de 10m"
        premiumAlertCard.visibility = LinearLayout.GONE
        simulatedMeters = 8
    }

    private fun simulatePresence() {
        if (!isRadarActive) startRadar()

        simulatedMeters -= 2
        if (simulatedMeters < 1) simulatedMeters = 7

        onSignalDetected("Garota Misteriosa", -50 - (simulatedMeters * 3), simulatedMeters)
    }

    private fun onSignalDetected(name: String, rssi: Int, forcedMeters: Int? = null) {
        runOnUiThread {
            // Vibração tátil no bolso
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(150)
            }

            val meters = forcedMeters ?: when {
                rssi > -60 -> 2
                rssi > -72 -> 4
                rssi > -82 -> 6
                else -> 9
            }

            radarUserText.text = "⚡ SINAL DETECTADO: $name"
            radarUserText.setTextColor(Color.parseColor("#F43F5E"))
            radarDistanceText.text = "Aprox. $meters metros"
            radarDistanceText.setTextColor(Color.WHITE)
            radarCircle.setBackgroundColor(Color.parseColor("#3B0715"))

            // Mostra o card de monetização / proteção
            premiumAlertCard.visibility = LinearLayout.VISIBLE
        }
    }
}
