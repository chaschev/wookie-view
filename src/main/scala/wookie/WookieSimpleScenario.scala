package wookie

import java.util.concurrent.Semaphore
import javafx.application.Platform

import netscape.javascript.JSObject
import org.slf4j.LoggerFactory
import wookie.WookieScenarioLock.logger
import wookie.view._

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Await, Promise}
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookieScenarioLock(
  scenario: WookieSimpleScenario
){

  private val semaphore: Semaphore = new Semaphore(1)

  def await(): Unit = {
//    Preconditions.checkArgument(scenario.locksEnabled, "locks must be enabled!", Array())

    if(!scenario.locksEnabled) return

    var busy = true

    while(busy){
      if(semaphore.tryAcquire()){
        semaphore.release()
        busy = false
      }

//      logger.debug("awaiting semaphore..")

      Thread.sleep(100)
    }

  }

  def acquire(): Unit = {
    if(!scenario.locksEnabled) return

    logger.debug(s"-1 (${semaphore.availablePermits()}) - before the lock", new Exception)

    if(!semaphore.tryAcquire()){
      logger.warn("warning: second lock!", new Exception)
      semaphore.acquire()
    }
  }

  def wakeUp(): Unit = {
//    Preconditions.checkArgument(scenario.locksEnabled, "trying to wake up with disabled locks", Array())
    if(!scenario.locksEnabled) return

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

trait WrapperUtils {
  def wrapDomIntoJava(dom: JSObject, wookie: WookieView, url: String, selector: Option[String] = None, e: PageDoneEvent = null): JQueryWrapper = {
    bridgeJQueryWrapper(
      new DirectWrapper(true, dom, wookie, url, e, selector), selector.getOrElse("<none>"), url
    )
  }

  def wrapJQueryIntoJava($: JSObject, wookie: WookieView, url: String, selector: Option[String] = None, e: PageDoneEvent = null): JQueryWrapper = {
    bridgeJQueryWrapper(
      new DirectWrapper(false, $, wookie, url, e, selector), selector.getOrElse("<none>"), url
    )
  }

  private[wookie] def bridgeJQueryWrapper(delegate: JQueryWrapper, selector: String, url: String): JQueryWrapper
}

abstract class WookieSimpleScenario(val title: String, val panel: PanelSupplier)
  extends WrapperUtils {

  final val lock = new WookieScenarioLock(this)
  final val context = new WookieScenarioContext
  
  private[wookie] var locksEnabled = true

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

  // this mess redirects all calls to the default JQueryWrapper
  // and for three specific cases of following the links it adds locking/unlocking
  class SimpleScenarioBridge (delegate: JQueryWrapper, selector: String, url: String)
    extends JQueryWrapperBridge(delegate) {
    //ok they ignore the input argument which is not good
    override def followLink(arg: WaitArg): JQueryWrapper = {
      lock.acquire()
      val r = super.followLink(whenLoadedForSimpleScenario(url))

      logger.debug("awaiting!")

      lock.await()

      logger.debug("woke up!")
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
        wookie.wrapJQuery($, selector, url)
      }
    }
  }

  override private[wookie] def bridgeJQueryWrapper(delegate: JQueryWrapper, selector: String, url: String): JQueryWrapper = {
    if(delegate.isInstanceOf[SimpleScenarioBridge]) {
      logger.warn("trying to double wrap!", new Exception)
      delegate
    }else {
      new SimpleScenarioBridge(delegate, selector, url)
    }
  }

  def newArg(url: String): WaitArg = whenLoadedForSimpleScenario(url)

  private[this] def whenLoadedForSimpleScenario(url: String): WaitArg = {
    val whenLoaded = new WhenPageLoaded {
      override def apply()(implicit e: PageDoneEvent): Unit = {
        context.lastEvent = Some(e)
        context.last$ = Some(new JQuerySupplier {
          override def apply(selector: String): JQueryWrapper = {
            wookie.createJWrapper(selector, url)
          }
        })

        lock.wakeUp()
      }
    }

    wookie.defaultArg().whenLoaded(whenLoaded)
  }

  def load(arg: WaitArg) = {
    lock.acquire()

    wookie.load(arg.location.get, arg)

    lock.await()
  }


  def load(url: String) = {
    lock.acquire()

    wookie.load(url, whenLoadedForSimpleScenario(url))

    lock.await()
  }

  /**
   * TODO: there must be a way to check if the page is not ready, ideally, atomic way
   */
  def waitForPageReady() = {
    waitForLocation(newArg(wookie.getCurrentDocUri.getOrElse("")).matchIfPageReady())
  }

  def waitForLocation(arg: WaitArg) = {
    lock.acquire()

    wookie.waitForLocation(arg)

    lock.await()
  }

  /**
   * Script must be in a locked state when hook fires. It will unlock it when download finishes.
   */
  def addDownloadHook(matcher: NavigationMatcher): Future[DownloadResult] = {
    wookie
      .waitForDownloadToStart(matcher)
      .andThen({case _ => lock.wakeUp()})
  }

  def run()

  def asNotSimpleScenario = WookieSimpleScenario.asNotSimpleScenario(this)

//  /**
//   * A helper method to return Java's $ from a DOM element.
//   */
//  def wrapDomIntoJava(dom: JSObject, url: String, selector: Option[String] = None, e: PageDoneEvent): JQueryWrapper = {
//    wookie.wrapDomIntoJava(dom, url, selector, e)
//  }
//
//  /**
//   * A helper method to return Java's $ from JS's $.
//   */
//  def wrapJQueryIntoJava($: JSObject, url: String, selector: Option[String] = None, e: PageDoneEvent): JQueryWrapper = {
//    wookie.wrapJQueryIntoJava($, url, selector, e)
//  }
  
  def enableLocking(b: Boolean): Unit = locksEnabled = b
}

object WookieSimpleScenario {
  def asNotSimpleScenario(s: WookieSimpleScenario): WookieScenario = {
    new WookieScenario(s.title, None, s.panel, (p, wv, js) => {
      s.wookie = wv
      s.run()
    }) {
      override private[wookie] def bridgeJQueryWrapper(delegate: JQueryWrapper, selector: String, url: String): JQueryWrapper = s.bridgeJQueryWrapper(delegate, selector, url)
    }
  }
}
