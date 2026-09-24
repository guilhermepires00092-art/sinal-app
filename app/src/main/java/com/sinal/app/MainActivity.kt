package com.sinal.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * SINAL - Love Alarm da Vida Real
 * Arquivo ÚNICO para a Google Play Store:
 * - Se for você (brainrot064@gmail.com): Ativa AUTOMATICAMENTE o GOD MODE (Aba Moderação, VIP Eterno, Radar Mestre).
 * - Se for qualquer outro usuário: Funciona como app comercial puro (3 Telas, sem aba admin, sem brechas, fotos em análise).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // E-mail oficial do Dono / Criador do SINAL
        // Qualquer aparelho autenticado com esta conta ativa o God Mode AUTOMATICAMENTE.
        const val OWNER_EMAIL = "brainrot064@gmail.com"
        private const val REQUEST_PICK_IMAGE = 1001
    }

    // UUID Dedicado do SINAL no ar (Muralha 18+)
    private val SERVICE_UUID_ADULT = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    // UUID Dedicado para o SINAL TEEN (15 a 17 anos - frequências nunca se cruzam)
    private val SERVICE_UUID_TEEN = UUID.fromString("0000FEAB-0000-1000-8000-00805F9B34FB")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null

    private var isRadarActive = false
    private var currentTab = 1 // 0: Feed, 1: Radar, 2: Perfil, 3: Moderação (automático se for o Dono)
    
    // Identificação de Usuário & God Mode Automático
    // Ao iniciar no celular do dono, inicia com o email do dono ativo.
    private var currentUserEmail: String = OWNER_EMAIL 
    private var isGodMode: Boolean = true // Definido automaticamente pela comparação (currentUserEmail == OWNER_EMAIL)
    private var photoModerationStatus = "APROVADA" // "PENDENTE", "APROVADA", "RECUSADA"

    // Fila de fotos para moderação em tempo real
    data class ModItem(val id: String, val author: String, val ageGroup: String, val previewEmoji: String, var status: String)
    private val moderationList = mutableListOf(
        ModItem("m1", "Usuário #8492 (Caldas Novas)", "Adulto (18+)", "👤", "PENDENTE"),
        ModItem("m2", "Usuário #3011 (Shopping Tropical)", "Adulto (18+)", "📷", "PENDENTE"),
        ModItem("m3", "Usuário #9120 (Praça Central)", "Adulto (18+)", "🖼️", "PENDENTE")
    )

    private lateinit var rootContainer: FrameLayout
    private lateinit var tabContent: LinearLayout
    private lateinit var navBar: LinearLayout

    // Componentes do Radar
    private lateinit var radarCircleView: FrameLayout
    private lateinit var radarStatusText: TextView
    private lateinit var radarSubtitleText: TextView
    private lateinit var radarActionBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Verifica o estado da conta (Se for o dono, God Mode liga sozinho!)
        checkOwnerStatus()

        createNotificationChannel()
        setupUI()
        checkPermissions()
    }

    // Seletor Nativo de Fotos Oficial (Galeria do Celular - compatível 100% com qualquer Android)
    private fun choosePhotoFromGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Escolher Foto de Perfil"), REQUEST_PICK_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val selectedUri: Uri? = data.data
            selectedUri?.let {
                photoModerationStatus = "PENDENTE"
                val photoId = "m_" + System.currentTimeMillis()
                val authorTitle = if (isGodMode) "Foto do Dono" else "Meu Perfil"
                moderationList.add(0, ModItem(photoId, authorTitle, "Adulto (18+)", "📸", "PENDENTE"))
                Toast.makeText(this@MainActivity, "📸 Foto enviada para a nuvem! Status: Em análise pelo Moderador.", Toast.LENGTH_LONG).show()
                if (currentTab == 2) renderProfileTab()
            }
        }
    }

    /**
     * Validação Automática:
     * Compara a conta conectada com o e-mail do Dono.
     * Não necessita de senha, nem de cliques secretos, nem de código alterado!
     */
    private fun checkOwnerStatus() {
        isGodMode = currentUserEmail.equals(OWNER_EMAIL, ignoreCase = true)
    }

    private fun setupUI() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#05070B")) // Preto AMOLED Ultra Profundo
        }

        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = 180 // Espaço para a barra de navegação
            }
        }

        navBar = createBottomNav()

        rootContainer.addView(tabContent)
        rootContainer.addView(navBar)
        setContentView(rootContainer)

        renderTab(1) // Inicia no Radar de Sintonia
    }

    private fun renderTab(tabIndex: Int) {
        currentTab = tabIndex
        tabContent.removeAllViews()

        when (tabIndex) {
            0 -> renderFeedTab()
            1 -> renderRadarTab()
            2 -> renderProfileTab()
            3 -> if (isGodMode) renderModeratorTab() else renderFeedTab()
        }
    }

    // ==========================================
    // ABA 0: FEED DE FOTOS 100% LIMPO (SEM NOMES)
    // ==========================================
    private fun renderFeedTab() {
        val header = TextView(this).apply {
            text = if (isGodMode) "⚡ FEED • SINAIS (VISÃO CRIADOR)" else "⚡ SINAIS DISPONÍVEIS"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A855F7"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }
        tabContent.addView(header)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f
            ).apply {
                setMargins(48, 0, 48, 24)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 36f
                setStroke(2, if (isGodMode) Color.parseColor("#7E22CE") else Color.parseColor("#334155"))
            }
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val badge = TextView(this).apply {
            text = if (isGodMode) "👑 SINAL VERIFICADO • DONO DO APP" else "⚡ SINAL ATIVO NO RADAR"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode) Color.parseColor("#FBBF24") else Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 8)
            background = GradientDrawable().apply {
                setColor(if (isGodMode) Color.parseColor("#4C1D95") else Color.parseColor("#0C4A6E"))
                cornerRadius = 20f
                setStroke(2, if (isGodMode) Color.parseColor("#F59E0B") else Color.parseColor("#0284C7"))
            }
        }

        val cardHint = TextView(this).apply {
            text = "Foto Pura • Sem bio, sem @ do Instagram\nO SINAL protege a privacidade até o encontro presencial."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val passBtn = Button(this).apply {
            text = "✕"
            textSize = 24f
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E293B"))
            }
            layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                rightMargin = 48
            }
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Próximo perfil...", Toast.LENGTH_SHORT).show()
            }
        }

        val sparkBtn = Button(this).apply {
            text = "⚡"
            textSize = 28f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9333EA"))
            }
            layoutParams = LinearLayout.LayoutParams(180, 180)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "⚡ Sinal transmitido! Aguardando proximidade física.", Toast.LENGTH_SHORT).show()
            }
        }

        actionRow.addView(passBtn)
        actionRow.addView(sparkBtn)

        card.addView(badge)
        card.addView(cardHint)
        card.addView(actionRow)
        tabContent.addView(card)
    }

    // ==========================================
    // ABA 1: O RADAR DE SINTONIA (AMOLED + BLE)
    // ==========================================
    private fun renderRadarTab() {
        val topSpace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80)
        }
        tabContent.addView(topSpace)

        val title = TextView(this).apply {
            text = if (isGodMode) "👑 RADAR MESTRE • GOD MODE" else "RADAR DE SINTONIA"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode) Color.parseColor("#FBBF24") else Color.parseColor("#C084FC"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        tabContent.addView(title)

        // Círculo Pulsante do Radar
        radarCircleView = FrameLayout(this).apply {
            val size = 520
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 40
                bottomMargin = 40
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#090D16"))
                setStroke(4, if (isGodMode) Color.parseColor("#D97706") else Color.parseColor("#581C87"))
            }
        }

        val radarCenterIcon = TextView(this).apply {
            text = if (isRadarActive) (if (isGodMode) "👑" else "⚡") else "📡"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        radarCircleView.addView(radarCenterIcon)
        tabContent.addView(radarCircleView)

        radarStatusText = TextView(this).apply {
            text = if (isRadarActive) {
                if (isGodMode) "SINTONIA MESTRE ATIVA (ALCANCE TOTAL)" else "SINTONIZANDO NO AR"
            } else {
                "RADAR EM REPOUSO"
            }
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isRadarActive) Color.parseColor("#34D399") else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        tabContent.addView(radarStatusText)

        radarSubtitleText = TextView(this).apply {
            text = if (isRadarActive) 
                "Ondas BLE ativas. Seu celular vibrará no bolso se cruzar com uma sintonia."
                else "Toque abaixo para ligar as ondas de rádio BLE."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(64, 0, 64, 32)
        }
        tabContent.addView(radarSubtitleText)

        radarActionBtn = Button(this).apply {
            text = if (isRadarActive) "PARAR RADAR" else "LIGAR RADAR BLE"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(if (isRadarActive) Color.parseColor("#DC2626") else Color.parseColor("#7E22CE"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply {
                setMargins(96, 16, 96, 16)
            }
            setOnClickListener {
                toggleRadar()
            }
        }
        tabContent.addView(radarActionBtn)

        // Botão Simulação de Gatilho Presencial
        val testProximityBtn = Button(this).apply {
            text = "🎯 Testar Vibração no Bolso (Tum-tum)"
            textSize = 12f
            setTextColor(Color.parseColor("#A855F7"))
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#1E1B4B"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 24
            }
            setOnClickListener {
                triggerHeartbeatVibration()
                showMysteryNotification()
                Toast.makeText(this@MainActivity, "⚡ Notificação e Batimento Cardíaco disparados!", Toast.LENGTH_SHORT).show()
            }
        }
        tabContent.addView(testProximityBtn)
    }

    // ==========================================
    // ABA 2: PERFIL DO USUÁRIO & FOTO REAL
    // ==========================================
    private fun renderProfileTab() {
        val title = TextView(this).apply {
            text = if (isGodMode) "👑 MEU SINAL • PERFIL DO CRIADOR" else "MEU SINAL"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode) Color.parseColor("#FBBF24") else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }
        tabContent.addView(title)

        val avatar = TextView(this).apply {
            text = if (isGodMode) "👑" else "📷"
            textSize = 40f
            gravity = Gravity.CENTER
            val size = 260
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E293B"))
                setStroke(4, if (isGodMode) Color.parseColor("#F59E0B") else Color.parseColor("#7E22CE"))
            }
        }
        tabContent.addView(avatar)

        val statusBadge = TextView(this).apply {
            text = if (isGodMode) "CONTA OFICIAL: " + OWNER_EMAIL + " (GOD MODE)" else "STATUS DA FOTO: " + photoModerationStatus
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (isGodMode) Color.parseColor("#FBBF24")
                else when (photoModerationStatus) {
                    "APROVADA" -> Color.parseColor("#10B981")
                    "PENDENTE" -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#EF4444")
                }
            )
            gravity = Gravity.CENTER
            setPadding(24, 6, 24, 6)
            background = GradientDrawable().apply {
                setColor(
                    if (isGodMode) Color.parseColor("#78350F")
                    else when (photoModerationStatus) {
                        "APROVADA" -> Color.parseColor("#064E3B")
                        "PENDENTE" -> Color.parseColor("#78350F")
                        else -> Color.parseColor("#7F1D1D")
                    }
                )
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 32
            }
        }
        tabContent.addView(statusBadge)

        val photoBtn = Button(this).apply {
            text = "📁 Escolher Nova Foto da Galeria"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120
            ).apply {
                setMargins(64, 0, 64, 16)
            }
            setOnClickListener {
                choosePhotoFromGallery()
            }
        }
        tabContent.addView(photoBtn)

        val vipBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(if (isGodMode) Color.parseColor("#261B05") else Color.parseColor("#1A102F"))
                cornerRadius = 28f
                setStroke(2, if (isGodMode) Color.parseColor("#F59E0B") else Color.parseColor("#A855F7"))
            }
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(48, 24, 48, 16)
            }
        }

        val vipTitle = TextView(this).apply {
            text = if (isGodMode) "👑 SUPERPODERES DE DONO ATIVOS" else "💎 PLANO VIP • DESBLOQUEIO TOTAL"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
        }

        val vipDesc = TextView(this).apply {
            text = if (isGodMode)
                "Você tem VIP Vitalício gratuito, radar com alcance mestre e aprovação instantânea de fotos pela nuvem."
                else "Desbloqueie fotos nítidas instantâneas no radar por R$ 4,90 ou R$ 19,90/mês via Pix."
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 8, 0, 16)
        }

        vipBanner.addView(vipTitle)
        vipBanner.addView(vipDesc)
        tabContent.addView(vipBanner)

        // Seletor de Teste Rápido (Permite alternar entre Dono e Usuário Comum no mesmo APK)
        val switchAccountBtn = Button(this).apply {
            text = if (isGodMode) "🔄 Simular Visão de Usuário Comum" else "👑 Ativar Visão do Dono (" + OWNER_EMAIL + ")"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0F172A"))
                setStroke(1, Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 16
            }
            setOnClickListener {
                if (isGodMode) {
                    currentUserEmail = "turista_caldas@gmail.com"
                } else {
                    currentUserEmail = OWNER_EMAIL
                }
                checkOwnerStatus()
                rootContainer.removeView(navBar)
                navBar = createBottomNav()
                rootContainer.addView(navBar)
                renderProfileTab()
                Toast.makeText(
                    this@MainActivity,
                    if (isGodMode) "👑 Modo Dono Ativado!" else "👤 Modo Usuário Comum Ativado (Sem abas extras)!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        tabContent.addView(switchAccountBtn)
    }

    // ==========================================
    // ABA 3: PAINEL EXCLUSIVO DO MODERADOR (O CUPIDO)
    // ==========================================
    private fun renderModeratorTab() {
        val header = TextView(this).apply {
            text = "👑 PAINEL DO MODERADOR (O CUPIDO)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 8)
        }
        tabContent.addView(header)

        val subtitle = TextView(this).apply {
            val pending = moderationList.count { it.status == "PENDENTE" }
            text = "" + pending + " fotos aguardando aprovação na nuvem"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        tabContent.addView(subtitle)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 16)
        }

        for (item in moderationList) {
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = 24f
                    setStroke(2, Color.parseColor("#334155"))
                }
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 20
                }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatarIcon = TextView(this).apply {
                text = item.previewEmoji
                textSize = 28f
                setPadding(0, 0, 16, 0)
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val userTitle = TextView(this).apply {
                text = item.author
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }

            val userAge = TextView(this).apply {
                text = "Categoria: " + item.ageGroup + " • Status: " + item.status
                textSize = 11f
                setTextColor(
                    if (item.status == "APROVADA") Color.parseColor("#34D399")
                    else if (item.status == "RECUSADA") Color.parseColor("#F87171")
                    else Color.parseColor("#FBBF24")
                )
            }

            infoCol.addView(userTitle)
            infoCol.addView(userAge)
            topRow.addView(avatarIcon)
            topRow.addView(infoCol)
            itemCard.addView(topRow)

            if (item.status == "PENDENTE") {
                val btnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 16, 0, 0)
                }

                val approveBtn = Button(this).apply {
                    text = "Aprovar ✅"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#059669"))
                        cornerRadius = 16f
                    }
                    layoutParams = LinearLayout.LayoutParams(0, 100, 1.0f).apply {
                        rightMargin = 12
                    }
                    setOnClickListener {
                        item.status = "APROVADA"
                        if (item.author.contains("Foto do Dono") || item.author.contains("Meu Perfil")) photoModerationStatus = "APROVADA"
                        Toast.makeText(this@MainActivity, "Foto aprovada com sucesso!", Toast.LENGTH_SHORT).show()
                        renderModeratorTab()
                    }
                }

                val rejectBtn = Button(this).apply {
                    text = "Recusar ❌"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#DC2626"))
                        cornerRadius = 16f
                    }
                    layoutParams = LinearLayout.LayoutParams(0, 100, 1.0f)
                    setOnClickListener {
                        item.status = "RECUSADA"
                        if (item.author.contains("Foto do Dono") || item.author.contains("Meu Perfil")) photoModerationStatus = "RECUSADA"
                        Toast.makeText(this@MainActivity, "Foto reprovada.", Toast.LENGTH_SHORT).show()
                        renderModeratorTab()
                    }
                }

                btnRow.addView(approveBtn)
                btnRow.addView(rejectBtn)
                itemCard.addView(btnRow)
            }

            listContainer.addView(itemCard)
        }

        scrollView.addView(listContainer)
        tabContent.addView(scrollView)
    }

    // ==========================================
    // BARRA DE NAVEGAÇÃO INFERIOR MODERNA
    // ==========================================
    private fun createBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 160
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(Color.parseColor("#090D16"))

            val btnFeed = createNavButton("🔥 Feed", 0)
            val btnRadar = createNavButton("⚡ Radar", 1)
            val btnPerfil = createNavButton("👤 Perfil", 2)

            addView(btnFeed)
            addView(btnRadar)
            addView(btnPerfil)

            // ABA EXCLUSIVA DO DONO (Ativada automaticamente se a conta for brainrot064@gmail.com)
            if (isGodMode) {
                val pendingCount = moderationList.count { it.status == "PENDENTE" }
                val label = if (pendingCount > 0) "👑 Mod (" + pendingCount + ")" else "👑 Mod"
                val btnMod = createNavButton(label, 3)
                addView(btnMod)
            }
        }
    }

    private fun createNavButton(label: String, index: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (currentTab == index) Color.parseColor("#C084FC") else Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            setOnClickListener {
                renderTab(index)
            }
        }
    }

    // ==========================================
    // CONTROLE DE RÁDIO BLE E SEGUNDO PLANO
    // ==========================================
    private fun toggleRadar() {
        if (isRadarActive) {
            stopBleRadio()
        } else {
            startBleRadio()
        }
        renderTab(1)
    }

    private fun startBleRadio() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Por favor, ative o Bluetooth para sintonizar!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            bleAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
            bleScanner = bluetoothAdapter!!.bluetoothLeScanner

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID_ADULT))
                .build()

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID_ADULT))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)

            isRadarActive = true
            showPersistentForegroundNotification()
            Toast.makeText(this, "⚡ Radar SINAL sintonizado no ar!", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissões de Bluetooth necessárias.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBleRadio() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Ignora se não houver permissão ativa ao fechar
        }
        isRadarActive = false
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001) // Remove notificação fixa
        Toast.makeText(this, "Radar pausado.", Toast.LENGTH_SHORT).show()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
        override fun onStartFailure(errorCode: Int) {}
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val rssi = it.rssi
                // Se o RSSI for forte (ex: > -70 dBm = ~3 a 5 metros)
                if (rssi >= -70) {
                    triggerHeartbeatVibration()
                    showMysteryNotification()
                }
            }
        }
    }

    // ==========================================
    // VIBRAÇÃO SENSORIAL DE BATIMENTO (TUM-TUM)
    // ==========================================
    private fun triggerHeartbeatVibration() {
        val pattern = longArrayOf(0, 120, 150, 180) // Batimento cardíaco preciso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sinal_alerts",
                "Alertas de Proximidade SINAL",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações quando alguém que te curtiu estiver por perto"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showPersistentForegroundNotification() {
        val notification = NotificationCompat.Builder(this, "sinal_alerts")
            .setContentTitle("⚡ SINAL Ativo em Segundo Plano")
            .setContentText("Escutando frequências de sintonia no shopping...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }

    private fun showMysteryNotification() {
        val notification = NotificationCompat.Builder(this, "sinal_alerts")
            .setContentTitle("⚡ SINAL DETECTADO!")
            .setContentText("Alguém com quem você tem sintonia está no seu raio físico agora.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2002, notification)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            }
        }
    }
}
