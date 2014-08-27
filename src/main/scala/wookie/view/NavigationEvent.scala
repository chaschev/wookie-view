package wookie.view

import scala.concurrent.Promise

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
abstract class NavigationEvent(val arg: WaitArg) {
  def ok() : Boolean
}

class NokNavigationEvent(override val arg: WaitArg) extends NavigationEvent(arg){
  val ok = false
}

class LoadTimeoutNavigationEvent(override val arg: WaitArg) extends NavigationEvent(arg){
  val ok = false
}


// redesign: need to provide call back, don't need it outside
//
class OkNavigationEvent(val wookieEvent: WookiePageStateChangedEvent, override val arg: WaitArg) extends NavigationEvent(arg){
  val ok = true
}

case class NavigationRecord(arg: WaitArg, promise: Promise[NavigationEvent]){

}
