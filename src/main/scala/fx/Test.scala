package fx

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object Test {
  def main(args: Array[String]){
//    val array = List("a", "b", "c")

    val browser: SimpleBrowser2 = SimpleBrowser2.newBuilder
      .createWebView(true)
      .useFirebug(true)
      .useJQuery(true)
      .build

    browser.load("http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html", Some(() => {
      val jQuery = "\"a[name='agreementjdk-7u51-oth-JPR']\""
      browser.click(jQuery)
    }))
  }
}
