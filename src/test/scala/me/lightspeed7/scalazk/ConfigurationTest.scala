package me.lightspeed7.scalazk

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import me.lightspeed7.scalazk._
import org.apache.curator.framework.api.CuratorListener
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.util.Failure

class ConfigurationTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("duration should have distinct units") {
    val test1 = 5 minutes
    val unit = test1.unit
    val length = test1.length
    println(s"Length is $length, Unit is $unit")
    val test2 = FiniteDuration.apply(length, TimeUnit.valueOf(unit.toString()))
    assert(test1 == test2)
  }

  test("should store and recall values, and leave cleaned up") {
    val future = Configuration.initialize(client) map { config =>
      Await.result(config.setValue("tree/int", 123), timeout)
      Await.result(config.setValue("tree/long", 1234L), timeout)
      Await.result(config.setValue("tree/duration", 5 hours), timeout)
      Await.result(config.setValue("tree/string", "value"), timeout)
      Await.result(config.setValue("tree/dir1/dir2/value", 1234L), timeout)

      // test
      123 should be(Await.result(config.getValue("tree/int", 321), timeout))
      1234L should be(Await.result(config.getValue("tree/long", 4321L), timeout))
      (5 hours).toString should be(Await.result(config.getValue("tree/duration", 2 seconds), timeout).toString)
      "value" should be(Await.result(config.getValue("tree/string", "default"), timeout))
      1234L should be(Await.result(config.getValue("tree/dir1/dir2/value", 54321L), timeout))
    }

    val result = Await.result(future, timeout)
    val actual = dumpZKTree(client, "tree")

    val expected = """+ tree = 
|  + dir1 = 
|  |  + dir2 = 
|  |  |  + value = 1234
|  + duration = 5|HOURS
|  + long = 1234
|  + int = 123
|  + string = value
"""
    actual should be(expected)
  }

  test("Watcher support") {

    val count = new AtomicInteger(0)

    val listener = Watcher.addWatcher(client, "/foo") { context =>
      println(s"Event - ${context.event}")
      count.incrementAndGet()
    }

    val ready = Await.ready(listener, timeout)

    ready.map { context =>
      Configuration.initialize(client) map { zk =>
        Await.result(zk.setValue("foo/baz", "bar"), timeout)
        Thread.sleep(1000)
        count.get should be(1)
      }
    }

  }

  //
  // Private methods
  // /////////////////////////////
  def dumpZKTree(client: ZkClient, path: String): String = {
    def pad(level: Int, buf: String): String = {
      level match {
        case 0 => buf
        case _ => pad(level - 1, buf + "|  ")
      }
    }

    val buf: StringBuffer = new StringBuffer
    val f = new ZooKeeperTree(client, path).displayTree(path, timeout) { e =>
      buf.append(s"${pad(e.level, "")}+ ${e.name} = ${e.value}\n")
    }
    buf.toString()
  }
}