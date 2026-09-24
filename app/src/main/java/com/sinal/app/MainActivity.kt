package com.sinal.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * SINAL - Love Alarm da Vida Real
 * APK Único para Google Play Store:
 * - Se a conta for brainrot064@gmail.com: Ativa God Mode automaticamente (Aba Moderação, VIP Dono, Radar Mestre).
 * - Qualquer outro usuário: App comercial 100% puro (Feed, Radar e Upload), sem traços de administração.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // E-mail oficial do Criador / Dono do SINAL
        const val OWNER_EMAIL = "brainrot064@gmail.com"
        private val SERVICE_UUID_ADULT = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isRadarActive = false
    private var currentTab = 1 // 0: Feed, 1: Radar, 2: Perfil, 3: Moderação (automático)

    // Identificação do Usuário e God Mode Automático
    private var currentUserEmail: String = OWNER_EMAIL
    private var isGodMode: Boolean = true

    // Firebase Nuvem
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var feedListener: ListenerRegistration? = null
    private var modListener: ListenerRegistration? = null

    // Modelo de Fotos em Nuvem
    data class SinalPhoto(
        val id: String,
        val photoBase64: String?,
        val name: String,
        val age: Int,
        var status: String,
        val isCupid: Boolean = false
    )

    private val approvedSignals = mutableListOf<SinalPhoto>()
    private val pendingModSignals = mutableListOf<SinalPhoto>()
    private var currentFeedIndex = 0

    // Estado da Minha Própria Foto
    private var myPhotoBitmap: Bitmap? = null
    private var myPhotoStatus = "SEM FOTO"
    private val myUserId by lazy {
        val prefs = getSharedPreferences("sinal_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("user_id", null)
        if (id == null) {
            id = if (isGodMode) "cupido_dono_oficial" else "usr_" + UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("user_id", id).apply()
        }
        id
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var tabContent: LinearLayout
    private lateinit var navBar: LinearLayout

    // Seletor Nativo de Foto da Galeria
    private val pickPhotoLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadPhotoToCloud(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 1. Verificação Automática de God Mode:
        // Se a conta for brainrot064@gmail.com, isGodMode vira TRUE sem precisar de senhas ou cliques manuais
        checkOwnerStatus()

        createNotificationChannel()
        setupUI()
        checkPermissions()
        startCloudSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedListener?.remove()
        modListener?.remove()
    }

    /**
     * Validação Automática:
     * Compara o login com o e-mail do Dono.
     * Na Play Store, os turistas normais caem em isGodMode = false.
     */
    private fun checkOwnerStatus() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser?.email != null) {
            currentUserEmail = firebaseUser.email!!
        }
        isGodMode = currentUserEmail.equals(OWNER_EMAIL, ignoreCase = true)
        myPhotoStatus = if (isGodMode) "APROVADO (CRIADOR)" else "SEM FOTO"
    }

    // ==========================================================
    // SINCRONIZAÇÃO EM TEMPO REAL COM A NUVEM
    // ==========================================================
    private fun startCloudSync() {
        // 1. Escuta fotos aprovadas (para o Feed de todos os celulares)
        feedListener = firestore.collection("profiles")
            .whereEqualTo("status", "APROVADO")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                approvedSignals.clear()
                for (doc in snapshot.documents) {
                    approvedSignals.add(
                        SinalPhoto(
                            id = doc.id,
                            photoBase64 = doc.getString("photoUrl"),
                            name = doc.getString("name") ?: "Anônimo",
                            age = doc.getLong("age")?.toInt() ?: 20,
                            status = "APROVADO",
                            isCupid = doc.getBoolean("isCupid") ?: false
                        )
                    )
                }
                if (currentTab == 0) renderFeedTab()
            }

        // 2. Se for você (God Mode), escuta a fila de fotos que os usuários enviarem!
        if (isGodMode) {
            modListener = firestore.collection("profiles")
                .whereEqualTo("status", "PENDENTE")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener
                    pendingModSignals.clear()
                    for (doc in snapshot.documents) {
                        pendingModSignals.add(
                            SinalPhoto(
                                id = doc.id,
                                photoBase64 = doc.getString("photoUrl"),
                                name = doc.getString("name") ?: "Turista",
                                age = doc.getLong("age")?.toInt() ?: 20,
                                status = "PENDENTE"
                            )
                        )
                    }
                    if (currentTab == 3) renderModerationTab()
                    updateBottomNavBadges()
                }
        }
    }

    // ==========================================================
    // UPLOAD DA FOTO (Celular -> Firestore)
    // ==========================================================
    private fun uploadPhotoToCloud(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(original, 480, 640, true)
            myPhotoBitmap = scaled
            myPhotoStatus = if (isGodMode) "APROVADO (CRIADOR)" else "PENDENTE"

            val byteStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, byteStream)
            val base64Data = "data:image/jpeg;base64," + Base64.encodeToString(byteStream.toByteArray(), Base64.NO_WRAP)

            val profileData = hashMapOf(
                "id" to myUserId,
                "name" to if (isGodMode) "⚡ O CUPIDO" else "",
                "age" to 22,
                "photoUrl" to base64Data,
                // O dono é auto-aprovado; turistas comuns entram como PENDENTE na sua moderação
                "status" to if (isGodMode) "APROVADO" else "PENDENTE",
                "isCupid" to isGodMode,
                "bleToken" to "0x" + UUID.randomUUID().toString().substring(0, 6).uppercase(),
                "ageGroup" to "ADULT",
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("profiles").document(myUserId).set(profileData)
                .addOnSuccessListener {
                    val msg = if (isGodMode) "👑 Foto do Dono atualizada e APROVADA no ar!" else "📸 Foto enviada! Em análise pelo Cupido."
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    if (currentTab == 2) renderProfileTab()
                }

            if (currentTab == 2) renderProfileTab()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================================
    // MODERAÇÃO EM 1 TOQUE (EXCLUSIVA DO SEU CELULAR)
    // ==========================================================
    private fun approvePhoto(photoId: String) {
        firestore.collection("profiles").document(photoId)
            .update("status", "APROVADO")
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Foto aprovada! Já está no Feed de todos.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectPhoto(photoId: String) {
        firestore.collection("profiles").document(photoId)
            .update("status", "RECUSADO")
            .addOnSuccessListener {
                Toast.makeText(this, "❌ Foto reprovada e removida.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupUI() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#05070B"))
        }

        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = 170 }
        }

        navBar = createBottomNav()

        rootContainer.addView(tabContent)
        rootContainer.addView(navBar)
        setContentView(rootContainer)

        renderTab(1) // Inicia no Radar
    }

    private fun renderTab(tabIndex: Int) {
        currentTab = tabIndex
        tabContent.removeAllViews()

        when (tabIndex) {
            0 -> renderFeedTab()
            1 -> renderRadarTab()
            2 -> renderProfileTab()
            3 -> if (isGodMode) renderModerationTab() else renderFeedTab()
        }
        updateBottomNavSelection()
    }

    // ==========================================
    // ABA 0: FEED DE FOTOS APROVADAS
    // ==========================================
    private fun renderFeedTab() {
        val header = TextView(this).apply {
            text = if (isGodMode) "⚡ FEED • SINAIS (VISÃO CRIADOR)" else "⚡ SINAIS DISPONÍVEIS NO SHOPPING"
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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
            ).apply { setMargins(48, 0, 48, 24) }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 36f
                setStroke(2, if (isGodMode) Color.parseColor("#7E22CE") else Color.parseColor("#334155"))
            }
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        if (approvedSignals.isEmpty()) {
            val emptyNotice = TextView(this).apply {
                text = "📡 Procurando sinais no ar...\n\nAssim que o Cupido aprovar novos usuários, as fotos aparecerão aqui instantaneamente."
                textSize = 14f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(32, 64, 32, 64)
            }
            card.addView(emptyNotice)
        } else {
            val signal = approvedSignals[currentFeedIndex % approvedSignals.size]

            val photoView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
                ).apply { bottomMargin = 20 }
                scaleType = ImageView.ScaleType.CENTER_CROP

                if (signal.photoBase64 != null && signal.photoBase64.contains(",")) {
                    try {
                        val pure = signal.photoBase64.substringAfter(",")
                        val bytes = Base64.decode(pure, Base64.DEFAULT)
                        setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    } catch (e: Exception) {
                        setBackgroundColor(Color.parseColor("#1E293B"))
                    }
                } else {
                    setBackgroundColor(Color.parseColor("#1E293B"))
                }
            }
            card.addView(photoView)

            val badge = TextView(this).apply {
                text = if (signal.isCupid) "👑 O CUPIDO • DONO DO APP" else "⚡ SINAL ATIVO NO SHOPPING"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (signal.isCupid) Color.parseColor("#FBBF24") else Color.parseColor("#38BDF8"))
                gravity = Gravity.CENTER
                setPadding(16, 4, 16, 4)
                background = GradientDrawable().apply {
                    setColor(if (signal.isCupid) Color.parseColor("#78350F") else Color.parseColor("#0C4A6E"))
                    cornerRadius = 16f
                }
            }
            card.addView(badge)

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }

            val passBtn = Button(this).apply {
                text = "✕"
                textSize = 22f
                setTextColor(Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E293B"))
                }
                layoutParams = LinearLayout.LayoutParams(140, 140).apply { rightMargin = 40 }
                setOnClickListener {
                    currentFeedIndex++
                    renderFeedTab()
                }
            }

            val sparkBtn = Button(this).apply {
                text = "⚡"
                textSize = 26f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#9333EA"))
                }
                layoutParams = LinearLayout.LayoutParams(160, 160)
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "⚡ Sintonia enviada! Se cruzarem caminho, o radar vai vibrar.", Toast.LENGTH_SHORT).show()
                    currentFeedIndex++
                    renderFeedTab()
                }
            }

            actionRow.addView(passBtn)
            actionRow.addView(sparkBtn)
            card.addView(actionRow)
        }

        tabContent.addView(card)
    }

    // ==========================================
    // ABA 1: RADAR DE SINTONIA
    // ==========================================
    private fun renderRadarTab() {
        val title = TextView(this).apply {
            text = if (isGodMode) "👑 RADAR MESTRE • GOD MODE" else "RADAR DE SINTONIA PRESENCIAL"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode) Color.parseColor("#FBBF24") else Color.parseColor("#C084FC"))
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 16)
        }
        tabContent.addView(title)

        val radarCircleView = FrameLayout(this).apply {
            val size = 500
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 30
                bottomMargin = 30
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#090D16"))
                setStroke(4, if (isGodMode) Color.parseColor("#F59E0B") else Color.parseColor("#581C87"))
            }
        }

        val centerIcon = TextView(this).apply {
            text = if (isRadarActive) (if (isGodMode) "👑" else "⚡") else "📡"
            textSize = 46f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        radarCircleView.addView(centerIcon)
        tabContent.addView(radarCircleView)

        val statusText = TextView(this).apply {
            text = if (isRadarActive) {
                if (isGodMode) "SINTONIA MESTRE ATIVA (ALCANCE MÁXIMO)" else "SINTONIZANDO NO SHOPPING"
            } else {
                "RADAR EM REPOUSO"
            }
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isRadarActive) Color.parseColor("#34D399") else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        tabContent.addView(statusText)

        val actionBtn = Button(this).apply {
            text = if (isRadarActive) "PARAR RADAR" else "LIGAR RADAR BLE"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(if (isRadarActive) Color.parseColor("#DC2626") else Color.parseColor("#7E22CE"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 130
            ).apply { setMargins(90, 20, 90, 16) }
            setOnClickListener {
                isRadarActive = !isRadarActive
                if (isRadarActive) {
                    showPersistentForegroundNotification()
                    Toast.makeText(this@MainActivity, "⚡ Radar Ativo! Celular vibrará no bolso ao cruzar sintonia.", Toast.LENGTH_SHORT).show()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(1001)
                }
                renderTab(1)
            }
        }
        tabContent.addView(actionBtn)

        val testVibeBtn = Button(this).apply {
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
                topMargin = 16
            }
            setOnClickListener {
                triggerHeartbeatVibration()
                showMysteryNotification()
            }
        }
        tabContent.addView(testVibeBtn)
    }

    // ==========================================
    // ABA 2: PERFIL DO USUÁRIO
    // ==========================================
    private fun renderProfileTab() {
        val title = TextView(this).apply {
            text = if (isGodMode) "👑 MEU SINAL (PERFIL DO CRIADOR)" else "MEU SINAL"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode) Color.parseColor("#FBBF24") else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }
        tabContent.addView(title)

        val avatarContainer = FrameLayout(this).apply {
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

        val avatarView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            if (myPhotoBitmap != null) {
                setImageBitmap(myPhotoBitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
        avatarContainer.addView(avatarView)
        tabContent.addView(avatarContainer)

        val statusBadge = TextView(this).apply {
            text = if (isGodMode) "CONTA OFICIAL: $OWNER_EMAIL (GOD MODE)" else "STATUS DA SUA FOTO: $myPhotoStatus"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGodMode || myPhotoStatus.contains("APROV")) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER
            setPadding(24, 6, 24, 6)
            background = GradientDrawable().apply {
                setColor(if (isGodMode) Color.parseColor("#064E3B") else Color.parseColor("#78350F"))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        }
        tabContent.addView(statusBadge)

        val uploadBtn = Button(this).apply {
            text = if (isGodMode) "📸 Trocar Foto Oficial do Cupido" else "📁 Escolher Minha Foto da Galeria"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(if (isGodMode) Color.parseColor("#D97706") else Color.parseColor("#7E22CE"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120
            ).apply { setMargins(64, 0, 64, 16) }
            setOnClickListener {
                pickPhotoLauncher.launch("image/*")
            }
        }
        tabContent.addView(uploadBtn)

        // Seletor de Simulação para Teste (Permite ver exatamente como um turista vê)
        val simulateBtn = Button(this).apply {
            text = if (isGodMode) "🔄 Simular Visão de Usuário Comum" else "👑 Ativar Visão do Criador ($OWNER_EMAIL)"
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
                currentUserEmail = if (isGodMode) "turista_caldas@gmail.com" else OWNER_EMAIL
                checkOwnerStatus()
                rootContainer.removeView(navBar)
                navBar = createBottomNav()
                rootContainer.addView(navBar)
                renderProfileTab()
                Toast.makeText(
                    this@MainActivity,
                    if (isGodMode) "👑 Modo Criador Ativado!" else "👤 Modo Turista Ativado (Aba Mod ocultada)!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        tabContent.addView(simulateBtn)
    }

    // ==========================================================
    // ABA 3: SALA DE TRIAGEM (SÓ APARECE NO CELULAR DO DONO)
    // ==========================================================
    private fun renderModerationTab() {
        val title = TextView(this).apply {
            text = "👑 SALA DE TRIAGEM DO DONO"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 8)
        }
        tabContent.addView(title)

        val subtitle = TextView(this).apply {
            text = "${pendingModSignals.size} foto(s) de turistas aguardando sua bênção"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        tabContent.addView(subtitle)

        if (pendingModSignals.isEmpty()) {
            val emptyBox = TextView(this).apply {
                text = "✨ Nenhuma foto pendente no momento!\n\nQuando alguém no shopping subir uma foto pelo app, ela vai pipocar aqui na hora para você aprovar."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(48, 64, 48, 64)
            }
            tabContent.addView(emptyBox)
            return
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (item in pendingModSignals) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = 24f
                    setStroke(2, Color.parseColor("#F59E0B"))
                }
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
            }

            if (item.photoBase64 != null && item.photoBase64.contains(",")) {
                val photo = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 400
                    ).apply { bottomMargin = 16 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    try {
                        val pure = item.photoBase64.substringAfter(",")
                        val bytes = Base64.decode(pure, Base64.DEFAULT)
                        setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    } catch (e: Exception) {}
                }
                card.addView(photo)
            }

            val cardTitle = TextView(this).apply {
                text = "${item.name} (${item.age} anos)"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
            card.addView(cardTitle)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 0)
            }

            val approveBtn = Button(this).apply {
                text = "Aprovar ✅"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#059669"))
                    cornerRadius = 16f
                }
                layoutParams = LinearLayout.LayoutParams(0, 110, 1.0f).apply { rightMargin = 12 }
                setOnClickListener {
                    approvePhoto(item.id)
                }
            }

            val rejectBtn = Button(this).apply {
                text = "Recusar ❌"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DC2626"))
                    cornerRadius = 16f
                }
                layoutParams = LinearLayout.LayoutParams(0, 110, 1.0f)
                setOnClickListener {
                    rejectPhoto(item.id)
                }
            }

            btnRow.addView(approveBtn)
            btnRow.addView(rejectBtn)
            card.addView(btnRow)
            listContainer.addView(card)
        }

        scrollView.addView(listContainer)
        tabContent.addView(scrollView)
    }

    // ==========================================
    // BARRA INFERIOR DE NAVEGAÇÃO
    // ==========================================
    private fun createBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 160
            ).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(Color.parseColor("#090D16"))

            addView(createNavButton("🔥 Feed", 0))
            addView(createNavButton("⚡ Radar", 1))
            addView(createNavButton("👤 Perfil", 2))

            // ABA EXCLUSIVA DO DONO (Invisível para qualquer usuário da Play Store)
            if (isGodMode) {
                val count = pendingModSignals.size
                val label = if (count > 0) "👑 Mod ($count)" else "👑 Mod"
                addView(createNavButton(label, 3))
            }
        }
    }

    private fun createNavButton(label: String, index: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (currentTab == index) Color.parseColor("#C084FC") else Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            setOnClickListener { renderTab(index) }
        }
    }

    private fun updateBottomNavSelection() {
        for (i in 0 until navBar.childCount) {
            val v = navBar.getChildAt(i) as? TextView
            v?.setTextColor(if (i == currentTab) Color.parseColor("#C084FC") else Color.parseColor("#64748B"))
        }
    }

    private fun updateBottomNavBadges() {
        if (isGodMode && navBar.childCount >= 4) {
            val modBtn = navBar.getChildAt(3) as? TextView
            val count = pendingModSignals.size
            modBtn?.text = if (count > 0) "👑 Mod ($count)" else "👑 Mod"
        }
    }

    private fun triggerHeartbeatVibration() {
        val pattern = longArrayOf(0, 120, 150, 180)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(pattern, -1)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("sinal_alerts", "Alertas SINAL", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun showPersistentForegroundNotification() {
        val notif = NotificationCompat.Builder(this, "sinal_alerts")
            .setContentTitle("⚡ SINAL Ativo em Segundo Plano")
            .setContentText("Escutando frequências no shopping...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notif)
    }

    private fun showMysteryNotification() {
        val notif = NotificationCompat.Builder(this, "sinal_alerts")
            .setContentTitle("⚡ SINAL DETECTADO!")
            .setContentText("Alguém com quem você tem sintonia está no seu raio físico agora.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2002, notif)
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
