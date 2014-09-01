package wookie.view

import scala.concurrent.Promise
import scala.util.Random

trait WhenPageLoaded {
  def apply()(implicit e: PageDoneEvent)
}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WaitArg(var name: String = ""){
  //  var predicate:Option[((String, String, NavArg) => Boolean)] = None
  var timeoutMs: Option[Int] = Some(30000)
  private[this] var handler: Option[WhenPageLoaded] = None
  var async: Boolean = true
  var eventFilter: Option[(WookiePageStateChangedEvent) => Boolean] = Some(e => e.isInstanceOf[PageReadyEvent])

  val eventId: Int = Random.nextInt()  //currently not really used

  var startedAtMs:Long = -1
  var location: Option[String] = if(name.equals("")) None else Some(name)

  private[this] var navigationMatcher:NavigationMatcher = NextPageReadyMatcher.instance

  def timeoutNone(): WaitArg = {this.timeoutMs = None; this}
  def timeoutMs(i:Int): WaitArg = {this.timeoutMs = Some(i); this}
  def timeoutSec(sec:Int): WaitArg = {this.timeoutMs = Some(sec * 1000);this}
  def whenLoaded(whenPageLoaded:WhenPageLoaded): WaitArg = {this.handler = Some(whenPageLoaded); this}
  def async(b: Boolean): WaitArg = {this.async = b; this}
  def withName(n: String): WaitArg = {this.name = n; this}

  def withMatcher(matcher: NavigationMatcher): WaitArg = {
    navigationMatcher = matcher; this
  }

  def matchByAddress(p: (String) => Boolean): WaitArg =
    withMatcher(new LocationMatcher(p))

  def matchIfPageReady(_location:String): WaitArg = {
    this.navigationMatcher = NextPageReadyMatcher.instance; this
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
    if(timeoutMs.isEmpty) {
      false
    } else {
      startedAtMs + timeoutMs.get < System.currentTimeMillis()
    }

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

