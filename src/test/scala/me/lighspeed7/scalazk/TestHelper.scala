package me.lighspeed7.scalazk

import scala.concurrent.Await
import scala.concurrent.duration._

import me.lightspeed7.scalazk.ZkClient
import me.lightspeed7.scalazk.ZkClient._

trait TestHelper {
  lazy val timeout = 10 seconds
  lazy val client: ZkClient = Await.result(ZkClientBuilder(Seq("127.0.0.1")).namespace("configuration-unit-test").build, timeout)
}