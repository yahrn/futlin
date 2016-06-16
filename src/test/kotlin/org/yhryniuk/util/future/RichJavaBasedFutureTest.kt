package org.yhryniuk.util.future

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by yaroslav on 12.06.2016.
 */

class RichJavaBasedFutureTest {


    @Test
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

    @Test
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

    @Test
    fun should_return_successful_result_instantly() {
        val result = Await.result(RichFuture.successful("test"), 1.millis())
        assert(result == "test")
    }

    @Test(expected = RuntimeException::class)
    fun should_return_failed_result_instantly() {
        Await.result(RichFuture.exceptionally<Any>(RuntimeException("test")), 1.millis())
    }


    @Test
    fun should_call_invoke_onFailure() {
        var msg = AtomicReference("ss")
        var promise = Promise<Any>()
        val f = RichFuture<String> {
            throw RuntimeException("exception")
        }
        f onFailure {
            when (it) {
                is RuntimeException -> {
                    msg.set(it.message)
                    promise.successful("")
                }
                else -> fail()
            }
        }
        shouldThrow(RuntimeException::class.java) { f.wait() }
        promise.toRichFuture().wait()
        assert(msg.get() == "exception")
    }

    @Test
    fun should_call_invoke_onFailure_after_failed() {
        var msg: String? = null
        val f = RichFuture.exceptionally<String>(RuntimeException("exception"))
        f onFailure {
            when (it) {
                is RuntimeException -> msg = it.message
                else -> fail()
            }
        }
        shouldThrow(RuntimeException::class.java) { f.wait() }
        sleep(100)
        assert(msg == "exception")
    }

    @Test
    fun should_call_invoke_onSuccess() {
        var msg = AtomicReference<String>()
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
        sleep(100)
        assert(msg.get() == "success")
    }

    @Test
    fun should_call_invoke_onSuccess_after_successful() {
        var msg = AtomicReference<String>()
        val f = RichFuture.successful ("success")
        f onSuccess  {
            when (it) {
                is String -> msg.set(it)
                else -> fail()
            }
        }
        f.wait()
        sleep(100)
        assert(msg.get() == "success")
    }


    fun <T> shouldThrow(clazz: Class<T>, block: () -> Any) {
        var isCatched = false
        try {
            block()
        } catch(e: Exception) {
            if (clazz.isInstance(e)) {
                isCatched = true
            }
        }
        assertTrue(isCatched)
    }

    fun <T> RichFuture<T>.wait() = Await.result(this, 100.seconds())

}