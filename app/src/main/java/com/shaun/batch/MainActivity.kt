package com.shaun.batch

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shaun.batch.loader.BatchLoader
import com.shaun.batch.loader.Loader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        testThroughput()
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private suspend fun normalTest() = coroutineScope {
        val orderChunks = (0..3_000_10).toList().chunked(5)
        val normalAsync = orderChunks.map {
            async {
                getOrders(it)
            }
        }.awaitAll()

    }

    private suspend fun loadTest(loader: Loader<Int, Int>) = coroutineScope {
        val orderChunks = (0..3_000_10).toList().chunked(5)
        orderChunks.map { step ->
            async {
                val result = loader.loadByIds(ids = step.toSet())
                Log.d(TAG, "loadTest: ${result.size}")
                Log.d(TAG, "loadTest: $step")
            }
        }.awaitAll()
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    fun testThroughput() = runBlocking {
        val regularTime: Duration = measureTime { normalTest() }
        Log.d(TAG, "Benchmark - Regular time ${regularTime}ms")
        val batchedTime = measureTime { loadTest(BatchLoader(delegateLoader = BatchedOrderLoader())) }
        Log.d(TAG, "Benchmark - Batched time ${batchedTime}ms")
        val magnitude = (regularTime / batchedTime).roundToInt()
        Log.d(
            TAG,
            "Benchmark - Batch loaded in $batchedTime vs $regularTime  - $magnitude times faster!"
        )

        Log.d(TAG, "${batchedTime < regularTime}")
        Log.d(TAG, "Improvement is $magnitude" + (magnitude >= 4))
    }

}

private val rand = Random(System.currentTimeMillis())

suspend fun getOrders(ids: List<Int>): List<Int> {
    return withContext(Dispatchers.IO) {
        Thread.sleep(rand.nextLong(10, 30))
        ids
    }
}

class BatchedOrderLoader : Loader<Int, Int> {
    private val rand = Random(System.currentTimeMillis())

    override suspend fun loadByIds(ids: Set<Int>): Map<Int, Int> {
        return withContext(Dispatchers.IO) {
          val orders =   getOrders(ids.toList())
            return@withContext orders.map { it to it }.toMap()
        }
    }
}

