package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.GrammarSuggestion
import com.example.data.CharacterProfile
import com.example.data.ManuscriptNode
import com.example.data.WritingSettings
import kotlinx.coroutines.launch

// --- Textures Definition ---
data class WritingTexture(
    val name: String,
    val backgroundColor: Color,
    val paperColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val fontFamily: FontFamily,
    val lineSpacingMultiplier: Float
)

val ParchmentTexture = WritingTexture(
    name = "Parchment",
    backgroundColor = Color(0xFFEAD8B1),
    paperColor = Color(0xFFF5E6CA),
    textColor = Color(0xFF3E2723),
    accentColor = Color(0xFF8D6E63),
    fontFamily = FontFamily.Serif,
    lineSpacingMultiplier = 1.3f
)

val CreamTexture = WritingTexture(
    name = "Classic Cream",
    backgroundColor = Color(0xFFF1EFE7),
    paperColor = Color(0xFFFFFDF6),
    textColor = Color(0xFF2D3748),
    accentColor = Color(0xFF319795),
    fontFamily = FontFamily.Serif,
    lineSpacingMultiplier = 1.25f
)

val TypewriterTexture = WritingTexture(
    name = "Typewriter",
    backgroundColor = Color(0xFFE2E8F0),
    paperColor = Color(0xFFFAFAFA),
    textColor = Color(0xFF1A202C),
    accentColor = Color(0xFFE53E3E),
    fontFamily = FontFamily.Monospace,
    lineSpacingMultiplier = 1.4f
)

val VelvetTexture = WritingTexture(
    name = "Royal Velvet",
    backgroundColor = Color(0xFF0F081D),
    paperColor = Color(0xFF1A0F2E),
    textColor = Color(0xFFF3E8FF),
    accentColor = Color(0xFFD6BCFA),
    fontFamily = FontFamily.SansSerif,
    lineSpacingMultiplier = 1.25f
)

val CharcoalTexture = WritingTexture(
    name = "Dark Charcoal",
    backgroundColor = Color(0xFF121212),
    paperColor = Color(0xFF1E1E1E),
    textColor = Color(0xFFE2E8F0),
    accentColor = Color(0xFFECC94B),
    fontFamily = FontFamily.SansSerif,
    lineSpacingMultiplier = 1.25f
)

fun getTextureByName(name: String): WritingTexture {
    return when (name) {
        "Parchment" -> ParchmentTexture
        "Classic Cream" -> CreamTexture
        "Typewriter" -> TypewriterTexture
        "Royal Velvet" -> VelvetTexture
        "Dark Charcoal" -> CharcoalTexture
        else -> ParchmentTexture
    }
}

