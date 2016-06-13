package org.yhryniuk.util.future

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Created by yaroslav on 12.06.2016.
 */
class Promise<T> {
    val future: CompletableFuture<T> = CompletableFuture()

    fun successful(t: T) {
        future.complete(t)
    }

    fun exceptionally(throwable: Throwable) {
        future.completeExceptionally(throwable)
    }

    fun toRichFuture(): RichFuture<T> {
        return toRichFuture(RichFuture.pool)
    }

    fun toRichFuture(async: Executor): RichFuture<T> {
        return RichJavaBasedFuture(future, async)
    }
}