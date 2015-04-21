ScalaZK
=================================

A Scala wrapper around Apache Curator to make it non-blocking and Scala friendly

## Motivation
I had a desire and need to use a scala client for Zookeeper that did not include Twitter libraries and there dependencies. While there is nothing wrong with Twitter's libraries per-se, including one Twitter library does include a world of dependencies. Also using Twitter has forced my software to stay stuck on Scala 2.10.x (until very recently) and I have wanted to move to Scala 2.11.x for a while.

This library only depends on the Apache Curator library which depends on the Zookeeper Java library to keep things controlled and small.

*NOTE:* There is one gotcha to this, Curator has a direct dependency on Log4j which can cause problems in Play apps, I have bridged log4j over to Slf4j and blocked the log4j dependency so it plays nice with Play Framework apps.
 
## Where to find the library

The library is publish in the Maven central repo, to add it as a dependency: 
```
"me.lightspeed7.scalazk" %% "scalaZK" % "0.2.0" 
```
Versions for Scala 2.10.x and 2.11.x are available.

# Usage

### Initialize

```
import me.lightspeed7.scalazk.ZkClient._
import scala.concurrent.duration._

lazy val timeout = 10 seconds

lazy val builder: Future[ZkClient] = ZkClientBuilder(Seq("127.0.0.1"))
    									.namespace("configuration-unit-test").build
lazy val client: ZkClient = Await.result(builder, timeout)
```

This library expose the standard curator factory builder so all existing curator configurations should be available in this library.

I have added reading, parsing and validation of IP addresses on the front of the builder to allow for better handling of passed in IP address, ports can be omitted for the default 2181.

### Shutdown

There is a shutdown hook that should be called upon application termination. 

```
client.shutdown()
``` 

### Configuration

A Configuration object is available to make calls for config values simpler with automatic directory and default value handling built in.

Here is the integration test showing gets and sets
```
val future = Configuration.initialize(client) map { config =>
  Await.result(config.setValue("tree/int", 123), timeout)
  Await.result(config.setValue("tree/long", 1234L), timeout)
  Await.result(config.setValue("tree/duration", 5 hours), timeout)
  Await.result(config.setValue("tree/string", "value"), timeout)
  Await.result(config.setValue("tree/dir1/dir2/value", 1234L), timeout)

  // test
  123 should be(Await.result(config.getValue("tree/int", 321), timeout))
  1234L should be(Await.result(config.getValue("tree/long", 4321L), timeout))
  (5 hours).toString should be(Await.result(config.getValue("tree/duration", 2 seconds), timeout).toString)
  "value" should be(Await.result(config.getValue("tree/string", "default"), timeout))
  1234L should be(Await.result(config.getValue("tree/dir1/dir2/value", 54321L), timeout))
}
``` 
### Watchers
Sometimes you want to match for changes in a part of the ZooKeeper data tree, the Watcher API handles that  : 
```
val listener = Watcher.addWatcher(client, "/foo") { context =>
  println(s"Event - ${context.event}")
  count.incrementAndGet()
}

val context = Await.result(listener)
```
and it is ready to use. To stop listening for a given watcher:  
```
context.stopListening()
```

# Transactions 
A very simple transaction API is available to create atomic sets of operations 
```
val future = Transaction.start(client) { ops =>
  ops :+
    Create("/transaction/foo", 12345L) :+
    Set("/transaction/foo", 2345L)
}

val results = Await.result(future, timeout)

results map { result =>
  result match {
    case OperationResult(op, _, _, Some(ex)) =>         ??? // any failure
    case OperationResult(op, Some(path), None, None) => ??? // create success
    case OperationResult(op, None, Some(stat), None) => ??? // set success 
  }
}
```

# Recipes
One of the great things about the Apache Curator library is the recipes that come build in and ready to use, the only problem is that they are Java centric and not very Scala friendly. Some of the recipes have been wrapped in Scala friendly wrappers such as: 

## Leader Election
```
Elections.leaderElection(client, s"${baseDir}/leaderElection", false) { client =>
  Thread.sleep(102)
  leaderElected.set(true)
}

val result = Await.result(Future { while (leaderElected.get() == false) { Thread.sleep(100) } }, timeout)
leaderElected.get() should be(true)
```
## Leader Latch
```
val latch = Elections.leaderLatch[Unit](client, s"${baseDir}/leaderLatch1", Some("foo1"))(15 seconds) { client =>
  leaderLatched.set(true)
}
val result = Await.result(latch, timeout)
leaderLatched.get() should be(true)
```
## Distributed Barrier
```
val barrierObj = Barriers.distributedBarrier[String](client, s"${baseDir}/dist/barrier")
val barrier = barrierObj.waitOnBarrier(5 seconds) { barrier =>
  bool.set(true)
  "Success"
}
val result = Await.result(barrier, timeout)
```
## Shared Counters
```
val counts = new AtomicInteger(0)

Counters.sharedCounter(client, s"${baseDir}/shared/counter", 123) map { counter =>
  counter.addListener { newValue => counts.incrementAndGet() }
  Await.result(counter.getCount, timeout) should be(123)

  Await.result(counter.setCount(234), timeout)
  Await.result(counter.getCount, timeout) should be(234)

  val vv = Await.result(counter.getVersionedValue, timeout)
  Await.result(counter.setCount(345), timeout)

  Await.result(counter.trySetCount(vv, 345), timeout) should be(true)
  
  counts.get() should be(3)
}
```
## Shared Reentrant Lock
```
val future = Locks.sharedReentrantLock[String](client, s"${baseDir}/throwException", timeout) { client =>
  Thread.sleep(100) // make the test wait
  "String"
}

val result = Await.ready(future, timeout)
```
## Other recipes have not yet been implemented

## License - Apache 2.0


