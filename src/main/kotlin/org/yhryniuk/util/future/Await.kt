package org.yhryniuk.util.future

import java.util.concurrent.TimeUnit

/**
 * Created by yaroslav on 12.06.2016.
 */
object Await {
    fun <T> result(future: RichFuture<T>, duration: Duration): T {
        return future.toCompletionFuture().get(duration.amount, duration.unit)
    }
}


fun Int.seconds() = Duration(this.toLong(), TimeUnit.SECONDS)
fun Int.second() = Duration(this.toLong(), TimeUnit.SECONDS)
fun Int.millis() = Duration(this.toLong(), TimeUnit.MILLISECONDS)
fun Int.minutes() = Duration(this.toLong(), TimeUnit.MINUTES)
fun Int.minute() = Duration(this.toLong(), TimeUnit.MINUTES)

class Duration(val amount: Long, val unit: TimeUnit)