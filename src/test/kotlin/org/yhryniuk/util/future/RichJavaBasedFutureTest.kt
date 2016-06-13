package org.yhryniuk.util.future

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Created by yaroslav on 12.06.2016.
 */
class RichJavaBasedFutureTest {


    @Test
    fun foo() {
        val future = RichFuture {
            "2" + "2"
        }
        val res = future.toCompletionFuture().get(1, TimeUnit.SECONDS)
        assertEquals(res, "22")
    }

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

    fun <T> RichFuture<T>.wait() = Await.result(this, 100.seconds())

}