package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.PI
import com.example.data.CharacterProfile
import com.example.data.CharacterRelationship
import com.example.data.StoryEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipMapScreen(viewModel: NovelViewModel, onOpenDrawer: () -> Unit) {
    val characters by viewModel.filteredCharacters.collectAsState()
    val events by viewModel.filteredEvents.collectAsState()
    val relationships by viewModel.filteredRelationships.collectAsState()
    val selectedNovelId by viewModel.selectedNovelId.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val novels by viewModel.rootNovels.collectAsState()
                    val activeNovel = remember(novels, selectedNovelId) { novels.find { it.id == selectedNovelId } }
                    var showDropdown by remember { mutableStateOf(false) }

                    Column {
                        Text(
                            text = "Character-Event Relationship Map",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showDropdown = true }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Novel: ${activeNovel?.name ?: "All Novels"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_relationship_button")
                    ) {
                        Icon(Icons.Default.DeviceHub, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect Node", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        }
    ) { innerPadding ->
        var selectedSubTab by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Visual Connection Diagram", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connection Database Ledger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedSubTab == 0) {
                    // LEFT SIDE: Interactive Visual ER Network Schema Diagram
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "VISUAL CONNECTION DIAGRAM",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        var selectedCharId by remember { mutableStateOf<Int?>(null) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            if (characters.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No characters found for this novel.\nCreate character profiles to view the relationship graph.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val density = androidx.compose.ui.platform.LocalDensity.current
                                    val widthPx = with(density) { maxWidth.toPx() }
                                    val heightPx = with(density) { maxHeight.toPx() }

                                    val centerX = widthPx / 2f
                                    val centerY = heightPx / 2f
                                    val radius = (min(widthPx, heightPx) * 0.36f).coerceAtLeast(100f)

                                    // 1. Calculate deterministic positions for character nodes
                                    val characterPositions = remember(characters, widthPx, heightPx) {
                                        characters.mapIndexed { index, char ->
                                            if (characters.size == 1) {
                                                char.id to Offset(centerX, centerY)
                                            } else {
                                                val angle = (2.0 * PI * index / characters.size) - PI / 2.0
                                                val x = centerX + (radius * cos(angle)).toFloat()
                                                val y = centerY + (radius * sin(angle)).toFloat()
                                                char.id to Offset(x, y)
                                            }
                                        }.toMap()
                                    }

                                    // 2. Calculate positions for any events referenced by relationships
                                    val referencedEventIds = remember(relationships) {
                                        relationships.filter { it.isToEvent }.map { it.targetId }.distinct()
                                    }
                                    val referencedEvents = remember(events, referencedEventIds) {
                                        events.filter { referencedEventIds.contains(it.id) }
                                    }
                                    val eventPositions = remember(referencedEvents, widthPx, heightPx) {
                                        val eventRadius = radius * 0.5f
                                        referencedEvents.mapIndexed { index, event ->
                                            if (referencedEvents.size == 1) {
                                                event.id to Offset(centerX, centerY)
                                            } else {
                                                val angle = (2.0 * PI * index / referencedEvents.size)
                                                val x = centerX + (eventRadius * cos(angle)).toFloat()
                                                val y = centerY + (eventRadius * sin(angle)).toFloat()
                                                event.id to Offset(x, y)
                                            }
                                        }.toMap()
                                    }

                                    // 3. Draw actual data-driven edges for every database relationship
                                    val primaryColor = MaterialTheme.colorScheme.primary

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        relationships.forEach { rel ->
                                            val startPos = characterPositions[rel.sourceCharacterId]
                                            val endPos = if (rel.isToEvent) {
                                                eventPositions[rel.targetId]
                                            } else {
                                                characterPositions[rel.targetId]
                                            }

                                            if (startPos != null && endPos != null) {
                                                val isHighlighted = selectedCharId == null ||
                                                        rel.sourceCharacterId == selectedCharId ||
                                                        (!rel.isToEvent && rel.targetId == selectedCharId)

                                                val alpha = if (isHighlighted) 0.85f else 0.15f
                                                val strokeWidth = if (isHighlighted) 3.5f else 1.5f

                                                val edgeColor = when (rel.relationType) {
                                                    "Ally", "Friend", "Protagonist" -> Color(0xFF10B981)
                                                    "Antagonist", "Rival", "Enemy" -> Color(0xFFEF4444)
                                                    "Family", "Sibling", "Lover", "Adoptive Sibling" -> Color(0xFFEC4899)
                                                    "Mentor" -> Color(0xFFF59E0B)
                                                    else -> primaryColor
                                                }

                                                // Draw line connecting the actual nodes
                                                drawLine(
                                                    color = edgeColor.copy(alpha = alpha),
                                                    start = startPos,
                                                    end = endPos,
                                                    strokeWidth = strokeWidth,
                                                    pathEffect = if (rel.isToEvent) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                                                )

                                                // Draw direction indicator dot towards target
                                                val midOffset = Offset(
                                                    x = startPos.x * 0.4f + endPos.x * 0.6f,
                                                    y = startPos.y * 0.4f + endPos.y * 0.6f
                                                )
                                                drawCircle(
                                                    color = edgeColor.copy(alpha = alpha),
                                                    radius = 4.5f,
                                                    center = midOffset
                                                )
                                            }
                                        }
                                    }

                                    // 4. Render interactive nodes for characters
                                    characters.forEach { char ->
                                        val pos = characterPositions[char.id] ?: return@forEach
                                        val isSelected = selectedCharId == char.id
                                        val nodeColor = Color(char.avatarColor)

                                        Box(
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        x = (pos.x - 70.dp.toPx()).toInt().coerceAtLeast(4),
                                                        y = (pos.y - 25.dp.toPx()).toInt().coerceAtLeast(4)
                                                    )
                                                }
                                                .width(140.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else nodeColor.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedCharId = if (selectedCharId == char.id) null else char.id
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(nodeColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = char.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = char.role,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 5. Render nodes for referenced events
                                    referencedEvents.forEach { event ->
                                        val pos = eventPositions[event.id] ?: return@forEach
                                        Box(
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        x = (pos.x - 60.dp.toPx()).toInt().coerceAtLeast(4),
                                                        y = (pos.y - 18.dp.toPx()).toInt().coerceAtLeast(4)
                                                    )
                                                }
                                                .width(120.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Flag,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = event.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // 6. Selected character info card if tapped
                                    selectedCharId?.let { selId ->
                                        val selChar = characters.find { it.id == selId }
                                        if (selChar != null) {
                                            val charRels = relationships.filter { it.sourceCharacterId == selId || (!it.isToEvent && it.targetId == selId) }
                                            Card(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(12.dp)
                                                    .fillMaxWidth(0.9f),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = CardDefaults.cardElevation(4.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(selChar.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                        Text("${charRels.size} connection(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    if (charRels.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = charRels.joinToString(", ") { r ->
                                                                val otherName = if (r.sourceCharacterId == selId) {
                                                                    if (r.isToEvent) events.find { it.id == r.targetId }?.title ?: "Event"
                                                                    else characters.find { it.id == r.targetId }?.name ?: "Character"
                                                                } else {
                                                                    characters.find { it.id == r.sourceCharacterId }?.name ?: "Character"
                                                                }
                                                                "${r.relationType} ($otherName)"
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                } else {
                    // RIGHT SIDE: Connection Schema Ledger/List
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "CONNECTION DATABASE LEDGER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Map custom links like database foreign-keys connecting your narrative profiles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (relationships.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.DeviceHub,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No connections created.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Click 'Connect Node' above to link characters and events.", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(relationships) { rel ->
                                    val sourceChar = characters.find { it.id == rel.sourceCharacterId }
                                    val targetName = if (rel.isToEvent) {
                                        events.find { it.id == rel.targetId }?.title ?: "Unknown Event"
                                    } else {
                                        characters.find { it.id == rel.targetId }?.name ?: "Unknown Character"
                                    }

                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = sourceChar?.name ?: "Character",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Icon(
                                                        imageVector = if (rel.isToEvent) Icons.Default.ArrowRightAlt else Icons.Default.SwapHoriz,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = targetName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = rel.relationType,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                if (rel.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = rel.description,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteRelationship(rel) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Connection",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
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
    }

    if (showAddDialog) {
        AddRelationshipDialog(
            characters = characters,
            events = events,
            onDismiss = { showAddDialog = false },
            onSave = { sourceCharId, targetId, isToEvent, relationType, desc ->
                viewModel.addRelationship(sourceCharId, targetId, isToEvent, relationType, desc)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRelationshipDialog(
    characters: List<CharacterProfile>,
    events: List<StoryEvent>,
    onDismiss: () -> Unit,
    onSave: (sourceCharId: Int, targetId: Int, isToEvent: Boolean, relationType: String, desc: String) -> Unit
) {
    if (characters.isEmpty()) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No characters available.", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        }
        return
    }

    var selectedSourceId by remember { mutableStateOf(characters.first().id) }
    var relationType by remember { mutableStateOf("Ally") }
    var description by remember { mutableStateOf("") }
    var isToEvent by remember { mutableStateOf(false) }

    // Target Selection state
    var selectedTargetId by remember {
        mutableStateOf(
            if (isToEvent) {
                if (events.isNotEmpty()) events.first().id else 0
            } else {
                val nonSource = characters.filter { it.id != selectedSourceId }
                if (nonSource.isNotEmpty()) nonSource.first().id else characters.first().id
            }
        )
    }

    // Recalculate target selections when source character or target type changes
    LaunchedEffect(selectedSourceId, isToEvent) {
        selectedTargetId = if (isToEvent) {
            if (events.isNotEmpty()) events.first().id else 0
        } else {
            val nonSource = characters.filter { it.id != selectedSourceId }
            if (nonSource.isNotEmpty()) nonSource.first().id else characters.first().id
        }
    }

    val relationTypes = listOf("Ally", "Rival", "Family", "Enemy", "Mentor", "Love Interest", "Present At")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Schematic Connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // 1. Source character selection chip row
                Text("Select Origin Character:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    characters.take(4).forEach { char ->
                        FilterChip(
                            selected = selectedSourceId == char.id,
                            onClick = { selectedSourceId = char.id },
                            label = { Text(char.name, fontSize = 10.sp) }
                        )
                    }
                }

                // 2. Target type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Connection target is a milestone event?", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = isToEvent,
                        onCheckedChange = { isToEvent = it }
                    )
                }

                // 3. Target entity selection list
                if (isToEvent) {
                    if (events.isEmpty()) {
                        Text(
                            "No events defined yet. Add events in the storyline timeline tab first.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Select Target Event:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            events.take(3).forEach { event ->
                                FilterChip(
                                    selected = selectedTargetId == event.id,
                                    onClick = { selectedTargetId = event.id },
                                    label = { Text(event.title, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                } else {
                    val availableTargets = characters.filter { it.id != selectedSourceId }
                    if (availableTargets.isEmpty()) {
                        Text("Create more characters to connect them together.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Select Target Character:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableTargets.take(4).forEach { char ->
                                FilterChip(
                                    selected = selectedTargetId == char.id,
                                    onClick = { selectedTargetId = char.id },
                                    label = { Text(char.name, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }

                // 4. Relationship type dropdown chips
                Text("Select Relationship Label:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    relationTypes.take(4).forEach { type ->
                        FilterChip(
                            selected = relationType == type,
                            onClick = { relationType = type },
                            label = { Text(type, fontSize = 10.sp) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    relationTypes.drop(4).forEach { type ->
                        FilterChip(
                            selected = relationType == type,
                            onClick = { relationType = type },
                            label = { Text(type, fontSize = 10.sp) }
                        )
                    }
                }

                // 5. Short Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Connection details / backstory") },
                    placeholder = { Text("How do these two elements interact in your narrative?") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedTargetId != 0) {
                                onSave(
                                    selectedSourceId,
                                    selectedTargetId,
                                    isToEvent,
                                    relationType,
                                    description
                                )
                            }
                        },
                        enabled = selectedTargetId != 0,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
