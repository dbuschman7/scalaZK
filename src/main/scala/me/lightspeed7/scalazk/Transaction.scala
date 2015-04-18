package me.lightspeed7.scalazk

import org.apache.curator.framework.api.transaction.CuratorTransaction
import org.apache.curator.framework.api.transaction.CuratorTransactionBridge
import org.apache.curator.framework.api.transaction.CuratorTransactionResult
import java.util.Collection
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Try

object Transaction {

  def start(client: ZkClient)(f: CuratorTransaction => CuratorTransactionBridge)(implicit ec: ExecutionContext): Future[Try[Seq[CuratorTransactionResult]]] = {
    import scala.collection.JavaConverters._
    Future {
      Try {
        val trans = client.curator.inTransaction()
        val bridge = f(trans)
        val result = bridge.and().commit(); // done for you
        result.asScala.toVector
      }
    }
  }

}