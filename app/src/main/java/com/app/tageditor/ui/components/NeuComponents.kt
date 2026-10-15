package com.app.tageditor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuAccentSoft
import com.app.tageditor.ui.theme.NeuError
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuLight
import com.app.tageditor.ui.theme.NeuOnAccent
import com.app.tageditor.ui.theme.NeuSurface
import com.app.tageditor.ui.theme.NeuTextHint
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.ui.theme.neuInset
import com.app.tageditor.ui.theme.neuRaised

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

/** A soft card that rises out of the background. */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .neuRaised(corner = corner, elevation = elevation)
            .padding(contentPadding),
        content = content
    )
}

/** A panel that is pressed into the background. */
@Composable
fun NeuWell(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .neuInset(corner = corner, depth = 4.dp)
            .padding(contentPadding),
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** Tactile button: raised when idle, pushed in while pressed. */
@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed && enabled
    val contentColor = when {
        !enabled -> NeuTextHint
        primary -> NeuOnAccent
        danger -> NeuError
        else -> NeuTextPrimary
    }
    val base = modifier.defaultMinSize(minHeight = 54.dp)
    val styled = when {
        primary && !enabled -> base.neuRaised(corner = 18.dp, elevation = 3.dp, body = NeuLight)
        primary && down -> base.neuRaised(corner = 18.dp, elevation = 2.dp, body = NeuAccentSoft)
        primary -> base.neuRaised(corner = 18.dp, elevation = 7.dp, body = NeuAccent)
        down -> base.neuInset(corner = 18.dp, depth = 4.dp)
        else -> base.neuRaised(corner = 18.dp, elevation = 7.dp)
    }
    Row(
        modifier = styled
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 48.dp,
    tint: Color = NeuTextPrimary
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed && enabled
    val sized = modifier.size(buttonSize)
    val styled = if (down) {
        sized.neuInset(corner = buttonSize / 2, depth = 3.dp)
    } else {
        sized.neuRaised(corner = buttonSize / 2, elevation = 5.dp)
    }
    Box(
        modifier = styled.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else NeuTextHint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Small raised pill, used for template placeholders and quick actions. */
@Composable
fun NeuChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = modifier.defaultMinSize(minHeight = 40.dp)
    val styled = if (pressed && enabled) {
        base.neuInset(corner = 14.dp, depth = 2.dp)
    } else {
        base.neuRaised(corner = 14.dp, elevation = 4.dp)
    }
    Box(
        modifier = styled
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) NeuAccent else NeuTextHint,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------

/** Sunken text field with its label above. */
@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NeuTextSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) NeuTextPrimary else NeuTextHint
            ),
            cursorBrush = SolidColor(NeuAccent),
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = if (singleLine) imeAction else ImeAction.Default
            ),
            modifier = Modifier
                .fillMaxWidth()
                .neuInset(corner = 16.dp, depth = 4.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeuTextHint
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/** Square check box: raised when off, sunken with an amber tick when on. */
@Composable
fun NeuCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        val box = Modifier.size(28.dp)
        Box(
            modifier = if (checked) {
                box.neuInset(corner = 9.dp, depth = 3.dp)
            } else {
                box.neuRaised(corner = 9.dp, elevation = 4.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = NeuIcons.Check,
                    contentDescription = null,
                    tint = NeuAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Sliding switch with a sunken track and a raised thumb. */
@Composable
fun NeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "switchThumb"
    )
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 34.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .neuInset(corner = 17.dp, depth = 3.dp)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .neuRaised(
                    corner = 13.dp,
                    elevation = 3.dp,
                    body = if (checked) NeuAccent else NeuSurface
                )
        )
    }
}

/** A label with a switch on the right, used for options. */
@Composable
fun NeuSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuTextSecondary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        NeuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Slider in a sunken groove. */
@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neuInset(corner = 18.dp, depth = 3.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = NeuAccent,
                activeTrackColor = NeuAccent,
                inactiveTrackColor = NeuLight
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------------------------

/** Progress groove. A null [progress] shows a pulsing indeterminate bar. */
@Composable
fun NeuProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "busy")
    val pulse by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "busyPulse"
    )
    val fraction = (progress ?: pulse).coerceIn(0.02f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .neuInset(corner = 8.dp, depth = 2.dp)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(5.dp))
                .background(NeuAccent)
        )
    }
}

/** Read-only fact shown in a small sunken pill. */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(
        modifier = modifier
            .neuInset(corner = 12.dp, depth = 2.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) NeuAccent else NeuTextSecondary,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(pills: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (pill in pills) {
            InfoPill(text = pill.first, highlight = pill.second)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Layout helpers
// ---------------------------------------------------------------------------------------------

@Composable
fun NeuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            NeuIconButton(
                icon = NeuIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                buttonSize = 46.dp
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeuTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeuTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NeuTextSecondary,
        modifier = modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp)
    )
}
