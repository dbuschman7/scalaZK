package me.lightspeed7.scalazk

import java.util.concurrent.atomic.AtomicInteger
import org.apache.zookeeper.KeeperException
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import scala.concurrent.Await
import org.apache.zookeeper.CreateMode

class ZkClientTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("initialize a ZkClient instance ") {
    assert(client != null)
    assert(client.curator != null)
    assert(client.isStarted())
  }

  test("CallWithRetry that always fails") {

    // cleanup
    client.delete().deletingChildrenIfNeeded().forPath("/")
    assert(null == client.checkExists().forPath("/")) // missing

    val zkConfig = Await.result(Configuration.initialize(client), timeout).asInstanceOf[ZooKeeperConfiguration]

    val msg = "nothing here"
    val result = zkConfig.callWithRetry(client => { throw new Exception(msg) })

    result.isFailure should be(true)
    result.failed.get.getMessage() should be(msg)

  }

  test("CallWithRetry that returns correctly") {

    val counter = new AtomicInteger(0)

    Configuration.initialize(client) map { config =>
      val zkConfig = config.asInstanceOf[ZooKeeperConfiguration]

      val result = zkConfig.callWithRetry(client => {
        val count = counter.incrementAndGet()
        println(s"Count = ${count}")
        if (count < 2) throw new KeeperException.OperationTimeoutException()
      })
      result.isSuccess should be(true)
      counter.get() should be(2)
    }

  }

  // Have not gotten these to work yet
  ignore("Transaction support") {

    val testPath = s"${baseDir}/a/path"
    println(s"TestPath = ${testPath}")

    // ??? - for some reason the transaction api has no way to create sub dirs
    val mkDirsResult = Await.result(client.mkdirs(testPath), timeout)
    if (mkDirsResult.isFailure) {
      println(s"Failure = ${mkDirsResult.failed.get.getMessage()}")
    }
    mkDirsResult.isSuccess should be(true)
    println("MkDirs passed")

    val future = Transaction.start(client) { transaction =>
      transaction
        .create().forPath(testPath, "some data".getBytes())
        .and()
        .setData().forPath(testPath, "other data".getBytes())
        .and()
        .delete().forPath(testPath)
      // Look MA! No and().commit() - its done for you
    }

    val result = Await.result(future, timeout)
    if (result.isFailure) {
      println(s"Failure = ${result.failed.get.getMessage()}")
    }
    future.isCompleted should be(true)
    println(s"Result => ${result}")
    result.isSuccess should be(true)
    result.get.size should be(3)
    result.get.foreach(cur => println(cur))
  }

  // TODO transactions
}