package org.yhryniuk.util.future

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Created by yaroslav on 12.06.2016.
 */
class Promise<T> {
    val future: CompletableFuture<T> = CompletableFuture()

    companion object {
        fun <T> successful(t: T) = Promise<T>().successful(t)


        fun <T> exceptionally(throwable: Throwable) = Promise<T>().exceptionally(throwable)

    }

    fun successful(t: T) = this.apply {  future.complete(t) }


    fun exceptionally(throwable: Throwable) = this.apply { future.completeExceptionally(throwable) }


    fun future(): RichFuture<T> {
        return future(RichFuture.pool)
    }

    fun future(async: Executor): RichFuture<T> {
        return CompletableFutureBased(future, async)
    }
}