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
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SINAL_UUID: UUID = UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private lateinit var radarCircle: LinearLayout
    private lateinit var radarPulseText: TextView
    private lateinit var distanceText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnSimulate: Button

    private var isScanning = false
    private var simulatedDistance = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Layout Principal - Tema Escuro Imersivo
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#090D16"))
            setPadding(48, 64, 48, 48)
        }

        val appTitle = TextView(this).apply {
            text = "S I N A L"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        statusBadge = TextView(this).apply {
            text = "● RADAR EM ESPERA"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        // Radar Central Estilo Love Alarm
        radarCircle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#131B2E"))
            val size = 520
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 32, 0, 48)
            }
        }

        radarPulseText = TextView(this).apply {
            text = "⚡"
            textSize = 48f
            gravity = Gravity.CENTER
        }

        distanceText = TextView(this).apply {
            text = "Nenhum sinal"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        radarCircle.addView(radarPulseText)
        radarCircle.addView(distanceText)

        // Botão Principal de Ativação
        btnToggle = Button(this).apply {
            text = "ATIVAR SINAL NO LOCAL"
            setBackgroundColor(Color.parseColor("#F43F5E"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 16) }
            layoutParams = params
            setOnClickListener {
                if (!isScanning) startSinalRadar() else stopSinalRadar()
            }
        }

        // Botão de Simulação (Para testar com apenas 1 celular!)
        btnSimulate = Button(this).apply {
            text = "⚡ SIMULAR APROXIMAÇÃO (-2m)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
            layoutParams = params
            setOnClickListener {
                simulateSignalDetection()
            }
        }

        root.addView(appTitle)
        root.addView(statusBadge)
        root.addView(radarCircle)
        root.addView(btnToggle)
        root.addView(btnSimulate)

        setContentView(root)
    }

    private fun startSinalRadar() {
        isScanning = true
        statusBadge.text = "● TRANSMITINDO SINAL • PROCURANDO"
        statusBadge.setTextColor(Color.parseColor("#10B981"))
        btnToggle.text = "DESATIVAR SINAL"
        btnToggle.setBackgroundColor(Color.parseColor("#334155"))
        distanceText.text = "Varrendo raio de 10m..."
        distanceText.setTextColor(Color.parseColor("#38BDF8"))

        // Se tiver Bluetooth ligado, ativa o rádio real
        if (bluetoothAdapter?.isEnabled == true) {
            try {
                bleScanner = bluetoothAdapter?.bluetoothLeScanner
                bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(ParcelUuid(SINAL_UUID))
                    .build()

                bleAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {})

                val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SINAL_UUID)).build()
                val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

                bleScanner?.startScan(listOf(scanFilter), scanSettings, object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.let {
                            triggerAlarm(it.rssi)
                        }
                    }
                })
            } catch (e: SecurityException) {
                // Modo protegido
            }
        }
    }

    private fun stopSinalRadar() {
        isScanning = false
        statusBadge.text = "● RADAR EM ESPERA"
        statusBadge.setTextColor(Color.parseColor("#64748B"))
        btnToggle.text = "ATIVAR SINAL NO LOCAL"
        btnToggle.setBackgroundColor(Color.parseColor("#F43F5E"))
        distanceText.text = "Nenhum sinal"
        distanceText.setTextColor(Color.parseColor("#94A3B8"))
        radarCircle.setBackgroundColor(Color.parseColor("#131B2E"))
        simulatedDistance = 10
    }

    private fun simulateSignalDetection() {
        if (!isScanning) {
            startSinalRadar()
        }
        simulatedDistance -= 2
        if (simulatedDistance <= 1) {
            simulatedDistance = 8
        }
        val estimatedRssi = -50 - (simulatedDistance * 3)
        triggerAlarm(estimatedRssi, simulatedDistance)
    }

    private fun triggerAlarm(rssi: Int, forcedMeters: Int? = null) {
        runOnUiThread {
            // Vibra o celular com pulso tátil
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(120)
            }

            val meters = forcedMeters ?: when {
                rssi > -60 -> 2
                rssi > -70 -> 4
                rssi > -80 -> 7
                else -> 9
            }

            distanceText.text = "ALGUÉM A ~$meters METROS DE VOCÊ!"
            distanceText.setTextColor(Color.parseColor("#F43F5E"))
            radarCircle.setBackgroundColor(Color.parseColor("#3B0715"))
        }
    }
}
