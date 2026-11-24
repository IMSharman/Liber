package com.imsharman.liber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.imsharman.liber.domain.model.BookFormat
import java.io.File

/**
 * Renders either:
 * 1. The book's internal sandbox cover (`context.filesDir/covers/<id>.webp`), OR
 * 2. A refined clothbound-style typographic book cover when the book has no embedded cover.
 */
@Composable
fun BookCoverImage(
    title: String,
    author: String,
    format: BookFormat,
    internalCoverPath: String?,
    seedColorArgb: Long,
    modifier: Modifier = Modifier,
    showFormatBadge: Boolean = true
) {
    val shape = RoundedCornerShape(8.dp)
    val coverFile = remember(internalCoverPath) {
        internalCoverPath?.let { File(it) }?.takeIf { it.exists() }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape)
    ) {
        if (coverFile != null) {
            AsyncImage(
                model = coverFile,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val baseColor = Color(seedColorArgb)
            val darkerColor = Color(
                red = (baseColor.red * 0.68f).coerceIn(0f, 1f),
                green = (baseColor.green * 0.68f).coerceIn(0f, 1f),
                blue = (baseColor.blue * 0.68f).coerceIn(0f, 1f),
                alpha = 1f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(baseColor, darkerColor)))
            ) {
                // Subtle book spine shadow strip on left edge
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(Color.Black.copy(alpha = 0.28f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFF6EFE4),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = author,
                        color = Color(0xFFD9CFC1),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showFormatBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(format.badgeColorArgb).copy(alpha = 0.92f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = format.displayName,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
