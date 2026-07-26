fun sum(nums: List<Int>): Int = nums.sum()

fun average(nums: List<Int>): Double = if (nums.isEmpty()) 0.0 else nums.average()

fun main() {
    val numbers = listOf(10, 20, 30, 40)
    println("Sum: ${sum(numbers)}")
    println("Average: ${average(numbers)}")
}
