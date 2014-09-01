package wookie.view

import scala.concurrent.Promise

/**
 * @author Andrey Chaschev chaschev@gmail.com
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


// redesign: need to provide call back, don't need it outside
//
case class OkPageDoneEvent(
  wookieEvent: WookiePageStateChangedEvent, override val arg: WaitArg) extends PageDoneEvent(arg) {
  val ok = true
}

case class NavigationRecord(arg: WaitArg, promise: Promise[PageDoneEvent]){

}
