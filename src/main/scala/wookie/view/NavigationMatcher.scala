package wookie.view

import java.net.URL

import org.apache.commons.lang3.StringUtils

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
abstract class NavigationMatcher {
  def matches(r:NavigationRecord, w: WookieNavigationEvent):Boolean
}

case class LocationMatcher(url: String) extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    compareURLs(r.arg.location.get, w.newLoc)
  }

  def compareURLs(_s1: String, _s2: String) = {
    val s1 =  removeLastSlash(new URL(_s1).toExternalForm)
    val s2 =  removeLastSlash(new URL(_s2).toExternalForm)

    s1.equals(s2)
  }

  def removeLastSlash(_s1: String): String =
  {
    if (_s1.endsWith("/")) StringUtils.substringBeforeLast(_s1, "/") else _s1
  }
}

case class PredicateMatcher(p: ((WookieNavigationEvent, WaitArg) => Boolean)) extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    p.apply(w, r.arg)
  }
}

object NextPageReadyMatcher{
  val instance = new NextPageReadyMatcher
}

case class NextPageReadyMatcher() extends NavigationMatcher {
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    w.isPageReadyEvent
  }
}
