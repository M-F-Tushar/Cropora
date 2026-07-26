sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val code: Int, val message: String) : Result()
}

fun handle(r: Result): String = when (r) {
    is Result.Success -> "OK: ${r.data}"
    is Result.Error -> "ERR ${r.code}: ${r.message}"
}

fun main() {
    val a: Result = Result.Success("Loaded")
    val b: Result = Result.Error(404, "Not Found")
    println(handle(a))
    println(handle(b))
}
