package me.lightspeed7.scalazk
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.curator.framework.api.transaction._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.data.Stat
import me.lightspeed7.scalazk.Operations.Operation
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.OpResult._
import org.apache.zookeeper.OpResult

object Transaction {
  import Implicits._

  case class OperationResult(op: Operation, path: Option[String], stat: Option[Stat], error: Option[KeeperException])

  def start(client: ZkClient)(f: Seq[Operation] => Seq[Operation])(implicit ec: ExecutionContext): Future[Seq[OperationResult]] = {
    WrapInFuture[Seq[OperationResult]](client) { client =>
      performTransaction(client, f(Seq()))
    }
  }

  private def performTransaction(client: ZkClient, ops: Seq[Operation]): Seq[OperationResult] = {
    try {
      processResults(ops, client.getZookeeperClient.getZooKeeper().multi(ops map { _.toZOp(client.namespace()) }))
    }
    catch {
      case e: KeeperException => processResults(ops, e.getResults())
    }
  }

  private def processResults(ops: Seq[Operation], zResults: java.util.List[OpResult]): Seq[OperationResult] = {
    ops zip zResults map {
      case (op, result) =>
        val path: Option[String] = if (result.isInstanceOf[CreateResult]) result.asInstanceOf[CreateResult].getPath else None
        val stat: Option[Stat] = if (result.isInstanceOf[SetDataResult]) result.asInstanceOf[SetDataResult].getStat else None
        val error: Option[KeeperException] = {
          val rc = if (result.isInstanceOf[ErrorResult]) result.asInstanceOf[ErrorResult].getErr else 0
          rc match {
            case 0   => None
            case num => Some(KeeperException.create(Code.get(num)))
          }
        }

        OperationResult(op, path, stat, error)
    }
  }
}