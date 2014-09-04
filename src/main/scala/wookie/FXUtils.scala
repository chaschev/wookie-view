package wookie

import javafx.application.Platform

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object FXUtils {
  def toRunnable(lambda: () => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = lambda()
    }
  }

  def  execInFx[R](lambda: () => R): Future[R] = {
    val promise = Promise[R]()

    if(Platform.isFxApplicationThread){
      promise.complete(Try(lambda()))
    } else {
      Platform.runLater(toRunnable(() => {
        promise.complete(Try(lambda()))
      }))
    }

    promise.future
  }

  def execInFxAndAwait[R](lambda: () => R, timeoutMs: Int = 5000): R = {
    Await.result(execInFx(lambda), Duration(timeoutMs, "ms"))
  }
}
