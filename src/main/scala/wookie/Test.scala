package wookie

import wookie.view.{NavigationEvent, WookieView}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object Test {
  def main(args: Array[String]){
//    val array = List("a", "b", "c")

    val m = collection.immutable.HashMap(0 -> 1, 2 -> 3)

    val browser: WookieView = WookieView.newBuilder
      .createWebView(true)
      .useFirebug(true)
      .useJQuery(true)
      .build

    browser.load("http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html",
      (event: NavigationEvent) => {
        val jQuery = "\"a[name='agreementjdk-7u51-oth-JPR']\""
        browser.click(jQuery)
      })
  }
}
