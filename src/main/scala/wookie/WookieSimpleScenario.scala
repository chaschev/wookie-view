package wookie

import java.util.concurrent.{TimeUnit, Semaphore}

import wookie.view._

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

class WookieScenarioLock(
   private val semaphore: Semaphore = new Semaphore(1)){
  def await() = semaphore.tryAcquire(100, TimeUnit.DAYS)

  def acquire(): Unit = semaphore.acquire()
  def wakeUp(): Unit = semaphore.release()
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
  private var wookie: WookieView = _

  def $(selector: String): JQueryWrapper = {
    context.last$.get.apply(selector)
  }

  private[this] def whenLoadedForSimpleScenario(url: String): WhenPageLoaded = {
    new WhenPageLoaded {
      override def apply()(implicit e: PageDoneEvent): Unit = {
        context.lastEvent = Some(e)
        context.last$ = Some(new JQuerySupplier {
          override def apply(selector: String): JQueryWrapper = {
            // this mess redirects all calls to the default JQueryWrapper
            // and for three specific cases of following the links it adds locking/unlocking
            new JQueryWrapperBridge(wookie.createJWrapper(selector, url)) {
              override def followLink(whenLoaded: Option[WhenPageLoaded]): JQueryWrapper = {
                lock.wakeUp()
                val r = super.followLink(Some(whenLoadedForSimpleScenario(url)))
                lock.await()
                r
              }

              override def mouseClick(whenDone: Option[WhenPageLoaded]): JQueryWrapper = {
                lock.acquire()
                val r = super.mouseClick(Some(whenLoadedForSimpleScenario(url)))
                lock.await()
                r
              }

              override def submit(whenLoaded: Option[WhenPageLoaded]): JQueryWrapper = {
                lock.acquire()
                val r = super.submit(Some(whenLoadedForSimpleScenario(url)))
                lock.await()
                r
              }
            }
          }
        })

        lock.wakeUp()
      }
    }
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
