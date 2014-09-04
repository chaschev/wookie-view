package wookie

import java.util.concurrent.Semaphore
import javafx.application.Platform

import org.slf4j.LoggerFactory
import wookie.WookieScenarioLock.logger
import wookie.view._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

class WookieScenarioLock(
  private val semaphore: Semaphore = new Semaphore(1)){
  def await() = {
    var busy = true

    while(busy){
      if(semaphore.tryAcquire()){
        semaphore.release()
        busy = false
      }

      logger.debug("awaiting semaphore..")

      Thread.sleep(100)
    }

  }

  def acquire(): Unit = {
    if(!semaphore.tryAcquire()){
      logger.warn("warning: second lock!", new Exception)
      semaphore.acquire()
    }

    logger.debug(s"-1 (${semaphore.availablePermits()})", new Exception)
  }

  def wakeUp(): Unit = {
    semaphore.release()
    logger.debug(s"+1 (${semaphore.availablePermits()})")

    if(semaphore.availablePermits() == 0){
      logger.info("zero permits after release!")
    }
  }
}

object WookieScenarioLock {
  val logger = LoggerFactory.getLogger(classOf[WookieScenarioLock])
}



class WookieScenarioContext(
  @volatile
  var lastEvent: Option[PageDoneEvent] = None,
  @volatile
  var last$: Option[JQuerySupplier] = None
){
  var timeoutMs: Int = 30000
}

abstract class WookieSimpleScenario(val title: String, val panel: PanelSupplier) {
  import scala.concurrent.ExecutionContext.Implicits.global

  final val lock = new WookieScenarioLock()
  final val context = new WookieScenarioContext

  @volatile
  protected var wookie: WookieView = _

  def $(selector: String): JQueryWrapper = {
    val promise = Promise[JQueryWrapper]()
    
    Platform.runLater(new Runnable {
      override def run(): Unit = {
        val obj = context.last$.get.apply(selector)
        promise.complete(Try(obj))
      }
    })

    Await.result(promise.future, Duration(5, "s"))
  }

  private[this] def createSimpleScenarioWrapperBridge(delegate: JQueryWrapper, selector: String, url: String): JQueryWrapper = {
    // this mess redirects all calls to the default JQueryWrapper
    // and for three specific cases of following the links it adds locking/unlocking
    new JQueryWrapperBridge(delegate) {
      //ok they ignore the input argument which is not good
      override def followLink(arg: WaitArg): JQueryWrapper = {
        lock.acquire()
        val r = super.followLink(whenLoadedForSimpleScenario(url))
        lock.await()
        r
      }

      override def mouseClick(arg: WaitArg): JQueryWrapper = {
        lock.acquire()
        val r = super.mouseClick(whenLoadedForSimpleScenario(url))
        lock.await()
        r
      }

      override def submit(arg: WaitArg): JQueryWrapper = {
        lock.acquire()
        val r = super.submit(whenLoadedForSimpleScenario(url))
        lock.await()
        r
      }

      override def asResultList(): List[JQueryWrapper] = {
        super.asResultList().map { $ =>
          createSimpleScenarioWrapperBridge($, selector, url)
        }
      }
    }

  }

  private[this] def whenLoadedForSimpleScenario(url: String): WaitArg = {
    val whenLoaded = new WhenPageLoaded {
      override def apply()(implicit e: PageDoneEvent): Unit = {
        context.lastEvent = Some(e)
        context.last$ = Some(new JQuerySupplier {
          override def apply(selector: String): JQueryWrapper = {
            createSimpleScenarioWrapperBridge(wookie.createJWrapper(selector, url), selector, url)
          }
        })

        lock.wakeUp()
      }
    }

    wookie.defaultArg().whenLoaded(whenLoaded)
  }

  def load(url: String) = {
    lock.acquire()

    wookie.load(url, whenLoadedForSimpleScenario(url))

    lock.await()
  }

  def download(matcher: NavigationMatcher) = {
    lock.acquire()

    wookie
      .waitForDownloadToStart(matcher)
      .andThen({case _ => lock.wakeUp()})

    lock.await()
  }

  def run()

  def asScenario = WookieSimpleScenario.asScenario(this)
}

object WookieSimpleScenario {
  def asScenario(s: WookieSimpleScenario): WookieScenario = {
    new WookieScenario(s.title, None, s.panel, (p, wv, js) => {
      s.wookie = wv
      s.run()
    })
  }

}
