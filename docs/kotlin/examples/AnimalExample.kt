open class Animal(val name: String) {
    open fun speak() = println("...")
}

class Dog(name: String) : Animal(name) {
    override fun speak() = println("$name: Woof!")
}

class Cat(name: String) : Animal(name) {
    override fun speak() = println("$name: Meow!")
}

fun main() {
    val animals: List<Animal> = listOf(Dog("Rex"), Cat("Mittens"))
    animals.forEach { it.speak() }
}
