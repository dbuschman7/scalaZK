package me.lightspeed7.scalazk
import scala.concurrent.Await

import org.scalatest.FunSuite
import org.scalatest.Matchers._

import me.lightspeed7.scalazk.Transaction.OperationResult

class TransactionTest extends FunSuite with TestHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("Transaction support") {
    import Operations._
    import Implicits._

    val testPath = s"${baseDir}/a/path"

    val prep = client.exists("/transaction") map { result =>
      println("Deleting...")
      client.delete("/transaction", true) map { result => }
    }
    Await.result(prep, timeout)

    val create = client.create("/transaction", "", true) map { result => }
    Await.result(create, timeout)

    val future = Transaction.start(client) { ops =>
      ops :+
        Create("/transaction/foo", 12345L) :+
        Set("/transaction/foo", 2345L)
    }

    val results = Await.result(future, timeout)
    println(s"Results = ${results}")

    var count: Int = 0

    results map { result =>
      result match {
        case OperationResult(op, _, _, Some(ex)) => fail("Transaction failed") // any failure
        case OperationResult(op, Some(path), None, None) => { // create success
          count += 1
          op.toZOp(client.namespace).getType() should be(1)
          path should not be (None)
        }
        case OperationResult(op, None, Some(stat), None) => { // set success 
          count += 1
          op.toZOp(client.namespace).getType() should be(5)
        }
      }
    }
    count should be(2) // two commands succeeded
  }

}  