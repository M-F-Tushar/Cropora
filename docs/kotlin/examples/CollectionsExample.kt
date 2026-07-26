fun processList(nums: List<Int>): List<Int> =
    nums.filter { it % 2 == 0 }.map { it * 2 }

fun main() {
    val input = listOf(1, 2, 3, 4, 5, 6)
    println(processList(input)) // [4, 8, 12]
}
