package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

class MainActivity : ComponentActivity() {

    private var isLoopActive by mutableStateOf(true)
    private var adStatusState by mutableStateOf("جاري تهيئة SDK...")
    private var adCountState by mutableIntStateOf(0)
    private var lastAdTimeState by mutableStateOf("لم يتم العرض بعد")
    private var isAdLoadingState by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())
    private var pendingAdRunnable: Runnable? = null
    private var currentStartAppAd: StartAppAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize StartApp SDK with App ID "203960445"
        try {
            StartAppSDK.init(this, "203960445", false)
            StartAppSDK.enableReturnAds(true)
            adStatusState = "SDK جاهز - Start.io (App ID: 203960445)"
        } catch (e: Exception) {
            adStatusState = "خطأ في تهيئة SDK: ${e.localizedMessage}"
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AdTopBar()
                    }
                ) { innerPadding ->
                    AdDashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        isLoopActive = isLoopActive,
                        adStatus = adStatusState,
                        adCount = adCountState,
                        lastAdTime = lastAdTimeState,
                        isAdLoading = isAdLoadingState,
                        onToggleLoop = { active ->
                            isLoopActive = active
                            if (active) {
                                triggerNextAdWithDelay(0)
                            } else {
                                cancelPendingAd()
                                adStatusState = "تم إيقاف الحلقة التلقائية مؤقتاً"
                            }
                        },
                        onManualShowAd = {
                            cancelPendingAd()
                            loadAndShowInterstitialAd()
                        }
                    )
                }
            }
        }

        // Start automatic ad loop on initial creation
        triggerNextAdWithDelay(1000)
    }

    override fun onResume() {
        super.onResume()
        if (isLoopActive && pendingAdRunnable == null) {
            triggerNextAdWithDelay(1000)
        }
    }

    override fun onPause() {
        super.onPause()
        cancelPendingAd()
    }

    private fun cancelPendingAd() {
        pendingAdRunnable?.let { handler.removeCallbacks(it) }
        pendingAdRunnable = null
        isAdLoadingState = false
    }

    private fun triggerNextAdWithDelay(delayMs: Long) {
        cancelPendingAd()
        if (!isLoopActive) return

        adStatusState = if (delayMs > 0) "انتظار ${delayMs / 1000.0}s لعرض الإعلان التالي..." else "جاري تحميل الإعلان..."
        val runnable = Runnable {
            if (isLoopActive && !isDestroyed && !isFinishing) {
                loadAndShowInterstitialAd()
            }
        }
        pendingAdRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun loadAndShowInterstitialAd() {
        if (isFinishing || isDestroyed) return

        isAdLoadingState = true
        adStatusState = "جاري تحميل إعلان Interstitial..."

        val startAppAd = StartAppAd(this)
        currentStartAppAd = startAppAd

        startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                isAdLoadingState = false
                adStatusState = "تم تحميل الإعلان - جاري العرض..."
                
                startAppAd.showAd(object : AdDisplayListener {
                    override fun adDisplayed(ad: Ad?) {
                        adCountState++
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        lastAdTimeState = timeStr
                        adStatusState = "الإعلان يعرض الآن ($timeStr)"
                    }

                    override fun adHidden(ad: Ad?) {
                        adStatusState = "تم إغلاق الإعلان. تحضير التالي بعد 1.5 ثانية..."
                        // User dismissed/skipped the ad -> wait 1.5s and trigger next ad loop
                        triggerNextAdWithDelay(1500)
                    }

                    override fun adClicked(ad: Ad?) {
                        adStatusState = "تم النقر على الإعلان!"
                    }

                    override fun adNotDisplayed(ad: Ad?) {
                        adStatusState = "تعذر عرض الإعلان. إعادة المحاولة بعد 1.5 ثانية..."
                        triggerNextAdWithDelay(1500)
                    }
                })
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                isAdLoadingState = false
                val errorMsg = ad?.errorMessage ?: "فشل التحميل"
                adStatusState = "فشل تحميل الإعلان: $errorMsg. محاولة بعد 1.5 ثانية..."
                triggerNextAdWithDelay(1500)
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdTopBar() {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD1E4FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Ads Logo",
                        tint = Color(0xFF001D36),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "in ads",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "com.in.ads • v0.1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0061A4)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFFDFCFF)
        )
    )
}

@Composable
fun AdDashboardScreen(
    modifier: Modifier = Modifier,
    isLoopActive: Boolean,
    adStatus: String,
    adCount: Int,
    lastAdTime: String,
    isAdLoading: Boolean,
    onToggleLoop: (Boolean) -> Unit,
    onManualShowAd: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFDFCFF))
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Minimalist Cycle Gauge
        Box(
            modifier = Modifier
                .size(180.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val strokeColor by animateColorAsState(
                if (isLoopActive) Color(0xFF0061A4) else Color(0xFFE53935),
                label = "strokeColor"
            )
            val ringBgColor = Color(0xFFD1E4FF)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = ringBgColor,
                        shape = CircleShape
                    )
            )

            if (isAdLoading || isLoopActive) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = strokeColor,
                    strokeWidth = 4.dp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "1.5s",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = strokeColor
                )
                Text(
                    text = "CYCLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
                )
            }
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF1F3F9)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val badgeColor by animateColorAsState(
                            if (isLoopActive) Color(0xFF4CAF50) else Color(0xFFE53935),
                            label = "badgeColor"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLoopActive) "الحلقة التلقائية نشطة" else "الحلقة متوقفة",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF1A1C1E)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F0FF)
                    ) {
                        Text(
                            text = "Interstitial",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF004A77),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // SDK Info Rows
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow(label = "موقع الإعلان", value = "Start.io (Latest)")
                    InfoRow(label = "App ID", value = "203960445")
                }

                // Status Message Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "حالة الإعلان الحالية",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = adStatus,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0061A4)
                        )
                    }
                }

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "إجمالي العروض",
                        value = "$adCount",
                        icon = Icons.Default.CheckCircle
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "آخر توقيت",
                        value = lastAdTime,
                        icon = Icons.Default.Info
                    )
                }
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onToggleLoop(!isLoopActive) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoopActive) Color(0xFFBA1A1A) else Color(0xFF0061A4)
                )
            ) {
                Icon(
                    imageVector = if (isLoopActive) Icons.Default.PauseCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoopActive) "إيقاف" else "تشغيل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onManualShowAd,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = !isAdLoading,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "عرض الآن",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Live Banner Ad Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF1F3F9)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Start.io Banner Ad",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    AndroidView(
                        factory = { ctx ->
                            Banner(ctx)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0061A4)
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F0FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF0061A4),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E)
                )
            }
        }
    }
}

