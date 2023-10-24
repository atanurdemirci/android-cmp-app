package com.example.uitestutil

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert
import kotlin.jvm.Throws

@Throws(Throwable::class)
suspend fun wr(
    delay: Long = 200,
    times: Int = 30,
    backup: (() -> Unit)? = null,
    task: () -> Unit
) {
    var res: TestRes.NotVerified = TestRes.NotVerified(RuntimeException("Condition Not initialized!"))
    delay(delay)
    repeat(times) {
        delay(250)
        when (val t = checkCondition(task)) {
            TestRes.Verified -> return
            is TestRes.NotVerified -> {
                if(it % 5 == 0 && it > times / 5) {
                    backup?.invoke()
                }
                res = t
            }
        }

    }
    throw res.th
}

@Throws(Throwable::class)
suspend fun periodicWr(
    period: Long = 1500,
    times: Int = 5,
    backup: (() -> Unit)? = null,
    task: () -> Unit
) {
    var res: TestRes.NotVerified = TestRes.NotVerified(RuntimeException("Condition Not initialized!"))
    repeat(times) {
        delay(period)
        when (val t = checkCondition(task)) {
            TestRes.Verified -> return
            is TestRes.NotVerified -> {
                backup?.invoke()
                res = t
            }
        }

    }
    throw res.th
}

fun checkCondition(task: () -> Unit): TestRes {
    return try {
        task()
        TestRes.Verified
    } catch (th: Throwable) {
        TestRes.NotVerified(th)
    }
}

fun JSONObject.toList() : List<Pair<String, Any?>> {
    val list = mutableListOf<Pair<String, Any?>>()
    this.keys().forEach {
        check { this.getString(it) }?.let { v -> list.add(Pair(it, v)) }
        check { this.getBoolean(it) }?.let { v -> list.add(Pair(it, v)) }
        check { this.getInt(it) }?.let { v -> list.add(Pair(it, v)) }
    }
    return list
}

private fun <E> check(block: () -> E): E? {
    return try {
        block.invoke()
    } catch (e: Exception) {
        null
    }
}

sealed class TestRes {
    object Verified : TestRes()
    data class NotVerified(val th: Throwable) : TestRes()
}

fun Boolean.assertTrue() = Assert.assertTrue(this)
fun Boolean.assertFalse() = Assert.assertFalse(false)
fun <T : Any?> T.assertNull() = Assert.assertNull(this)
infix fun <T> T.assertEquals(t: T) = apply { Assert.assertEquals(t, this) }
infix fun <T> T.assertNotEquals(t: T) = apply { Assert.assertNotEquals(t, this) }
fun <T : Any?> T.assertNotNull() = apply { Assert.assertNotNull(this) }

/**
 * Receive file.json and return the content as string
 */
fun String.jsonFile2String(): String = Thread.currentThread()
    .contextClassLoader
    .getResourceAsStream(this)
    .bufferedReader().use { it.readText() }

fun<A : Activity> ActivityScenario<A>.recreateAndResume(){
    this.moveToState(Lifecycle.State.RESUMED)
    this.recreate()
}