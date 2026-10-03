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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
                    val allNodes by viewModel.allNodes.collectAsState()
                    val novels = remember(allNodes) { allNodes.filter { it.isFolder } }
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
                                        "Create characters in Profiles first to draw relationship lines.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // Custom ER Schema Line Drawer Canvas
                                // Let's draw schematic connection waves between imaginary grid anchors to simulate database ER links
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val secondaryColor = MaterialTheme.colorScheme.secondary
                                val tertiaryColor = MaterialTheme.colorScheme.tertiary

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height

                                    // Visual ER connection curves
                                    relationships.forEachIndexed { index, rel ->
                                        val offsetMultiplier = (index % 4) + 1
                                        val startX = width * 0.2f
                                        val startY = height * (0.2f + (index * 0.12f).coerceAtMost(0.6f))
                                        val endX = width * 0.8f
                                        val endY = if (rel.isToEvent) {
                                            height * (0.3f + (index * 0.1f).coerceAtMost(0.5f))
                                        } else {
                                            height * (0.15f + (index * 0.15f).coerceAtMost(0.7f))
                                        }

                                        val pathColor = when (rel.relationType) {
                                            "Protagonist", "Ally", "Friend" -> Color(0xFF10B981)
                                            "Antagonist", "Rival", "Enemy" -> Color(0xFFEF4444)
                                            "Sibling", "Family", "Lover" -> Color(0xFFEC4899)
                                            else -> secondaryColor
                                        }

                                        // Draw modern ER path curves
                                        drawLine(
                                            color = pathColor.copy(alpha = 0.6f),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = 3f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                                        )

                                        drawCircle(
                                            color = pathColor,
                                            radius = 6f,
                                            center = Offset(startX, startY)
                                        )

                                        drawCircle(
                                            color = pathColor,
                                            radius = 6f,
                                            center = Offset(endX, endY)
                                        )
                                    }
                                }

                                // Overlay Interactive Floating Anchors/Badges for Characters and Milestones
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left list: Character profiles
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "CHARACTERS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        characters.take(5).forEach { char ->
                                            val colorHex = char.avatarColor
                                            val composeColor = Color(colorHex)
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .width(180.dp)
                                                    .border(1.dp, composeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .shadow(1.dp, RoundedCornerShape(8.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(composeColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = char.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(20.dp))

                                    // Right list: Targets (Characters or Events)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "DESTINATIONS & EVENTS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )

                                        if (events.isNotEmpty()) {
                                            events.take(3).forEach { event ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .width(180.dp)
                                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                        .shadow(1.dp, RoundedCornerShape(8.dp))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Flag,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = event.title,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        characters.drop(1).take(2).forEach { char ->
                                            val composeColor = Color(char.avatarColor)
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .width(180.dp)
                                                    .border(1.dp, composeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .shadow(1.dp, RoundedCornerShape(8.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(composeColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = char.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
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
