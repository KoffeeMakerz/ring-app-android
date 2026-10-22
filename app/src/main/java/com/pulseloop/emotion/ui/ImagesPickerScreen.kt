package com.pulseloop.emotion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.ExperienceImages
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.SlideImage
import com.pulseloop.emotion.SlideRef
import com.pulseloop.ui.slideshow.SlideshowImageSet
import com.pulseloop.ui.slideshow.SlideshowImageSets
import kotlinx.coroutines.launch

/**
 * Build the slideshow one image at a time: tap app images to add or remove them, add your own
 * photos (appended, never replacing), and reorder or remove from the strip at the top.
 */
@Composable
fun ImagesPickerScreen(
    initial: List<String>,
    feelings: List<Feeling>,
    onDone: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refs by remember { mutableStateOf(SlideRef.normalized(initial)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var importing by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    val isFull = refs.size >= SlideRef.MAX

    fun toggle(ref: String) {
        refs = if (ref in refs) refs - ref else if (isFull) refs else refs + ref
        if (selected == ref) selected = null
    }
    fun remove(ref: String) {
        refs = refs - ref
        if (selected == ref) selected = null
    }
    fun move(ref: String, delta: Int) {
        val i = refs.indexOf(ref)
        val j = i + delta
        if (i < 0 || j !in refs.indices) return
        refs = refs.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(SlideRef.MAX)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        failed = 0
        importing += uris.size
        scope.launch {
            for (uri in uris) {
                val name = ExperienceImages.import(context, uri)
                importing -= 1
                if (name != null && refs.size < SlideRef.MAX) refs = refs + "file:$name" else failed += 1
            }
        }
    }

    Column(Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = EColors.text) }
            Text("Images", color = EColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (refs.isNotEmpty()) TextButton(onClick = { refs = emptyList(); selected = null }) { Text("Clear", color = EColors.accent) }
        }

        // Current slideshow.
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your slideshow", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${refs.size} of ${SlideRef.MAX}", color = EColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (refs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, EColors.line, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("Nothing yet. Tap images below to add them one by one.", color = EColors.textSecondary, fontSize = 13.sp) }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(refs.size) { if (refs.isNotEmpty()) listState.animateScrollToItem(refs.lastIndex) }
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(refs, key = { _, r -> r }) { i, ref ->
                        val slide = SlideRef.parse(ref) ?: return@itemsIndexed
                        val isSelected = selected == ref
                        Box(
                            Modifier.size(width = 66.dp, height = 76.dp).clip(RoundedCornerShape(10.dp))
                                .border(2.5.dp, if (isSelected) EColors.accent else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { selected = if (isSelected) null else ref }
                                .semantics { contentDescription = "Image ${i + 1} of ${refs.size}"; stateDescription = if (isSelected) "Selected" else "" },
                        ) {
                            SlideImage(slide, Modifier.fillMaxSize(), maxSide = 300)
                            Box(
                                Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp).clip(CircleShape).background(EColors.accent),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", color = EColors.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            Box(
                                Modifier.align(Alignment.TopEnd).size(32.dp).clickable { remove(ref) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove image ${i + 1}", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
                val sel = selected
                if (sel != null && sel in refs) {
                    val index = refs.indexOf(sel)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { move(sel, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Move earlier", tint = if (index > 0) EColors.text else EColors.textMuted)
                        }
                        IconButton(onClick = { move(sel, 1) }, enabled = index < refs.lastIndex) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Move later", tint = if (index < refs.lastIndex) EColors.text else EColors.textMuted)
                        }
                        Text("Image ${index + 1}", color = EColors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { remove(sel) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = EColors.danger, modifier = Modifier.size(18.dp))
                            Text("Remove", color = EColors.danger, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                } else {
                    Text("Tap an image to move it · × removes it", color = EColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = EColors.ground,
            contentColor = EColors.text,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = EColors.accent)
            },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("App images") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Your photos") })
        }

        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                val (suggested, more) = remember(feelings) { SlideshowImageSets.ordered(feelings.map { it.raw }) }
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (suggested.isNotEmpty()) {
                        item { SectionLabel("SUGGESTED FOR " + feelings.joinToString(" → ") { it.label.uppercase() }) }
                        suggested.forEach { set -> item(key = "s-" + set.id) { SetBlock(set, refs, isFull, ::toggle) { refs = it } } }
                    }
                    item { SectionLabel(if (suggested.isEmpty()) "ALL IMAGES" else "MORE IMAGES") }
                    more.forEach { set -> item(key = "m-" + set.id) { SetBlock(set, refs, isFull, ::toggle) { refs = it } } }
                }
            } else {
                val own = refs.count { it.startsWith("file:") }
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !isFull && importing == 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.accent)
                        Text(if (own == 0) "Add photos" else "Add more photos", color = EColors.accent, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (importing > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = EColors.accent, strokeWidth = 2.dp)
                            Text("Adding $importing photo${if (importing == 1) "" else "s"}…", color = EColors.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    if (failed > 0) Text("$failed photo${if (failed == 1) "" else "s"} couldn't be added.", color = EColors.textSecondary, fontSize = 13.sp)
                    if (isFull) Text("Your slideshow is full (${SlideRef.MAX}). Remove one to add another.", color = EColors.textSecondary, fontSize = 13.sp)
                    Text(
                        if (own == 0) "Pick one or several. They go to the end of your slideshow, and you can add more any time."
                        else "$own of your photos in the slideshow. New ones go to the end.",
                        color = EColors.textSecondary, fontSize = 13.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EColors.card).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = EColors.textSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            "We keep a private copy on this phone, so the slideshow still works if you delete the original. Nothing is uploaded.",
                            color = EColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onDone(refs) },
            enabled = importing == 0,
            colors = ButtonDefaults.buttonColors(containerColor = EColors.accent, contentColor = EColors.onAccent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 52.dp),
        ) {
            Text(if (refs.isEmpty()) "Done" else "Use ${refs.size} image${if (refs.size == 1) "" else "s"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SetBlock(
    set: SlideshowImageSet,
    refs: List<String>,
    isFull: Boolean,
    onToggle: (String) -> Unit,
    onSetRefs: (List<String>) -> Unit,
) {
    val all = set.images.map { "img:${it.name}" }
    val allIn = all.all { it in refs }
    val feeling = Feeling.fromRaw(set.feeling)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(set.name, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (feeling != null) {
                Box(Modifier.padding(start = 8.dp).size(7.dp).clip(CircleShape).background(feeling.color))
                Text(feeling.label, color = EColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (allIn) onSetRefs(refs.filterNot { it in all })
                    else {
                        val out = refs.toMutableList()
                        for (r in all) if (r !in out && out.size < SlideRef.MAX) out.add(r)
                        onSetRefs(out)
                    }
                },
                enabled = allIn || !isFull,
            ) { Text(if (allIn) "Remove all" else "Add all", color = EColors.accent, fontSize = 13.sp) }
        }
        set.images.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { _, image ->
                    val ref = "img:${image.name}"
                    val pos = refs.indexOf(ref)
                    val n = set.images.indexOf(image) + 1
                    Box(
                        Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(10.dp))
                            .border(2.5.dp, if (pos >= 0) EColors.accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onToggle(ref) }
                            .semantics {
                                contentDescription = "${set.name}, image $n"
                                stateDescription = if (pos >= 0) "In slideshow, number ${pos + 1}" else "Not added"
                            },
                    ) {
                        SlideImage(SlideRef.App(image.name), Modifier.fillMaxSize(), maxSide = 400)
                        if (pos < 0 && isFull) Box(Modifier.fillMaxSize().background(EColors.ground.copy(alpha = 0.6f)))
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                                .background(if (pos >= 0) EColors.accent else Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pos >= 0) Text("${pos + 1}", color = EColors.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            else Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
