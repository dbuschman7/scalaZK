package me.lightspeed7.scalazk.recipes

import me.lightspeed7.scalazk.TestHelper
import org.scalatest.FunSuite
import scala.concurrent.Await
import org.scalatest.Matchers._

class LocksTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("distributed lock success") {
    val future = Locks.sharedReentrantLock[String](client, s"${baseDir}/runsGreat", timeout) { client =>
      Thread.sleep(100) // make the test wait
      "String"
    }

    val result = Await.result(future, timeout)
    future.isCompleted should be(true)
    result should be("String")
  }

  test("distributed lock failed on thrown exception") {
    val msg = "Because I have to throw"

    val future = Locks.sharedReentrantLock[String](client, s"${baseDir}/throwException", timeout) { client =>
      Thread.sleep(100) // make the test wait
      throw new Exception(msg)
    }

    val result = Await.ready(future, timeout)
    future.isCompleted should be(true)
    future.recover {
      case t => t.getMessage() should be(msg)
    }
  }
}