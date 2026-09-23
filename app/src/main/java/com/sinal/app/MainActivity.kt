package com.sinal.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private val CHANNEL_ID = "sinal_radar_channel"
    private val NOTIF_ID = 1001

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null

    // Contêineres das 3 Abas
    private lateinit var tabFeed: FrameLayout
    private lateinit var tabRadar: LinearLayout
    private lateinit var tabProfile: ScrollView

    // Botões da Barra Inferior
    private lateinit var btnNavFeed: TextView
    private lateinit var btnNavRadar: TextView
    private lateinit var btnNavProfile: TextView

    // Elementos do Radar
    private lateinit var statusBadge: TextView
    private lateinit var radarPulseRing: FrameLayout
    private lateinit var radarCenterCircle: LinearLayout
    private lateinit var radarDistanceText: TextView
    private lateinit var radarPresenceSubtext: TextView
    private lateinit var btnToggleRadar: Button
    private lateinit var signalsListContainer: LinearLayout
    private lateinit var expandedPhotoOverlay: FrameLayout
    private lateinit var expandedPhotoCard: LinearLayout
    private lateinit var expandedPhotoAvatar: TextView
    private lateinit var expandedPhotoInfo: TextView

    private var pulseAnimator: ObjectAnimator? = null
    private var isRadarActive = false
    private var isPremium = true // Modo de demonstração com recurso Premium liberado

    // Dados Mockados para o Feed de Fotos Vertical (Estilo TikTok)
    private val mockFeedProfiles = listOf(
        Pair("Mariana", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500"),
        Pair("Camila", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500"),
        Pair("Juliana", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500"),
        Pair("Beatriz", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500")
    )
    private var currentFeedIndex = 0
    private lateinit var feedPhotoView: FrameLayout
    private lateinit var feedNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Layout Raiz em Camadas
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#05070B"))
        }

        // Conteúdo Principal com Espaço para Navegação Inferior
        val mainContent = FrameLayout(this).apply {
            val p = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            p.setMargins(0, 0, 0, 160) // Reserva espaço da barra inferior
            layoutParams = p
        }

        // Criação das 3 Telas
        tabFeed = buildFeedTab()
        tabRadar = buildRadarTab()
        tabProfile = buildProfileTab()

        mainContent.addView(tabFeed)
        mainContent.addView(tabRadar)
        mainContent.addView(tabProfile)

        // Overlay de Expansão Fluida para o Usuário Premium
        expandedPhotoOverlay = buildExpandedPhotoOverlay()

        // Barra de Navegação Inferior Estilo Tinder / TikTok
        val bottomNav = buildBottomNav()

        root.addView(mainContent)
        root.addView(expandedPhotoOverlay)
        root.addView(bottomNav)

        setContentView(root)

        // Abre na Aba Principal do Feed
        switchTab(0)
    }

    // ==========================================
    // ABA 1: FEED VERTICAL (Deslize de Fotos + Coração / X)
    // ==========================================
    private fun buildFeedTab(): FrameLayout {
        val frame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(32, 48, 32, 24)
        }

        feedPhotoView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111827"))
                cornerRadius = 64f
                setStroke(3, Color.parseColor("#1F2937"))
            }
        }

        // Avatar / Indicador Visual do Perfil Atual
        val photoPlaceholder = TextView(this).apply {
            text = "📷"
            textSize = 70f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        feedNameText = TextView(this).apply {
            text = "Mariana"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(48, 0, 0, 200)
            val p = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = Gravity.BOTTOM or Gravity.START
            layoutParams = p
        }

        val hintText = TextView(this).apply {
            text = "⚡ Apenas uma foto. Se houver conexão mútua no mesmo local, o SINAL avisa."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(48, 0, 48, 140)
            val p = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = Gravity.BOTTOM or Gravity.START
            layoutParams = p
        }

        // Botões de Ação Redondos: X na Esquerda, Coração na Direita
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val p = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = Gravity.BOTTOM
            p.setMargins(0, 0, 0, 24)
            layoutParams = p
        }

        val btnDislike = TextView(this).apply {
            text = "✕"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F43F5E"))
            gravity = Gravity.CENTER
            val size = 150
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 48, 0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E1B4B"))
                setStroke(3, Color.parseColor("#E11D48"))
            }
            setOnClickListener { nextFeedProfile(liked = false) }
        }

        val btnLike = TextView(this).apply {
            text = "⚡"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER
            val size = 150
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(48, 0, 0, 0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#064E3B"))
                setStroke(3, Color.parseColor("#10B981"))
            }
            setOnClickListener { nextFeedProfile(liked = true) }
        }

        actionsLayout.addView(btnDislike)
        actionsLayout.addView(btnLike)

        feedPhotoView.addView(photoPlaceholder)
        feedPhotoView.addView(feedNameText)
        feedPhotoView.addView(hintText)
        feedPhotoView.addView(actionsLayout)

        frame.addView(feedPhotoView)
        return frame
    }

    private fun nextFeedProfile(liked: Boolean) {
        if (liked) {
            Toast.makeText(this, "⚡ Sinal enviado em silêncio.", Toast.LENGTH_SHORT).show()
        }
        currentFeedIndex = (currentFeedIndex + 1) % mockFeedProfiles.size
        feedNameText.text = mockFeedProfiles[currentFeedIndex].first
    }

    // ==========================================
    // ABA 2: O RADAR DE PROXIMIDADE (Com Notificação Fixa & Expansão)
    // ==========================================
    private fun buildRadarTab(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 48, 40, 20)

            val header = TextView(this@MainActivity).apply {
                text = "⚡ S I N T O N I A"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                letterSpacing = 0.2f
                gravity = Gravity.CENTER
            }

            statusBadge = TextView(this@MainActivity).apply {
                text = "● RADAR ADORMECIDO"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(28, 10, 28, 10)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0E131F"))
                    cornerRadius = 40f
                    setStroke(2, Color.parseColor("#1E293B"))
                }
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 16, 0, 20)
                layoutParams = p
            }

            // Radar Central com Anéis Fluídos
            val radarWrapper = FrameLayout(this@MainActivity).apply {
                val size = 520
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 8, 0, 20) }
            }

            radarPulseRing = FrameLayout(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#180D2B"))
                    setStroke(3, Color.parseColor("#7C3AED"))
                }
                alpha = 0.2f
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }

            radarCenterCircle = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val centerSize = 420
                layoutParams = FrameLayout.LayoutParams(centerSize, centerSize).apply { gravity = Gravity.CENTER }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#0B0F19"))
                    setStroke(4, Color.parseColor("#334155"))
                }
            }

            radarPresenceSubtext = TextView(this@MainActivity).apply {
                text = "Em silêncio"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }

            radarDistanceText = TextView(this@MainActivity).apply {
                text = "Raio Imediato"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }

            radarCenterCircle.addView(radarPresenceSubtext)
            radarCenterCircle.addView(radarDistanceText)

            radarWrapper.addView(radarPulseRing)
            radarWrapper.addView(radarCenterCircle)

            // Botão Principal Arredondado
            btnToggleRadar = Button(this@MainActivity).apply {
                text = "SINTONIZAR MEU SINAL"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(24, 20, 24, 20)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#7C3AED"))
                    cornerRadius = 48f
                }
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 8, 0, 16)
                layoutParams = p
                setOnClickListener {
                    if (!isRadarActive) startRadar() else stopRadar()
                }
            }

            val listHeader = TextView(this@MainActivity).apply {
                text = "SINAIS QUE CURTIRAM VOCÊ NESTE LOCAL:"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 8, 0, 8)
            }

            signalsListContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val scroll = ScrollView(this@MainActivity).apply {
                addView(signalsListContainer)
            }

            addView(header)
            addView(statusBadge)
            addView(radarWrapper)
            addView(btnToggleRadar)
            addView(listHeader)
            addView(scroll)
        }
    }

    // ==========================================
    // ABA 3: PERFIL (Foto Única, Filtros & Privacidade)
    // ==========================================
    private fun buildProfileTab(): ScrollView {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 48, 40, 40)
        }

        val title = TextView(this).apply {
            text = "MEU PERFIL"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        // Avatar com Foto Única
        val avatar = TextView(this).apply {
            text = "📷"
            textSize = 48f
            gravity = Gravity.CENTER
            val size = 220
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 24, 0, 16) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E1B4B"))
                setStroke(4, Color.parseColor("#6366F1"))
            }
        }

        val photoHint = TextView(this).apply {
            text = "Sua única foto visível no SINAL. Sem textos, sem bio."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        // Preferência de Gênero
        val genderLabel = TextView(this).apply {
            text = "QUEM VOCÊ DESEJA ENCONTRAR:"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B"))
        }

        val genderRadioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 8, 0, 24)
        }
        val rb1 = RadioButton(this).apply { text = "Mulheres"; setTextColor(Color.WHITE); isChecked = true }
        val rb2 = RadioButton(this).apply { text = "Homens"; setTextColor(Color.WHITE) }
        val rb3 = RadioButton(this).apply { text = "Todos"; setTextColor(Color.WHITE) }
        genderRadioGroup.addView(rb1)
        genderRadioGroup.addView(rb2)
        genderRadioGroup.addView(rb3)

        // Limite de Distância para o Feed
        val distanceLabel = TextView(this).apply {
            text = "RAIO DO FEED: ATÉ 15 KM"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B"))
        }

        val distanceSeek = SeekBar(this).apply {
            max = 50
            progress = 15
            setPadding(0, 16, 0, 24)
        }

        // Opções de Privacidade
        val privCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 32f
            }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 16, 0, 16)
            layoutParams = p
        }

        val priv1 = Switch(this).apply {
            text = "Modo Invisível (Não aparecer no radar de terceiros)"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val priv2 = Switch(this).apply {
            text = "Apenas conexões mútuas podem me ver"
            setTextColor(Color.WHITE)
            textSize = 13f
            isChecked = true
        }

        privCard.addView(priv1)
        privCard.addView(priv2)

        content.addView(title)
        content.addView(avatar)
        content.addView(photoHint)
        content.addView(genderLabel)
        content.addView(genderRadioGroup)
        content.addView(distanceLabel)
        content.addView(distanceSeek)
        content.addView(privCard)

        scroll.addView(content)
        return scroll
    }

    // ==========================================
    // OVERLAY DE EXPANSÃO SUAVE DA FOTO (Recurso Premium)
    // ==========================================
    private fun buildExpandedPhotoOverlay(): FrameLayout {
        return FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#E605070B")) // Fundo escuro fosco
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { collapsePhoto() }

            expandedPhotoCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#150A21"))
                    cornerRadius = 64f
                    setStroke(3, Color.parseColor("#A855F7"))
                }
                val p = FrameLayout.LayoutParams(700, 950).apply { gravity = Gravity.CENTER }
                layoutParams = p
            }

            expandedPhotoAvatar = TextView(this@MainActivity).apply {
                text = "✨"
                textSize = 80f
                gravity = Gravity.CENTER
            }

            expandedPhotoInfo = TextView(this@MainActivity).apply {
                text = "⚡ SINAL DESBLOQUEADO (PREMIUM)\nEsta pessoa está muito próxima de você agora."
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 16)
            }

            val closeHint = TextView(this@MainActivity).apply {
                text = "Toque em qualquer lugar para fechar"
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }

            expandedPhotoCard.addView(expandedPhotoAvatar)
            expandedPhotoCard.addView(expandedPhotoInfo)
            expandedPhotoCard.addView(closeHint)

            addView(expandedPhotoCard)
        }
    }

    private fun expandPhoto(name: String) {
        expandedPhotoAvatar.text = "👤"
        expandedPhotoInfo.text = "⚡ SINAL DESBLOQUEADO (PREMIUM)\n$name está no seu raio imediato agora."
        expandedPhotoOverlay.visibility = View.VISIBLE

        expandedPhotoCard.scaleX = 0.6f
        expandedPhotoCard.scaleY = 0.6f
        expandedPhotoCard.alpha = 0f

        val sx = ObjectAnimator.ofFloat(expandedPhotoCard, View.SCALE_X, 0.6f, 1.0f)
        val sy = ObjectAnimator.ofFloat(expandedPhotoCard, View.SCALE_Y, 0.6f, 1.0f)
        val sa = ObjectAnimator.ofFloat(expandedPhotoCard, View.ALPHA, 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(sx, sy, sa)
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun collapsePhoto() {
        expandedPhotoOverlay.visibility = View.GONE
    }

    // ==========================================
    // BARRA DE NAVEGAÇÃO INFERIOR ESTILO TIKTOK
    // ==========================================
    private fun buildBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#080C14"))
            val p = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 150).apply {
                gravity = Gravity.BOTTOM
            }
            layoutParams = p

            btnNavFeed = TextView(this@MainActivity).apply {
                text = "🔥 FEED"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                layoutParams = lp
                setOnClickListener { switchTab(0) }
            }

            btnNavRadar = TextView(this@MainActivity).apply {
                text = "⚡ SINAL"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                layoutParams = lp
                setOnClickListener { switchTab(1) }
            }

            btnNavProfile = TextView(this@MainActivity).apply {
                text = "👤 PERFIL"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                layoutParams = lp
                setOnClickListener { switchTab(2) }
            }

            addView(btnNavFeed)
            addView(btnNavRadar)
            addView(btnNavProfile)
        }
    }

    private fun switchTab(tabIndex: Int) {
        tabFeed.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        tabRadar.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        tabProfile.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        btnNavFeed.setTextColor(if (tabIndex == 0) Color.WHITE else Color.parseColor("#64748B"))
        btnNavRadar.setTextColor(if (tabIndex == 1) Color.WHITE else Color.parseColor("#64748B"))
        btnNavProfile.setTextColor(if (tabIndex == 2) Color.WHITE else Color.parseColor("#64748B"))
    }

    // ==========================================
    // NOTIFICAÇÃO FIXA DE SEGUNDO PLANO
    // ==========================================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canal do SINAL",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o SINAL escutando presenças próximas"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val stopIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_STOP_RADAR"
        }
        val stopPending = PendingIntent.getActivity(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ SINAL: Você está sintonizado")
            .setContentText("Escutando sinais de interesse no seu raio imediato.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desligar Radar", stopPending)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {}
    }

    private fun cancelForegroundNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
    }

    // ==========================================
    // CONTROLE DO RADAR
    // ==========================================
    private fun startRadar() {
        isRadarActive = true
        statusBadge.text = "● SINTONIZADO NO AMBIENTE"
        statusBadge.setTextColor(Color.parseColor("#10B981"))
        btnToggleRadar.text = "DESLIGAR MEU SINAL"
        btnToggleRadar.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1E293B"))
            cornerRadius = 48f
        }
        radarDistanceText.text = "Escutando..."
        radarPresenceSubtext.text = "Buscando sinais"

        startPulseAnimation()
        showForegroundNotification()

        // Adiciona sinal de demonstração para testar a expansão no shopping
        addSignalCard("Pessoa Misteriosa", "Muito próxima de você")
    }

    private fun stopRadar() {
        isRadarActive = false
        stopPulseAnimation()
        cancelForegroundNotification()

        statusBadge.text = "● RADAR ADORMECIDO"
        statusBadge.setTextColor(Color.parseColor("#64748B"))
        btnToggleRadar.text = "SINTONIZAR MEU SINAL"
        btnToggleRadar.background = GradientDrawable().apply {
            setColor(Color.parseColor("#7C3AED"))
            cornerRadius = 48f
        }

        radarCenterCircle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0B0F19"))
            setStroke(4, Color.parseColor("#334155"))
        }
        radarPresenceSubtext.text = "Em silêncio"
        radarDistanceText.text = "Raio Imediato"
        signalsListContainer.removeAllViews()
    }

    private fun addSignalCard(name: String, desc: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#131B2E"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#3B82F6"))
            }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 16)
            layoutParams = p
            setOnClickListener {
                if (isPremium) {
                    expandPhoto(name)
                } else {
                    Toast.makeText(this@MainActivity, "Desbloqueie o Premium para ver a foto.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val avatar = TextView(this).apply {
            text = "👤"
            textSize = 28f
            gravity = Gravity.CENTER
            val size = 100
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 24, 0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E1B4B"))
            }
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "⚡ SINAL DETECTADO"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
        }

        val subtitle = TextView(this).apply {
            text = "$name • $desc (Toque para ver)"
            textSize = 11f
            setTextColor(Color.parseColor("#CBD5E1"))
        }

        infoLayout.addView(title)
        infoLayout.addView(subtitle)

        card.addView(avatar)
        card.addView(infoLayout)

        signalsListContainer.addView(card)

        // Vibração dupla no bolso
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
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.05f)

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
        radarPulseRing.alpha = 0.2f
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
    }
}
