package me.lightspeed7.scalazk

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await

import org.apache.zookeeper.KeeperException
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class ZkClientTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("initialize a ZkClient instance ") {
    assert(client != null)
    assert(client.curator != null)
    assert(client.isStarted())
  }

  test("CallWithRetry that always fails") {

    // cleanup
    Await.result(client.delete("/", true), timeout)
    val exists:Boolean = Await.result(client.exists("/"), timeout)
    assert(!exists) // missing

    val zkConfig = Await.result(Configuration.initialize(client), timeout)

    val msg = "nothing here"
    val result = zkConfig.callWithRetry(client => { throw new Exception(msg) })

    result.isFailure should be(true)
    result.failed.get.getMessage() should be(msg)

  }

  test("CallWithRetry that returns correctly") {

    val counter = new AtomicInteger(0)

    Configuration.initialize(client) map { zkConfig =>
      val result = zkConfig.callWithRetry(client => {
        val count = counter.incrementAndGet()
        println(s"Count = ${count}")
        if (count < 2) throw new KeeperException.OperationTimeoutException()
      })
      result.isSuccess should be(true)
      counter.get() should be(2)
    }

  }

}