fun getColorSchemeForTexture(name: String, isDarkSystem: Boolean): ColorScheme {
    return when (name) {
        "Parchment" -> lightColorScheme(
            primary = Color(0xFF8B5A2B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF5EFE0),
            onPrimaryContainer = Color(0xFF5C3A1A),
            secondary = Color(0xFFCD853F),
            background = Color(0xFFFDFBF7),
            surface = Color(0xFFF9F5EC),
            onBackground = Color(0xFF2C1E11),
            onSurface = Color(0xFF2C1E11),
            outlineVariant = Color(0xFFE2DAC5)
        )
        "Classic Cream" -> lightColorScheme(
            primary = Color(0xFF0F766E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCCFBF1),
            onPrimaryContainer = Color(0xFF115E59),
            secondary = Color(0xFF4A5568),
            background = Color(0xFFFFFDF6),
            surface = Color(0xFFF5EFE0),
            onBackground = Color(0xFF1A202C),
            onSurface = Color(0xFF1A202C),
            outlineVariant = Color(0xFFE6DFD3)
        )
        "Typewriter" -> lightColorScheme(
            primary = Color(0xFFDC2626),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFEE2E2),
            onPrimaryContainer = Color(0xFF991B1B),
            secondary = Color(0xFF4B5563),
            background = Color(0xFFFAFAFA),
            surface = Color(0xFFF3F4F6),
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827),
            outlineVariant = Color(0xFFE5E7EB)
        )
        "Royal Velvet" -> darkColorScheme(
            primary = Color(0xFFD6BCFA),
            onPrimary = Color(0xFF3B0066),
            primaryContainer = Color(0xFF4C1D95),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = Color(0xFFECC94B),
            background = Color(0xFF0F081D),
            surface = Color(0xFF1A0F2E),
            onBackground = Color(0xFFF3E8FF),
            onSurface = Color(0xFFF3E8FF),
            outlineVariant = Color(0xFF312E81)
        )
        "Dark Charcoal" -> darkColorScheme(
            primary = Color(0xFFFBBF24),
            onPrimary = Color(0xFF451A03),
            primaryContainer = Color(0xFF78350F),
            onPrimaryContainer = Color(0xFFFEF3C7),
            secondary = Color(0xFF34D399),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color(0xFFF3F4F6),
            onSurface = Color(0xFFF3F4F6),
            outlineVariant = Color(0xFF374151)
        )
        else -> {
            if (isDarkSystem) darkColorScheme() else lightColorScheme()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelApp(
    viewModel: NovelViewModel = viewModel()
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentTexture = getTextureByName(settings.selectedTexture)
    val isSavingLocally by viewModel.isSavingLocally.collectAsState()
    val lastSavedTimeText by viewModel.lastSavedTimeText.collectAsState()
    val selectedNodeId by viewModel.selectedNodeId.collectAsState()
    val activeNode by viewModel.activeNode.collectAsState()
    val proposedChanges by viewModel.proposedChanges.collectAsState()
    val showReviewDialog by viewModel.showReviewDialog.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val systemIsDark = isSystemInDarkTheme()
    val themeColorScheme = remember(settings.selectedTexture, systemIsDark) {
        getColorSchemeForTexture(settings.selectedTexture, systemIsDark)
    }

    MaterialTheme(colorScheme = themeColorScheme, typography = MaterialTheme.typography) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "NOVEL WRITER",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Professional Studio Workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Divider(modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp))
                
                NavigationDrawerItem(
                    label = { Text("Manuscript Workspace", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.Manuscript,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.Manuscript)
                    },
                    icon = { Icon(Icons.Outlined.LibraryBooks, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_manuscript")
                )
                
                NavigationDrawerItem(
                    label = { Text("Character Profiles", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.Characters,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.Characters)
                    },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_characters")
                )

                NavigationDrawerItem(
                    label = { Text("Plot Arc Tracker", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.StorylineTracker,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.StorylineTracker)
                    },
                    icon = { Icon(Icons.Outlined.Timeline, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_storyline")
                )

                NavigationDrawerItem(
                    label = { Text("Character-Event Map", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.RelationshipMap,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.RelationshipMap)
                    },
                    icon = { Icon(Icons.Outlined.Hub, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_relationship_map")
                )

                NavigationDrawerItem(
                    label = { Text("Export Document", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.Exporter,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.Exporter)
                    },
                    icon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_exporter")
                )

                NavigationDrawerItem(
                    label = { Text("App Settings", fontWeight = FontWeight.SemiBold) },
                    selected = activeTab is ActiveTab.Settings,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.selectTab(ActiveTab.Settings)
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_settings")
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Local Storage: Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastSavedTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_scaffold")
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (activeTab) {
                    is ActiveTab.Manuscript -> {
                        if (selectedNodeId != null) {
                            NovelEditorScreen(viewModel = viewModel)
                        } else {
                            ManuscriptWorkspaceScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                    }
                    is ActiveTab.Characters -> {
                        CharactersManagerScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    is ActiveTab.StorylineTracker -> {
                        StorylineTrackerScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    is ActiveTab.RelationshipMap -> {
                        RelationshipMapScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    is ActiveTab.Exporter -> {
                        ExporterScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    is ActiveTab.Settings -> {
                        SettingsScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                }

                // AI Story Proposed Changes Review Dialog
                if (showReviewDialog && proposedChanges.isNotEmpty()) {
                    StoryReviewDialog(
                        changes = proposedChanges,
                        onAcceptChange = { viewModel.acceptProposedChange(it) },
                        onRejectChange = { viewModel.rejectProposedChange(it) },
                        onAcceptAll = { viewModel.acceptAllProposedChanges() },
                        onRejectAll = { viewModel.rejectAllProposedChanges() },
                        onDismiss = { viewModel.dismissReviewDialog() }
                    )
                }

                // Local Save Indicator (Unobtrusive floating pill on top right)
                if (selectedNodeId == null || !settings.isDistractionFree) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isSavingLocally,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Saving locally...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ==========================================
// 1. MANUSCRIPT WORKSPACE SCREEN (Folders/Files)
// ==========================================
@Composable
fun ManuscriptWorkspaceScreen(viewModel: NovelViewModel, onOpenDrawer: () -> Unit) {
    val allNodes by viewModel.allNodes.collectAsState()
    val rootNovels by viewModel.rootNovels.collectAsState()
    val selectedNovelId by viewModel.selectedNovelId.collectAsState()

    var showCreateDialog by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // Pair(isFolder, parentId?)
    var showCreateNovelDialog by remember { mutableStateOf(false) }
    var selectedParentIdForNew by remember { mutableStateOf<Int?>(null) }
    var activeFolderId by remember { mutableStateOf<Int?>(null) } // folder filter to drill down
    var nodePendingDeletion by remember { mutableStateOf<ManuscriptNode?>(null) }
    var showNovelDropdown by remember { mutableStateOf(false) }

    val activeNovel = remember(rootNovels, selectedNovelId) {
        rootNovels.find { it.id == selectedNovelId }
    }

    val currentNodes = remember(allNodes, activeFolderId, selectedNovelId) {
        val targetParent = activeFolderId ?: selectedNovelId
        if (targetParent == null) emptyList()
        else allNodes.filter { it.parentId == targetParent }
    }

    val activeFolderName = remember(allNodes, activeFolderId, activeNovel) {
        if (activeFolderId == null) activeNovel?.name ?: "No Novel Selected"
        else allNodes.find { it.id == activeFolderId }?.name ?: "Folder"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Workspace Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeFolderId != null) {
                        IconButton(onClick = {
                            val parentFolder = allNodes.find { it.id == activeFolderId }?.parentId
                            activeFolderId = if (parentFolder == selectedNovelId) null else parentFolder
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = activeFolderName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Novel Switcher Chip / Selector
                if (activeFolderId == null) {
                    Box(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { showNovelDropdown = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Novel: ${activeNovel?.name ?: "Select Novel"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showNovelDropdown,
                            onDismissRequest = { showNovelDropdown = false }
                        ) {
                            rootNovels.forEach { novel ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            novel.name,
                                            fontWeight = if (novel.id == selectedNovelId) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Book,
                                            contentDescription = null,
                                            tint = if (novel.id == selectedNovelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectNovel(novel.id)
                                        activeFolderId = null
                                        showNovelDropdown = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("+ Create New Novel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showNovelDropdown = false
                                    showCreateNovelDialog = true
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Viewing contents of subfolder inside ${activeNovel?.name ?: "Novel"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // Button to Add a New Root Novel directly from header
            if (activeFolderId == null) {
                IconButton(
                    onClick = { showCreateNovelDialog = true },
                    modifier = Modifier.testTag("create_novel_header_button")
                ) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = "Create New Novel", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create Quick Buttons Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    showCreateDialog = Pair(false, false) // Chapter (File)
                    selectedParentIdForNew = activeFolderId ?: selectedNovelId
                },
                enabled = selectedNovelId != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_chapter_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Chapter")
            }

            OutlinedButton(
                onClick = {
                    showCreateDialog = Pair(true, true) // Folder
                    selectedParentIdForNew = activeFolderId ?: selectedNovelId
                },
                enabled = selectedNovelId != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_folder_button")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Folder")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Files List
        if (selectedNovelId == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Novel Created Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Start a new novel project to organize your manuscript, character arcs, and storylines.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreateNovelDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("create_first_novel_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Novel")
                    }
                }
            }
        } else if (currentNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (activeFolderId != null) "This folder is empty" else "No chapters in this novel yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a chapter or a folder above to start writing.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentNodes) { node ->
                    ManuscriptItemRow(
                        node = node,
                        onSelectFile = { viewModel.selectNode(it.id) },
                        onSelectFolder = { activeFolderId = it.id },
                        onDelete = { nodePendingDeletion = it },
                        onRename = { id, name -> viewModel.renameNode(id, name) }
                    )
                }
            }
        }
    }

    // Modal Dialog to Create Folder / Chapter
    showCreateDialog?.let { (isFolder, _) ->
        var inputName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreateDialog = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isFolder) "Create New Folder" else "Create New Chapter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Name") },
                        placeholder = { Text(if (isFolder) "World Lore, Characters, Plot..." else "Chapter 1...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_name_input")
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputName.isNotBlank()) {
                                    val parent = selectedParentIdForNew ?: activeFolderId ?: selectedNovelId
                                    if (isFolder) {
                                        viewModel.createFolder(inputName, parent)
                                    } else {
                                        viewModel.createFile(inputName, parent)
                                    }
                                    showCreateDialog = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dialog_submit_button")
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog to Create a Root Novel
    if (showCreateNovelDialog) {
        var novelNameInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreateNovelDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create New Novel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This sets up a dedicated novel project with its own characters, chapters, and storylines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = novelNameInput,
                        onValueChange = { novelNameInput = it },
                        label = { Text("Novel Title") },
                        placeholder = { Text("The Silent Archipelago...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_novel_input")
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateNovelDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (novelNameInput.isNotBlank()) {
                                    viewModel.createNovel(novelNameInput)
                                    showCreateNovelDialog = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("confirm_create_novel_button")
                        ) {
                            Text("Create Novel")
                        }
                    }
                }
            }
        }
    }

    // Safe Deletion Confirmation Dialog
    if (nodePendingDeletion != null) {
        val node = nodePendingDeletion!!
        val isRootNovel = node.parentId == null && node.isFolder

        AlertDialog(
            onDismissRequest = { nodePendingDeletion = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isRootNovel) "Delete Entire Novel?"
                    else if (node.isFolder) "Delete Folder & All Contents?"
                    else "Delete Chapter Draft?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isRootNovel) {
                        "Are you sure you want to permanently delete the novel \"${node.name}\"?\n\nThis will safely and permanently remove all chapters, subfolders, characters, plot events, and relationships associated with this novel. This action cannot be undone."
                    } else if (node.isFolder) {
                        "Are you sure you want to delete folder \"${node.name}\"?\n\nAll nested chapters and subfolders inside this folder will be permanently deleted. This action cannot be undone."
                    } else {
                        "Are you sure you want to delete chapter \"${node.name}\"? This draft will be permanently removed."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNode(node)
                        nodePendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { nodePendingDeletion = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ManuscriptItemRow(
    node: ManuscriptNode,
    onSelectFile: (ManuscriptNode) -> Unit,
    onSelectFolder: (ManuscriptNode) -> Unit,
    onDelete: (ManuscriptNode) -> Unit,
    onRename: (Int, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(node.name) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (node.isFolder) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (node.isFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .clickable {
                if (node.isFolder) onSelectFolder(node) else onSelectFile(node)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isFolder) Icons.Default.Folder else Icons.Outlined.Description,
                contentDescription = null,
                tint = if (node.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            if (isRenaming) {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (renameInput.isNotBlank()) {
                            onRename(node.id, renameInput)
                            isRenaming = false
                        }
                    })
                )
                IconButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        onRename(node.id, renameInput)
                        isRenaming = false
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.Green)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (node.isFolder) "Folder" else "${node.wordCount} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                isRenaming = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDelete(node)
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. IMMERSIVE NOVEL EDITOR SCREEN (with Textures & Distraction-free)
// ==========================================
@Composable
fun NovelEditorScreen(viewModel: NovelViewModel) {
    val activeNode by viewModel.activeNode.collectAsState()
    val editorText by viewModel.editorText.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentTexture = getTextureByName(settings.selectedTexture)
    val selectedFont by viewModel.selectedFont.collectAsState()

    val activeFontFamily = when (selectedFont) {
        "Classic Serif" -> FontFamily.Serif
        "Modern Sans-Serif" -> FontFamily.SansSerif
        "Retro Monospace" -> FontFamily.Monospace
        "Dyslexic-Friendly" -> FontFamily.Cursive
        else -> currentTexture.fontFamily
    }

    // Grammar checking states
    val isCheckingGrammar by viewModel.isCheckingGrammar.collectAsState()
    val grammarSuggestions by viewModel.grammarSuggestions.collectAsState()

    // Local commits (Backups)
    val allCommits by viewModel.allCommits.collectAsState()
    val nodeCommits = remember(allCommits, activeNode) {
        allCommits.filter { it.nodeId == (activeNode?.id ?: 0) }
    }

    var isSidebarOpen by remember { mutableStateOf(true) } // default open on wide screen, can be toggled
    var commitMessageInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTexture.backgroundColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // MAIN EDITING AREA (Left Side / Center)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Editor Toolbar
                if (!settings.isDistractionFree) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.selectNode(null) }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                                Column {
                                    Text(
                                        text = activeNode?.name ?: "Chapter",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$wordCount words",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Toolbar Action Buttons
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Analyze Story Button
                                val isAnalyzingStory by viewModel.isAnalyzingStory.collectAsState()
                                IconButton(
                                    onClick = { activeNode?.let { viewModel.requestAIStoryAnalysis(it.id) } },
                                    enabled = activeNode != null && !isAnalyzingStory,
                                    modifier = Modifier.testTag("analyze_story_button")
                                ) {
                                    if (isAnalyzingStory) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Analyze Story",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Toggle Right Workspace Utilities Sidebar
                                IconButton(
                                    onClick = { isSidebarOpen = !isSidebarOpen },
                                    modifier = Modifier.testTag("toggle_sidebar_button")
                                ) {
                                    Icon(
                                        imageVector = if (isSidebarOpen) Icons.Default.SettingsSuggest else Icons.Default.Tune,
                                        contentDescription = "Toggle Sidebar",
                                        tint = if (isSidebarOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Distraction-Free Toggle
                                IconButton(
                                    onClick = { viewModel.setDistractionFree(true) },
                                    modifier = Modifier.testTag("distraction_free_toggle")
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Distraction-Free Mode")
                                }
                            }
                        }
                    }

                    // Analysis Status Message Banner
                    val statusMsg by viewModel.analysisStatusMessage.collectAsState()
                    if (statusMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusMsg!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearAnalysisStatusMessage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Writing Surface with Paper Layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            horizontal = if (settings.isDistractionFree) 0.dp else 16.dp,
                            vertical = if (settings.isDistractionFree) 0.dp else 8.dp
                        )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(if (settings.isDistractionFree) 0.dp else 4.dp, RoundedCornerShape(if (settings.isDistractionFree) 0.dp else 16.dp))
                            .clip(RoundedCornerShape(if (settings.isDistractionFree) 0.dp else 16.dp))
                            .background(currentTexture.paperColor),
                        colors = CardDefaults.cardColors(containerColor = currentTexture.paperColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (settings.isDistractionFree) 24.dp else 20.dp,
                                    end = if (settings.isDistractionFree) 24.dp else 20.dp,
                                    top = if (settings.isDistractionFree) 40.dp else 20.dp,
                                    bottom = 20.dp
                                )
                        ) {
                            if (settings.isDistractionFree) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$wordCount words",
                                        fontFamily = activeFontFamily,
                                        fontSize = 12.sp,
                                        color = currentTexture.textColor.copy(alpha = 0.4f)
                                    )
                                    TextButton(
                                        onClick = { viewModel.setDistractionFree(false) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = currentTexture.accentColor)
                                    ) {
                                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit distraction-free")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Exit Focus", fontFamily = activeFontFamily)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Text(
                                text = activeNode?.name ?: "Writing Draft",
                                fontFamily = activeFontFamily,
                                fontSize = (settings.fontSize + 4).sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTexture.textColor,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Divider(
                                color = currentTexture.textColor.copy(alpha = 0.15f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            BasicTextField(
                                value = editorText,
                                onValueChange = { viewModel.updateEditorText(it) },
                                textStyle = TextStyle(
                                    fontFamily = activeFontFamily,
                                    fontSize = settings.fontSize.sp,
                                    color = currentTexture.textColor,
                                    lineHeight = (settings.fontSize * currentTexture.lineSpacingMultiplier).sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("manuscript_text_editor"),
                                decorationBox = { innerTextField ->
                                    if (editorText.isEmpty()) {
                                        Text(
                                            text = "Tap here and begin writing your novel masterwork...",
                                            fontFamily = activeFontFamily,
                                            fontSize = settings.fontSize.sp,
                                            color = currentTexture.textColor.copy(alpha = 0.35f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
            }

            // WRITING OPTIONS SIDEBAR (Right Side)
            AnimatedVisibility(
                visible = isSidebarOpen && !settings.isDistractionFree,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Sidebar Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Writing Tools",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { isSidebarOpen = false }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Close Sidebar")
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // 1. AI proofread section
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("AI Proofread", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            Button(
                                                onClick = { viewModel.runGrammarCheck() },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                if (isCheckingGrammar) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                                } else {
                                                    Text("Check", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val grammarError by viewModel.grammarErrorMessage.collectAsState()
                                        if (grammarError != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        grammarError ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = { viewModel.clearGrammarErrorMessage() },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Dismiss",
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        if (grammarSuggestions.isEmpty()) {
                                            Text(
                                                text = "No grammar issues found. Tap Check to scan your text using Gemini.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        } else {
                                            Text(
                                                text = "Found ${grammarSuggestions.size} suggestions:",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            grammarSuggestions.take(3).forEach { suggestion ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = "Instead of: \"${suggestion.originalText}\"",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "Suggested: \"${suggestion.suggestedText}\"",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Button(
                                                            onClick = { viewModel.applyGrammarSuggestion(suggestion) },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(24.dp),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text("Apply", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 1.5 AI Story Tracker Section
                            item {
                                val isAnalyzingStory by viewModel.isAnalyzingStory.collectAsState()
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AutoAwesome, 
                                                    contentDescription = null, 
                                                    tint = MaterialTheme.colorScheme.secondary, 
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Story Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            Button(
                                                onClick = { activeNode?.let { viewModel.requestAIStoryAnalysis(it.id) } },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp).testTag("ai_story_analysis_button"),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                enabled = activeNode != null && !isAnalyzingStory
                                            ) {
                                                if (isAnalyzingStory) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                                } else {
                                                    Text("Analyze Story", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Scan chapter to extract characters, milestones, and relationships. All proposed changes are presented for your review before saving to the database.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            // 2. Immersive Workspaces (Textures Selection)
                            item {
                                Column {
                                    Text("Immersive Workspace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val textures = listOf(
                                        ParchmentTexture,
                                        CreamTexture,
                                        TypewriterTexture,
                                        VelvetTexture,
                                        CharcoalTexture
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        textures.forEach { texture ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(texture.backgroundColor)
                                                    .border(
                                                        width = if (settings.selectedTexture == texture.name) 2.dp else 1.dp,
                                                        color = if (settings.selectedTexture == texture.name) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.updateTexture(texture.name) }
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(texture.paperColor)
                                                            .border(1.dp, texture.textColor.copy(alpha = 0.2f), CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = texture.name,
                                                        fontFamily = texture.fontFamily,
                                                        color = texture.textColor,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (settings.selectedTexture == texture.name) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = texture.accentColor, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Local Version Backups (GitHub Style Repository)
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Historical Snapshots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Save manuscript snapshots to roll back or view historical drafts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = commitMessageInput,
                                            onValueChange = { commitMessageInput = it },
                                            placeholder = { Text("Snapshot note (e.g., Chapter 1 Draft 1)", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = TextStyle(fontSize = 12.sp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                if (commitMessageInput.isNotBlank() && activeNode != null) {
                                                    viewModel.commitDraft(activeNode!!.id, commitMessageInput)
                                                    commitMessageInput = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save Draft Snapshot", style = MaterialTheme.typography.bodySmall)
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "Snapshot History (${nodeCommits.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (nodeCommits.isEmpty()) {
                                            Text(
                                                text = "No drafts saved yet. Save your first snapshot above!",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                nodeCommits.forEach { commit ->
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = commit.commitHash,
                                                                            fontFamily = FontFamily.Monospace,
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = commit.commitMessage,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(commit.timestamp)
                                                                Text(
                                                                    text = dateStr,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { viewModel.restoreToCommit(commit) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore this snapshot", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Font scale
                            item {
                                Column {
                                    Text("Typography Scale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.updateFontSize(maxOf(12, settings.fontSize - 1)) }) {
                                            Text("A-", fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = settings.fontSize.toFloat(),
                                            onValueChange = { viewModel.updateFontSize(it.toInt()) },
                                            valueRange = 12f..24f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.updateFontSize(minOf(26, settings.fontSize + 1)) }) {
                                            Text("A+", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionItemCard(
    suggestion: GrammarSuggestion,
    onApply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onApply() }
            .testTag("grammar_suggestion_item")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionBadge(severity = suggestion.severity)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Replace \"${suggestion.originalText}\" with \"${suggestion.suggestedText}\"",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.explanation,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onApply) {
                Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.Green)
            }
        }
    }
}

@Composable
fun SuggestionBadge(severity: String) {
    val containerColor = when (severity) {
        "Critical" -> Color(0xFFFFECEF)
        "Typos" -> Color(0xFFFEF3C7)
        "Style" -> Color(0xFFEFF6FF)
        else -> Color(0xFFF3F4F6)
    }
    val contentColor = when (severity) {
        "Critical" -> Color(0xFFDC2626)
        "Typos" -> Color(0xFFD97706)
        "Style" -> Color(0xFF2563EB)
        else -> Color(0xFF4B5563)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = severity,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// ==========================================
// 3. CHARACTER PROFILE MANAGER SCREEN
// ==========================================
@Composable
fun CharactersManagerScreen(viewModel: NovelViewModel, onOpenDrawer: () -> Unit) {
    val characters by viewModel.filteredCharacters.collectAsState()
    val editingCharacter by viewModel.editingCharacter.collectAsState()
    val selectedNovelId by viewModel.selectedNovelId.collectAsState()
    val novels by viewModel.rootNovels.collectAsState()
    val activeNovel = remember(novels, selectedNovelId) { novels.find { it.id == selectedNovelId } }
    var showDropdown by remember { mutableStateOf(false) }

    if (editingCharacter != null) {
        CharacterDetailEditor(
            character = editingCharacter!!,
            onSave = { id, name, role, age, appearance, backstory, plotArc, notes, color ->
                viewModel.saveCharacter(id, name, role, age, appearance, backstory, plotArc, notes, color, selectedNovelId)
            },
            onCancel = { viewModel.startEditingCharacter(null) },
            onDelete = { viewModel.deleteCharacter(it) }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Character Profiles",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showDropdown = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Novel: ${activeNovel?.name ?: "All Novels"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Novels") },
                                    onClick = {
                                        viewModel.selectNovel(null)
                                        showDropdown = false
                                    }
                                )
                                novels.forEach { novel ->
                                    DropdownMenuItem(
                                        text = { Text(novel.name) },
                                        onClick = {
                                            viewModel.selectNovel(novel.id)
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        viewModel.startEditingCharacter(
                            CharacterProfile(
                                name = "",
                                role = "Protagonist",
                                avatarColor = 0xFF6366F1.toInt()
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_character_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Character")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (characters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No characters yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create dossier profiles to maintain consistency across drafts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(characters) { character ->
                        CharacterCard(
                            character = character,
                            onClick = { viewModel.startEditingCharacter(character) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterCard(
    character: CharacterProfile,
    onClick: () -> Unit
) {
    val roleColor = when (character.role) {
        "Protagonist" -> Color(0xFF6366F1)
        "Antagonist" -> Color(0xFFEF4444)
        "Supporting" -> Color(0xFF10B981)
        else -> Color(0xFF8B5CF6)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("character_card_item")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circle Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(character.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = character.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = character.name.ifBlank { "Unnamed Character" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Role Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(roleColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = character.role,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = roleColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (character.backstory.isNotBlank()) {
                Text(
                    text = character.backstory,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Tap to edit details",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CharacterDetailEditor(
    character: CharacterProfile,
    onSave: (Int, String, String, String, String, String, String, String, Int) -> Unit,
    onCancel: () -> Unit,
    onDelete: (CharacterProfile) -> Unit
) {
    var name by remember { mutableStateOf(character.name) }
    var role by remember { mutableStateOf(character.role) }
    var age by remember { mutableStateOf(character.age) }
    var appearance by remember { mutableStateOf(character.appearance) }
    var backstory by remember { mutableStateOf(character.backstory) }
    var plotArc by remember { mutableStateOf(character.plotArc) }
    var notes by remember { mutableStateOf(character.notes) }
    var avatarColor by remember { mutableStateOf(character.avatarColor) }

    val roles = listOf("Protagonist", "Antagonist", "Supporting", "Dynamic")
    val avatarColors = listOf(0xFF6366F1, 0xFFEF4444, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Dossier Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (character.id == 0) "Create Dossier" else "Edit Dossier",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                if (character.id != 0) {
                    IconButton(onClick = { onDelete(character) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Character", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(character.id, name, role, age, appearance, backstory, plotArc, notes, avatarColor)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_character_button")
                ) {
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile Initial Block
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(avatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(2).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text("Dossier Color Profile", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            avatarColors.forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (avatarColor == colorVal.toInt()) 2.dp else 0.dp,
                                            color = if (avatarColor == colorVal.toInt()) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { avatarColor = colorVal.toInt() }
                                )
                            }
                        }
                    }
                }
            }

            // Forms fields
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Character Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("character_name_input")
                )
            }

            item {
                Text("Role Archetype", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    roles.forEach { roleOpt ->
                        val isSelected = role == roleOpt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { role = roleOpt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = roleOpt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age / Lifespan") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = { Text("Physical Appearance") },
                    placeholder = { Text("Height, eye color, dress style, distinguishing features...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                OutlinedTextField(
                    value = backstory,
                    onValueChange = { backstory = it },
                    label = { Text("Origin & Backstory") },
                    placeholder = { Text("Where did they grow up? Key events that shaped their worldview...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = plotArc,
                    onValueChange = { plotArc = it },
                    label = { Text("Plot Arc & Motivation") },
                    placeholder = { Text("What is their primary goal? How do they change throughout the narrative?") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Internal Secret Notes") },
                    placeholder = { Text("Strengths, flaws, secrets, relationships, favorite quotes...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }
    }
}

// ==========================================
// 4. INTEGRATED EXPORTER SCREEN
// ==========================================
@Composable
fun ExporterScreen(viewModel: NovelViewModel, onOpenDrawer: () -> Unit) {
    val allNodes by viewModel.allNodes.collectAsState()
    val rootNovels by viewModel.rootNovels.collectAsState()
    val selectedNovelId by viewModel.selectedNovelId.collectAsState()
    val context = LocalContext.current

    var selectedFormat by remember { mutableStateOf("Standard Manuscript (TXT)") }
    var selectedExportNodeId by remember { mutableStateOf<Int?>(null) } // null means Entire Book
    var showNovelDropdown by remember { mutableStateOf(false) }
    var showChapterDropdown by remember { mutableStateOf(false) }

    val activeNovel = remember(rootNovels, selectedNovelId) {
        rootNovels.find { it.id == selectedNovelId }
    }

    val availableChapters = remember(allNodes, selectedNovelId) {
        val currentNovelId = selectedNovelId
        if (currentNovelId == null) {
            allNodes.filter { !it.isFolder }
        } else {
            fun getDescendants(parentId: Int): List<ManuscriptNode> {
                val direct = allNodes.filter { it.parentId == parentId }
                return direct.flatMap { if (it.isFolder) getDescendants(it.id) else listOf(it) }
            }
            getDescendants(currentNovelId)
        }
    }

    val formats = listOf(
        "Standard Manuscript (TXT)" to "Standard industry layout with double-spaced typography formatting.",
        "Creative Markdown (MD)" to "Rich text formatting with structural hierarchy tags (#) and styles.",
        "Professional HTML" to "Clean e-book ready script with embedded typographic styles."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Manuscript Exporter",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Share or export your drafts to standard publishing formats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exporter Form card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Novel Selector
                Text(
                    text = "Target Novel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { showNovelDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeNovel?.name ?: "Select Novel",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = showNovelDropdown,
                        onDismissRequest = { showNovelDropdown = false }
                    ) {
                        rootNovels.forEach { novel ->
                            DropdownMenuItem(
                                text = { Text(novel.name) },
                                onClick = {
                                    viewModel.selectNovel(novel.id)
                                    selectedExportNodeId = null
                                    showNovelDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Select Node to export
                Text(
                    text = "Export Scope",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // target selector row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedExportNodeId == null) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (selectedExportNodeId == null) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedExportNodeId = null }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Entire Novel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedExportNodeId == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedExportNodeId != null) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (selectedExportNodeId != null) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (selectedExportNodeId == null) {
                                    selectedExportNodeId = availableChapters.firstOrNull()?.id
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Selected Chapter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedExportNodeId != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If Selected Chapter scope is chosen, allow picking from chapter list
                if (selectedExportNodeId != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { showChapterDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentChapterName = allNodes.find { it.id == selectedExportNodeId }?.name ?: "Choose Chapter"
                                Text(
                                    text = "Chapter: $currentChapterName",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showChapterDropdown,
                            onDismissRequest = { showChapterDropdown = false }
                        ) {
                            if (availableChapters.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No chapters found in this novel") },
                                    onClick = { showChapterDropdown = false }
                                )
                            } else {
                                availableChapters.forEach { chapter ->
                                    DropdownMenuItem(
                                        text = { Text(chapter.name) },
                                        onClick = {
                                            selectedExportNodeId = chapter.id
                                            showChapterDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Format Options
                Text(
                    text = "Professional Manuscript Format",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                formats.forEach { (formatName, desc) ->
                    val isSelected = selectedFormat == formatName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable { selectedFormat = formatName }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedFormat = formatName }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = formatName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val uri = viewModel.exportManuscript(context, selectedFormat, selectedExportNodeId, selectedNovelId)
                        if (uri != null) {
                            Toast.makeText(context, "Manuscript compiled and ready to share!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please ensure a valid novel or chapter is available to export.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_trigger_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compile & Share Manuscript")
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: NovelViewModel, onOpenDrawer: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    val lastSavedTimeText by viewModel.lastSavedTimeText.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure storage, analysis modes, and typography.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Storage & Synchronization Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Storage & Synchronization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Local offline storage active. All data safely saved on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Local SQLite Storage: Active", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastSavedTimeText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Manuscripts, characters, story events, and drafts are permanently stored locally using Room persistence.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cloud Sync: Not Configured", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // AI Story Analysis Mode Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "AI Narrative Analysis Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Control how and when Gemini analyzes chapter text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Background Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "When enabled, analyzes text after typing pauses. (Default is OFF — manual 'Analyze Story' button is recommended).",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.isAutoAnalysisEnabled,
                                onCheckedChange = { viewModel.setAutoAnalysisEnabled(it) },
                                modifier = Modifier.testTag("auto_analysis_switch")
                            )
                        }
                    }
                }
            }

            // Typography Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Editor Typographic Scale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adjust standard text rendering size.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = settings.fontSize.toFloat(),
                                onValueChange = { viewModel.updateFontSize(it.toInt()) },
                                valueRange = 14f..24f,
                                steps = 5,
                                modifier = Modifier.weight(1f)
                            )
                            Text("A+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Manuscript Typeface",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select your preferred storytelling font style.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val fonts = listOf("Classic Serif", "Modern Sans-Serif", "Retro Monospace", "Dyslexic-Friendly")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            fonts.take(2).forEach { font ->
                                FilterChip(
                                    selected = selectedFont == font,
                                    onClick = { viewModel.updateSelectedFont(font) },
                                    label = { Text(font, style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            fonts.drop(2).forEach { font ->
                                FilterChip(
                                    selected = selectedFont == font,
                                    onClick = { viewModel.updateSelectedFont(font) },
                                    label = { Text(font, style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        val previewFontFamily = when (selectedFont) {
                            "Classic Serif" -> FontFamily.Serif
                            "Modern Sans-Serif" -> FontFamily.SansSerif
                            "Retro Monospace" -> FontFamily.Monospace
                            "Dyslexic-Friendly" -> FontFamily.Cursive
                            else -> FontFamily.Default
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Visual Preview: ${settings.fontSize}sp under $selectedFont style.",
                            fontSize = settings.fontSize.sp,
                            fontFamily = previewFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
