package me.lightspeed7.scalazk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import org.apache.curator.RetryPolicy
import org.apache.curator.framework._
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.EnsurePath
import me.lightspeed7.scalazk.ZkClient._
import javax.naming.Name
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import scala.util.Failure
import scala.concurrent.ExecutionContext

object ZkClient {
  def ZkClientBuilder(serverList: Seq[InetSocketAddress]) = builder(serverList)

  private def builder(serverList: Seq[InetSocketAddress], retryPolicy: RetryPolicy = new ExponentialBackoffRetry(1000, 3)): CuratorFrameworkFactory.Builder = {
    CuratorFrameworkFactory.builder() //
      .connectString(mkString(serverList)) //
      .retryPolicy(retryPolicy) //
      .connectionTimeoutMs(5000) // 5 seconds default, user can overwrite
  }

  implicit def toSocketAddr(in: String): InetSocketAddress = Option(in) match {
    case None => throw new IllegalArgumentException("No IP address given")
    case Some(raw) => {
      val parts = raw.split(":")
      val port = if (parts.length == 2) parts(1).toInt else 2181
      val ip = InetAddress.getByName(parts(0))
      new InetSocketAddress(ip, port)
    }
  }

  private def mkString(list: Seq[InetSocketAddress]): String = {
    list map { server: InetSocketAddress => s"${server.getAddress().getHostAddress()}:${server.getPort()}" } mkString (",")
  }

  implicit def toZkClient(curator: CuratorFrameworkFactory.Builder): Future[ZkClient] = toZkClient(curator.build())
  implicit def toZkClient(curator: CuratorFramework): Future[ZkClient] = ZkClient(curator).start

  implicit class StringToValueConverters(s: String) {

    def notNull: Option[String] = Option(s)

    def notEmpty: Option[String] = s match {
      case "" => None
      case _  => notNull
    }

    def notBlank: Option[String] = s.notEmpty.flatMap(_ => s.trim.notEmpty)
  }

}

case class ZkClient(curator: CuratorFramework, connectTimeout: Duration = 5 seconds) {
  import org.apache.curator.framework.imps.CuratorFrameworkState._
  def isStarted() = curator.getState() match {
    case STARTED => true
    case _       => false
  }

  def start(atMost: Duration): ZkClient = Await.result(start, atMost)

  def start(): Future[ZkClient] = {
    import org.apache.curator.framework.imps.CuratorFrameworkState._
    import scala.concurrent.ExecutionContext.Implicits.global

    curator.getState() match {
      case LATENT => {
        println("Initializing ZK client")
        try {
          Future {
            curator.start();
            curator.blockUntilConnected(connectTimeout.length.toInt, connectTimeout.unit) match {
              case false => throw new TimeoutException("ScalaZK.start timeout")
              case true  => // good to go 
            }

            // make sure our root app path exists, it blocks
            curator.getNamespace() notBlank match {
              case Some(namespace) => new EnsurePath("/" + namespace).ensure(curator.getZookeeperClient())
              case None            =>
            }
            this
          }
        }
        catch {
          case (ex: Exception) => Future failed ex
        }
      }
      case STARTED => Future.successful(this) // Do NOT complain
      case STOPPED => Future failed new IllegalStateException("Stop has been called on client")
      case state   => Future failed new IllegalStateException(s"Unknown curator state encountered ${state}")
    }
  }

  // pass through methods 
  def checkExists() = curator.checkExists()
  def close() = if (isStarted()) curator.close()
  def create() = curator.create()
  def delete() = curator.delete()
  def getChildren() = curator.getChildren()
  def getData() = curator.getData()
  def getZookeeperClient() = curator.getZookeeperClient()
  def hasNamespace(): Boolean = { (curator.getNamespace() notBlank) isDefined }
  def setData() = curator.setData()
  def shutdown() = close() // sugar
}

