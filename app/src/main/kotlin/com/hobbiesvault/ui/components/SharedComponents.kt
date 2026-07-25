package com.hobbiesvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hobbiesvault.model.MediaStatus

// ── Overflow menu (estilo Rokku: escurece o fundo e abre ancorado no canto
// superior direito, logo abaixo da top bar, em vez do DropdownMenu padrão sem
// scrim do Material) ───────────────────────────────────────────────────────
@Composable
fun OverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onDismissRequest,
                ),
        ) {
            Column(Modifier.align(Alignment.TopEnd)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(56.dp))
                Surface(
                    modifier        = Modifier.padding(end = 10.dp).widthIn(min = 230.dp),
                    shape           = RoundedCornerShape(14.dp),
                    tonalElevation  = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(Modifier.padding(vertical = 6.dp), content = content)
                }
            }
        }
    }
}

@Composable
fun OverflowMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Proportional Tab Row ──────────────────────────────────────────────────────
// Cada aba recebe largura proporcional ao texto medido, preenchendo a tela sem scroll.
// Conversão fiel do ProportionalTabBar do Flutter (shared_widgets.dart).
@Composable
fun ProportionalTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    selectedColor: Color,
    onTabSelected: (Int) -> Unit,
) {
    val density     = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val boldStyle   = remember { TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    val unselColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    // Mede a largura de cada label (bold = variante mais larga)
    val textWidthsPx = remember(tabs) {
        tabs.map { textMeasurer.measure(it, boldStyle).size.width.toFloat() }
    }
    val totalTextPx = textWidthsPx.sum()

    // Padding horizontal uniforme: distribui o espaço restante igualmente
    val hPaddingPx = ((screenWidthPx - totalTextPx) / (2 * tabs.size)).coerceAtLeast(0f)

    val tabWidths = textWidthsPx.map { with(density) { (it + 2 * hPaddingPx).toDp() } }
    val textWidths = textWidthsPx.map { with(density) { it.toDp() } }

    Column(Modifier.fillMaxWidth()) {
        // Labels
        Row(Modifier.fillMaxWidth().height(44.dp)) {
            tabs.forEachIndexed { i, label ->
                val selected = i == selectedTabIndex
                Box(
                    Modifier
                        .width(tabWidths[i])
                        .fillMaxHeight()
                        .clickable { onTabSelected(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize   = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (selected) selectedColor else unselColor,
                    )
                }
            }
        }
        // Indicator
        Row(Modifier.fillMaxWidth().height(2.dp)) {
            tabs.forEachIndexed { i, _ ->
                Box(Modifier.width(tabWidths[i]).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (i == selectedTabIndex) {
                        Box(Modifier.width(textWidths[i]).fillMaxHeight().background(selectedColor))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun CoverImage(url: String?, modifier: Modifier = Modifier, accentColor: Color = Color.Transparent) {
    if (url != null) {
        AsyncImage(
            model              = url,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = modifier,
        )
    } else {
        val bg = if (accentColor == Color.Transparent)
            MaterialTheme.colorScheme.surfaceVariant
        else
            accentColor.copy(alpha = 0.15f)
        Box(modifier.background(bg), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = if (accentColor == Color.Transparent)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else
                    accentColor.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun MediaGridCard(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Transparent,
    inLibrary: Boolean = false,
    onAddClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.56f)) {
            CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize(), accentColor = accentColor)
            if (inLibrary) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Na biblioteca",
                        color    = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            } else if (onAddClick != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text      = title,
            style     = MaterialTheme.typography.labelSmall,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier  = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun StatusChip(status: MediaStatus, modifier: Modifier = Modifier) {
    Surface(
        color    = status.color.copy(alpha = 0.15f),
        shape    = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text     = status.label,
            color    = status.color,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String = "",
    buttonLabel: String? = null,
    onButton: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
        if (buttonLabel != null && onButton != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onButton) { Text(buttonLabel) }
        }
    }
}

// ── Status option tile ───────────────────────────────────────────────────────
// Usado nas telas de "Adicionar" (Filmes, Jogos, Séries, Livros, Mangás) para
// listar as opções de status com ícone + descrição curta, em vez de uma lista simples.
@Composable
fun StatusOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(150), label = "bg",
    )
    val borderColor by animateColorAsState(
        if (selected) color else Color.Transparent,
        animationSpec = tween(150), label = "border",
    )
    val iconColor = if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = bgColor,
        border   = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (selected) color else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
    }
}
