package me.lightspeed7.scalazk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.curator.RetryPolicy
import org.apache.curator.framework._
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.EnsurePath
import org.apache.curator.utils.InternalACLProvider
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Stat

import me.lightspeed7.scalazk.Watcher.WatcherContext
import me.lightspeed7.scalazk.Watcher.ZkWatchedEvent
import me.lightspeed7.scalazk.ZkClient._

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

  def mkdirs(path: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    mkdirs(path, true, null)
  }

  def mkdirs(path: String, makeLastNode: Boolean)(implicit ec: ExecutionContext): Future[Boolean] = {
    mkdirs(path, makeLastNode, null)
  }

  def mkdirs(path: String, makeLastNode: Boolean, aclProvider: InternalACLProvider)(implicit ec: ExecutionContext): Future[Boolean] = {
    WrapInFuture(this) { client =>
      ZKPaths.mkdirs(client.curator.getZookeeperClient().getZooKeeper(), path, makeLastNode, aclProvider)
      true
    }
  }

  def ensurePath(path: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    WrapInFuture(this) { client =>
      val fullPath = path
      new EnsurePath(fullPath).ensure(curator.getZookeeperClient())
      true
    }
  }

  // pass through methods 
  def exists(path: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val p = Promise[Boolean]
    Future {
       Try(curator.checkExists().forPath(path)) match { 
         case Success(result) => { 
           p success (result != null) // treat null as not-exists
         }
         case Failure(ex) => { 
           println("Exception is ${ex}")
           if (ex.isInstanceOf[NoNodeException]) p success false else p failure ex
         }
       }
    }
    p.future
  }

  def existsWithWatcher(path: String)(f: ZkWatchedEvent => Unit = identity)(implicit ec: ExecutionContext): Future[Option[WatcherContext]] = {
    val r = Watcher.addWatcher(this, path)(f) map { context =>
      val stat = curator.checkExists().forPath(path)
      Some(context)
    }
    r
  }

  def close() = if (isStarted()) curator.close()

  def create(path: String, data: Array[Byte], createParents: Boolean = false,
             disp: CreateMode = CreateMode.PERSISTENT, acl: Option[Seq[ACL]] = None)(implicit ec: ExecutionContext): Future[String] = {
    import scala.collection.JavaConversions._
    val p = Promise[String]
    Future {
      val f1 = curator.create()
      val f2 = if (createParents) f1.creatingParentsIfNeeded() else f1
      val f3 = f2.withMode(disp)
      val f4 = acl match {
        case None      => f3
        case Some(acl) => f3.withACL(scala.collection.JavaConversions.seqAsJavaList(acl))
      }
      Try(f4.forPath(path, data)) match {
        case Success(result) => p success (result)
        case Failure(ex)     => p failure (ex)
      }
    }
    p.future
  }

  def delete(path: String, children: Boolean = false, version: Option[Int] = None)(implicit ec: ExecutionContext): Future[Unit] = {
    val p = Promise[Unit]
    Future {
      val f1 = curator.delete()
      val f2 = if (children) f1.deletingChildrenIfNeeded() else f1
      val f3 = version match {
        case None          => f2
        case Some(version) => f2.withVersion(version)
      }
      Try(f3.forPath(path)) match {
        case Success(void) => p success ()
        case Failure(ex)   => p failure (ex)
      }
    }
    p.future
  }

  def getACL(path: String)(implicit ec: ExecutionContext): Future[Seq[ACL]] = {
    val p = Promise[Seq[ACL]]
    Future {
      val f1 = curator.getACL()
      Try(f1.forPath(path)) match {
        case Success(result) => p success (scala.collection.JavaConversions.collectionAsScalaIterable(result).toSeq)
        case Failure(ex)     => p failure (ex)
      }
    }
    p.future
  }

  def children(path: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val p = Promise[Seq[String]]
    Future {
      val f1 = curator.getChildren()
      Try(f1.forPath(path)) match {
        case Success(result) => p success (scala.collection.JavaConversions.collectionAsScalaIterable(result).toSeq)
        case Failure(ex)     => p failure (ex)
      }
    }
    p.future
  }

  def get(path: String)(implicit ec: ExecutionContext): Future[Array[Byte]] = {
    val p = Promise[Array[Byte]]
    Future {
      val f1 = curator.getData()
      Try(f1.forPath(path)) match {
        case Success(result) => p success result
        case Failure(ex)     => p failure (ex)
      }
    }
    p.future

  }

  def getCuratorClient() = curator
  def getZookeeperClient() = curator.getZookeeperClient()
  def hasNamespace(): Boolean = { (curator.getNamespace() notBlank) isDefined }

  def setACL(path: String, acl: Seq[ACL], version: Option[Int])(implicit ec: ExecutionContext): Future[Stat] = {
    val p = Promise[Stat]
    Future {
      val f1 = curator.setACL()
      val f2 = version match {
        case None          => f1
        case Some(version) => f1.withVersion(version)
      }
      val aclList = scala.collection.JavaConversions.seqAsJavaList(acl)
      Try(f2.withACL(aclList).forPath(path)) match {
        case Success(stat) => p success stat
        case Failure(ex)   => p failure (ex)
      }
    }
    p.future
  }

  def set(path: String, data: Array[Byte], createParents:Boolean = false, version: Option[Int] = None)(implicit ec: ExecutionContext): Future[Stat] = {
    val p = Promise[Stat]
    Future {
      val f1 = curator.setData()
      val f3 = version match {
        case None          => f1
        case Some(version) => f1.withVersion(version)
      }
      Try(f3.forPath(path, data)) match {
        case Success(stat) => p success stat
        case Failure(ex)   => p failure (ex)
      }
    }
    p.future
  }

  def shutdown() = close() // sugar
}

