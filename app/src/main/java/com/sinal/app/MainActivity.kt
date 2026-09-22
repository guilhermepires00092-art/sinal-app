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
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null

    private lateinit var statusBadge: TextView
    private lateinit var radarCircle: LinearLayout
    private lateinit var radarDistanceText: TextView
    private lateinit var radarUserText: TextView
    private lateinit var btnToggleRadar: Button
    private lateinit var devicesContainer: LinearLayout

    private var isRadarActive = false
    // Armazena aparelhos detectados e seus sinais em dBm
    private val nearbyDevices = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#080C14"))
            setPadding(40, 50, 40, 40)
        }

        val appLogo = TextView(this).apply {
            text = "⚡ S I N A L"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }

        statusBadge = TextView(this).apply {
            text = "● RADAR PRONTO PARA VARRER AMBIENTE"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // Radar Central
        radarCircle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            val size = 460
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 8, 0, 24)
            }
        }

        radarUserText = TextView(this).apply {
            text = "Aparelho Mais Próximo:"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        radarDistanceText = TextView(this).apply {
            text = "Aguardando..."
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }

        radarCircle.addView(radarUserText)
        radarCircle.addView(radarDistanceText)

        btnToggleRadar = Button(this).apply {
            text = "VARRER SALA / PESSOAS PRÓXIMAS"
            setBackgroundColor(Color.parseColor("#E11D48"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(24, 20, 24, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = params
            setOnClickListener {
                if (!isRadarActive) checkAndStartScan() else stopScan()
            }
        }

        val subtitle = TextView(this).apply {
            text = "SINAIS DE RÁDIO DETECTADOS NA SALA:"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 10, 0, 10)
        }

        devicesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(devicesContainer)
        }

        root.addView(appLogo)
        root.addView(statusBadge)
        root.addView(radarCircle)
        root.addView(btnToggleRadar)
        root.addView(subtitle)
        root.addView(scroll)

        setContentView(root)
    }

    private fun checkAndStartScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 102)
        } else {
            startScan()
        }
    }

    private fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Ative o Bluetooth do celular primeiro!", Toast.LENGTH_LONG).show()
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        nearbyDevices.clear()
        devicesContainer.removeAllViews()

        // Configuração de Varredura Agressiva em Baixa Latência (Pega tudo no ar)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Escaneia sem filtros para pegar qualquer emissor da sala
            bleScanner?.startScan(null, settings, scanCallback)
            isRadarActive = true
            statusBadge.text = "● RADAR LIGADO: ESCUTANDO ONDAS NO AR"
            statusBadge.setTextColor(Color.parseColor("#10B981"))
            btnToggleRadar.text = "PARAR VARREDURA"
            btnToggleRadar.setBackgroundColor(Color.parseColor("#334155"))
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissão de Bluetooth negada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {}

        isRadarActive = false
        statusBadge.text = "● RADAR DESLIGADO"
        statusBadge.setTextColor(Color.parseColor("#64748B"))
        btnToggleRadar.text = "VARRER SALA / PESSOAS PRÓXIMAS"
        btnToggleRadar.setBackgroundColor(Color.parseColor("#E11D48"))
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val address = it.device.address ?: "Desconhecido"
                val name = try {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        it.device.name ?: "Dispositivo Ativo"
                    } else "Dispositivo Ativo"
                } catch (e: Exception) {
                    "Dispositivo Ativo"
                }

                val rssi = it.rssi
                nearbyDevices["$name ($address)"] = rssi
                updateUI()
            }
        }
    }

    private fun updateUI() {
        runOnUiThread {
            if (nearbyDevices.isEmpty()) return@runOnUiThread

            // Encontra o sinal mais forte (o mais perto de você)
            val closest = nearbyDevices.maxByOrNull { it.value }
            closest?.let {
                val meters = calculateDistance(it.value)
                radarUserText.text = "Mais próximo: ${it.key.take(18)}..."
                radarUserText.setTextColor(Color.parseColor("#F43F5E"))
                radarDistanceText.text = "Aprox. $meters metros!"
                radarDistanceText.setTextColor(Color.WHITE)
                radarCircle.setBackgroundColor(Color.parseColor("#3B0715"))
            }

            // Atualiza a lista na tela
            devicesContainer.removeAllViews()
            for ((device, rssi) in nearbyDevices.entries.sortedByDescending { it.value }.take(6)) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.parseColor("#131B2E"))
                    setPadding(24, 20, 24, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 12) }
                    layoutParams = params
                }

                val devName = TextView(this).apply {
                    text = "📡 $device"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                }

                val devDist = TextView(this).apply {
                    val m = calculateDistance(rssi)
                    text = "Distância estimada: ~$m metros (Potência: $rssi dBm)"
                    textSize = 12f
                    setTextColor(Color.parseColor("#38BDF8"))
                    setPadding(0, 4, 0, 0)
                }

                card.addView(devName)
                card.addView(devDist)
                devicesContainer.addView(card)
            }
        }
    }

    private fun calculateDistance(rssi: Int): Int {
        return when {
            rssi > -60 -> 1
            rssi > -70 -> 3
            rssi > -80 -> 5
            rssi > -90 -> 8
            else -> 10
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
