package org.yhryniuk.util.future

import org.yhryniuk.util.`try`.Try
import java.util.*
import java.util.concurrent.CompletableFuture
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
    return RichJavaBasedFuture(this, pool)
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
    fun toCompletionFuture(): CompletableFuture<T>
    fun withPool(async: Executor): RichFuture<T>
//    fun executor():Executor

    companion object {

        val pool = ForkJoinPool()

        fun <T> successful(b: T) = RichFuture(b)

        fun <T> exceptionally(exception: Exception): RichJavaBasedFuture<T> {
            val delegate = CompletableFuture<T>().apply { completeExceptionally(exception) }
            return RichJavaBasedFuture(delegate, RichFuture.pool)
        }

    }
}

data class RichJavaBasedFuture<T>(val delegate: CompletableFuture<T>, val async: Executor) : RichFuture<T> {
    override fun withPool(async: Executor): RichFuture<T> = RichJavaBasedFuture(delegate, async)

    override fun failed(): RichFuture<Throwable> {
        val promise = CompletableFuture<Throwable>()
        delegate.whenCompleteAsync(BiConsumer { value, exception ->
            if (exception != null)
                promise.completeExceptionally(exception)
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
        delegate.whenCompleteAsync(BiConsumer<T, Throwable> { t, u ->
            if (t != null)
                f(u)
        }, async)
    }

    override fun <U> onSuccess(f: (T) -> U) {
        delegate.whenCompleteAsync(BiConsumer<T, Throwable> { t, u ->
            f(t)
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
            copy(delegate.thenCompose { z -> x(z).toCompletionFuture() })

    override fun <B> map(x: (T) -> B): RichFuture<B> =
            copy(delegate.thenApplyAsync(Function<T, B> { e -> x(e) }, async))


    override fun toCompletionFuture() = this.delegate


    fun <B> copy(delegate: CompletableFuture<B>) = RichJavaBasedFuture(delegate, async)

    
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
        RichJavaBasedFuture(CompletableFuture<T>().apply { complete(b) }, RichFuture.pool)

fun <T> RichFuture(async: Executor, b: () -> T): RichFuture<T> {
    val promise = CompletableFuture.supplyAsync(Supplier<T> { b() }, RichFuture.pool)
    return RichJavaBasedFuture(promise, async)
}

fun <T> List<RichFuture<T>>.sequence(): RichFuture<List<T>> {
    return this.sequence(RichFuture.pool)
}


fun <T> List<RichFuture<T>>.sequence(pool: Executor): RichFuture<List<T>> {
    val completableStages = this.map { it.toCompletionFuture() }
    val promise = CompletableFuture.allOf(*completableStages.toTypedArray())
    return RichJavaBasedFuture(promise.thenApplyAsync({ ign ->
        completableStages.map { it.join() }
    }), pool)
}
