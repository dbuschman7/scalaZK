package me.lightspeed7.scalazk

import java.util.concurrent.atomic.AtomicInteger

import org.apache.zookeeper.KeeperException
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class ZkClientTest extends FunSuite with TestHelper {

  test("initialize a ZkClient instance ") {
    assert(client != null)
    assert(client.curator != null)
    assert(client.isStarted())
  }

  test("CallWithRetry that always fails") {

    // cleanup
    client.delete().deletingChildrenIfNeeded().forPath("/")
    assert(null == client.checkExists().forPath("/")) // missing

    val zkConfig = ZooKeeperConfiguration(client)

    val msg = "nothing here"
    val result = zkConfig.callWithRetry(client => { throw new Exception(msg) })

    result.isFailure should be(true)
    result.failed.get.getMessage() should be(msg)

  }

  test("CallWithRetry that returns correctly") {
    val zkConfig = ZooKeeperConfiguration(client)

    val counter = new AtomicInteger(0)

    val result = zkConfig.callWithRetry(client => {
      val count = counter.incrementAndGet()
      println(s"Count = ${count}")
      if (count < 2) throw new KeeperException.OperationTimeoutException()
    })

    result.isSuccess should be(true)
    counter.get() should be(2)
  }

}