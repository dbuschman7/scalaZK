package me.lightspeed7.scalazk.recipes

import me.lightspeed7.scalazk.ZkClient
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import me.lightspeed7.scalazk.WrapInFuture
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import scala.util.Try

object Locks {

  //
  // Distributed Lock 
  // ///////////////////////////////
  def sharedReentrantLock[T](client: ZkClient, path: String, timeout: Duration)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[T] = {
    WrapInFuture(client) { client =>
      val lock: InterProcessMutex = new InterProcessMutex(client.curator, path)
      try {
        if (lock.acquire(timeout.length, timeout.unit)) f(client)
        else throw new IllegalAccessException("Unable to acquire lock for path '${path}'")
      }
      finally {
        lock.release()
      }
    }
  }

  
  // TODO : 
  // Shared Lock -> InterProcessSemaphoreMutex
  // Shared Reentrant Read Write Lock -> InterProcessReadWriteLock
  // Shared Semaphore -> InterProcessSemaphoreV2
  // Multi Shared Locks -> InterProcessMultiLock
}