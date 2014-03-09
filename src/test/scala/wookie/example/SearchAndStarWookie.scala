package wookie.example

import fx._
import fx.WookieScenario

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class SearchAndStarWookie {

}

object SearchAndStarWookie {
  val defaultPanel = () => {
    val wookieView = WookieView.newBuilder.useJQuery(true).build

    WookiePanel.newBuilder(wookieView).build
  }

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    app.runOnStage(
      new WookieScenario("http://www.google.com", None,
        defaultPanel,
        (wookiePanel, wookie, $) => {
          println($("input").html() + "-----\n")
          println($("div").html() + "-----\n")
          println($("div").text() + "-----\n")
          println($("a").attr("href") + "-----\n")
          println($("input[maxlength]").html() + "-----\n")

          wookie.waitForLocation(new NavArg()
            .matchByPredicate((v1, arg) => {v1.newLoc.contains("q=")})
            .handler((e) => {
            println("h3s: " + $("h3").html())
            println("results: " + $("h3.r").asResultList())

            val githubLink = $("h3.r").asResultList().find(_.text().contains("chaschev"))

            val aLink = githubLink.get.find("a")(0)

            println("1:" + githubLink.get.html())
            println("2:" + aLink.html())

            aLink.click()
          }))

          $("input[maxlength]")
            .value("wookie-view")
            .submit()
        }))
  }

}
