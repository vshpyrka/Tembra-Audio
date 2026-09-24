package audio.tembra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import audio.tembra.ui.resources.Res
import audio.tembra.ui.resources.inter_medium
import audio.tembra.ui.resources.inter_regular
import audio.tembra.ui.resources.inter_semibold
import audio.tembra.ui.resources.space_grotesk_bold
import audio.tembra.ui.resources.space_grotesk_medium
import audio.tembra.ui.resources.space_grotesk_semibold
import org.jetbrains.compose.resources.Font

/** Bundled static instances (SIL OFL 1.1; licences in composeResources/files). */
object TembraFonts {
    @Composable
    fun spaceGrotesk(): FontFamily = FontFamily(
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )

    @Composable
    fun inter(): FontFamily = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )

    @Composable
    fun of(font: TembraFont): FontFamily = when (font) {
        TembraFont.SpaceGrotesk -> spaceGrotesk()
        TembraFont.Inter -> inter()
    }
}
