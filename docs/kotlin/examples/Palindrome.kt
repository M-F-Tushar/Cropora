fun String.isPalindrome(): Boolean {
    val cleaned = this.filter { it.isLetterOrDigit() }.lowercase()
    return cleaned == cleaned.reversed()
}

fun main() {
    println("racecar".isPalindrome())
    println("Hello".isPalindrome())
}
