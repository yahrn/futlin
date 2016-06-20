package org.yhryniuk.util.future

import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Created by yaroslav on 20.06.2016.
 */
class AwaitTest{

    @Test
    fun basic_duration_dsl_test(){
        assert(Duration(1, TimeUnit.SECONDS) == 1.seconds())
        assert(Duration(1, TimeUnit.SECONDS) == 1.second())
        assert(Duration(1, TimeUnit.MINUTES) == 1.minute())
        assert(Duration(1, TimeUnit.MINUTES) == 1.minutes())
        assert(Duration(1, TimeUnit.MILLISECONDS) == 1.millis())
    }

}