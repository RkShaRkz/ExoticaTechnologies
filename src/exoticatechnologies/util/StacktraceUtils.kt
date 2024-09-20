package exoticatechnologies.util

object StacktraceUtils {

    /**
     * Method for unwinding the stacktrace and returning a string containing a whole "typical" stacktrace
     * you see in logs. Whether the imminent place of happening will be at the top or the bottom of the trace
     * depends on the [startToEnd] argument, which defaults to [true] (*start* being the place of happening and *end*
     * being the place it originated from)
     *
     * @param stacktraceArray array containing [StackTraceElement]s
     * @param startToEnd whether the place you should start looking from will be located at the top or the bottom of the stacktrace
     * @return printable, formatted stacktrace, as a String
     */
    fun unwindStacktrace(stacktraceArray: Array<StackTraceElement?>, startToEnd: Boolean = true, useAtLikeNormalStacktrace: Boolean = true): String {
        val sb = StringBuilder()
        if (startToEnd) {
            for (i in stacktraceArray.indices) {
                sb
//                        .append("Element [")
//                        .append(i)
//                        .append("]:")
                        .append(if(useAtLikeNormalStacktrace) { "\tat " } else { "\t\t" } )
                        .append(stacktraceArray[i])
                        .append("\n")
            }
        } else {
            for (i in stacktraceArray.size - 1 downTo 0) {
                sb
//                        .append("Element [")
//                        .append(i)
//                        .append("]:")
                        .append(if(useAtLikeNormalStacktrace) { "\tat " } else { "\t\t" } )
                        .append(stacktraceArray[i])
                        .append("\n")
            }
        }
        return sb.toString()
    }

    fun unwindStacktraceFromException(exception: Exception): String {
        val sb = StringBuilder()
        sb
                .append(exception)
                .append("\n")
                .append(unwindStacktrace(exception.stackTrace))

        return sb.toString()
    }

    fun unwindStacktraceFromException(throwable: Throwable): String {
        val sb = StringBuilder()
        sb
                .append(throwable)
                .append("\n")
                .append(unwindStacktrace(throwable.stackTrace))

        return sb.toString()
    }
}