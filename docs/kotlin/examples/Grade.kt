fun grade(score: Int): String = when (score) {
    in 90..100 -> "A"
    in 80..89 -> "B"
    in 70..79 -> "C"
    in 60..69 -> "D"
    else -> "F"
}

fun main() {
    val scores = listOf(95, 82, 76, 63, 50)
    for (s in scores) println("Score: $s -> Grade: ${grade(s)}")
}
