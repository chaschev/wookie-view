package wookie.view

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
abstract class NavigationMatcher {
  def matches(r:NavigationRecord, w: WookieNavigationEvent):Boolean
}

case class PredicateMatcher(p: ((WookieNavigationEvent, WaitArg) => Boolean)) extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    p.apply(w, r.arg)
  }
}

case class LocationMatcher(p: ((String) => Boolean)) extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    p.apply(w.newLoc)
  }
}

object NextPageReadyMatcher{
  val instance = new NextPageReadyMatcher
}

case class NextPageReadyMatcher() extends NavigationMatcher {
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    w.isInstanceOf[PageReadyEvent]
  }
}
