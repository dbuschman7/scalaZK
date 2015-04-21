package me.lightspeed7.scalazk
import org.apache.zookeeper.KeeperException

import org.scalatest.FunSuite
import org.scalatest.Matchers._

import me.lightspeed7.scalazk.Operations._
import me.lightspeed7.scalazk.Transaction._

class OperationsTest extends FunSuite with TestHelper {
  import Implicits._
  import scala.concurrent.ExecutionContext.Implicits.global

  test("construct a list of operations") {
    val ops: Seq[Operation] = Seq() :+ Create("/foo", "data1") :+ Delete("/foobar") :+ Check("/check", 123) :+ Set("/set", "set")
    val results: Seq[OperationResult] = ops.map { op => OperationResult(op, None, None, new KeeperException.BadVersionException()) }
    results.size should be(4)

    // makre sure every object type is represented
    val total = ops.foldLeft(0)((tot, cur) => tot + cur.toZOp(client.namespace).getType())
    total should be(21)
    val total2 = results.foldLeft(total)((tot, cur) => tot - cur.op.toZOp(client.namespace).getType())
    total2 should be(0)

  }
}