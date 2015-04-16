package me.lightspeed7.scalazk.recipes
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.scalatest.FunSuite
import org.scalatest.Matchers._

import me.lightspeed7.scalazk.TestHelper

class ElectionsTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("leader election set boolean") {
    val leaderElected: AtomicBoolean = new AtomicBoolean(false)

    Elections.leaderElection(client, s"${baseDir}/leaderElection", false) { client =>
      Thread.sleep(102)
      leaderElected.set(true)
    }

    val result = Await.result(Future { while (leaderElected.get() == false) { Thread.sleep(100) } }, timeout)
    leaderElected.get() should be(true)
  }

  test("leader latch set boolean") {
    val leaderLatched: AtomicBoolean = new AtomicBoolean(false)

    val latch = Elections.leaderLatch[Unit](client, s"${baseDir}/leaderLatch1", Some("foo1"))(15 seconds) { client =>
      leaderLatched.set(true)
    }
    val result = Await.result(latch, timeout)
    result.isSuccess should be(true)
    leaderLatched.get() should be(true)
  }

  test("leader latch fails 1") {

    val latch = Elections.leaderLatch(client, s"${baseDir}/leaderLatch2", Some("foo2"))(5 seconds) { client =>
      throw new TimeoutException("Mine")
    }
    val result = Await.result(latch, timeout)
    result.isFailure should be(true)
    result.recover { case t => t.getMessage() should be("Mine") }
  }

  test("leader latch times out") {

    val latch1 = Elections.leaderLatch(client, s"${baseDir}/leaderLatch3", Some("foo2"))(5 seconds) { client =>
      println("Latch 1 sleeping")
      Thread.sleep(5000)
      println("Latch 1 releasing")
    }
    val latch2 = Elections.leaderLatch[String](client, s"${baseDir}/leaderLatch3", Some("foo2"))(2 seconds) { client =>
      println("NO!! - Latch 2 body executing")
      "DaVe."
    }
    val result = Await.result(latch2, timeout)
    result.isFailure should be(true)
    result.recover { case t => t.getMessage() should be("Mine") }
    Await.result(latch1, timeout)

  }
}