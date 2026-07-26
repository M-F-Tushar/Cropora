data class Person(val name: String, val age: Int)

fun adults(people: List<Person>): List<String> =
    people.filter { it.age >= 18 }.map { it.name }

fun main() {
    val people = listOf(Person("Alice", 30), Person("Bob", 15), Person("Carol", 20))
    println(adults(people)) // [Alice, Carol]
}
