package com.example

import android.hardware.usb.UsbDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DiagnosticViewModel
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val isSplashActive by viewModel.isSplashActive.collectAsState()
            
            LaunchedEffect(Unit) {
                viewModel.restartSplash()
            }

            CompositionLocalProvider(LocalThemeIsDark provides isDark) {
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()
                        var selectedTab by remember { mutableStateOf(0) }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                DiagnosticDrawerContent(
                                    selectedTab = selectedTab,
                                    onTabSelected = { index ->
                                        selectedTab = index
                                        scope.launch { drawerState.close() }
                                    },
                                    onCloseDrawer = {
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SlateDark)
                                    .windowInsetsPadding(WindowInsets.safeDrawing),
                                topBar = {
                                    DiagnosticTopBar(viewModel, onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    })
                                }
                            ) { innerPadding ->
                                MainScreen(
                                    viewModel = viewModel,
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }

                        var splashVisible by remember { mutableStateOf(true) }
                        if (splashVisible) {
                            val alpha by animateFloatAsState(
                                targetValue = if (isSplashActive) 1f else 0f,
                                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                                finishedListener = { if (it == 0f) splashVisible = false },
                                label = "splash_fade_out"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = alpha)
                            ) {
                                DiagnosticSplashScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticTopBar(viewModel: DiagnosticViewModel, onMenuClick: () -> Unit) {
    val isRoot by viewModel.isRootAvailable.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val context = LocalContext.current

    TopAppBar(
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Diagnostic Menu",
                        tint = Purple80
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.img_app_icon_chrome_style_1782924350952),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, Purple80.copy(alpha = 0.6f), CircleShape)
                )
            }
        },
        title = {
            Column {
                Text(
                    text = "HARD DRIVE DIAGNOSTICS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Purple80
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (connectedDrive != null) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.7f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_alpha"
                        )

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale, alpha = pulseAlpha)
                                    .background(NeonGreen.copy(alpha = 0.5f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(NeonGreen, CircleShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(NeonAmber, CircleShape)
                        )
                    }

                    Text(
                        text = if (connectedDrive != null) {
                            "ACTIVE: ${connectedDrive!!.vendor} ${connectedDrive!!.model}"
                        } else {
                            "NO DRIVE MOUNTED - SIMULATION MODE READY"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (connectedDrive != null) NeonGreen else NeonAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            val isDark by viewModel.isDarkMode.collectAsState()
            IconButton(
                onClick = { viewModel.toggleTheme() },
                modifier = Modifier.testTag("toggle_theme_button")
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.Brightness3,
                    contentDescription = "Toggle Theme",
                    tint = Purple80
                )
            }
            if (connectedDrive != null) {
                IconButton(
                    onClick = { viewModel.exportReport(context) },
                    modifier = Modifier.testTag("top_bar_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Report",
                        tint = Purple80
                    )
                }
            }
            Badge(
                containerColor = if (isRoot) NeonBlue else SlateBorder,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = if (isRoot) "ROOT" else "ROOTLESS",
                    fontFamily = FontFamily.Monospace,
                    color = if (isRoot) SlateDark else TerminalText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            IconButton(
                onClick = { viewModel.refreshDevices() },
                modifier = Modifier.testTag("refresh_devices_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan USB Devices",
                    tint = Purple80
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SlateDark,
            titleContentColor = TerminalText
        )
    )
}

@Composable
fun DiagnosticDrawerContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val tabs = listOf(
        "S.M.A.R.T. Health", "Surface Heatmap", "Signal Repair", 
        "Internal Self Tests", "Partition Editor", "Recovery Wizard", 
        "SA & ATA Direct", "Pro Scripting & Hex", "Terminal Console"
    )
    val tabIcons = listOf(
        Icons.Default.HealthAndSafety,
        Icons.Default.GridOn,
        Icons.Default.FlashOn,
        Icons.Default.PlayArrow,
        Icons.Default.Storage,
        Icons.Default.AutoFixHigh,
        Icons.Default.Usb,
        Icons.Default.DeveloperMode,
        Icons.Default.Code
    )
    val tabDescriptions = listOf(
        "S.M.A.R.T. parameters & drive status",
        "Sector latency & read/write diagnostic map",
        "SpinRite-style weak flux recovery & remap",
        "Run internal firmware diagnostic routines",
        "Low-level volume magic table editor",
        "Deep signature file carver & salvage",
        "Service Area & direct hardware register link",
        "Interactive ATA automation IDE & sector hex editor",
        "Real-time microcode diagnostic console log"
    )

    ModalDrawerSheet(
        drawerContainerColor = SlateCard,
        drawerContentColor = TerminalText,
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateCard)
        ) {
            // High-tech Drawer Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateDark)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon_chrome_style_1782924350952),
                            contentDescription = "PC-3000 Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.2.dp, Purple80.copy(alpha = 0.7f), CircleShape)
                        )
                        Column {
                            Text(
                                text = "PC-3000 MOBILE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple80
                            )
                            Text(
                                text = "ACE LAB SYSTEM CORE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NeonGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "STATUS: SYSTEM ONLINE (HANDSHAKE OK)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalText.copy(alpha = 0.6f)
                    )
                }
            }
            HorizontalDivider(color = SlateBorder, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Groups
            val categories = listOf(
                "PHYSICAL DIAGNOSTICS" to listOf(0, 1, 2),
                "LOW-LEVEL UTILITIES" to listOf(3, 4),
                "DATA SALVAGE & CORE" to listOf(5, 6, 7)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { (catTitle, indices) ->
                    item {
                        Text(
                            text = catTitle,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    items(indices.size) { idx ->
                        val tabIdx = indices[idx]
                        val isSelected = selectedTab == tabIdx
                        val bgColor = if (isSelected) Purple80.copy(alpha = 0.12f) else Color.Transparent
                        val borderColor = if (isSelected) Purple80.copy(alpha = 0.3f) else Color.Transparent
                        val textColor = if (isSelected) Purple80 else TerminalText
                        val iconColor = if (isSelected) Purple80 else TerminalText.copy(alpha = 0.6f)

                        Card(
                            onClick = { onTabSelected(tabIdx) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_item_$tabIdx")
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            border = BorderStroke(1.dp, borderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (isSelected) Purple80.copy(alpha = 0.1f) else SlateDark,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(0.5.dp, if (isSelected) Purple80.copy(alpha = 0.4f) else SlateBorder, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tabIcons[tabIdx],
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tabs[tabIdx],
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor
                                    )
                                    Text(
                                        text = tabDescriptions[tabIdx],
                                        fontSize = 10.sp,
                                        color = TerminalText.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Purple80, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer info
            HorizontalDivider(color = SlateBorder, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateDark)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PC-3000 ENGINE v6.8.12",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalText.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: DiagnosticViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("S.M.A.R.T.", "HEATMAP SCAN", "SIGNAL REPAIR", "SELF TESTS", "PARTITION MANAGER", "RECOVERY WIZARD", "SA & ATA DIRECT", "PRO SCRIPT & HEX", "CONSOLE")
    var isHeaderExpanded by remember { mutableStateOf(false) }
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val driveModel = connectedDrive?.model ?: "NO DRIVE DETECTED"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // High-tech collapsible toggle header bar at the very top
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { isHeaderExpanded = !isHeaderExpanded }
                .testTag("hardware_header_toggle"),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, if (isHeaderExpanded) Purple80 else SlateBorder),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isHeaderExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Header",
                        tint = Purple80,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isHeaderExpanded) "▲ COLLAPSE DEVICE CONTROL PANEL" else "▼ EXPAND HARDWARE CONTROL PANEL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple80
                        )
                        Text(
                            text = "Target Platter Unit: $driveModel",
                            fontSize = 9.sp,
                            color = TerminalText.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Text(
                    text = if (isHeaderExpanded) "إخفاء لوحة التحكم" else "تغيير القرص / عرض البيانات",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHeaderExpanded) Color.Gray else NeonGreen
                )
            }
        }

        // Collapsible banner container
        AnimatedVisibility(
            visible = isHeaderExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Drive Selection Banner
                DriveSelectorPanel(viewModel)

                // USB-OTG Drive Identification Summary Panel
                UsbOtgDriveSummaryPanel(viewModel)

                // Active S.M.A.R.T. Attributes Alert Banner
                ActiveSmartAlertsBanner(viewModel)

                // AI Diagnosis and Auto Repair Banner
                AutoRepairSection(viewModel)

                // PRO suite announcement with quick action button
                ProToolsQuickLauncherBanner(onLaunchPro = { onTabSelected(7) })
            }
        }

        // Custom Navigation Tab Bar
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SlateCard,
            contentColor = Purple80,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Purple80
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                val icon = when (index) {
                    0 -> Icons.Default.HealthAndSafety
                    1 -> Icons.Default.GridOn
                    2 -> Icons.Default.FlashOn
                    3 -> Icons.Default.PlayArrow
                    4 -> Icons.Default.Storage
                    5 -> Icons.Default.AutoFixHigh
                    6 -> Icons.Default.Usb
                    7 -> Icons.Default.DeveloperMode
                    else -> Icons.Default.Code
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.testTag("tab_$index"),
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == index) Purple80 else TerminalText.copy(alpha = 0.5f)
                        )
                    },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) Purple80 else TerminalText.copy(alpha = 0.6f)
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
        ) {
            when (selectedTab) {
                0 -> SmartTab(viewModel)
                1 -> HeatmapScanTab(viewModel)
                2 -> SignalRepairTab(viewModel)
                3 -> SelfTestsTab(viewModel)
                4 -> PartitionManagerTab(viewModel)
                5 -> RecoveryWizardTab(viewModel)
                6 -> SaAtaDirectTab(viewModel)
                7 -> ProScriptingTab(viewModel)
                8 -> ConsoleTab(viewModel)
            }
        }
    }
}

