fun safeLength(s: String?): Int = s?.length ?: 0

fun main() {
    println(safeLength(null))
    println(safeLength("Kotlin"))
}
