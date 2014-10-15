package wookie.view

import scala.concurrent.Promise

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

/**
 * Eventually now two types of page-done event are used: ok & timeout.
 */
abstract class PageDoneEvent(val arg: WaitArg) {
  def ok() : Boolean
}

case class NokPageDoneEvent(override val arg: WaitArg) extends PageDoneEvent(arg){
  val ok = false
}

case class LoadTimeoutPageDoneEvent(override val arg: WaitArg) extends PageDoneEvent(arg){
  val ok = false
}


case class OkPageDoneEvent(
  wookieEvent: WookiePageStateChangedEvent, override val arg: WaitArg) extends PageDoneEvent(arg) {
  val ok = true

  def url: String = wookieEvent.newLoc
}

case class NavigationRecord(arg: WaitArg, promise: Promise[PageDoneEvent]){

}
