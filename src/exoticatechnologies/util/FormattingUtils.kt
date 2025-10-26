package exoticatechnologies.util

import java.text.DecimalFormat

/**
 * Formats a [Float] to a #,### format. See [DecimalFormat]
 */
fun Float.toFormattedString(): String {
    val decimalFormat = DecimalFormat("#,###")
    val stringRepresentation = decimalFormat.format(this)
    return stringRepresentation
}
