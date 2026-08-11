package com.karan.distancewidget.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karan.distancewidget.data.ChatRepository
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.LocationData
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.data.db.MessageEntity
import com.karan.distancewidget.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val myId = Prefs.getUserId(context) ?: return
    val partnerId = Prefs.getPartnerId(context)
    val partnerInitial = Prefs.getPartnerInitial(context)

    val chatRepo = remember { ChatRepository(context) }
    var partnerLocation by remember { mutableStateOf<LocationData?>(null) }
    var myLocation by remember { mutableStateOf<LocationData?>(null) }

    val messages by chatRepo.getLocalMessages().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var chatTheme by remember { mutableStateOf(Prefs.getChatTheme(context)) }

    val themeColor by animateColorAsState(
        targetValue = when (chatTheme) {
            "MidnightBlue" -> Color(0xFF1E3A8A)
            "AccentRose" -> AccentRose
            "Emerald" -> Color(0xFF059669)
            "Violet" -> AccentViolet
            else -> AccentRose
        },
        label = "themeColor"
    )

    LaunchedEffect(Unit) {
        chatRepo.startListeningForMessages(myId, partnerId)
        partnerLocation = FirebaseHelper.getLocation(partnerId)
        myLocation = FirebaseHelper.getLocation(myId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var messageText by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                chatRepo.sendPhotoMessage(myId, partnerId, uri)
            }
        }
    }

    // Gesture state for sliding timestamps (Instagram style)
    var swipeOffset by remember { mutableStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = tween(durationMillis = if (swipeOffset == 0f) 300 else 0), // animate when released
        label = "swipeOffset"
    )
    val maxSwipePx = with(LocalDensity.current) { -80.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary) // Clean solid background for modern look
            .statusBarsPadding()
            .imePadding()
    ) {
        // ─── TOP BAR (MODERNIZED) ───────────────────────────────────────────────
        Surface(
            color = BgSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(themeColor, themeColor.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(partnerInitial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        partnerId.replaceFirstChar { it.uppercase() },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    val dist = if (myLocation != null && partnerLocation != null) {
                        val km = FirebaseHelper.distanceKm(myLocation!!, partnerLocation!!)
                        if (km <= 0.05) "Together! 💕" else FirebaseHelper.formatDistance(km) + " away"
                    } else "Syncing..."

                    Text(dist, color = TextSecondary, fontSize = 12.sp)
                }

                IconButton(onClick = { context.startActivity(Intent(context, StoryActivity::class.java)) }) {
                    Icon(Icons.Default.PhotoLibrary, "Shared Photos", tint = TextPrimary)
                }
                IconButton(onClick = {
                    chatTheme = when (chatTheme) {
                        "MidnightBlue" -> "AccentRose"
                        "AccentRose" -> "Emerald"
                        "Emerald" -> "Violet"
                        else -> "MidnightBlue"
                    }
                    Prefs.setChatTheme(context, chatTheme)
                }) {
                    Icon(Icons.Default.ColorLens, "Change Theme", tint = themeColor)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Settings", tint = TextPrimary)
                }
            }
        }

        // ─── CHAT MESSAGES WITH GESTURE REVEAL ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { swipeOffset = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(maxSwipePx, 0f)
                        }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp) // Tighter grouping for modern feel
            ) {
                var prevSenderId: String? = null
                items(messages.size) { index ->
                    val message = messages[index]
                    val isMine = message.senderId == myId
                    
                    // Grouping bubbles logic
                    val nextMessage = if (index < messages.size - 1) messages[index + 1] else null
                    val isNextMine = nextMessage?.senderId == myId
                    val isLastInGroup = isNextMine != isMine

                    MessageBubble(
                        message = message,
                        isMine = isMine,
                        isLastInGroup = isLastInGroup,
                        themeColor = themeColor,
                        swipeOffset = animatedSwipeOffset
                    )
                    
                    if (isLastInGroup && index != messages.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    prevSenderId = message.senderId
                }
            }
        }

        // ─── BOTTOM INPUT BAR (MODERNIZED) ──────────────────────────────────────
        Surface(
            color = Color.Transparent, // Removed surface color to match Telegram
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp), // Tighter padding
                verticalAlignment = Alignment.Bottom
            ) {
                // Text field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    placeholder = { Text("Message", color = TextSecondary, fontSize = 16.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = BgCard,
                        unfocusedContainerColor = BgCard,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = themeColor
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.EmojiEmotions,
                            contentDescription = "Emoji",
                            tint = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(26.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(
                                imageVector = Icons.Default.AttachFile, 
                                contentDescription = "Attach", 
                                tint = TextSecondary,
                                modifier = Modifier.rotate(-45f).size(26.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                // Send / Mic button
                val isTyping = messageText.isNotBlank()
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(themeColor)
                        .clickable {
                            if (isTyping) {
                                val text = messageText
                                messageText = ""
                                scope.launch {
                                    chatRepo.sendMessage(myId, partnerId, text)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isTyping) {
                        Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp).offset(x = 2.dp))
                    } else {
                        Icon(Icons.Default.Mic, "Voice", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMine: Boolean,
    isLastInGroup: Boolean,
    themeColor: Color,
    swipeOffset: Float
) {
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeString = formatter.format(Date(message.timestamp))

    // Calculate dynamic corner radii based on grouping
    val topStart = 20.dp
    val topEnd = 20.dp
    val bottomStart = if (isMine) 20.dp else if (isLastInGroup) 20.dp else 4.dp
    val bottomEnd = if (!isMine) 20.dp else if (isLastInGroup) 20.dp else 4.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Timestamp revealed when swiping
        val maxOffset = with(LocalDensity.current) { 80.dp.toPx() }
        val revealProgress = (swipeOffset / -maxOffset).coerceIn(0f, 1f)
        
        Text(
            text = timeString,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset((80.dp.toPx() * (1f - revealProgress)).roundToInt(), 0) }
                .alpha(revealProgress)
                .padding(end = 16.dp)
        )

        // The actual chat bubble shifted by the swipe gesture
        Column(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = topStart,
                            topEnd = topEnd,
                            bottomStart = bottomStart,
                            bottomEnd = bottomEnd
                        )
                    )
                    .background(if (isMine) themeColor else BgCard)
                    .padding(
                        horizontal = if (message.isPhoto) 4.dp else 16.dp,
                        vertical = if (message.isPhoto) 4.dp else 10.dp
                    )
            ) {
                if (message.isPhoto && message.photoUrl != null) {
                    AsyncImage(
                        model = "data:image/jpeg;base64,${message.photoUrl}",
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .heightIn(min = 150.dp, max = 250.dp)
                            .widthIn(min = 150.dp, max = 250.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
