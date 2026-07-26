// Requires kotlinx-coroutines-core on the classpath to run.
import kotlinx.coroutines.*

fun main() = runBlocking {
    launch {
        repeat(3) { i ->
            println("Coroutine A: $i")
            delay(100)
        }
    }
    launch {
        repeat(3) { i ->
            println("Coroutine B: $i")
            delay(150)
        }
    }
}
