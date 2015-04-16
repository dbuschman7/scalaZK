package me.lightspeed7.scalazk.recipes

import me.lightspeed7.scalazk.TestHelper
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await

class BarriersTest extends FunSuite with TestHelper {
  import scala.concurrent.ExecutionContext.Implicits.global

  test("Barrier test") {
    val bool = new AtomicBoolean(false)

    val barrierObj = Barriers.distributedBarrier[String](client, s"${baseDir}/dist/barrier")
    val barrier = barrierObj.waitOnBarrier(5 seconds) { barrier =>
      bool.set(true)
      "Success"
    }
    val result = Await.result(barrier, timeout)
    bool.get() should be(true)
    result.isSuccess should be(true)
    result.get should be("Success")
    
    val result2 = barrierObj.removeBarrier
    result2.isSuccess should be(true)
  }
}