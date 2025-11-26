package exoticatechnologies.util

import exoticatechnologies.util.tests.UtilTests

/**
 * Utility method that is barely worth the time it took to type it out.
 * It performs a logical range check for any subtype of [Number] and the check will be done in the class type of [this].
 *
 * There are three different cases in which it operates:
 *
 *     - the X <op> [rightVal] case, when [leftVal] is null
 *     - the [leftVal] <op> X case, when [rightVal] is null
 *     - the [leftVal] <op> X <op> [rightVal] case when both are non-null
 *
 *
 * While it doesn't throw for examples such as the following:
 * ```
 *     val num: Int = 123
 *     val left: Double = 123.toDouble()
 *     val right: Short = 255.toShort()
 *     num.isInLogicalRange(left, right)
 * ```
 * the caller **has to be aware** that both the [leftVal] and [rightVal] will be typecasted to [this]'s type.
 *
 * As such, imagine the shenanigans that happens in a seemingly-identical but vastly different case to the one above:
 * ```
 *     val num: Short = 123
 *     val left: Int = -65535
 *     val right: Double = 65536.toDouble()
 *     num.isInLogicalRange(left, right)
 * ```
 * in which case, regardless of the [workMode] that is passed, this will evaluate to an absurd statement such as this:
 * ```
 *     123.isInLogicalRange(1, 0)
 * ```
 * and whichever [InLogicalRangeWorkMode] value was actually used - it will surely result in [false] due to type folding.
 * *To better clarify*, this is the equivalent of:
 * ```
 *     1 < 123 < 0
 * or
 *     1 > 123 > 0
 * ```
 *
 * **Similar warning holds for testing [Int]s with [Long]s outside of the [Int.MIN_VALUE] or [Int.MAX_VALUE] range**
 * and other overflowing scenarios.
 *
 * Take a look at [UtilTests.test_what_happens_when_a_outofbound_number_is_cast_to_Short]
 * and [UtilTests.test_what_happens_in_the_absurd_case_mentioned_in_isInLogicalRange_javadoc]
 *
 * @param leftVal The 'left' value of the range check. If left null, the check defaults to this <op> rightVal
 * @param rightVal The 'right' value of the range check. If left null, the check defaults to leftVal <op> this
 * @param workMode The [InLogicalRangeWorkMode] to use for comparing the ```leftVal <op> this <op> rightVal``` range. Examples:
 *
 *     123.isInLogicalRange(10, 200, InLogicalRangeWorkMode.LESS_THAN)
 *     // evaluates to 10 < 123 < 200
 *
 *     3000.isInLogicalRange(5000,1000, InLogicalRangeWorkMode.GREATER_OR_EQUAL)
 *     // evaluates to 5000 >= 3000 >= 1000
 */
fun Number.isInLogicalRange(leftVal: Number? = null, rightVal: Number? = null, workMode: InLogicalRangeWorkMode): Boolean {
    // we have few cases:
    // 1. the X <= rightVal
    // 2. the leftVal <= X
    // 3. the leftVal <= X <= rightVal
    // 4. leftVal == rightVal or both being null or any other absurd illegal situation

    if (leftVal == null && rightVal == null) throw IllegalArgumentException("leftVal and rightVal cannot both be null!")
    if (leftVal == rightVal) throw IllegalArgumentException("leftVal and rightVal cannot be equal!")

    // So first thing's first, figure out which case we're dealing with
    return when(this) {
        // Double type
        is Double -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toDouble()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toDouble()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toDouble()
                    val right = rightVal.toDouble()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Double! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // Float type
        is Float -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toFloat()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toFloat()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toFloat()
                    val right = rightVal.toFloat()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Float! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // Long type
        is Long -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toLong()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toLong()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toLong()
                    val right = rightVal.toLong()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Long! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // Int type
        is Int -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toInt()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toInt()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toInt()
                    val right = rightVal.toInt()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Int! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // Short type
        is Short -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toShort()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toShort()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toShort()
                    val right = rightVal.toShort()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Short! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // Byte type
        is Byte -> {
            return when {
                leftVal == null && rightVal != null -> {
                    // Case 1, the X <op> rightVal
                    val right = rightVal.toByte()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> this >= right
                    }.exhaustive
                }

                leftVal != null && rightVal == null -> {
                    // Case 2: the leftVal <op> X
                    val left = leftVal.toByte()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this
                    }.exhaustive
                }

                leftVal != null && rightVal != null -> {
                    // Case 3: the leftVal <op> X <op> rightVal
                    val left = leftVal.toByte()
                    val right = rightVal.toByte()
                    when(workMode) {
                        InLogicalRangeWorkMode.LESS_THAN -> left < this && this < right
                        InLogicalRangeWorkMode.LESS_OR_EQUAL -> left <= this && this <= right
                        InLogicalRangeWorkMode.GREATER_THAN -> left > this && this > right
                        InLogicalRangeWorkMode.GREATER_OR_EQUAL -> left >= this && this >= right
                    }.exhaustive
                }

                else -> {
                    throw IllegalStateException("This should really never happen - but it happened for Byte! leftVal: ${leftVal}, rightVal: ${rightVal}")
                }
            }
        }

        // necessary else
        else -> {
            throw IllegalArgumentException("Unsupported Number subclass: ${this::class}")
        }
    }
}

/**
 * Enum class representing the work mode of [isInLogicalRange] method.
 *
 * @see LESS_THAN
 * @see LESS_OR_EQUAL
 * @see GREATER_THAN
 * @see GREATER_OR_EQUAL
 */
enum class InLogicalRangeWorkMode {
    /**
     * The ordering operation used for left operand and this, as well as this and right operand.
     * Represents the LESS-THAN (`<`) operation, such as
     *
     * `1 < this < 5`
     */
    LESS_THAN,
    /**
     * The ordering operation used for left operand and this, as well as this and right operand.
     * Represents the LESS-OR-EQUAL (`<=`) operation, such as
     *
     * `1 <= this <= 5`
     */
    LESS_OR_EQUAL,
    /**
     * The ordering operation used for left operand and this, as well as this and right operand.
     * Represents the GREATER-THAN (`>`) operation, such as
     *
     * `1 > this > 5`
     *
     * (yes, I **hope** you see the issue here)
     */
    GREATER_THAN,
    /**
     * The ordering operation used for left operand and this, as well as this and right operand.
     * Represents the GREATER-OR-EQUAL (`>=`) operation, such as
     *
     * `1 >= this >= 5`
     *
     * (yes, I **hope** you see the issue here)
     */
    GREATER_OR_EQUAL
}
