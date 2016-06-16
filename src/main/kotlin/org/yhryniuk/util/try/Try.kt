package org.yhryniuk.util.`try`
import java.util.*

/**
 * Created by yaroslav on 11.06.2016.
 */

fun <T> Try<Try<T>>.unwrap() = this.flatMap { it }

interface Try<A> {
    fun isSuccess(): Boolean
    fun isFailure(): Boolean
    infix fun <B> map(m: (A) -> B): Try<B>
    infix fun <B> flatMap(f: (A) -> Try<B>): Try<B>
    infix fun recover(r: (Throwable) -> A): Try<A>
    fun get(): A
    fun toOption(): Optional<A>
    infix fun <U> foreach(f: (A) -> U): Unit

    companion object {

        fun <A> successful(a: A) =
                Success(a)

        fun <A> failed(exception: Throwable) =
                Failed<A>(exception)
    }
}


class Success<A>(val value: A) : Try<A> {
    override fun toOption(): Optional<A> =
            Optional.of(value)

    override fun <U> foreach(f: (A) -> U) {
        f(value)
    }

    override fun get(): A = value

    override fun recover(r: (Throwable) -> A) = this

    override fun <B> map(m: (A) -> B) = Try { m(value) }

    override fun <B> flatMap(f: (A) -> Try<B>) = f(value)

    override fun isSuccess() = true

    override fun isFailure() = false
}

class Failed<A>(val exception: Throwable) : Try<A> {
    override fun toOption(): Optional<A> =
            Optional.empty<A>()


    override fun <U> foreach(f: (A) -> U) {
    }

    override fun get(): A = throw exception

    override fun recover(r: (Throwable) -> A) = Try { r(exception) }

    override fun isSuccess() = false

    override fun isFailure() = true

    override fun <B> map(m: (A) -> B): Try<B> = this as Failed<B>

    override fun <B> flatMap(f: (A) -> Try<B>): Try<B> = this as Failed<B>
}

inline fun <A> Try(b: () -> A): Try<A> {
    return try {
        Success(b())
    } catch(e: Exception) {
        Failed<A>(e)
    }
}