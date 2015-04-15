package me.lighspeed7.scalazk

import org.scalatest.FunSuite
import org.scalatest.Matchers._
import me.lightspeed7.scalazk.Recipes
import scala.concurrent.Await
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

class RecipesTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("distributed lock success") {
    val future = Recipes.distributedLock[String](client, "/runsGreat", timeout) { client =>
      Thread.sleep(100) // make the test wait
      "String"
    }

    val result = Await.result(future, timeout)
    future.isCompleted should be(true)
    result.get should be("String")
  }

  test("distributed lock failed on thrown exception") {
    val msg = "Because I have to throw"

    val future = Recipes.distributedLock[String](client, "/throwException", timeout) { client =>
      Thread.sleep(100) // make the test wait
      throw new Exception(msg)
    }

    val result = Await.result(future, timeout)
    future.isCompleted should be(true)
    result.isFailure should be(true)
    result.recover {
      case t => t.getMessage() should be(msg)
    }
  }

  test("leader election set boolean") {

    val leaderElected: AtomicBoolean = new AtomicBoolean(false)

    Recipes.leaderElection(client, "/leaderElection", false) { client =>
      Thread.sleep(102)
      leaderElected.set(true)
    }

    val result = Await.result(Future { while (leaderElected.get() == false) { Thread.sleep(100) } }, timeout)
    leaderElected.get() should be(true)
  }
}