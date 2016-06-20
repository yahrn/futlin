package org.yhryniuk.util.future

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.yhryniuk.test.helpers.RepeatRule
import org.yhryniuk.util.`try`.Success
import org.yhryniuk.test.helpers.Repeat;
import org.yhryniuk.util.`try`.Failed
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by yaroslav on 12.06.2016.
 */

open class RichCompletableFuture {

    @Rule
    fun repeatRule() = RepeatRule();

    @Test
    @Repeat(100)
    fun should_Recover_from_exception() {
        val future = RichFuture<String> {
            throw RuntimeException("test")
        }

        val recover = future.recover {
            when (it) {
                is RuntimeException -> if (it.message == "test") "ok" else "not_ok"
                else -> "else"
            }
        }
        val result = Await.result(recover, 10.seconds())
        assert(result == "ok") {
            "recover doesn't work"
        }
    }

    @Test
    @Repeat(100)
    fun Recover_should_not_be_called() {
        val future = RichFuture {
            "test"
        } recover {
            fail()
            ""
        }

        assert("test" == future.wait())
    }

    @Test
    fun should_return_success_try() {
        val future = RichFuture { "test" }
        future.toTry().wait().recover { fail(); return@recover "" }
    }

    @Test @Repeat(100)
    fun should_return_failed_try() {
        val future = RichFuture {
            throw RuntimeException("exception")
            "test"
        }
        (future.toTry().wait().recover {
            when (it) {
                is RuntimeException ->
                    "exception"
                else -> "default"
            }
        }.get() == "exception")
    }

    @Test @Repeat(100)
    fun should_return_successful_result_instantly() {
        val result = Await.result(RichFuture.successful("test"), 1.millis())
        assert(result == "test")
    }

    @Test(expected = RuntimeException::class) @Repeat(100)
    fun should_return_failed_result_instantly() {
        Await.result(RichFuture.exceptionally<Any>(RuntimeException("test")), 1.millis())
    }


    @Test @Repeat(100)
    fun should_call_invoke_onFailure() {
        val msg = AtomicReference<String>()
        val f = RichFuture<String> {
            throw RuntimeException("exception")
        }
        f onFailure {
            when (it) {
                is RuntimeException -> {
                    msg.set(it.message)
                }
                else -> fail()
            }
        }
        shouldThrow(RuntimeException::class.java) { f.wait() }
        msg.waitForNotNull()
        assert(msg.get() == "exception")
    }

    @Test @Repeat(100)
    fun should_call_invoke_onFailure_after_failed() {
        val msg = AtomicReference<String>()
        val f = RichFuture.exceptionally<String>(RuntimeException("exception"))
        f onFailure {
            when (it) {
                is RuntimeException -> msg.set(it.message)
                else -> fail()
            }
        }
        shouldThrow(RuntimeException::class.java) { f.wait() }
        msg.waitForNotNull()
        assert(msg.get() == "exception")
    }

    @Test @Repeat(100)
    fun should_call_invoke_onSuccess() {
        val msg = AtomicReference<String>()
        val f = RichFuture {
            "success"
        }
        f onSuccess  {
            when (it) {
                is String -> msg.set(it)
                else -> fail()
            }
        }
        f.wait()
        msg.waitForNotNull()
        assert(msg.get() == "success")
    }

    @Test(timeout = 500) @Repeat(100)
    fun should_call_invoke_onSuccess_after_successful() {
        val msg = AtomicReference<String>()
        val f = RichFuture.successful ("success")
        f onSuccess  {
            when (it) {
                is String -> msg.set(it)
                else -> fail()
            }
        }
        f.wait()
        msg.waitForNotNull()
        assert(msg.get() == "success")
    }


    @Test(expected = NoSuchElementException::class) @Repeat(100)
    fun should_throw_NoSuchElementException() {
        RichFuture.successful("test").failed().wait()
    }

    @Test @Repeat(100)
    fun should_end_successfully_with_exception() {
        val failed = RichFuture {
            throw RuntimeException("asd")
        }.failed()
        val e: Throwable = failed.wait()
        when (e) {
            is RuntimeException -> assert(e.message == "asd")
            else -> fail()
        }
    }

    @Test @Repeat(100)
    fun should_call_onComplete_with_success_try() {
        val msg = AtomicReference<String>()
        val richFuture = RichFuture {
            "test"
        }
        richFuture.onComplete {
            when (it) {
                is Success -> msg.set(it.value)
                else -> fail()
            }
        }
        msg.waitForNotNull()
        assert(msg.get() == "test")
    }

    @Test @Repeat(100)
    fun should_call_onComplete_with_failed_try() {
        val msg = AtomicReference<String>()
        val future = RichFuture { throw RuntimeException("test") }
        future.onComplete {
            when (it) {
                is Success -> fail()
                is Failed -> msg.set(it.exception.message)
            }
        }
        shouldThrow(RuntimeException::class.java) { future.wait() }
        msg.waitForNotNull()
        assert(msg.get() == "test")
    }

    @Test @Repeat(100)
    fun should_filter_and_end_with_NoSuchElementException() {
        val f = RichFuture { "test" }
        shouldThrow(NoSuchElementException::class.java) {
            f.filter { it != "test" }.wait()
        }
    }

    @Test @Repeat(100)
    fun should_not_filter() {
        val f = RichFuture { "test" }.filter { it == "test" }
        assert(f.wait() == "test")
    }

    @Test @Repeat(100)
    fun should_zip_futures() {
        val f1 = RichFuture { "33" }
        val f2 = RichFuture { 2 }
        val zip = f1.zip(f2) {
            a, b ->
            Integer.valueOf(a) + 2
        }
        assert(zip.wait() == 35)
    }

    @Test
    fun should_sequencify() {
        val range = 1..100
        val res = range.map {
            RichFuture {
                Thread.sleep(ThreadLocalRandom.current().nextLong(100))
                it
            }
        }.sequence().wait()
        assert(res.sum() == range.sum())
    }

    @Test @Repeat(100)
    fun should_add_two_futures() {
        val f = RichFuture { 1 } + RichFuture { 2 }
        val res = f.map {
            val (first, second) = it
            first + second
        }.wait()
        assert(res == 3)
    }

    @Test @Repeat(100)
    fun should_call_foreach() {
        val msg = AtomicReference<String>()
        RichFuture {
            "test"
        }.foreach {
            msg.set(it)
        }
        msg.waitForNotNull()
        assert(msg.get() == "test")
    }


    //helpers
    fun <T> AtomicReference<T>.waitForNotNull() {
        val start = System.currentTimeMillis()
        while (get() == null) {
            val elapsed = System.currentTimeMillis() - start
            if (elapsed > 500) {
                throw TimeoutException()
            }
        }
    }


    fun <T> RichFuture<T>.wait() = wait(100)
    fun <T> RichFuture<T>.wait(i: Int) = Await.result(this, i.seconds())

    fun <T> shouldThrow(clazz: Class<T>, block: () -> Any) {
        var isCatched = false
        try {
            block()
        } catch(e: Throwable) {
            if (clazz.isInstance(e)) {
                isCatched = true
            }
        }
        assertTrue(isCatched)
    }

}