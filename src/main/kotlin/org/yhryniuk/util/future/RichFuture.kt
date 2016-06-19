package org.yhryniuk.util.future

import org.yhryniuk.util.`try`.Failed
import org.yhryniuk.util.`try`.Success
import org.yhryniuk.util.`try`.Try
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

/**
 * Created by yaroslav on 11.06.2016.
 */
fun <T> CompletableFuture<T>.toRich(pool: Executor = RichFuture.pool): RichFuture<T> {
    return CompletableFutureBased(this, pool)
}

fun <T> RichFuture<RichFuture<T>>.unwrap() = this.flatMap { it }

interface RichFuture<T> {
    infix fun <B> map(x: (T) -> B): RichFuture<B>
    infix fun <B> flatMap(x: (T) -> RichFuture<B>): RichFuture<B>
    fun <B, U> zip(other: RichFuture<B>, m: (T, B) -> U): RichFuture<U>
    infix fun <U> foreach(f: (T) -> U): Unit
    infix fun <U : T> recover(f: (Throwable) -> U): RichFuture<T>
    infix fun <U> onFailure(f: (Throwable) -> U): Unit
    infix fun <U> onSuccess(f: (T) -> U): Unit
    fun failed(): RichFuture<Throwable>
    fun toTry(): RichFuture<Try<T>>
    infix fun filter(p: (T) -> Boolean): RichFuture<T>
    fun toCompletableFuture(): CompletableFuture<T>
    fun withPool(async: Executor): RichFuture<T>
    fun onComplete(f: (Try<T>) -> Unit): Unit

    companion object {

        val pool = ForkJoinPool()

        fun <T> successful(b: T) = RichFuture(b)

        fun <T> exceptionally(exception: Exception): CompletableFutureBased<T> {
            val delegate = CompletableFuture<T>().apply { completeExceptionally(CompletionException(exception)) }
            return CompletableFutureBased(delegate, RichFuture.pool)
        }

    }
}

data class CompletableFutureBased<T>(val delegate: CompletableFuture<T>, val async: Executor) : RichFuture<T> {
    override fun onComplete(f: (Try<T>) -> Unit) {
        delegate.whenComplete { value, exception ->
            if (exception != null)
                Failed<T>(exception)
            else
                Success(value)
        }
    }

    override fun withPool(async: Executor): RichFuture<T> = CompletableFutureBased(delegate, async)

    override fun failed(): RichFuture<Throwable> {
        val promise = CompletableFuture<Throwable>()
        delegate.whenCompleteAsync(BiConsumer { value, exception ->
            if (exception != null)
                promise.complete(exception)
            else
                promise.completeExceptionally(NoSuchElementException())
        }, RichFuture.pool)
        return copy(promise)
    }

    override fun toTry(): RichFuture<Try<T>> {
        return copy(delegate.handleAsync(BiFunction<T, Throwable, Try<T>> { value, exception ->
            if (exception != null)
                Try.failed(exception)
            else
                Try.successful(value)
        }, async))
    }

    override fun <U : T> recover(f: (Throwable) -> U): RichFuture<T> {
        return copy(delegate.handleAsync(BiFunction <T, Throwable, T> { value, exception ->
            if (exception != null)
                f(exception.cause ?: RuntimeException())
            else
                value
        }, async))
    }

    override fun <U> onFailure(f: (Throwable) -> U) {
        delegate.whenCompleteAsync(BiConsumer<T, Throwable> { value, exception ->
            if (exception != null && exception.cause != null) {
                f(exception.cause!!)
            }
        }, async)
    }

    override fun <U> onSuccess(f: (T) -> U) {
        delegate.whenCompleteAsync(BiConsumer<T, Throwable> { value, exception ->
            if (exception === null) {
                f(value)
            }
        }, async)
    }

    override fun <U> foreach(f: (T) -> U) {
        map { f(it) }
    }

    override fun filter(p: (T) -> Boolean): RichFuture<T> =
            flatMap {
                if (p(it)) {
                    this
                } else {
                    val promise = CompletableFuture<T>()
                    promise.completeExceptionally(NoSuchElementException())
                    copy(promise)
                }
            }

    override fun <B, U> zip(other: RichFuture<B>, m: (T, B) -> U): RichFuture<U> =
            other.flatMap { it -> this.map { e -> m(e, it) } }

    override fun <B> flatMap(x: (T) -> RichFuture<B>): RichFuture<B> =
            copy(delegate.thenCompose { z -> x(z).toCompletableFuture() })

    override fun <B> map(x: (T) -> B): RichFuture<B> =
            copy(delegate.thenApplyAsync(Function<T, B> { e -> x(e) }, async))


    override fun toCompletableFuture() = this.delegate


    fun <B> copy(delegate: CompletableFuture<B>) = CompletableFutureBased(delegate, async)


}


operator fun <A, B> RichFuture<A>.plus(other: RichFuture<B>): RichFuture<Pair<A, B>> {
    return this.zip(other) {
        a, b ->
        Pair(a, b)
    }
}

data class Pair<A, B>(val first: A, val second: B)

fun <T> RichFuture(b: () -> T): RichFuture<T> {
    return RichFuture(RichFuture.pool, b)
}

fun <T> RichFuture(b: T): RichFuture<T> =
        CompletableFutureBased(CompletableFuture<T>().apply { complete(b) }, RichFuture.pool)

fun <T> RichFuture(async: Executor, b: () -> T): RichFuture<T> {
    val promise = CompletableFuture.supplyAsync(Supplier<T> { b() }, RichFuture.pool)
    return CompletableFutureBased(promise, async)
}

fun <T> List<RichFuture<T>>.sequence(): RichFuture<List<T>> {
    return this.sequence(RichFuture.pool)
}


fun <T> List<RichFuture<T>>.sequence(pool: Executor): RichFuture<List<T>> {
    val completableStages = this.map { it.toCompletableFuture() }
    val promise = CompletableFuture.allOf(*completableStages.toTypedArray())
    return CompletableFutureBased(promise.thenApplyAsync({ ign ->
        completableStages.map { it.join() }
    }), pool)
}
