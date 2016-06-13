package org.yhryniuk.util.future

import org.junit.Assert.assertEquals
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
}