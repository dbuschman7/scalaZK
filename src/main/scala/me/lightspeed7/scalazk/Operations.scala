package me.lightspeed7.scalazk

import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.Op
import org.apache.zookeeper.ZooDefs.Ids

object Operations {
  import Implicits._

  sealed trait Operation {
    val path: String

    def toZOp(namespace: Option[String]): Op

    protected def prepend(head: Option[String], tail: String): String = {
      head match {
        case None       => tail
        case Some(head) => head + tail
      }
    }
  }

  /**
   * Create operation
   *
   * @param path the node path
   * @param data the value for the node
   * @param acl the ACL to associate with the node
   * @param createMode the creation mode of the node
   */
  case class Create(path: String, data: Array[Byte], acl: Seq[ACL] = Ids.OPEN_ACL_UNSAFE, createMode: CreateMode = CreateMode.PERSISTENT) extends Operation {
    def toZOp(namespace: Option[String]): Op = Op.create(prepend(namespace, path), data, toJava(acl), createMode.toFlag)
  }

  /**
   * Delete Operation
   *
   * @param path the node path
   * @param version the expected version or None if a match is not important
   */
  case class Delete(path: String, version: Option[Int] = None) extends Operation {
    def toZOp(namespace: Option[String]): Op = Op.delete(prepend(namespace, path), version getOrElse -1)
  }

  /**
   * Check Operation
   *
   * @param path the node path
   * @param version the expected version
   */
  case class Check(path: String, version: Option[Int]) extends Operation {
    def toZOp(namespace: Option[String]): Op = Op.check(prepend(namespace, path), version getOrElse -1)
  }

  /**
   * Set Operation
   *
   * @param path the node path
   * @param data the value for the node
   * @param version the expected version or None if a match is not important
   */
  case class Set(path: String, data: Array[Byte], version: Option[Int] = None) extends Operation {
    def toZOp(namespace: Option[String]): Op = Op.setData(prepend(namespace, path), data, version getOrElse -1)
  }

}