package wookie.view

import java.lang.{Boolean => JBoolean}
import java.util.function.{Function => JFunction}

import scala.concurrent.Promise
import scala.util.Random

trait WhenPageLoaded {
  def apply()(implicit e: PageDoneEvent)
}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WaitArg(var name: String = "", val wookie: WookieView){
  //  var predicate:Option[((String, String, NavArg) => Boolean)] = None
  var timeoutMs: Option[Int] = Some(30000)
  private[this] var handler: Option[WhenPageLoaded] = None
  var async: Boolean = true
  var eventFilter: Option[(WookiePageStateChangedEvent) => Boolean] = Some(e => e.isInstanceOf[PageReadyEvent])

  val eventId: Int = Random.nextInt()  //currently not really used

  var startedAtMs: Long = -1
  var location: Option[String] = if(name.equals("")) None else Some(name)

  private[this] var navigationMatcher: NavigationMatcher = NextPageReadyMatcher

  def timeoutNone(): WaitArg = {this.timeoutMs = None; this}
  def timeoutMs(i: Int): WaitArg = {this.timeoutMs = Some(i); this}
  def getTimeoutMs: Int = timeoutMs.getOrElse(wookie.options.defaultTimeoutMs)
  def timeoutSec(sec:Int): WaitArg = {this.timeoutMs = Some(sec * 1000);this}
  def whenLoaded(whenPageLoaded:WhenPageLoaded): WaitArg = {this.handler = Some(whenPageLoaded); this}
  def async(b: Boolean): WaitArg = {this.async = b; this}
  def withName(n: String): WaitArg = {this.name = n; this}

  def withMatcher(matcher: NavigationMatcher): WaitArg = {
    navigationMatcher = matcher; this
  }

  def matchByAddress(p: (String) => Boolean): WaitArg =
    withMatcher(new LocationMatcher(p))

  def matchByAddress(p: JFunction[String, JBoolean]): WaitArg =
    withMatcher(new LocationMatcher(p.apply))

  def matchIfPageReady(): WaitArg = {
    this.navigationMatcher = NextPageReadyMatcher; this
  }

  def filterEvents(eventFilter: (WookiePageStateChangedEvent) => Boolean): WaitArg = {this.eventFilter = Some(eventFilter); this}

  private[view] def acceptsEvent(e: WookiePageStateChangedEvent): Boolean = {
    if(eventFilter.isDefined)
      eventFilter.get.apply(e)
    else
      true
  }

  def matchByPredicate(p:((WookieNavigationEvent, WaitArg) => Boolean)):WaitArg = {
    this.navigationMatcher = new PredicateMatcher(p); this
  }

  def location(_s: String): WaitArg = {this.location = Some(_s); this}
  def matcher = navigationMatcher


  protected[wookie] def handleIfDefined(e: PageDoneEvent) = if(this.handler.isDefined) this.handler.get.apply()(e)

  //todo make package local
  protected[wookie] def startedAtMs(t: Long): WaitArg = {this.startedAtMs = t; this}

  def isDue =
     startedAtMs + getTimeoutMs < System.currentTimeMillis()


  def toNavigationRecord:NavigationRecord = {
    new NavigationRecord(this, Promise[PageDoneEvent]())
  }

  override def toString: String = {
    if(!name.isEmpty) {
      s"NavArg{'$name'}"
    } else
    if(location.isDefined){
      s"NavArg{'${location.get}'}"
    }
    else {
      super.toString
    }
  }

}