@Composable
fun PanelTechHeader(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Purple80.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 12.dp)
                    .background(Purple80, RoundedCornerShape(1.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Purple80
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TerminalText.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun DriveSelectorPanel(viewModel: DiagnosticViewModel) {
    val usbDevices by viewModel.usbDevices.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    var showMockDialog by remember { mutableStateOf(false) }
    var pendingConnectionAction by remember { mutableStateOf<String?>(null) }

    val otgPowerStatus by viewModel.otgPowerStatus.collectAsState()
    val directAtaHandshake by viewModel.directAtaHandshake.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val isConnectionRepairing by viewModel.isConnectionRepairing.collectAsState()
    val connectionRepairProgress by viewModel.connectionRepairProgress.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PanelTechHeader(
                title = "Mount Physical or Simulated Drive",
                subtitle = "SATA / OTG LINK"
            )
            Column(modifier = Modifier.padding(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // USB devices selection or Mock selector
                if (usbDevices.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = "USB OTG",
                                tint = NeonAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No USB device detected.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TerminalText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.refreshDevices() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, NeonBlue),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(30.dp).padding(vertical = 0.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "RE-SCAN USB OTG PORT 🔍 (البحث عن الفلاشات)",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NeonBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // List connected physical USB drives
                    Column(modifier = Modifier.weight(1f)) {
                        usbDevices.forEach { device ->
                            Button(
                                onClick = { viewModel.connectToUsbDevice(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("usb_device_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.SettingsInputHdmi, contentDescription = "USB Device")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connect physical: ${device.productName ?: "USB Drive"}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TerminalText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { showMockDialog = true },
                    modifier = Modifier.testTag("mount_mock_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGrey80),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "LOAD PROFILE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Purple80
                    )
                }
            }

            if (usbDevices.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SlateBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "BUS DIAGNOSTICS & TROUBLESHOOTING WIZARD",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonAmber,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "إذا لم يظهر الهارد المتصل بـ OTG، قد تكون المشكلة فولتية الطاقة أو قيود ملفات النظام. جرب استخدام أدوات المحاكاة ومعالجة خطوط الناقل أدناه لتوصيل الهارد:",
                    fontSize = 10.sp,
                    color = TerminalText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isConnectionRepairing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF05070A), RoundedCornerShape(4.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = NeonAmber, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "STABILIZING USB-OTG BUS PHY REGISTERS...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { connectionRepairProgress },
                            color = NeonAmber,
                            trackColor = SlateBorder,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp))
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Row 1: VBUS Power Limit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "VBUS Bus Power Limit:",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when (otgPowerStatus) {
                                        "OK" -> "🟢 Stable 5.0V (900mA Peak)"
                                        "LOW" -> "🔴 Power Starved (< 4.5V)"
                                        else -> "⚪ Unmeasured / Default (500mA)"
                                    },
                                    fontSize = 9.5.sp,
                                    color = TerminalText
                                )
                            }
                            if (otgPowerStatus != "OK") {
                                Button(
                                    onClick = { pendingConnectionAction = "POWER_BOOST" },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("power_boost_btn")
                                ) {
                                    Text("BOOST VOLTS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonAmber)
                                }
                            } else {
                                Text("STABILIZED", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f), thickness = 0.5.dp)

                        // Row 2: Direct ATA Sync
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "ATA Register Pipeline Handshake:",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when (directAtaHandshake) {
                                        "BYPASSED" -> "🟢 Sync Complete (BSY=0, DRDY=1)"
                                        else -> "⚪ Blocked by Host filesystem mount"
                                    },
                                    fontSize = 9.5.sp,
                                    color = TerminalText
                                )
                            }
                            if (directAtaHandshake != "BYPASSED") {
                                Button(
                                    onClick = { pendingConnectionAction = "DIRECT_SYNC" },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("direct_sync_btn")
                                ) {
                                    Text("FORCE SYNC", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonAmber)
                                }
                            } else {
                                Text("BYPASSED", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f), thickness = 0.5.dp)

                        // Row 3: Bridge Unlock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "USB-SATA Bulk-Only Command Bridge:",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when (bridgeStatus) {
                                        "UNLOCKED" -> "🟢 Bridge responsive & listening"
                                        else -> "⚪ Locked / Frozen on bulk transfer"
                                    },
                                    fontSize = 9.5.sp,
                                    color = TerminalText
                                )
                            }
                            if (bridgeStatus != "UNLOCKED") {
                                Button(
                                    onClick = { pendingConnectionAction = "BRIDGE_UNLOCK" },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("bridge_unlock_btn")
                                ) {
                                    Text("UNLOCK BRIDGE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonAmber)
                                }
                            } else {
                                Text("ACTIVE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Dialogs for Connection Wizard Actions
    if (pendingConnectionAction != null) {
        val action = pendingConnectionAction!!
        AlertDialog(
            onDismissRequest = { pendingConnectionAction = null },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "CONFIRM BUS RESTORATION ACTION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = when (action) {
                            "POWER_BOOST" -> "Increase VBUS driving limit from 500mA to 900mA (USB 3.0 peak power) to stabilize magnetic spindle motor rotational torque."
                            "DIRECT_SYNC" -> "Issue low-level ATA Status register polling bypass to ignore OS file lock holds and establish raw sector communication channels."
                            "BRIDGE_UNLOCK" -> "Reset the USB-to-SATA translation bridge FIFO buffers to flush locked bulk transfer packets and restore serial responsiveness."
                            else -> ""
                        },
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (action) {
                            "POWER_BOOST" -> "تأكيد زيادة فولتية الناقل VBUS: سيقوم هذا برفع التيار الكهربائي إلى 900mA لتوفير طاقة كافية للموتور لتجاوز الانهيار الكهربائي."
                            "DIRECT_SYNC" -> "تأكيد فرض مزامنة ATA: سيقوم هذا بتجاوز أقفال ملفات نظام التشغيل لتأسيس اتصال مباشر على مستوى سجلات التحكم الثنائية للهارد."
                            "BRIDGE_UNLOCK" -> "تأكيد إلغاء قفل الجسر USB-SATA: سيقوم هذا بإعادة تهيئة ذاكرة التخزين المؤقت FIFO لجسر التوصيل لاستئناف نقل البيانات بعد التجميد."
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.runConnectionRepairAction(action)
                        pendingConnectionAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Text("RUN ACTION (تشغيل)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConnectionAction = null }) {
                    Text("CANCEL (إلغاء)", color = TerminalText)
                }
            }
        )
    }
}

    if (showMockDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("All") }
        
        val categories = listOf("All", "WD Blue / Green", "WD Black", "WD Red", "WD Purple", "WD Gold", "WD Portable / Elements", "Non-WD Presets")
        
        val filteredMocks = viewModel.mockDrivesPresetList.filter { preset ->
            (selectedCategory == "All" || preset.category == selectedCategory) &&
            (preset.displayName.contains(searchQuery, ignoreCase = true) || 
             preset.model.contains(searchQuery, ignoreCase = true))
        }

        AlertDialog(
            onDismissRequest = { showMockDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT DRIVE PROFILE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Purple80
                    )
                    Text(
                        text = "دعم جميع مساحات وموديلات WD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Load a pre-configured Western Digital or multi-brand SATA/SSD profile to simulate Victoria diagnostics and hardware repair.",
                        fontSize = 11.sp,
                        color = TerminalText.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name/model... (بحث)", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.5f)) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TerminalText.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    )

                    // Horizontal Category Chips
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Purple80 else SlateDark)
                                    .border(1.dp, if (isSelected) Purple80 else SlateBorder, RoundedCornerShape(4.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SlateDark else TerminalText
                                )
                            }
                        }
                    }

                    // Drives List (Fixes broken scroller completely!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp) // bounded height to prevent off-screen overflow
                            .background(SlateDark, RoundedCornerShape(4.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        if (filteredMocks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No matching drive profiles found.",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TerminalText.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredMocks) { preset ->
                                    val isWD = preset.vendor.lowercase().contains("digital") || preset.vendor.lowercase().contains("wd")
                                    val brandColor = if (isWD) NeonBlue else Purple80
                                    
                                    Button(
                                        onClick = {
                                            viewModel.selectMockDrive(preset.displayName)
                                            showMockDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("drive_preset_${preset.displayName.replace(" ", "_")}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                        border = BorderStroke(1.dp, if (preset.issue != DiagnosticViewModel.DriveIssue.NONE) NeonRed.copy(alpha = 0.6f) else SlateBorder),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = preset.displayName,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TerminalText,
                                                    fontSize = 11.sp
                                                )
                                                if (preset.issue != DiagnosticViewModel.DriveIssue.NONE) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(NeonRed.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                            .border(0.5.dp, NeonRed, RoundedCornerShape(2.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "FAILING / DAMAGED",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = NeonRed,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                            .border(0.5.dp, NeonGreen, RoundedCornerShape(2.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "HEALTHY",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = NeonGreen,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Model: ${preset.model}",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = TerminalText.copy(alpha = 0.6f)
                                                )
                                                val capacityText = if (preset.capacityBytes == 0L) "0 LBA (Damaged)" else {
                                                    val gb = preset.capacityBytes / 1000000000L
                                                    if (gb >= 1000) {
                                                        val tb = gb.toDouble() / 1000.0
                                                        String.format("%.1f TB", tb)
                                                    } else {
                                                        "$gb GB"
                                                    }
                                                }
                                                Text(
                                                    text = capacityText,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp,
                                                    color = brandColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMockDialog = false }) {
                    Text("Cancel", color = TerminalText)
                }
            },
            containerColor = SlateCard
        )
    }
}

// --- Tab 1: S.M.A.R.T. Health ---
@Composable
fun SmartDashboardComponent(smartAttributes: List<SmartAttributeParser.SmartAttribute>) {
    val reallocatedAttr = smartAttributes.find { it.id == 0x05 }
    val pendingAttr = smartAttributes.find { it.id == 0xC5 }
    val slateBorderColor = SlateBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CRITICAL HARDWARE TELEMETRY RADAR",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Purple80
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reallocated Sectors Card Viz
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(SlateDark, RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    val rawVal = reallocatedAttr?.rawValue ?: 0L
                    val normVal = reallocatedAttr?.value ?: 100
                    val thresh = reallocatedAttr?.threshold ?: 10
                    val status = reallocatedAttr?.status ?: SmartAttributeParser.HealthStatus.OK

                    val gaugeColor = when (status) {
                        SmartAttributeParser.HealthStatus.OK -> NeonGreen
                        SmartAttributeParser.HealthStatus.WARNING -> NeonAmber
                        SmartAttributeParser.HealthStatus.CRITICAL -> NeonRed
                    }

                    Text(
                        text = "REALLOCATED SECTORS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = TerminalText.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color.Black, RoundedCornerShape(3.dp))
                            .border(0.5.dp, slateBorderColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Grid overlay lines
                            val spacing = size.width / 5
                            for (i in 1 until 5) {
                                drawLine(
                                    color = slateBorderColor.copy(alpha = 0.4f),
                                    start = Offset(i * spacing, 0f),
                                    end = Offset(i * spacing, size.height),
                                    strokeWidth = 1f
                                )
                            }

                            // Dynamic filled indicator representing remaining lifespan of sectors
                            val progress = (normVal / 253f).coerceIn(0f, 1f)
                            drawRect(
                                color = gaugeColor.copy(alpha = 0.25f),
                                size = Size(size.width * progress, size.height)
                            )
                            drawLine(
                                color = gaugeColor,
                                start = Offset(0f, size.height - 2f),
                                end = Offset(size.width * progress, size.height - 2f),
                                strokeWidth = 3f
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RAW: $rawVal",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TerminalText
                            )
                            Text(
                                text = "$normVal%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = gaugeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Remapped LBA: $rawVal | Thr: $thresh",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        color = TerminalText.copy(alpha = 0.5f)
                    )
                }

                // Current Pending Sectors Card Viz
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(SlateDark, RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    val rawVal = pendingAttr?.rawValue ?: 0L
                    val normVal = pendingAttr?.value ?: 100
                    val thresh = pendingAttr?.threshold ?: 0
                    val status = pendingAttr?.status ?: SmartAttributeParser.HealthStatus.OK

                    val gaugeColor = when (status) {
                        SmartAttributeParser.HealthStatus.OK -> NeonGreen
                        SmartAttributeParser.HealthStatus.WARNING -> NeonAmber
                        SmartAttributeParser.HealthStatus.CRITICAL -> NeonRed
                    }

                    Text(
                        text = "CURRENT PENDING",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = TerminalText.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color.Black, RoundedCornerShape(3.dp))
                            .border(0.5.dp, slateBorderColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Grid overlay lines
                            val spacing = size.width / 5
                            for (i in 1 until 5) {
                                drawLine(
                                    color = slateBorderColor.copy(alpha = 0.4f),
                                    start = Offset(i * spacing, 0f),
                                    end = Offset(i * spacing, size.height),
                                    strokeWidth = 1f
                                )
                            }

                            val progress = (normVal / 253f).coerceIn(0f, 1f)
                            drawRect(
                                color = gaugeColor.copy(alpha = 0.25f),
                                size = Size(size.width * progress, size.height)
                            )
                            drawLine(
                                color = gaugeColor,
                                start = Offset(0f, size.height - 2f),
                                end = Offset(size.width * progress, size.height - 2f),
                                strokeWidth = 3f
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RAW: $rawVal",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TerminalText
                            )
                            Text(
                                text = "$normVal%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = gaugeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unstable LBA: $rawVal | Thr: $thresh",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        color = TerminalText.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ArabicDiagnosticReportComponent(viewModel: DiagnosticViewModel) {
    val report by viewModel.arabicAiReport.collectAsState()
    val isLoading by viewModel.isArabicAiReportLoading.collectAsState()
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, Purple80.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PanelTechHeader(
                title = "تقرير تشخيص الأعطال بالذكاء الاصطناعي",
                subtitle = "AI ARABIC REPORT"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = Purple80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تقرير تشخيص الأعطال بالذكاء الاصطناعي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Purple80.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "عربي / AR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple80,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "تقنية ذكية تفحص سجلات ميكروكود المنطقة الخدمية ومؤشرات S.M.A.R.T ومسح الخلايا المغناطيسية لتشخيص المشكلة الدقيقة وتقديم حلول مهندسي PC-3000.",
                fontSize = 12.sp,
                color = TerminalText.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Purple80, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تحليل مؤشرات الميديا والمنطقة الخدمية بالذكاء الاصطناعي...",
                        fontSize = 12.sp,
                        color = Purple80,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (report != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF040608), RoundedCornerShape(6.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                            Text(
                                text = report!!,
                                fontSize = 12.sp,
                                color = TerminalText,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = SlateBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(report!!))
                                    android.widget.Toast.makeText(context, "تم نسخ التقرير الحرج إلى الحافظة", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                border = BorderStroke(1.dp, TerminalText.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TerminalText, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ التقرير", fontSize = 11.sp, color = TerminalText)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "تقرير فحص قرص مغناطيسي")
                                        putExtra(android.content.Intent.EXTRA_TEXT, report!!)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "مشاركة تقرير التشخيص"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = SlateDark, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة التقرير", fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.generateArabicAiReport() },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = SlateDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بدء الفحص وتوليد تقرير الأعطال باللغة العربية",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDark
                    )
                }
            }
        }
    }
}
}

@Composable
fun SmartTab(viewModel: DiagnosticViewModel) {
    val smartAttributes by viewModel.smartAttributes.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val activeProfile by viewModel.activeSmartProfile.collectAsState()
    val context = LocalContext.current

    if (connectedDrive == null) {
        EmptyDriveState(message = "Please connect or select a drive profile above to display raw S.M.A.R.T. register mappings.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // S.M.A.R.T Overview Header
            val overallHealth = calculateOverallHealth(smartAttributes)
            val healthColor = when (overallHealth) {
                "PASS" -> NeonGreen
                "WARNING" -> NeonAmber
                else -> NeonRed
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DIAGNOSTIC STATUS: S.M.A.R.T.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple80
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = connectedDrive!!.model,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                            Text(
                                text = "Serial: ${connectedDrive!!.serial} | Firmware: ${connectedDrive!!.firmware}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.6f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "OVERALL RATING",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalText.copy(alpha = 0.6f)
                            )
                            Text(
                                text = overallHealth,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = healthColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Low-level parameter register mapping ready.",
                            fontSize = 11.sp,
                            color = TerminalText.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { viewModel.exportReport(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("export_report_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export Report", tint = SlateDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT REPORT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            ArabicDiagnosticReportComponent(viewModel)
        }

        item {
            SmartDashboardComponent(smartAttributes)
        }

        item {
            // Drive Health Profiler Selector (for simulation override)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DRIVE SENSOR OVERRIDE PROFILE:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalText.copy(alpha = 0.7f)
                )

                Row {
                    val currentLabel = when (activeProfile) {
                        SmartAttributeParser.DriveProfile.HEALTHY_SATA -> "HEALTHY HDD"
                        SmartAttributeParser.DriveProfile.FAILING_SATA -> "FAILING HDD"
                        SmartAttributeParser.DriveProfile.HEALTHY_SSD -> "HEALTHY SSD"
                        SmartAttributeParser.DriveProfile.FAILING_SSD -> "FAILING SSD"
                    }

                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { expanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, SlateBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = currentLabel,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Purple80
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .background(SlateCard)
                                .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NeonGreen, CircleShape)
                                    )
                                },
                                text = { Text("HEALTHY HDD", fontFamily = FontFamily.Monospace, color = TerminalText, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSmartProfile(SmartAttributeParser.DriveProfile.HEALTHY_SATA)
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NeonRed, CircleShape)
                                    )
                                },
                                text = { Text("FAILING HDD", fontFamily = FontFamily.Monospace, color = TerminalText, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSmartProfile(SmartAttributeParser.DriveProfile.FAILING_SATA)
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NeonGreen, CircleShape)
                                    )
                                },
                                text = { Text("HEALTHY SSD", fontFamily = FontFamily.Monospace, color = TerminalText, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSmartProfile(SmartAttributeParser.DriveProfile.HEALTHY_SSD)
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NeonRed, CircleShape)
                                    )
                                },
                                text = { Text("FAILING SSD", fontFamily = FontFamily.Monospace, color = TerminalText, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSmartProfile(SmartAttributeParser.DriveProfile.FAILING_SSD)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        items(smartAttributes) { attr ->
            SmartAttributeCard(attr)
        }
    }
}

@Composable
fun SmartAttributeCard(attr: SmartAttributeParser.SmartAttribute) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, if (isExpanded) Purple80 else SlateBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = attr.hexId,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (attr.isCritical) NeonRed else Purple80,
                        fontSize = 14.sp,
                        modifier = Modifier.width(50.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attr.name,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val statusColor = when (attr.status) {
                    SmartAttributeParser.HealthStatus.OK -> NeonGreen
                    SmartAttributeParser.HealthStatus.WARNING -> NeonAmber
                    SmartAttributeParser.HealthStatus.CRITICAL -> NeonRed
                }

                Badge(
                    containerColor = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.border(1.dp, statusColor, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = attr.status.name,
                        fontFamily = FontFamily.Monospace,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Value Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VAL: ${attr.value} (Worst: ${attr.worst}) [Thr: ${attr.threshold}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalText.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (attr.value / 253f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (attr.status == SmartAttributeParser.HealthStatus.OK) NeonGreen else if (attr.status == SmartAttributeParser.HealthStatus.WARNING) NeonAmber else NeonRed,
                        trackColor = SlateBorder
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(120.dp)
                ) {
                    Text(
                        text = "RAW VALUE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalText.copy(alpha = 0.5f)
                    )
                    Text(
                        text = attr.rawHex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = attr.description,
                        fontSize = 12.sp,
                        color = TerminalText.copy(alpha = 0.8f)
                    )
                    if (attr.isCritical) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠ This is a CRITICAL physical health attribute. Failure signals imminent drive collapse.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonRed
                        )
                    }
                }
            }
        }
    }
}

fun calculateOverallHealth(attributes: List<SmartAttributeParser.SmartAttribute>): String {
    if (attributes.isEmpty()) return "UNKNOWN"
    var warningCount = 0
    var criticalCount = 0
    attributes.forEach {
        if (it.status == SmartAttributeParser.HealthStatus.CRITICAL && it.isCritical) {
            criticalCount++
        }
        if (it.status == SmartAttributeParser.HealthStatus.WARNING) {
            warningCount++
        }
    }
    return when {
        criticalCount > 0 -> "FAILING"
        warningCount > 1 -> "WARNING"
        else -> "PASS"
    }
}

// --- Tab 2: Victoria Heatmap Scanner ---
@Composable
fun HeatmapScanTab(viewModel: DiagnosticViewModel) {
    val stats by viewModel.scanStats.collectAsState()
    val status by viewModel.scanStatus.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val grid by viewModel.heatmapGrid.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val currentDriveIssue by viewModel.currentDriveIssue.collectAsState()
    val isAutoRepairing by viewModel.isAutoRepairing.collectAsState()
    val autoRepairProgress by viewModel.autoRepairProgress.collectAsState()
    val autoRepairStatusLog by viewModel.autoRepairStatusLog.collectAsState()

    if (connectedDrive == null) {
        EmptyDriveState(message = "Please connect or select a drive profile above to display Victoria-style sector-by-sector scanning.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Scanner Control Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VICTORIA HDD SCANNER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Purple80
                        )
 
                        // Mode Selector: Read, Remap, Erase
                        Row {
                            listOf(
                                DiagnosticViewModel.ScanMode.READ,
                                DiagnosticViewModel.ScanMode.REMAP,
                                DiagnosticViewModel.ScanMode.ERASE
                            ).forEach { mode ->
                                Button(
                                    onClick = { viewModel.updateScanMode(mode) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (scanMode == mode) Purple80 else SlateDark
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .height(28.dp)
                                ) {
                                    Text(
                                        text = mode.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (scanMode == mode) SlateDark else TerminalText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
 
                    Spacer(modifier = Modifier.height(16.dp))
 
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.startHeatmapScan() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                                .testTag("start_scan_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            enabled = status != DiagnosticViewModel.ScanStatus.RUNNING,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Start Scan", tint = SlateDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("START", fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                        }
 
                        Button(
                            onClick = { viewModel.pauseHeatmapScan() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .testTag("pause_scan_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                            enabled = status == DiagnosticViewModel.ScanStatus.RUNNING,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause Scan", tint = SlateDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PAUSE", fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                        }
 
                        Button(
                            onClick = { viewModel.stopHeatmapScan() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                                .testTag("stop_scan_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            enabled = status == DiagnosticViewModel.ScanStatus.RUNNING || status == DiagnosticViewModel.ScanStatus.PAUSED,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Scan", tint = TerminalText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", fontFamily = FontFamily.Monospace, color = TerminalText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
 
        item {
            // Live Stats Display Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular Progress radial dashboard card
                Card(
                    modifier = Modifier.weight(1f).height(155.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val progress = if (stats.blocksTotal > 0) stats.blocksScanned.toFloat() / stats.blocksTotal else 0f
                        
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = Purple80,
                                strokeWidth = 5.dp,
                                trackColor = SlateBorder
                            )
                            Text(
                                text = String.format("%.1f%%", progress * 100),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TerminalText
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TOTAL PROGRESS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Purple80
                        )
                        Text(
                            text = "${stats.blocksScanned}/${stats.blocksTotal} BLOCKS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TerminalText.copy(alpha = 0.6f)
                        )
                    }
                }

                // Victoria Bin Histogram Legend
                Card(
                    modifier = Modifier.weight(1.2f).height(155.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "SCAN TELEMETRY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Purple80,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        TelemetryRow("LBA", "0x" + String.format("%08X", stats.currentSector))
                        TelemetryRow("SPEED", "${String.format("%.1f", stats.averageSpeedMbs)} MB/s")
                        val remainingSecs = stats.estimatedTimeRemainingMs / 1000L
                        val remainingStr = if (status == DiagnosticViewModel.ScanStatus.IDLE) "--:--" else String.format("%02d:%02d", remainingSecs / 60, remainingSecs % 60)
                        TelemetryRow("EST REMAINING", remainingStr)
                    }
                }
            }
        }

        item {
            // Histogram Row breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VICTORIA BLOCK LATENCY DISTRIBUTION HISTOGRAM",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Purple80,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            HistogramRow("< 3ms (Grey)", stats.binUnder3ms, Color.Gray)
                            HistogramRow("< 10ms (Lt Grey)", stats.binUnder10ms, Color.LightGray)
                            HistogramRow("< 50ms (Green)", stats.binUnder50ms, NeonGreen)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            HistogramRow("< 150ms (Amber)", stats.binUnder150ms, NeonAmber)
                            HistogramRow("< 500ms (Orange)", stats.binUnder500ms, Color(0xFFFF5722))
                            HistogramRow("> 500ms (Red)", stats.binOver500ms, NeonRed)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HistogramRow("ERROR (Bad Sector / Read Failure)", stats.binError, Color.Blue)
                }
            }
        }
 
        item {
            // Heatmap Grid Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VICTORIA BLOCK HEATMAP (LATEST 240 BLOCKS)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Purple80,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
 
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(SlateDark)
                            .border(1.dp, SlateBorder)
                            .padding(6.dp)
                    ) {
                        if (grid.isEmpty()) {
                            Text(
                                text = "LBA mapping grid is offline.\nPress START to scan direct logical block addresses.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 14.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(grid) { block ->
                                    val isLastScanned = grid.isNotEmpty() && block.id == grid.last().id
                                    val infiniteTransition = rememberInfiniteTransition(label = "scan_head_glow")
                                    val scanHeadAlpha by if (isLastScanned && status == DiagnosticViewModel.ScanStatus.RUNNING) {
                                        infiniteTransition.animateFloat(
                                            initialValue = 0.4f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(250, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "scan_alpha"
                                        )
                                    } else {
                                        remember { mutableStateOf(1f) }
                                    }

                                    val blockColor = when (block.type) {
                                        DiagnosticViewModel.BlockDelayType.UNDER_3MS -> Color.DarkGray
                                        DiagnosticViewModel.BlockDelayType.UNDER_10MS -> Color.Gray
                                        DiagnosticViewModel.BlockDelayType.UNDER_50MS -> NeonGreen
                                        DiagnosticViewModel.BlockDelayType.UNDER_150MS -> NeonAmber
                                        DiagnosticViewModel.BlockDelayType.UNDER_500MS -> Color(0xFFFF5722)
                                        DiagnosticViewModel.BlockDelayType.OVER_500MS -> NeonRed
                                        DiagnosticViewModel.BlockDelayType.ERROR -> Color.Blue
                                    }
 
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer(alpha = scanHeadAlpha)
                                            .background(
                                                if (isLastScanned && status == DiagnosticViewModel.ScanStatus.RUNNING) NeonBlue else blockColor,
                                                RoundedCornerShape(3.dp)
                                            )
                                            .border(
                                                width = if (isLastScanned && status == DiagnosticViewModel.ScanStatus.RUNNING) 1.5.dp else 0.5.dp,
                                                color = if (isLastScanned && status == DiagnosticViewModel.ScanStatus.RUNNING) Color.White else if (blockColor == Color.DarkGray) SlateBorder.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE || stats.badCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("scan_tab_auto_repair_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, NeonRed)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Errors Detected", tint = NeonRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تم اكتشاف عطل أو قطاعات تالفة أثناء الفحص!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = NeonRed
                                )
                                Text(
                                    text = "SCAN ISSUE DETECTED: RECOMMENDED IMMEDIATE ACTION",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    color = TerminalText.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val activeIssue = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) currentDriveIssue else DiagnosticViewModel.DriveIssue.BAD_SECTOR_CASCADE
                        
                        Text(
                            text = "العطل المكتشف: ${activeIssue.titleAr}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Failure Mode: ${activeIssue.titleEn}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "الحل المقترح: ${activeIssue.recommendationAr}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                        Text(
                            text = "Recommendation: ${activeIssue.recommendationEn}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        if (isAutoRepairing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF05070A), RoundedCornerShape(4.dp))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = autoRepairStatusLog,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Purple80,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { autoRepairProgress },
                                    color = Purple80,
                                    trackColor = SlateBorder,
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${(autoRepairProgress * 100).toInt()}% Complete",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = TerminalText,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.runAutoDriveRepair() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Repair", modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APPLY AI AUTO-REPAIR (تطبيق الصيانة التلقائية الفورية)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // Sector Remapping Guided Wizard
            SectorRemapWizardComponent(viewModel)
        }
    }
}

@Composable
fun TelemetryRow(label: String, valStr: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText.copy(alpha = 0.6f))
        Text(text = valStr, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerminalText)
    }
}

@Composable
fun HistogramRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TerminalText.copy(alpha = 0.7f))
        }
        Text(text = count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TerminalText)
    }
}

// --- Tab 3: SpinRite Signal Repair ---
@Composable
fun SignalRepairTab(viewModel: DiagnosticViewModel) {
    val stats by viewModel.repairStats.collectAsState()
    val isActive by viewModel.isRepairActive.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    
    val neonBlueColor = NeonBlue
    val neonGreenColor = NeonGreen

    if (connectedDrive == null) {
        EmptyDriveState(message = "Please connect or select a drive profile above to initialize SpinRite-style low-level signal repair diagnostics.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SPINRITE MAGNETIC RECOVERY SYSTEM",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Purple80
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Realigns flux reversals on magnetic storage surfaces to restore unreadable sectors using signal hysteresis loops.",
                        fontSize = 12.sp,
                        color = TerminalText.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.startSignalRepair() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                                .testTag("start_repair_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                            enabled = !isActive,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("RUN SPINRITE", fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.stopSignalRepair() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                                .testTag("stop_repair_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            enabled = isActive,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("HALT PROCESS", fontFamily = FontFamily.Monospace, color = TerminalText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Live Waveform Plotting (Canvas Drawing!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SURFACE MAGNETIC FLUX FIELD STRENGTH",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Purple80
                        )
                        Text(
                            text = if (isActive) stats.currentHysteresisPhase else "STANDBY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonGreen else NeonAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Draw the custom waveform Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(SlateDark)
                            .border(1.dp, SlateBorder)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw professional CRT oscilloscope grid background
                            val gridColor = Color(0xFF1E293B).copy(alpha = 0.35f)
                            val centerGridColor = Color(0xFF334155).copy(alpha = 0.6f)
                            val strokeWidth = 1.dp.toPx()
                            
                            // Horizontal grid divisions
                            val rows = 6
                            val rowHeight = size.height / rows
                            for (i in 1 until rows) {
                                drawLine(
                                    color = if (i == 3) centerGridColor else gridColor,
                                    start = Offset(0f, i * rowHeight),
                                    end = Offset(size.width, i * rowHeight),
                                    strokeWidth = if (i == 3) 1.5.dp.toPx() else strokeWidth
                                )
                            }
                            
                            // Vertical grid divisions
                            val cols = 10
                            val colWidth = size.width / cols
                            for (i in 1 until cols) {
                                drawLine(
                                    color = if (i == 5) centerGridColor else gridColor,
                                    start = Offset(i * colWidth, 0f),
                                    end = Offset(i * colWidth, size.height),
                                    strokeWidth = if (i == 5) 1.5.dp.toPx() else strokeWidth
                                )
                            }

                            val pulseList = stats.pulseHistory
                            if (pulseList.isNotEmpty()) {
                                val path = Path()
                                val stepX = size.width / (pulseList.size.coerceAtLeast(10))
                                var lastX = 0f
                                var lastY = size.height

                                pulseList.forEachIndexed { idx, value ->
                                    // Map 0 - 1000mV to vertical size (lower mV is higher y, meaning weaker signal)
                                    val mappedY = size.height - (value / 1100f) * size.height
                                    val currentX = idx * stepX

                                    if (idx == 0) {
                                        path.moveTo(currentX, mappedY)
                                    } else {
                                        path.lineTo(currentX, mappedY)
                                    }
                                    lastX = currentX
                                    lastY = mappedY
                                }

                                drawPath(
                                    path = path,
                                    color = neonBlueColor,
                                    style = Stroke(width = 3f)
                                )

                                // Draw glowing dot at latest index
                                if (isActive) {
                                    drawCircle(
                                        color = neonGreenColor.copy(alpha = 0.4f),
                                        radius = 12f,
                                        center = Offset(lastX, lastY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4f,
                                        center = Offset(lastX, lastY)
                                    )
                                }
                            }
                        }

                        if (!isActive) {
                            Text(
                                text = "Magnetic diagnostic stream is inactive.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Peak Surface Signal: ${stats.surfaceVoltageMv} mV", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText)
                        Text(text = "Nominal Spec: 500 mV", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText.copy(alpha = 0.6f))
                    }
                }
            }
        }

        item {
            // Repair stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "RECOVERY METRICS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Purple80,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TelemetryRow("CURRENT REPAIR SECTOR", "0x" + String.format("%08X", stats.activeSector))
                    TelemetryRow("SECTORS ANALYZED", stats.sectorsRepaired.toString())
                    TelemetryRow("WEAK FLUX SECTORS STABILIZED", stats.signalsRegenerated.toString())
                    TelemetryRow("LOST/UNREADABLE SECTORS RECOVERED", stats.sectorsRecovered.toString())
                }
            }
        }
    }
}

// --- Tab 4: SeaTools Diagnostics ---
@Composable
fun SelfTestsTab(viewModel: DiagnosticViewModel) {
    val selfTestState by viewModel.selfTestState.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()

    if (connectedDrive == null) {
        EmptyDriveState(message = "Please connect or select a drive profile above to access SeaTools & WD Data Lifeguard Diagnostic modules.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SEATOOLS FIRMWARE DIAGNOSTIC SUITE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Purple80
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Executes automated internal self-tests embedded directly in the drive's controller microcode.",
                        fontSize = 12.sp,
                        color = TerminalText.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runDriveSelfTest(DiagnosticViewModel.SelfTestType.SHORT_DST) },
                            modifier = Modifier.weight(1f).testTag("short_dst_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            enabled = selfTestState.status != DiagnosticViewModel.SelfTestStatus.RUNNING,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("SHORT DST (2m)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.runDriveSelfTest(DiagnosticViewModel.SelfTestType.LONG_DST) },
                            modifier = Modifier.weight(1f).testTag("long_dst_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            enabled = selfTestState.status != DiagnosticViewModel.SelfTestStatus.RUNNING,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("LONG DST (10m)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Diagnostic Status Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TEST STATUS: ${selfTestState.type.name}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Purple80,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val statusColor = when (selfTestState.status) {
                        DiagnosticViewModel.SelfTestStatus.IDLE -> TerminalText.copy(alpha = 0.5f)
                        DiagnosticViewModel.SelfTestStatus.RUNNING -> NeonBlue
                        DiagnosticViewModel.SelfTestStatus.PASSED -> NeonGreen
                        DiagnosticViewModel.SelfTestStatus.FAILED -> NeonRed
                    }

                    Text(
                        text = selfTestState.status.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = statusColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = selfTestState.summary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selfTestState.status == DiagnosticViewModel.SelfTestStatus.RUNNING) {
                        CircularProgressIndicator(
                            color = NeonBlue,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    } else if (selfTestState.status == DiagnosticViewModel.SelfTestStatus.PASSED) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Passed",
                            tint = NeonGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    } else if (selfTestState.status == DiagnosticViewModel.SelfTestStatus.FAILED) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failed",
                            tint = NeonRed,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Tab 5: Console & Linux Nodes ---
@Composable
fun ConsoleTab(viewModel: DiagnosticViewModel) {
    val logs by viewModel.consoleLogs.collectAsState()
    val isRoot by viewModel.isRootAvailable.collectAsState()
    val rootDevices by viewModel.rootDevices.collectAsState()
    val rootDevicePath by viewModel.rootDevicePath.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOW-LEVEL BUS COMMAND MONITOR",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Purple80
            )

            Button(
                onClick = { viewModel.clearConsoleLogs() },
                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("CLEAR MONITOR", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TerminalText)
            }
        }

        // Live scrolling terminal console
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(Color.Black)
                .border(1.dp, SlateBorder)
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(logs.asReversed()) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (log.contains("Error") || log.contains("FAILED") || log.contains("Alert")) NeonRed else if (log.contains("Success") || log.contains("stabilized") || log.contains("PASSED")) NeonGreen else TerminalText,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Direct device path explorer (/dev/block/ mapping helper)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "LINUX DEV BLOCK MAPPER (/DEV/BLOCK/*)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Purple80,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Shows target paths accessed on rooted kernels. Self-contained OTG Host takes precedence for safety.",
                    fontSize = 10.sp,
                    color = TerminalText.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!isRoot) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SlateDark)
                            .border(1.dp, SlateBorder)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Running in Standard OTG Mode",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Perfectly self-contained drive access configured.\nIf root were available, these nodes would be queried:\n- /dev/block/sda (SATA USB Adapter)\n- /dev/block/nvme0n1 (Host NVMe PCI Express)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TerminalText.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // List actual root block nodes found!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(rootDevices) { device ->
                            val isSelected = rootDevicePath == device.path
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Purple80.copy(alpha = 0.15f) else SlateDark)
                                    .border(1.dp, if (isSelected) Purple80 else SlateBorder)
                                    .clickable { viewModel.connectToRootDevice(device) }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = device.path,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) NeonAmber else Purple80
                                    )
                                    Text(
                                        text = "Type: ${device.type} | Read-Only: ${device.isReadOnly}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = TerminalText.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    text = if (device.sizeBytes > 0) "${device.sizeBytes / 1000000000L} GB" else "Unknown Size",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonAmber else TerminalText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PartitionManagerTab(viewModel: DiagnosticViewModel) {
    val partitions by viewModel.partitions.collectAsState()
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val isWiping by viewModel.isWiping.collectAsState()
    val wipeProgress by viewModel.wipeProgress.collectAsState()
    val wipingPartitionId by viewModel.wipingPartitionId.collectAsState()

    val isPartitionOperating by viewModel.isPartitionOperating.collectAsState()
    val partitionOpProgress by viewModel.partitionOpProgress.collectAsState()
    val partitionOpType by viewModel.partitionOpType.collectAsState()
    val partitionOpStatusText by viewModel.partitionOpStatusText.collectAsState()

    val currentDriveIssue by viewModel.currentDriveIssue.collectAsState()
    var showIoErrorDialog by remember { mutableStateOf(false) }
    var showConfirmAutoRepairDialog by remember { mutableStateOf(false) }

    var selectedPartId by remember { mutableStateOf<Int?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }

    // State for Confirmation Prompts on healthy drives
    var pendingActionType by remember { mutableStateOf<String?>(null) } // "FORMAT", "RESIZE", "DELETE", "WIPE", "BOOTABLE"
    var showActionConfirmationDialog by remember { mutableStateOf(false) }

    var createLabel by remember { mutableStateOf("") }
    var createFs by remember { mutableStateOf("NTFS") }
    var createSizeMb by remember { mutableStateOf("5000") }

    var formatFs by remember { mutableStateOf("NTFS") }
    var resizeSizeMb by remember { mutableStateOf("10000") }

    if (connectedDrive == null) {
        EmptyDriveState("Please connect or select a drive profile above to initialize Partition Management diagnostics.")
        return
    }

    val drive = connectedDrive!!

    // Ensure selected partition is valid or pick the first
    if (selectedPartId == null && partitions.isNotEmpty()) {
        selectedPartId = partitions.firstOrNull()?.id
    }

    val activePart = partitions.find { it.id == selectedPartId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Title block
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(0.5.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PARTITION MAGIC LOW-LEVEL EDITOR & MOUNT STATUS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Text(
                    text = "Sector allocation and partition structure manipulation. Simulate OS mounting states and diagnose high-level file system errors.",
                    fontSize = 11.sp,
                    color = TerminalText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // WINDOWS DISK MANAGEMENT REPLICA (As requested in user picture!)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("disk_management_panel")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C3240))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Disk Management",
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VIRTUAL DISK MANAGER (إدارة الأقراص الافتراضية)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed.copy(alpha = 0.2f) else NeonGreen.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                0.5.dp,
                                if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else NeonGreen,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "I/O BLOCKED" else "ONLINE",
                            color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else NeonGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Table of Volumes
                Text(
                    text = "VOLUME LIST (قائمة الأقراص النشطة):",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TerminalText.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Headers row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13161D))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(1f))
                    Text("Layout", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("Type", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("File System", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(1f))
                    Text("Status", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(1.8f))
                    Text("Capacity", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(1f))
                }

                // Volume 1: System Reserved
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("System Res", fontSize = 8.5.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Text("Simple", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("Basic", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("FAT32", fontSize = 8.5.sp, color = NeonBlue, modifier = Modifier.weight(1f))
                    Text("Healthy (System)", fontSize = 8.5.sp, color = NeonGreen, modifier = Modifier.weight(1.8f))
                    Text("500 MB", fontSize = 8.5.sp, color = Color.White, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = SlateBorder.copy(alpha = 0.2f), thickness = 0.5.dp)

                // Volume 2: Primary Data
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("OS_MAIN (C:)", fontSize = 8.5.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Text("Simple", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("Basic", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("NTFS", fontSize = 8.5.sp, color = Purple80, modifier = Modifier.weight(1f))
                    Text("Healthy (Boot)", fontSize = 8.5.sp, color = NeonGreen, modifier = Modifier.weight(1.8f))
                    Text("237 GB", fontSize = 8.5.sp, color = Color.White, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = SlateBorder.copy(alpha = 0.2f), thickness = 0.5.dp)

                // Volume 3: Target Diagnostic drive (Broken if currentDriveIssue != NONE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable {
                            if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                showIoErrorDialog = true
                            }
                        }
                        .padding(vertical = 3.dp)
                ) {
                    Text(
                        text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "ST_RAW_FAIL (D:)" else "${drive.model.take(12)} (D:)",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Simple", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text("Basic", fontSize = 8.5.sp, color = TerminalText, modifier = Modifier.weight(0.7f))
                    Text(
                        text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "RAW" else "NTFS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonAmber else Purple80,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "🔴 Unallocated / I/O Error" else "🟢 Healthy (Primary)",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else NeonGreen,
                        modifier = Modifier.weight(1.8f)
                    )
                    Text(
                        text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "0 Bytes" else "${drive.capacityBytes / (1024L * 1024L * 1024L)} GB",
                        fontSize = 8.5.sp,
                        color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom diagram
                Text(
                    text = "PHYSICAL DISK BLOCKS DIAGRAM (مخطط الأقراص المادي):",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TerminalText.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Disk 0
                DiskLayoutRow(
                    diskNum = "Disk 0",
                    diskType = "Basic\n238 GB\nOnline",
                    partitions = listOf(
                        DiskPartBoxData("System Reserved (FAT32)", "500 MB", NeonBlue, 0.15f),
                        DiskPartBoxData("OS_MAIN (NTFS)", "237 GB", Purple80, 0.75f),
                        DiskPartBoxData("Recovery Partition", "508 MB", Color.Gray, 0.1f)
                    ),
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Disk 1
                DiskLayoutRow(
                    diskNum = "Disk 1",
                    diskType = "Basic\n465 GB\nOnline",
                    partitions = listOf(
                        DiskPartBoxData("PRIMARY_VOL (NTFS)", "465 GB", Purple80, 1.0f)
                    ),
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Disk 3 (The Diagnostic Target)
                val targetDiskPartData = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                    listOf(
                        DiskPartBoxData("ST_RAW_FAIL\nRAW / Unallocated (I/O Device Error)", "0 Bytes", NeonRed, 1.0f, isFailed = true)
                    )
                } else {
                    listOf(
                        DiskPartBoxData("DATA_DRIVE (NTFS)\nHealthy Partition", "${drive.capacityBytes / (1024L * 1024L * 1024L)} GB", Purple80, 0.85f),
                        DiskPartBoxData("Unallocated", "Free Space", Color.DarkGray, 0.15f)
                    )
                }
                DiskLayoutRow(
                    diskNum = "Disk 3",
                    diskType = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "Unknown\n0 Bytes\nUninit" else "Basic\n${drive.capacityBytes / (1024L * 1024L * 1024L)} GB\nOnline",
                    partitions = targetDiskPartData,
                    onClick = {
                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                            showIoErrorDialog = true
                        }
                    }
                )
            }
        }

        // Horizontal visual map
        Text(
            text = "VOLUME MAP ALLOCATION",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalText,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                if (partitions.isEmpty() || currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showIoErrorDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) "⚠️ 0 Bytes (LBA 0 RAW Limit / I/O Device Error)" else "No Partition Table Initialized",
                            color = if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) NeonRed else TerminalText.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    val totalPartitionBytes = partitions.sumOf { it.sizeBytes }.coerceAtLeast(1L)
                    partitions.forEach { part ->
                        val weight = (part.sizeBytes.toFloat() / totalPartitionBytes).coerceAtLeast(0.05f)
                        val isSelected = part.id == selectedPartId
                        val blockColor = when {
                            part.isUnallocated -> DarkGreyBlock
                            part.fileSystem == "FAT32" -> NeonBlue
                            part.fileSystem == "NTFS" -> Purple40
                            part.fileSystem == "exFAT" -> NeonGreen
                            else -> NeonAmber
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(weight)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(blockColor.copy(alpha = if (isSelected) 1.0f else 0.5f))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) Color.White else SlateBorder,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .clickable { selectedPartId = part.id }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = if (part.isUnallocated) "FREE" else part.label.take(8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${part.sizeBytes / (1024 * 1024)} MB",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                                Text(
                                    text = part.fileSystem,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = blockColor,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Details on active partition
        if (activePart != null && currentDriveIssue == DiagnosticViewModel.DriveIssue.NONE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(0.5.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activePart.isUnallocated) "SELECTED: UNALLOCATED SPACE" else "SELECTED: PARTITION #${activePart.id}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TerminalText
                        )
                        if (activePart.isBootable) {
                            Box(
                                modifier = Modifier
                                    .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, NeonGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE / BOOTABLE", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Volume Label", fontSize = 10.sp, color = TerminalText.copy(alpha = 0.5f))
                            Text(
                                text = if (activePart.isUnallocated) "[None]" else activePart.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                        }
                        Column {
                            Text("File System", fontSize = 10.sp, color = TerminalText.copy(alpha = 0.5f))
                            Text(
                                text = activePart.fileSystem,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                        }
                        Column {
                            Text("Size Bytes", fontSize = 10.sp, color = TerminalText.copy(alpha = 0.5f))
                            Text(
                                text = "${activePart.sizeBytes / (1024 * 1024)} MB",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action grid
                    if (isWiping && wipingPartitionId == activePart.id) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("WIPING BLOCKS...", color = NeonRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { wipeProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = NeonRed,
                                trackColor = SlateBorder
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${(wipeProgress * 100).toInt()}% Complete", color = TerminalText, fontSize = 10.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activePart.isUnallocated) {
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            createSizeMb = "${activePart.sizeBytes / (1024 * 1024)}"
                                            showCreateDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Partition", fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            showResizeDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGrey40),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Resize", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            showFormatDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGrey40),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Format", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            pendingActionType = "BOOTABLE"
                                            showActionConfirmationDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGrey40),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (activePart.isBootable) "Deactivate" else "Set Active", fontSize = 11.sp)
                                }
                            }
                        }

                        if (!activePart.isUnallocated) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            pendingActionType = "WIPE"
                                            showActionConfirmationDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f), contentColor = NeonRed),
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(0.5.dp, NeonRed)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Secure Wipe", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
                                            showIoErrorDialog = true
                                        } else {
                                            pendingActionType = "DELETE"
                                            showActionConfirmationDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete Partition", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE) {
            // Warn that actions are disabled due to physical disk breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0E12)),
                border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = NeonRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DISK I/O IS CURRENTLY LOCKED (قنوات الإدخال/الإخراج معطلة)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NeonRed
                        )
                        Text(
                            text = "The system has thrown an I/O Device Error. You cannot perform partition, format, or wipe actions. Click on the damaged Disk 3 above to trigger the low-level PC-3000 restoration wizard.",
                            fontSize = 9.5.sp,
                            color = TerminalText
                        )
                    }
                }
            }
        }

        // Partition Table list
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CURRENT SECTOR ALLOCATION TABLE",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalText,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(0.5.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                if (currentDriveIssue != DiagnosticViewModel.DriveIssue.NONE || partitions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No partition records readable. Drive reports 0 LBA capacity.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TerminalText.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    partitions.forEachIndexed { idx, part ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPartId = part.id }
                                .background(if (part.id == selectedPartId) SlateBorder.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                part.isUnallocated -> Color.Gray
                                                part.fileSystem == "FAT32" -> NeonBlue
                                                part.fileSystem == "NTFS" -> Purple40
                                                else -> NeonGreen
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (part.isUnallocated) "Unallocated Free Space" else part.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TerminalText
                                    )
                                    Text(
                                        text = "FS: ${part.fileSystem} | ID: #${part.id}",
                                        fontSize = 10.sp,
                                        color = TerminalText.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Text(
                                text = "${part.sizeBytes / (1024 * 1024)} MB",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                        }
                        if (idx < partitions.size - 1) {
                            HorizontalDivider(color = SlateBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // THE REQUESTED WINDOWS I/O DEVICE ERROR ALERT (Directly matching user's photo!)
    if (showIoErrorDialog) {
        AlertDialog(
            onDismissRequest = { showIoErrorDialog = false },
            containerColor = Color(0xFF1E222B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error Icon",
                        tint = NeonRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Virtual Disk Manager",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "The request could not be performed because of an I/O device error.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تعذر تنفيذ الطلب بسبب خطأ في جهاز الإدخال والإخراج (I/O) على القرص مادي.",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SlateBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Physical root cause: The drive reports '${currentDriveIssue.titleEn}'. The SATA/USB Bridge controller cannot read sector sectors. To resolve this error, you must bypass the firmware lock or isolate physical failures using the automated PC-3000 restoration sequence.",
                        fontSize = 10.sp,
                        color = TerminalText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIoErrorDialog = false
                        showConfirmAutoRepairDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Repair via PC-3000 (إصلاح تلقائي)", fontSize = 10.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIoErrorDialog = false }) {
                    Text("OK (موافق)", color = TerminalText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // CONFIRM REPAIR DIALOG
    if (showConfirmAutoRepairDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmAutoRepairDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "CONFIRM AUTOMATED RESTORATION",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to initialize the low-level microcode reconstruction?",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Text(
                        text = "هل أنت متأكد من تطبيق عملية الصيانة وإعادة الإعمار التلقائية للفيرموير؟",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "This will apply direct firmware patches (Module 32, Module 02, Translator calibration) depending on the selected profile to clear bad sectors and stabilize power parameters.",
                        fontSize = 9.5.sp,
                        color = TerminalText.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmAutoRepairDialog = false
                        viewModel.runAutoDriveRepair()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Text("CONFIRM (تأكيد)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAutoRepairDialog = false }) {
                    Text("CANCEL (إلغاء)", color = TerminalText)
                }
            }
        )
    }

    // GENERAL CONFIRMATION PROMPTS FOR PARTITION ACTIONS ON HEALTHY DRIVES
    if (showActionConfirmationDialog && activePart != null) {
        AlertDialog(
            onDismissRequest = { showActionConfirmationDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "CONFIRM SYSTEM DESTRUCTIVE ACTION",
                    color = NeonRed,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "You are performing a low-level alteration on partition #${activePart.id} (${activePart.label}). Proceeding will modify partition parameters.",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تنبيه: أنت تقوم بإجراء تعديل مباشر على القرص المخصص. قد يؤدي الاستمرار لفقد البيانات أو تغيير خريطة القطاعات.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showActionConfirmationDialog = false
                        when (pendingActionType) {
                            "DELETE" -> viewModel.deletePartition(activePart.id)
                            "WIPE" -> viewModel.startWipePartition(activePart.id)
                            "BOOTABLE" -> viewModel.toggleActivePartition(activePart.id)
                        }
                        pendingActionType = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("CONFIRM & APPLY (تأكيد وتطبيق)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActionConfirmationDialog = false
                    pendingActionType = null
                }) {
                    Text("CANCEL (إلغاء)", color = TerminalText)
                }
            }
        )
    }

    // CREATE DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "CREATE NEW GPT PARTITION (إنشاء قسم)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = createLabel,
                        onValueChange = { createLabel = it },
                        label = { Text("Volume Label (اسم القسم)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select File System (نظام الملفات):", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("NTFS", "FAT32", "exFAT", "ext4").forEach { fs ->
                            Button(
                                onClick = { createFs = fs },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (createFs == fs) NeonBlue else PurpleGrey40
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(fs, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = createSizeMb,
                        onValueChange = { createSizeMb = it },
                        label = { Text("Size in Megabytes (الحجم بالميجابايت)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ This will allocate sectors from the unallocated block pool.",
                        fontSize = 10.sp,
                        color = TerminalText.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val size = createSizeMb.toLongOrNull() ?: 500L
                        viewModel.createPartition(createLabel.take(15), createFs, size)
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("CREATE (إنشاء)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("CANCEL (إلغاء)", color = TerminalText) }
            }
        )
    }

    // FORMAT DIALOG
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "FORMAT VOLUME (تهيئة القسم)",
                    color = NeonRed,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Select Target File System Type (نظام الملفات المستهدف):", fontSize = 11.sp, color = TerminalText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("NTFS", "FAT32", "exFAT", "ext4").forEach { fs ->
                            Button(
                                onClick = { formatFs = fs },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (formatFs == fs) NeonBlue else PurpleGrey40
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(fs, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "⚠️ WARNING: Formatting will erase ALL data on this partition forever.",
                        fontSize = 11.sp,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تنبيه: ستقوم عملية التهيئة بمسح جميع الملفات والبيانات الموجودة في هذا القسم نهائياً.",
                        fontSize = 11.sp,
                        color = NeonAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activePart?.let { viewModel.formatPartition(it.id, formatFs) }
                        showFormatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("FORMAT VOLUME (تهيئة القسم)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("CANCEL (إلغاء)", color = TerminalText) }
            }
        )
    }

    // RESIZE DIALOG
    if (showResizeDialog) {
        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            containerColor = SlateCard,
            title = { Text("Resize Partition Boundaries", color = TerminalText, fontSize = 15.sp, fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    Text("Enter New Partition Size (MB):", fontSize = 12.sp, color = TerminalText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resizeSizeMb,
                        onValueChange = { resizeSizeMb = it },
                        label = { Text("Size in Megabytes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val size = resizeSizeMb.toLongOrNull()
                        if (size != null && activePart != null) {
                            viewModel.resizePartition(activePart.id, size)
                        }
                        showResizeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("Apply Boundaries")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResizeDialog = false }) { Text("Cancel", color = TerminalText) }
            }
        )
    }

    if (isPartitionOperating) {
        AlertDialog(
            onDismissRequest = {}, // Force complete without closing
            containerColor = Color(0xFF151821),
            modifier = Modifier.border(1.dp, SlateBorder, RoundedCornerShape(8.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NeonBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (partitionOpType) {
                            "CREATE" -> "CREATING PARTITION..."
                            "DELETE" -> "DELETING PARTITION..."
                            "FORMAT" -> "FORMATTING PARTITION..."
                            "RESIZE" -> "RESIZING PARTITION..."
                            else -> "PARTITION MAGIC ENGINE..."
                        },
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = when (partitionOpType) {
                            "CREATE" -> "جاري إنشاء القسم البرمجي ومزامنة جدول GPT..."
                            "DELETE" -> "جاري مسح وسجل حرق التوصيفات وإلغاء تهيئة القسم..."
                            "FORMAT" -> "جاري عمل تهيئة منخفضة المستوى وإعادة كتابة قطاع الإقلاع..."
                            "RESIZE" -> "جاري إعادة توزيع مساحات القطاعات ونقل البيانات بأمان..."
                            else -> "جاري تنفيذ الإجراء على القرص الصلب..."
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = partitionOpStatusText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { partitionOpProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonBlue,
                        trackColor = SlateBorder
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(partitionOpProgress * 100).toInt()}% Complete",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalText.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Spacer(modifier = Modifier.size(1.dp))
            }
        )
    }
}

@Composable
fun DiskLayoutRow(
    diskNum: String,
    diskType: String,
    partitions: List<DiskPartBoxData>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF13161D))
            .border(1.dp, SlateBorder.copy(alpha = 0.5f))
            .clickable { onClick() }
    ) {
        // Left Column (Disk Info)
        Column(
            modifier = Modifier
                .width(82.dp)
                .fillMaxHeight()
                .background(Color(0xFF212530))
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(diskNum, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
            Text(diskType, fontSize = 7.5.sp, color = TerminalText, textAlign = TextAlign.Center, lineHeight = 9.sp)
        }

        VerticalDivider(color = SlateBorder.copy(alpha = 0.5f), thickness = 1.dp)

        // Right Row (Partitions)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(2.dp)
        ) {
            partitions.forEach { part ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(part.weight)
                        .padding(2.dp)
                        .background(if (part.isFailed) Color(0xFF2E1317) else part.color.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            color = if (part.isFailed) NeonRed else part.color.copy(alpha = 0.5f)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            text = part.label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (part.isFailed) NeonRed else Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = part.size,
                            fontSize = 7.5.sp,
                            color = TerminalText.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

data class DiskPartBoxData(
    val label: String,
    val size: String,
    val color: Color,
    val weight: Float,
    val isFailed: Boolean = false
)

@Composable
fun RecoveryWizardTab(viewModel: DiagnosticViewModel) {
    val connectedDrive by viewModel.connectedDrive.collectAsState()
    val isRecoveryActive by viewModel.isRecoveryActive.collectAsState()
    val recoveryProgress by viewModel.recoveryProgress.collectAsState()
    val recoveryMode by viewModel.recoveryMode.collectAsState()
    val recoveredFiles by viewModel.recoveredFiles.collectAsState()
    val recoveredPartitionsList by viewModel.recoveredPartitionsList.collectAsState()
    val destinationPath by viewModel.recoveryDestinationPath.collectAsState()
    val startLba by viewModel.recoveryStartLba.collectAsState()
    val context = LocalContext.current
    val sectorCount by viewModel.recoverySectorCount.collectAsState()

    var selectedRecoveryType by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var previewFile by remember { mutableStateOf<com.example.ui.DiagnosticViewModel.RecoveredFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Header
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(0.5.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADVANCED FORENSIC FILE RECOVERY (ICARE PRO)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
                Text(
                    text = "Advanced logical & deep physical-like sector carving algorithms to scan storage, rebuild databases, locate signature headers, and reconstruct deleted images or data.",
                    fontSize = 11.sp,
                    color = TerminalText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Recovery Mode Selection Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(SlateCard, RoundedCornerShape(8.dp))
                .border(0.5.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedRecoveryType = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedRecoveryType == 0) NeonGreen.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (selectedRecoveryType == 0) NeonGreen else TerminalText.copy(alpha = 0.6f)
                ),
                border = if (selectedRecoveryType == 0) BorderStroke(1.dp, NeonGreen) else null,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Internal Memory (الهاتف)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { selectedRecoveryType = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedRecoveryType == 1) NeonBlue.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (selectedRecoveryType == 1) NeonBlue else TerminalText.copy(alpha = 0.6f)
                ),
                border = if (selectedRecoveryType == 1) BorderStroke(1.dp, NeonBlue) else null,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("External OTG (جهاز خارجي)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Active Scanner UI
        if (isRecoveryActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { recoveryProgress },
                        color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (recoveryMode == "INTERNAL") "FORENSIC SWEEP ACTIVE: SCANNING SECTORS" else "DEEP CLUSTER ANALYSIS ACTIVE: CARVING BLOCK",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanned Clusters: ${(recoveryProgress * 153072).toLong()} / 153072 | Speed: 184 MB/s",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalText.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Live Sector Map!
                    ActiveSectorMap(progress = recoveryProgress)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { recoveryProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue,
                        trackColor = SlateBorder
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Progress: ${(recoveryProgress * 100).toInt()}% | Reconstructing fragments... Found files: ${recoveredFiles.size}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue
                    )
                }
            }
        } else if (recoveredFiles.isNotEmpty() && (recoveryMode == "FILE" || recoveryMode == "INTERNAL")) {
            // Found Files List Panel
            Text(
                text = "CARVED RECOVERED FILES (${recoveredFiles.size} IDENTIFIED)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf(
                    "ALL" to "📁 All",
                    "IMAGES" to "🖼️ Photos",
                    "VIDEOS" to "🎥 Videos",
                    "DOCS" to "📄 Docs",
                    "ARCHIVES" to "📦 Archives"
                )
                categories.forEach { (catKey, catLabel) ->
                    val isSelected = selectedCategory == catKey
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) (if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue).copy(alpha = 0.15f) else SlateCard,
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) (if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue) else SlateBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedCategory = catKey }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = catLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) (if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue) else TerminalText
                        )
                    }
                }
            }

            // Filtered Files List
            val filteredFiles = remember(recoveredFiles, selectedCategory) {
                recoveredFiles.filter { file ->
                    val ext = file.type.uppercase()
                    when (selectedCategory) {
                        "ALL" -> true
                        "IMAGES" -> ext in listOf("JPG", "JPEG", "PNG", "WEBP")
                        "VIDEOS" -> ext in listOf("MP4", "MKV", "AVI")
                        "DOCS" -> ext in listOf("PDF", "TXT", "DOCX", "XLSX")
                        "ARCHIVES" -> ext in listOf("ZIP", "RAR", "TAR", "7Z")
                        else -> true
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(0.5.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No recovered files found in this category.", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.5f))
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(0.5.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column {
                        filteredFiles.forEachIndexed { index, file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val fileIcon = when (file.type.uppercase()) {
                                    "JPG", "JPEG", "PNG", "WEBP" -> Icons.Default.Image
                                    "MP4", "MKV", "AVI" -> Icons.Default.PlayArrow
                                    "PDF", "TXT", "DOCX", "XLSX" -> Icons.Default.Description
                                    "ZIP", "RAR", "TAR", "7Z" -> Icons.Default.Folder
                                    else -> Icons.Default.Description
                                }

                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = fileIcon,
                                        contentDescription = null,
                                        tint = if (file.isRecovered) NeonGreen else (if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = file.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (file.isRecovered) NeonGreen else TerminalText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${file.sizeBytes / 1024} KB | Type: ${file.type} | Sig: ${file.signature}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TerminalText.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            
                                            val badgeColor = when (file.integrity) {
                                                "Excellent", "Excellent (Intact)", "Excellent (Forensic Carving)" -> NeonGreen
                                                "Good", "Intact" -> NeonAmber
                                                else -> Purple80
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                                                    .border(0.5.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(file.integrity.take(15), color = badgeColor, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { previewFile = file },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Preview", fontSize = 10.sp)
                                    }

                                    if (file.isRecovered) {
                                        Box(
                                            modifier = Modifier
                                                .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .border(0.5.dp, NeonGreen, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text("SAVED", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.recoverFile(file.name) },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Salvage", fontSize = 10.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                            if (index < filteredFiles.size - 1) {
                                HorizontalDivider(color = SlateBorder)
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = { 
                    viewModel.scanInternalStorage(android.os.Environment.getExternalStorageDirectory().absolutePath)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Start Fresh Storage Scan", color = if (recoveryMode == "INTERNAL") NeonGreen else NeonBlue)
            }
        } else if (recoveredPartitionsList.isNotEmpty() && recoveryMode == "PARTITION") {
            // TestDisk Results
            Text(
                text = "TESTDISK FOUND CORRUPTED PARTITIONS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalText,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(0.5.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    recoveredPartitionsList.forEachIndexed { index, part ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(part.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerminalText)
                                }
                                Text("Format: ${part.fileSystem} | Recovery confidence: 100% (MBR Signature Match)", fontSize = 10.sp, color = TerminalText.copy(alpha = 0.5f))
                            }
                            Text("${part.sizeBytes / (1024*1024*1024)} GB", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TerminalText)
                        }
                        if (index < recoveredPartitionsList.size - 1) {
                            HorizontalDivider(color = SlateBorder)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            recoveredPartitionsList.forEach {
                                viewModel.createPartition(it.label, it.fileSystem, it.sizeBytes / (1024*1024))
                            }
                            viewModel.addConsoleLog("TestDisk Success: Partition structures written to sector zero. Partition table successfully updated.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("WRITE PARTITION MAP TO MBR/GPT")
                    }
                }
            }
            TextButton(
                onClick = { viewModel.initializePartitions(connectedDrive?.capacityBytes ?: 4000000000L) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Back to Recovery Panel", color = NeonBlue)
            }
        } else {
            if (selectedRecoveryType == 0) {
                // Internal storage recovery panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(0.5.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FORENSIC PHONE STORAGE CARVER",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This mode performs a deep sector scan of unallocated system space, database containers, cache registers, and media dumps on the device's internal memory.",
                            fontSize = 11.sp,
                            color = TerminalText.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = destinationPath,
                            onValueChange = { viewModel.setRecoveryDestinationPath(it) },
                            label = { Text("Salvage Folder Path (مسار مجلد الحفظ الحقيقي)") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalText),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen,
                                unfocusedBorderColor = SlateBorder,
                                focusedLabelColor = NeonGreen,
                                unfocusedLabelColor = TerminalText.copy(alpha = 0.5f)
                            )
                        )

                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    if (!android.os.Environment.isExternalStorageManager()) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.setData(android.net.Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.scanInternalStorage(android.os.Environment.getExternalStorageDirectory().absolutePath)
                                    }
                                } else {
                                    viewModel.scanInternalStorage(android.os.Environment.getExternalStorageDirectory().absolutePath)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, NeonGreen),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("COMMENCE FORENSIC STORAGE SCAN", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Requires All Files Access permission to browse system thumbnails and unallocated blocks.",
                            fontSize = 9.sp,
                            color = TerminalText.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                if (connectedDrive == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(0.5.dp, SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "EXTERNAL DRIVE RECOVERY OFFLINE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NeonAmber
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "To scan external OTG hard disks or flash drives, connect a physical USB drive to your smartphone, or select a virtual PC-3000 drive profile from the Drive Profile selector at the top of the screen.",
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(0.5.dp, SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "REAL SECTOR-LEVEL EXTERNAL RECOVERY CONFIG",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonBlue,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = destinationPath,
                                onValueChange = { viewModel.setRecoveryDestinationPath(it) },
                                label = { Text("Salvage Storage Directory") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalText),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonBlue,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedLabelColor = NeonBlue,
                                    unfocusedLabelColor = TerminalText.copy(alpha = 0.5f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = startLba.toString(),
                                    onValueChange = { newValue ->
                                        val parsed = newValue.toLongOrNull() ?: 0L
                                        viewModel.setRecoveryStartLba(parsed)
                                    },
                                    label = { Text("Start LBA") },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalText),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonBlue,
                                        unfocusedBorderColor = SlateBorder,
                                        focusedLabelColor = NeonBlue,
                                        unfocusedLabelColor = TerminalText.copy(alpha = 0.5f)
                                    )
                                )

                                OutlinedTextField(
                                    value = sectorCount.toString(),
                                    onValueChange = { newValue ->
                                        val parsed = newValue.toLongOrNull() ?: 1000L
                                        viewModel.setRecoverySectorCount(parsed)
                                    },
                                    label = { Text("Sectors Count") },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalText),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonBlue,
                                        unfocusedBorderColor = SlateBorder,
                                        focusedLabelColor = NeonBlue,
                                        unfocusedLabelColor = TerminalText.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(0.5.dp, SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clickable { viewModel.startPartitionRecovery() }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(32.dp))
                                Text("TestDisk Partition", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerminalText, textAlign = TextAlign.Center)
                                Text("Scan boundaries to salvage partition tables.", fontSize = 8.sp, color = TerminalText.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                                Button(
                                    onClick = { viewModel.startPartitionRecovery() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("TESTDISK", fontSize = 9.sp, color = Color.Black)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(0.5.dp, SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clickable { viewModel.startDeepFileCarver() }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(32.dp))
                                Text("Deep File Carver", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerminalText, textAlign = TextAlign.Center)
                                Text("Sift raw bytes for magic headers (JPG, ZIP...).", fontSize = 8.sp, color = TerminalText.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                                Button(
                                    onClick = { viewModel.startDeepFileCarver() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("SCALPEL", fontSize = 9.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewFile != null) {
        val file = previewFile!!
        val hexDumpLines = remember(file.rawBytes) { getHexDumpRows(file.rawBytes) }
        val bitmapImage = remember(file.rawBytes) {
            file.rawBytes?.let { bytes ->
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }

        Dialog(onDismissRequest = { previewFile = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, NeonGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FORENSIC CARVED PREVIEW (معاينة الملف المسترجع)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(0.5.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Name: ${file.name}", fontSize = 10.sp, color = TerminalText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Size: ${file.sizeBytes} bytes", fontSize = 10.sp, color = TerminalText)
                            Text("Header Signature: ${file.signature}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonGreen)
                            Text("Integrity Estimation: ${file.integrity}", fontSize = 10.sp, color = NeonAmber)
                        }
                    }

                    Text(
                        text = "RECONSTRUCTED PAYLOAD DATA:",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalText.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                            .border(0.5.dp, SlateBorder)
                            .padding(8.dp)
                    ) {
                        if (bitmapImage != null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmapImage,
                                    contentDescription = "Carved Image Payload",
                                    modifier = Modifier
                                        .size(140.dp)
                                        .border(1.dp, NeonGreen)
                                        .background(Color.DarkGray)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("SUCCESSFUL JPEG DECODING: 100% OK", fontSize = 9.sp, color = NeonGreen, fontFamily = FontFamily.Monospace)
                            }
                        } else if (file.type.uppercase() in listOf("TXT", "XML", "JSON", "PDF")) {
                            val textStr = remember(file.rawBytes) {
                                file.rawBytes?.let { bytes ->
                                    try {
                                        String(bytes, Charsets.UTF_8).take(400)
                                    } catch (e: Exception) {
                                        "Unable to parse characters."
                                    }
                                } ?: ""
                            }
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = textStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TerminalText
                                )
                            }
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                hexDumpLines.forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = TerminalText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { previewFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close")
                        }

                        if (!file.isRecovered) {
                            Button(
                                onClick = {
                                    viewModel.recoverFile(file.name)
                                    previewFile = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Salvage File", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSectorMap(progress: Float) {
    val blockCount = 96
    val columns = 12
    val scannedCount = (progress * blockCount).toInt()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(1.dp, SlateBorder)
            .padding(8.dp)
    ) {
        Text(
            text = "LIVE CLUSTER ANALYSIS PLOT (خريطة قطاعات الذاكرة التفاعلية)",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        val rows = blockCount / columns
        Column {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        val isScanned = index < scannedCount
                        val isCurrent = index == scannedCount
                        
                        val color = when {
                            isCurrent -> NeonBlue
                            isScanned -> {
                                if (index % 11 == 0) NeonGreen
                                else if (index % 19 == 0) NeonAmber
                                else Purple80.copy(alpha = 0.6f)
                            }
                            else -> SlateBorder.copy(alpha = 0.2f)
                        }
                        
                        Box(
                            modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(color, RoundedCornerShape(1.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem("Unscanned", SlateBorder.copy(alpha = 0.2f))
            LegendItem("Sectors OK", Purple80.copy(alpha = 0.6f))
            LegendItem("Carved JPG", NeonGreen)
            LegendItem("Active LBA", NeonBlue)
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TerminalText.copy(alpha = 0.7f))
    }
}

fun getHexDumpRows(bytes: ByteArray?, maxBytes: Int = 128): List<String> {
    val data = bytes ?: ByteArray(maxBytes)
    val result = mutableListOf<String>()
    val actualLen = minOf(data.size, maxBytes)
    for (i in 0 until actualLen step 16) {
        val hexPart = StringBuilder()
        val asciiPart = StringBuilder()
        for (j in 0 until 16) {
            if (i + j < actualLen) {
                val b = data[i + j]
                hexPart.append(String.format("%02X ", b))
                val c = b.toInt().toChar()
                if (c in ' '..'~') {
                    asciiPart.append(c)
                } else {
                    asciiPart.append(".")
                }
            } else {
                hexPart.append("   ")
            }
        }
        val address = String.format("%04X", i)
        result.add("$address  $hexPart |$asciiPart|")
    }
    return result
}

@Composable
fun EmptyDriveState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .border(1.dp, SlateBorder)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SettingsInputHdmi,
                contentDescription = "USB Connection Offline",
                tint = NeonAmber,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DRIVE INTERFACE OFFLINE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Purple80
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = TerminalText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun UsbOtgDriveSummaryPanel(viewModel: DiagnosticViewModel) {
    val connectedDrive by viewModel.connectedDrive.collectAsState()

    if (connectedDrive != null) {
        val drive = connectedDrive!!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("usb_otg_summary_panel"),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PanelTechHeader(
                    title = "USB-OTG Identification Details",
                    subtitle = "SATA PROTOCOL BRIDGING"
                )
                Column(modifier = Modifier.padding(14.dp)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column
                    Column(modifier = Modifier.weight(1.1f)) {
                        DriveDetailRow("Manufacturer", drive.vendor)
                        DriveDetailRow("Model Name", drive.model)
                        DriveDetailRow("Firmware Rev", drive.firmware)
                        DriveDetailRow("Serial Number", drive.serial)
                    }
                    
                    // Right Column
                    Column(modifier = Modifier.weight(0.9f)) {
                        val capGb = drive.capacityBytes / (1024L * 1024L * 1024L)
                        val capFormatted = if (capGb >= 1000) "${String.format("%.1f", capGb.toFloat() / 1024f)} TB" else "$capGb GB"
                        
                        DriveDetailRow("Total Capacity", capFormatted)
                        DriveDetailRow("Sectors Count", String.format("%,d", drive.totalSectors))
                        DriveDetailRow("Sector Size", "${drive.sectorSize} Bytes")
                        DriveDetailRow("Interface", drive.connectionType)
                    }
                }
            }
        }
    }
}
}

@Composable
fun DriveDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            color = TerminalText.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AutoRepairSection(viewModel: DiagnosticViewModel) {
    val issue by viewModel.currentDriveIssue.collectAsState()
    val isAutoRepairing by viewModel.isAutoRepairing.collectAsState()
    val autoRepairProgress by viewModel.autoRepairProgress.collectAsState()
    val autoRepairStatusLog by viewModel.autoRepairStatusLog.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    if (issue != DiagnosticViewModel.DriveIssue.NONE) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("auto_repair_banner"),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, Purple80),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Auto Fix",
                        tint = Purple80,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI AUTO-REPAIR & FIRMWARE RESTORATION",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Purple80
                        )
                        Text(
                            text = "تشخيص الفحص المتقدم وتوصيات الإصلاح الفوري",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Detected Failure: ${issue.titleEn}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "العطل المكتشف: ${issue.titleAr}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonAmber,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "Recommended Solution: ${issue.recommendationEn}",
                    fontSize = 11.sp,
                    color = TerminalText.copy(alpha = 0.8f)
                )
                Text(
                    text = "الحل المقترح: ${issue.recommendationAr}",
                    fontSize = 10.5.sp,
                    color = TerminalText.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isAutoRepairing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF05070A), RoundedCornerShape(4.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = autoRepairStatusLog,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { autoRepairProgress },
                            color = Purple80,
                            trackColor = SlateBorder,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(autoRepairProgress * 100).toInt()}% Complete",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TerminalText,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Button(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("apply_auto_repair_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Repair Icon",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "APPLY AUTONOMOUS DRIVE REPAIR (إصلاح وتجاوز العطل تلقائياً)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "CONFIRM AUTONOMOUS DRIVE REPAIR",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "You are about to issue low-level microcode patches to repair the detected issue: '${issue.titleEn}'.",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "هل أنت متأكد من تطبيق عملية الصيانة التلقائية وإعادة هيكلة الفيرموير لحل مشكلة: '${issue.titleAr}'؟",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "This action will rebuild the sector translation maps, adjust target voltage rails, and clear any firmware serial terminal blocks. Make sure you don't disconnect the drive.",
                        fontSize = 9.5.sp,
                        color = TerminalText.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.runAutoDriveRepair()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                ) {
                    Text("CONFIRM (تأكيد)", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCEL (إلغاء)", color = TerminalText)
                }
            }
        )
    }
}

@Composable
fun ActiveSmartAlertsBanner(viewModel: DiagnosticViewModel) {
    val alerts by viewModel.activeAlerts.collectAsState()

    if (alerts.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            alerts.forEach { alert ->
                val containerColor = if (alert.severity == DiagnosticViewModel.AlertSeverity.CRITICAL) {
                    NeonRed.copy(alpha = 0.12f)
                } else {
                    NeonAmber.copy(alpha = 0.12f)
                }
                val borderColor = if (alert.severity == DiagnosticViewModel.AlertSeverity.CRITICAL) NeonRed else NeonAmber
                val icon = if (alert.severity == DiagnosticViewModel.AlertSeverity.CRITICAL) Icons.Default.Dangerous else Icons.Default.ReportProblem

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("smart_alert_${alert.id}"),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = alert.severity.name,
                            tint = borderColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "S.M.A.R.T. ALERT: ${alert.attributeName.uppercase()}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = borderColor
                                )
                                Box(
                                    modifier = Modifier
                                        .background(borderColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = alert.severity.name,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = borderColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = alert.message,
                                fontSize = 11.sp,
                                color = TerminalText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Current Raw Value: ${alert.currentValue} | Threshold: ${alert.threshold}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TerminalText.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissAlert(alert.id) },
                            modifier = Modifier.size(28.dp).testTag("dismiss_alert_${alert.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TerminalText.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectorRemapWizardComponent(viewModel: DiagnosticViewModel) {
    val remapStep by viewModel.remapStep.collectAsState()
    val badSectors by viewModel.detectedBadSectors.collectAsState()
    val selectedSector by viewModel.selectedRemapSector.collectAsState()
    val progress by viewModel.remapProgress.collectAsState()
    
    var isConfirmed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("sector_remap_wizard_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SECTOR REMAP ASSISTANT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Purple80
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Guided wizard to safely relocate damaged sectors using physical drive remapping commands.",
                fontSize = 11.sp,
                color = TerminalText.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Step Progress indicators
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val steps = listOf("IDENTIFY", "SPARE CHECK", "CONFIRM", "EXECUTE")
                steps.forEachIndexed { idx, title ->
                    val isCurrent = idx == remapStep.ordinal || (remapStep == DiagnosticViewModel.RemapStep.COMPLETED && idx == 3)
                    val isPassed = idx < remapStep.ordinal || remapStep == DiagnosticViewModel.RemapStep.COMPLETED
                    val color = if (isCurrent) Purple80 else if (isPassed) NeonGreen else TerminalText.copy(alpha = 0.3f)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(color.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (idx + 1).toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = color
                        )
                    }
                    if (idx < 3) {
                        Divider(
                            modifier = Modifier.weight(0.1f).padding(horizontal = 4.dp),
                            color = if (isPassed) NeonGreen else SlateBorder,
                            thickness = 1.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (remapStep) {
                DiagnosticViewModel.RemapStep.IDENTIFY -> {
                    Column {
                        Text(
                            text = "STEP 1: IDENTIFY TARGET DAMAGED SECTORS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (badSectors.isEmpty()) {
                            Text(
                                text = "No uncorrectable sector addresses loaded in memory. Press the button below to scan platter boundaries and populate detected bad sectors.",
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.scanForBadSectors() },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("remap_scan_bad_btn")
                            ) {
                                Text("LOAD CANDIDATE BAD SECTORS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TerminalText)
                            }
                        } else {
                            Text(
                                text = "Select identified physical block address (LBA):",
                                fontSize = 11.sp,
                                color = TerminalText.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            badSectors.forEach { sector ->
                                val isSel = selectedSector == sector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectRemapSector(sector) }
                                        .background(if (isSel) Purple80.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (isSel) Purple80 else SlateBorder, RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = "Bad LBA",
                                            tint = NeonRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "LBA: 0x${java.lang.Long.toHexString(sector).uppercase()}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TerminalText
                                        )
                                    }
                                    Text(
                                        text = "STATUS: UNREADABLE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = NeonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.setRemapStep(DiagnosticViewModel.RemapStep.SPARE_CHECK) },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.End).testTag("remap_step1_next")
                            ) {
                                Text("PROCEED TO SPARE CHECK", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                DiagnosticViewModel.RemapStep.SPARE_CHECK -> {
                    Column {
                        Text(
                            text = "STEP 2: DRIVE HEALTH & SPARE SECTOR RESERVE CHECK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Low-level remapping requires available physical 'spare' sectors in the drive's reserved service area. Inspecting S.M.A.R.T. Attribute 0x05 (Reallocated Sectors Count) and 0xC4 (Reallocation Event Count).",
                            fontSize = 11.sp,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateDark, RoundedCornerShape(4.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Spare Sectors Remaining", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.6f))
                                Text("S.M.A.R.T. Health Status", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.6f))
                                Text("Relocation Capacity", fontSize = 11.sp, color = TerminalText.copy(alpha = 0.6f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("94% AVAILABLE", fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = FontFamily.Monospace)
                                Text("PASSED", fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = FontFamily.Monospace)
                                Text("SUFFICIENT", fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = FontFamily.Monospace)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { viewModel.setRemapStep(DiagnosticViewModel.RemapStep.IDENTIFY) }) {
                                Text("Back", fontFamily = FontFamily.Monospace, color = TerminalText)
                            }
                            Button(
                                onClick = { viewModel.setRemapStep(DiagnosticViewModel.RemapStep.CONFIRM) },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("remap_step2_next")
                            ) {
                                Text("CONTINUE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                DiagnosticViewModel.RemapStep.CONFIRM -> {
                    Column {
                        Text(
                            text = "STEP 3: DESTRUCTIVE WRITE OPERATION CONFIRMATION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = NeonRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CRITICAL WRITE OPERATION WARNING",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = NeonRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To trigger the drive's built-in firmware reallocation sequence, this tool will issue a low-level WRITE command directly targeting LBA address 0x${java.lang.Long.toHexString(selectedSector ?: 0L).uppercase()}.\n\nThis will force-overwrite any degraded, unreadable charge values at this physical coordinate. Any unrecovered, fragmented bits of data currently trapped on this sector will be erased and lost.",
                                    fontSize = 10.5.sp,
                                    color = TerminalText
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isConfirmed = !isConfirmed }
                        ) {
                            Checkbox(
                                checked = isConfirmed,
                                onCheckedChange = { isConfirmed = it },
                                modifier = Modifier.testTag("remap_confirm_checkbox"),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Purple80,
                                    uncheckedColor = SlateBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I confirm that I want to perform sector reallocation and write directly to LBA 0x${java.lang.Long.toHexString(selectedSector ?: 0L).uppercase()}.",
                                fontSize = 10.sp,
                                color = TerminalText
                             )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { viewModel.setRemapStep(DiagnosticViewModel.RemapStep.SPARE_CHECK) }) {
                                Text("Back", fontFamily = FontFamily.Monospace, color = TerminalText)
                            }
                            Button(
                                onClick = { viewModel.startRemapOperation { isConfirmed = false } },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                shape = RoundedCornerShape(4.dp),
                                enabled = isConfirmed,
                                modifier = Modifier.testTag("initiate_remap_btn")
                            ) {
                                Text("INITIATE WRITE REALLOCATION", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                DiagnosticViewModel.RemapStep.EXECUTING -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Purple80,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "WRITING RAW BLOCKS TO LBA 0x${java.lang.Long.toHexString(selectedSector ?: 0L).uppercase()}...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple80
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Awaiting HDD microcontroller relocation acknowledgment.",
                            fontSize = 10.sp,
                            color = TerminalText.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Purple80,
                            trackColor = SlateBorder,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalText
                        )
                    }
                }
                DiagnosticViewModel.RemapStep.COMPLETED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = NeonGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SECTOR RELOCATION SUCCESSFUL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The sector at address 0x${java.lang.Long.toHexString(selectedSector ?: 0L).uppercase()} has been completely remapped. The disk's internal controller now permanently redirects reads/writes from this address to a healthy backup block.",
                            fontSize = 11.sp,
                            color = TerminalText.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.setRemapStep(DiagnosticViewModel.RemapStep.IDENTIFY) },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("remap_close_wizard_btn")
                        ) {
                            Text("REMAP ANOTHER SECTOR", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaAtaDirectTab(viewModel: DiagnosticViewModel) {
    val selectedAtaCmd by viewModel.selectedAtaCmd.collectAsState()
    val isAtaExecuting by viewModel.isAtaExecuting.collectAsState()
    val hexDumpOutput by viewModel.hexDumpOutput.collectAsState()
    val enabledHeads by viewModel.enabledHeads.collectAsState()
    val isFirmwareApplying by viewModel.isFirmwareApplying.collectAsState()
    val firmwareProgress by viewModel.firmwareProgress.collectAsState()

    val bsy by viewModel.ataStatusBSY.collectAsState()
    val drdy by viewModel.ataStatusDRDY.collectAsState()
    val df by viewModel.ataStatusDF.collectAsState()
    val drq by viewModel.ataStatusDRQ.collectAsState()
    val err by viewModel.ataStatusERR.collectAsState()

    // Low-level newly introduced states
    val defectList by viewModel.defectList.collectAsState()
    val firmwareModules by viewModel.firmwareModules.collectAsState()
    val romHeadMap by viewModel.romHeadMap.collectAsState()
    val romBytes by viewModel.romBytes.collectAsState()
    val isRomReadingWriting by viewModel.isRomReadingWriting.collectAsState()

    val commands = listOf(
        "0xEC - IDENTIFY DEVICE",
        "0x20 - READ SECTORS (LBA 0x0)",
        "0x30 - WRITE SECTORS (LBA 0x0)",
        "0xE0 - SPIN UP MOTOR",
        "0xE1 - SPIN DOWN MOTOR",
        "0xF1 - SECURITY LOCK",
        "0xF2 - SECURITY UNLOCK",
        "0x90 - EXECUTE DIAGNOSTICS"
    )

    var showDropdown by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableStateOf(0) }
    val subTabs = listOf("1. ATA CONTROL", "2. DEFECT TABLES", "3. ROM & SA MODULES")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        item {
            Text(
                text = "LOW-LEVEL SERVICE AREA (SA) & DIRECT ATA CONTROLLER",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Purple80
            )
            Text(
                text = "Access physical disk cylinders, microcode overlays, pre-amplifiers, and directly execute ATA interface commands.",
                fontSize = 11.sp,
                color = TerminalText.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                subTabs.forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selectedSubTab == index) Purple80.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selectedSubTab == index) Purple80 else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { selectedSubTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (selectedSubTab == index) Purple80 else TerminalText.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- SUB-TAB 0: ATA DIRECT CONTROLLER ---
        if (selectedSubTab == 0) {
            // --- SECTION 1: ATA DIRECT COMMAND TRANSMITTER ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DIRECT ATA COMMAND REGISTER TRANSMITTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dropdown Selection
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showDropdown = true },
                                    border = BorderStroke(1.dp, SlateBorder),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("ata_cmd_dropdown_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedAtaCmd,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Purple80
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Command",
                                            tint = TerminalText
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false },
                                    modifier = Modifier.background(SlateCard).border(1.dp, SlateBorder)
                                ) {
                                    commands.forEach { cmd ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    cmd,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = TerminalText
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSelectedAtaCmd(cmd)
                                                showDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = { viewModel.executeAtaCommand() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                shape = RoundedCornerShape(4.dp),
                                enabled = !isAtaExecuting && !isFirmwareApplying,
                                modifier = Modifier.testTag("execute_ata_cmd_btn")
                            ) {
                                if (isAtaExecuting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp))
                                } else {
                                    Text(
                                        "SEND CMD",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Simulated ATA Controller Register Lights (BSY, DRDY, DF, DRQ, ERR)
                        Text(
                            text = "IDE / SATA Interface Register Status LEDs:",
                            fontSize = 10.sp,
                            color = TerminalText.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusLedIndicator(label = "BSY", isActive = bsy, activeColor = NeonAmber)
                            StatusLedIndicator(label = "DRDY", isActive = drdy, activeColor = NeonGreen)
                            StatusLedIndicator(label = "DF", isActive = df, activeColor = NeonRed)
                            StatusLedIndicator(label = "DRQ", isActive = drq, activeColor = NeonBlue)
                            StatusLedIndicator(label = "ERR", isActive = err, activeColor = NeonRed)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // --- SECTION 2: COMMAND OUTPUT DUMP ---
            if (hexDumpOutput.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RAW SECTOR / BUFFER HEX DUMP (512 BYTES)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen
                                )
                                Text(
                                    text = "Sector LBA: 0x00000000",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = TerminalText.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            hexDumpOutput.forEach { line ->
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    color = NeonGreen,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // --- SECTION 3: PHYSICAL HEADS PRE-AMP CONFIG ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "HARDWARE PRE-AMPLIFIER & HEAD MAP ISOLATION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Isolate or bypass clicking, physically degraded heads. Disabling damaged channels stops surface scraping and permits read operations on surviving platter sides.",
                            fontSize = 10.5.sp,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (head in 0..3) {
                                val isActive = enabledHeads.contains(head)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) Purple80.copy(alpha = 0.08f) else Color.Red.copy(alpha = 0.05f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive) Purple80 else Color.Red.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .clickable { viewModel.togglePhysicalHead(head) }
                                        .testTag("head_toggle_$head"),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = "Head status",
                                            tint = if (isActive) NeonGreen else NeonRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "HEAD $head",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isActive) TerminalText else NeonRed
                                        )
                                        Text(
                                            text = if (isActive) "ACTIVE" else "BYPASSED",
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isActive) NeonGreen else NeonRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- SUB-TAB 1: DEFECT TABLES (P-LIST & G-LIST) ---
        if (selectedSubTab == 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PLATED PLATER RECORDED DEFECT TABLES (P-LIST & G-LIST)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Primary List (P-List) represents factory-mapped surface scars. Grown List (G-List) maps bad sectors discovered during active drive operation onto reserved spare cylinders.",
                            fontSize = 10.5.sp,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Actions Panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.syncDefectsFromScanner() },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).testTag("sync_defects_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = SlateDark)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SYNC HEATMAP", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearGList() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).testTag("clear_glist_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CLEAR G-LIST", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Defect Interactive Add Form
            item {
                var customLbaText by remember { mutableStateOf("") }
                var defectType by remember { mutableStateOf("Physical Surface") }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "APPEND SECTOR MANUALLY TO G-LIST",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customLbaText,
                                onValueChange = { customLbaText = it.filter { c -> c.isDigit() } },
                                label = { Text("LBA Sector Number", color = TerminalText.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f).testTag("manual_defect_lba_field"),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Purple80,
                                    unfocusedBorderColor = SlateBorder
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val lba = customLbaText.toLongOrNull()
                                    if (lba != null) {
                                        viewModel.addDefectToGList(lba, defectType)
                                        customLbaText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(4.dp),
                                enabled = customLbaText.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("add_defect_glist_btn")
                            ) {
                                Text("MAP SECTOR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Defect Table
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DEFECT TRANSLATION MAP REGISTER (${defectList.size} ITEMS)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple80
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Table Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateCard)
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text("LBA SECTOR", modifier = Modifier.weight(1.2f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("CYL/HD/SEC", modifier = Modifier.weight(1.2f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("CLASS TYPE", modifier = Modifier.weight(1.4f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("SRC", modifier = Modifier.weight(0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText, textAlign = TextAlign.End)
                        }

                        if (defectList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Defect tables empty. Run translator repair to load.", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalText.copy(alpha = 0.5f))
                            }
                        } else {
                            defectList.forEach { defect ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, SlateBorder.copy(alpha = 0.3f))
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "${defect.lba}", modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                    Text(text = "C:${defect.cylinder}/H:${defect.head}/S:${defect.sector}", modifier = Modifier.weight(1.2f), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = TerminalText)
                                    Text(text = defect.type, modifier = Modifier.weight(1.4f), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = if (defect.type.contains("Surface")) NeonAmber else NeonBlue)
                                    Text(
                                        text = defect.source,
                                        modifier = Modifier.weight(0.7f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (defect.source == "P-List") Color.Gray else NeonGreen,
                                        textAlign = TextAlign.End,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- SUB-TAB 2: ROM CHIP & FIRMWARE MODULE RESTORE ---
        if (selectedSubTab == 2) {
            // Service Area Firmware Repair
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ACE LAB PC-3000 RECONSTRUCT TRANSLATOR & RESTORE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Common firmware failures block system BIOS recognition. Direct microcode patches restore low-level drive translation tables.",
                            fontSize = 10.5.sp,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isFirmwareApplying) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF05070A), RoundedCornerShape(4.dp))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Purple80, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "FLASHING ROM CHIP / REWRITING SA CYLINDERS...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Purple80
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { firmwareProgress },
                                    color = Purple80,
                                    trackColor = SlateBorder,
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${(firmwareProgress * 100).toInt()}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TerminalText
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Fix 1: Translator
                                FirmwareFixRow(
                                    title = "Regenerate Translator (P-List / G-List)",
                                    desc = "Fixes 0 LBA capacity or translation lock errors. Re-indexes factory defect tables.",
                                    actionName = "TRANSLATOR",
                                    isEnabled = !isAtaExecuting,
                                    onApply = { viewModel.applyFirmwareFix("TRANSLATOR") }
                                )

                                Divider(color = SlateBorder, thickness = 0.5.dp)

                                // Fix 2: WD Slow Fix
                                FirmwareFixRow(
                                    title = "WD Mod 02/32 Slow-Responding Repair",
                                    desc = "Bypasses loop read retry. Prevents continuous drive hanging on weak platters.",
                                    actionName = "WD_SLOW",
                                    isEnabled = !isAtaExecuting,
                                    onApply = { viewModel.applyFirmwareFix("WD_SLOW") }
                                )

                                Divider(color = SlateBorder, thickness = 0.5.dp)

                                // Fix 3: CC49 Seagate Unlock
                                FirmwareFixRow(
                                    title = "Seagate CC49 Terminal Lock Reset",
                                    desc = "Sends F3 serial commands to clear bus assertion state and reset internal SMART tables.",
                                    actionName = "SEAGATE_LOCK",
                                    isEnabled = !isAtaExecuting,
                                    onApply = { viewModel.applyFirmwareFix("SEAGATE_LOCK") }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ROM HEX Editor & Head Map Toggler
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "512KB EEPROM ROM CONTROLLER CHIP PROGRAMMER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Modify head adaptives and routing tables inside the ROM microcode. Rewriting head maps skips physical head pre-amps that are shorted or burned out.",
                            fontSize = 10.5.sp,
                            color = TerminalText.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Active Heads inside ROM Checkbox/Switch row
                        Text(
                            text = "ACTIVE ROUTING HEAD MAP IN ROM CODE:",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (head in 0..3) {
                                val isEnabledInRom = romHeadMap.contains(head)
                                OutlinedButton(
                                    onClick = { viewModel.toggleRomHead(head) },
                                    border = BorderStroke(1.dp, if (isEnabledInRom) Purple80 else Color.Gray.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isEnabledInRom) Purple80.copy(alpha = 0.08f) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp).testTag("rom_head_$head")
                                ) {
                                    Text(
                                        text = "H$head: ${if (isEnabledInRom) "ON" else "OFF"}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = if (isEnabledInRom) Purple80 else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        // HEX viewer of ROM Code
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "ROM Microcode Map Hex Buffer Display:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = NeonGreen
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                romBytes.forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = NeonGreen
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // ROM Actions: Recalculate checksum, flash ROM over terminal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.recalculateRomChecksum() },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1.1f).testTag("rom_calc_cs_btn")
                            ) {
                                Text("RECALC CHECKSUM", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SlateDark, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.flashRomOverTerminal() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                shape = RoundedCornerShape(4.dp),
                                enabled = !isRomReadingWriting,
                                modifier = Modifier.weight(1f).testTag("rom_flash_btn")
                            ) {
                                if (isRomReadingWriting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp))
                                } else {
                                    Text("FLASH ROM SERIAL", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Firmware Modules directory explorer list
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "FIRMWARE SERVICE AREA (SA) MODULE DIRECTORY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple80
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Module list headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateCard)
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text("ID", modifier = Modifier.weight(0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("MODULE NAME", modifier = Modifier.weight(2f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("SIZE/LOC", modifier = Modifier.weight(1.3f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("STATUS", modifier = Modifier.weight(1f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText)
                            Text("ACTIONS", modifier = Modifier.weight(1.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TerminalText, textAlign = TextAlign.End)
                        }

                        firmwareModules.forEach { module ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, SlateBorder.copy(alpha = 0.3f))
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = module.id, modifier = Modifier.weight(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Purple80, fontWeight = FontWeight.Bold)
                                Text(text = module.name, modifier = Modifier.weight(2f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                Text(text = "${module.sizeSectors}s / SA", modifier = Modifier.weight(1.3f), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = TerminalText)
                                Text(
                                    text = module.status,
                                    modifier = Modifier.weight(1.1f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = when (module.status) {
                                        "OK" -> NeonGreen
                                        "CORRUPT" -> NeonAmber
                                        else -> NeonRed
                                    },
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.weight(1.8f),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Diagnose Icon-Button
                                    IconButton(
                                        onClick = { viewModel.diagnoseSAModule(module) },
                                        modifier = Modifier.size(24.dp).testTag("diag_module_${module.id}")
                                    ) {
                                        Icon(Icons.Default.Troubleshoot, contentDescription = "Diagnose module", tint = Purple80, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Rewrite Icon-Button
                                    IconButton(
                                        onClick = { viewModel.rewriteSAModule(module) },
                                        modifier = Modifier.size(24.dp).testTag("rewrite_module_${module.id}")
                                    ) {
                                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore module", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StatusLedIndicator(label: String, isActive: Boolean, activeColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF040608), RoundedCornerShape(4.dp))
            .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "led_pulse")
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "led_glow"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "led_glow_alpha"
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
                        .background(activeColor.copy(alpha = glowAlpha), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isActive) activeColor else Color(0xFF2E323E), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = if (isActive) Color.White else TerminalText.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun FirmwareFixRow(
    title: String,
    desc: String,
    actionName: String,
    isEnabled: Boolean,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = TerminalText
            )
            Text(
                text = desc,
                fontSize = 9.5.sp,
                color = TerminalText.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            enabled = isEnabled,
            modifier = Modifier.testTag("apply_fw_$actionName")
        ) {
            Text(
                text = "APPLY FIX",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = SlateDark,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DiagnosticSplashScreen(viewModel: DiagnosticViewModel) {
    val splashLogs by viewModel.splashLogs.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll terminal logs to bottom as they arrive
    LaunchedEffect(splashLogs.size) {
        if (splashLogs.isNotEmpty()) {
            listState.animateScrollToItem(splashLogs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Header Image
            Image(
                painter = painterResource(id = R.drawable.img_hdd_splash_1782914239184),
                contentDescription = "PC-3000 Hardware Interface Emblem",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Purple80, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Subtitle indicating professional system
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeonRed, CircleShape)
                )
                Text(
                    text = "ACE LAB PC-3000 MOBILE UTILITY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonRed,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Major Title
            Text(
                text = "HDD REPAIR CORE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "ADVANCED RUSSIAN FIRMWARE DEVIATION RECOVERY",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 9.sp,
                color = TerminalText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Console Boot log display box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF040608), RoundedCornerShape(6.dp))
                    .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(splashLogs) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (log.contains("COMPLETE") || log.contains("OK") || log.contains("PASSED")) NeonGreen 
                                    else if (log.contains("v6.8.12") || log.contains("ACE")) Purple80 
                                    else TerminalText.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Skip / Interactive proceed button
            Button(
                onClick = { viewModel.skipSplash() },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("skip_splash_button")
            ) {
                Text(
                    text = "ENTER DIAGNOSTICS WORKSPACE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = SlateDark
                )
            }
        }
    }
}

@Composable
fun ProScriptingTab(viewModel: DiagnosticViewModel) {
    val scriptText by viewModel.scriptText.collectAsState()
    val isScriptRunning by viewModel.isScriptRunning.collectAsState()
    val scriptLogs by viewModel.scriptLogs.collectAsState()
    val scriptProgress by viewModel.scriptProgress.collectAsState()

    val hexEditorLba by viewModel.hexEditorLba.collectAsState()
    val hexEditorBuffer by viewModel.hexEditorBuffer.collectAsState()
    val isHexLoading by viewModel.isHexLoading.collectAsState()

    var activeScriptTemplate by remember { mutableStateOf("WD_SLOW") }
    var selectedByteIndex by remember { mutableStateOf<Int?>(null) }
    var customByteHexValue by remember { mutableStateOf("") }
    
    // Auto-load sector 0 when entering
    LaunchedEffect(Unit) {
        viewModel.loadHexSector(0L)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // TAB HEADER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ACE LAB PC-3000 PRO SCRIPTING & SECTOR EDITOR IDE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple80
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Automate diagnostic microcode repairs, bypass translator lock-ups, run pre-amp sweep diagnostics, or directly write raw sector modifications onto physical block layers.",
                        fontSize = 10.5.sp,
                        color = TerminalText.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // --- SECTION 1: INTERACTIVE AUTOMATION SCRIPTING IDE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AUTOMATION SCRIPT EXECUTION UNIT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dropdown for script templates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TEMPLATE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalText.copy(alpha = 0.5f)
                        )
                        
                        val scripts = listOf(
                            "WD_SLOW" to "WD Slow-Responding Mod 02 Fix",
                            "SEAGATE_LOCK" to "Seagate LED:000000CC Bus Unlock",
                            "HEADS_SWEEPER" to "Heads Read/Write Latency Sweeper",
                            "CUSTOM" to "Custom ATA Sequence Builder"
                        )
                        
                        scripts.forEach { (key, label) ->
                            val isSelected = activeScriptTemplate == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) Purple80.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) Purple80 else SlateBorder.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                    .clickable { activeScriptTemplate = key }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label.split(" ")[0] + "..",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = if (isSelected) Purple80 else TerminalText.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Script Code Panel
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0F)),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SCRIPT TERMINAL CODE:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Purple80,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "STATUS: ${if (isScriptRunning) "RUNNING" else "READY"}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = if (isScriptRunning) NeonAmber else NeonGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val codeLines = when (activeScriptTemplate) {
                                "WD_SLOW" -> listOf(
                                    "// WD Mod 32 Slow Fix Script",
                                    "open_sa_layer();",
                                    "read_module(0x02, track=1);",
                                    "verify_checksum_parity();",
                                    "patch_offset(0x4B, val=0x05);",
                                    "recalculate_checksum();",
                                    "write_module(0x02);",
                                    "recalibrate_spindle_handshake();"
                                )
                                "SEAGATE_LOCK" -> listOf(
                                    "// Seagate LED:000000CC Terminal Unlock",
                                    "initiate_serial_handshake(115200);",
                                    "send_terminal_cmd(\"/2\");",
                                    "send_terminal_cmd(\"U\"); // Spin Up",
                                    "check_exception_register();",
                                    "clear_translator_map(); // F,,,,,,22",
                                    "clear_smart_nonvolatile();",
                                    "rebuild_translator();"
                                )
                                "HEADS_SWEEPER" -> listOf(
                                    "// Heads Read/Write Latency Sweeper",
                                    "initiate_heads_diagnostic();",
                                    "for head in 0..3 {",
                                    "  seek_cylinder(0x4000);",
                                    "  measure_read_latency(head);",
                                    "  print_latency_log();",
                                    "}"
                                )
                                else -> listOf(
                                    "// Custom Command Pipeline",
                                    "send_ata_command(0xEC); // Identify",
                                    "read_lba_sector(0);",
                                    "display_hex_buffer();"
                                )
                            }

                            codeLines.forEachIndexed { idx, line ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = String.format("%02d ", idx + 1),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (line.startsWith("//")) Color.Gray else TerminalText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Run Controls & Script Log View
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.runAtaScript(activeScriptTemplate) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            enabled = !isScriptRunning,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RUN AUTOMATION", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        if (isScriptRunning) {
                            LinearProgressIndicator(
                                progress = { scriptProgress },
                                color = Purple80,
                                trackColor = SlateBorder,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    if (scriptLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "MICROCODE LOG OUTPUT:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF030508)),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                scriptLogs.forEach { log ->
                                    Text(
                                        text = ">> $log",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = if (log.contains("Success") || log.contains("OK")) NeonGreen 
                                                else if (log.contains("WARNING") || log.contains("Weak")) NeonAmber 
                                                else if (log.contains("dead") || log.contains("CRITICAL")) NeonRed
                                                else TerminalText.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 2: INTERACTIVE SECTOR HEX EDITOR ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "RAW PLATTER BLOCK HEX EDITOR (512-BYTE)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Edit raw hex fields directly on sectors. Values are flushed through buffer streams onto the physical device platter.",
                        fontSize = 10.sp,
                        color = TerminalText.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // LBA selector control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hexEditorLba.toString(),
                            onValueChange = {
                                val value = it.toLongOrNull() ?: 0L
                                viewModel.setHexEditorLba(value)
                            },
                            label = { Text("LBA Address", color = TerminalText.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple80,
                                unfocusedBorderColor = SlateBorder
                            )
                        )

                        Button(
                            onClick = { viewModel.loadHexSector(hexEditorLba) },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                            shape = RoundedCornerShape(4.dp),
                            enabled = !isHexLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isHexLoading) {
                                CircularProgressIndicator(color = SlateDark, modifier = Modifier.size(12.dp))
                            } else {
                                Text("LOAD SECTOR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SlateDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Byte inspector panel
                    selectedByteIndex?.let { index ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1017)),
                            border = BorderStroke(1.dp, Purple80.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SELECTED OFFSET: 0x${String.format("%03X", index)} (${index})",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Purple80,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "CURRENT VALUE: 0x${String.format("%02X", hexEditorBuffer.getOrElse(index) { 0 })}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = TerminalText.copy(alpha = 0.7f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customByteHexValue,
                                        onValueChange = { input ->
                                            val filtered = input.uppercase().filter { it in "0123456789ABCDEF" }.take(2)
                                            customByteHexValue = filtered
                                            if (filtered.length == 2) {
                                                val byteVal = filtered.toInt(16).toByte()
                                                viewModel.updateHexByte(index, byteVal)
                                            }
                                        },
                                        label = { Text("HEX", color = TerminalText.copy(alpha = 0.5f), fontSize = 8.sp) },
                                        singleLine = true,
                                        modifier = Modifier.width(64.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonGreen),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonGreen,
                                            unfocusedBorderColor = SlateBorder
                                        )
                                    )

                                    IconButton(onClick = { selectedByteIndex = null }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = NeonGreen)
                                    }
                                }
                            }
                        }
                    }

                    // Hex Display Grid (showing 64 bytes at a time for portrait friendliness)
                    Text(
                        text = "SECTOR DATA (LBA $hexEditorLba) OFFSET MAP:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalText.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // Show first 64 bytes as a visual grid
                            val displayBytes = hexEditorBuffer.take(64)
                            val columns = 8
                            val rows = displayBytes.size / columns
                            
                            for (r in 0 until rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Row offset text
                                    Text(
                                        text = String.format("%03X: ", r * columns),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = Purple80,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(36.dp)
                                    )

                                    // Hex bytes
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (c in 0 until columns) {
                                            val index = r * columns + c
                                            val byteVal = displayBytes.getOrElse(index) { 0.toByte() }
                                            val hexStr = String.format("%02X", byteVal)
                                            val isSelected = selectedByteIndex == index

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(
                                                        if (isSelected) Purple80.copy(alpha = 0.2f) 
                                                        else if (byteVal != 0.toByte()) NeonGreen.copy(alpha = 0.05f)
                                                        else Color.Transparent
                                                    )
                                                    .border(
                                                        0.5.dp, 
                                                        if (isSelected) Purple80 
                                                        else if (byteVal != 0.toByte()) NeonGreen.copy(alpha = 0.2f) 
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        selectedByteIndex = index
                                                        customByteHexValue = hexStr
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = hexStr,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = if (isSelected) Purple80 
                                                            else if (byteVal != 0.toByte()) NeonGreen 
                                                            else TerminalText.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }

                                    // ASCII section
                                    val asciiStr = StringBuilder()
                                    for (c in 0 until columns) {
                                        val index = r * columns + c
                                        val byteVal = displayBytes.getOrElse(index) { 0.toByte() }.toInt()
                                        val char = if (byteVal in 32..126) byteVal.toChar() else '.'
                                        asciiStr.append(char)
                                    }

                                    Text(
                                        text = "| $asciiStr |",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.width(64.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sector Actions (Save to drive / Reload)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.writeHexSector() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text("SAVE TO Platter", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SlateDark)
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadHexSector(hexEditorLba) },
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DISCARD", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalText.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProToolsQuickLauncherBanner(onLaunchPro: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("pro_tools_quick_launcher"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130E20)),
        border = BorderStroke(1.5.dp, Purple80),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = "Pro Tools",
                    tint = Purple80,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "★ ACE LAB PC-3000 PRO OPERATIONS INTERACTIVE IDE ★",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Purple80
                    )
                    Text(
                        text = "إلكترونيات التحكم بالقرص الصلب - واجهة البرمجة والتحكم بالقطاعات المتقدمة",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Discover the professional firmware and platter repair workstation. Automate microcode corrections, bypass hardware lockouts, execute pre-amplifier sweeps, or rewrite disk sectors byte-by-byte in real time.",
                fontSize = 10.sp,
                color = TerminalText.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onLaunchPro,
                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = SlateDark,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "LAUNCH PRO CONTROLS (افتح الأدوات الاحترافية)",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDark,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

