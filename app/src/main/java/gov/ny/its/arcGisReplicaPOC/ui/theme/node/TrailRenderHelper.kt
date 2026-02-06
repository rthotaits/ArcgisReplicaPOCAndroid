import com.arcgismaps.Color
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.symbology.UniqueValue
import com.arcgismaps.mapping.symbology.UniqueValueRenderer

object TrailRendererHelper {
    fun create(fieldToRender: String, isPrimary: Boolean): UniqueValueRenderer {
        val symbolStyle = if (isPrimary) SimpleLineSymbolStyle.Solid else SimpleLineSymbolStyle.Dash
        val strokeWidth = if (isPrimary) 3.7f else 2.5f

        val colorMap = listOf(
            "Red" to Color.fromRgba(255, 0, 0, 255),
            "Blue" to Color.fromRgba(0, 0, 255, 255),
            "White" to Color.fromRgba(255, 255, 255, 255),
            "Yellow" to Color.fromRgba(255, 255, 0, 255),
            "Orange" to Color.fromRgba(255, 165, 0, 255),
            "Gray" to Color.fromRgba(128, 128, 128, 255),
            "Green" to Color.fromRgba(0, 128, 0, 255),
            "Cyan" to Color.fromRgba(0, 255, 255, 255),
            "Aqua" to Color.fromRgba(0, 255, 255, 255),
            "Purple" to Color.fromRgba(128, 0, 128, 255),
            "Brown" to Color.fromRgba(165, 42, 42, 255),
            "Black" to Color.fromRgba(0, 0, 0, 255)
        )

        val uniqueValues = colorMap.map { (label, arcgisColor) ->
            UniqueValue(
                description = label,
                label = label,
                symbol = SimpleLineSymbol(
                    style = symbolStyle,
                    color = arcgisColor,
                    width = strokeWidth
                ),
                values = listOf(label)
            )
        }

        return UniqueValueRenderer(
            fieldNames = listOf(fieldToRender),
            uniqueValues = uniqueValues,
            defaultLabel = "Other",

            defaultSymbol = SimpleLineSymbol(
                style = symbolStyle,
                color = Color.fromRgba(128, 128, 128, 255),
                width = 2f
            )
        )
    }
}