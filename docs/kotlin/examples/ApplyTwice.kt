fun applyTwice(x: Int, f: (Int) -> Int): Int = f(f(x))

fun main() {
    val add3: (Int) -> Int = { it + 3 }
    println(applyTwice(5, add3)) // 11
}
