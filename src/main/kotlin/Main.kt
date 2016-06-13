/**
 * Created by yaroslav on 11.06.2016.
 */
import org.yhryniuk.util.future.Await
import org.yhryniuk.util.future.RichFuture
import org.yhryniuk.util.future.seconds

fun main(args: Array<String>) {
    println("hello")
    val future = RichFuture {
        Thread.sleep(5000)
        throw RuntimeException()
        "2" + "2"
    }.recover {
        when(it){
            is RuntimeException -> "bad"
            else -> "unknown"
        }
    }
    val result = Await.result(future, 6.seconds())
    println(result)
}