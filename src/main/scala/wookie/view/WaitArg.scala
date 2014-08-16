package wookie.view

import scala.concurrent.Promise
import scala.util.Random

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WaitArg(var name: String = ""){
  //  var predicate:Option[((String, String, NavArg) => Boolean)] = None
  var timeoutMs: Option[Int] = Some(10000)
  private[this] var handler: Option[(NavigationEvent)=>Unit] = None
  var async: Boolean = true
  var isPageReadyEvent:Boolean = true

  val eventId:Int = Random.nextInt()  //currently not really used

  var startedAtMs:Long = -1
  var location:Option[String] = if(name.equals("")) None else Some(name)

  private[this] var navigationMatcher:NavigationMatcher = NextPageReadyMatcher.instance

  def timeoutNone(): WaitArg = {this.timeoutMs = None; this}
  def timeoutMs(i:Int): WaitArg = {this.timeoutMs = Some(i); this}
  def timeoutSec(sec:Int): WaitArg = {this.timeoutMs = Some(sec * 1000);this}
  def whenLoaded(h:(NavigationEvent) => Unit): WaitArg = {this.handler = Some(h); this}
  def async(b: Boolean): WaitArg = {this.async = b; this}
  def withName(n: String): WaitArg = {this.name = n; this}

  def matchByLocation(p: (String) => Boolean):WaitArg = {
    matchByPredicate((e, arg) => { p.apply(e.newLoc) }); this
  }
  def matchIfPageReady(_location:String): WaitArg = {
    this.navigationMatcher = NextPageReadyMatcher.instance; this
  }

  def matchOnlyPageReadyEvent(b:Boolean):WaitArg = {this.isPageReadyEvent = b; this}

  def matchByPredicate(p:((WookieNavigationEvent, WaitArg) => Boolean)):WaitArg = {
    this.navigationMatcher = new PredicateMatcher(p); this
  }

  def location(_s:String): WaitArg = {this.location = Some(_s); this}
  def matcher = navigationMatcher

  def isPageReadyEvent(isPageReady: Boolean):WaitArg = {this.isPageReadyEvent = isPageReady; this}

  protected[wookie] def handleIfDefined(e: NavigationEvent) = if(this.handler.isDefined) this.handler.get.apply(e)

  //todo make package local
  protected[wookie] def startedAtMs(t: Long): WaitArg = {this.startedAtMs = t; this}

  def isDue =
    if(timeoutMs.isEmpty) {
      false
    } else {
      startedAtMs + timeoutMs.get < System.currentTimeMillis()
    }

  def toNavigationRecord:NavigationRecord = {
    new NavigationRecord(this, Promise[NavigationEvent]())
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

