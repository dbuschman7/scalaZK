package me.lightspeed7.scalazk.recipes

import me.lightspeed7.scalazk.TestHelper
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await

class CountersTest extends FunSuite with TestHelper {
  import scala.concurrent.ExecutionContext.Implicits.global

  test("Shared counter is counting") {

    val counts = new AtomicInteger(0)

    Counters.sharedCounter(client, s"${baseDir}/shared/counter", 123) map { counter =>
      counter.addListener { newValue => counts.incrementAndGet() }
      Await.result(counter.getCount, timeout) should be(123)

      Await.result(counter.setCount(234), timeout)
      Await.result(counter.getCount, timeout) should be(234)

      val vv = Await.result(counter.getVersionedValue, timeout)
      Await.result(counter.setCount(345), timeout)

      Await.result(counter.trySetCount(vv, 345), timeout) should be(true)
      
      counts.get() should be(3)
    }

  }
}