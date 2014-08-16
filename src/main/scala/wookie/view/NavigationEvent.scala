package wookie.view

import scala.concurrent.Promise

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
abstract class NavigationEvent(waitArg:WaitArg) {
  val arg = waitArg
  def ok() : Boolean
}

class NokNavigationEvent(waitArg:WaitArg) extends NavigationEvent(waitArg){
  val ok = false
}

// redesign: need to provide call back, don't need it outside
//
class OkNavigationEvent(_wookieEvent:WookieNavigationEvent, arg:WaitArg) extends NavigationEvent(arg){
  val ok = true
  val wookieEvent = _wookieEvent
}

case class NavigationRecord(arg: WaitArg, promise: Promise[NavigationEvent]){

}
