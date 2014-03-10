package wookie.example

import fx._
import fx.WookieScenario

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

object SearchAndStarWookie {
  val login = "change login"
  val password = "change password"

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
          wookie.waitForLocation(new WaitArg("google search results")
            .matchByLocation(_.contains("q="))
            .whenLoaded((e) => {
            println("results: " + $("h3.r").asResultList())

            val githubLink = $("h3.r a").asResultList().find(_.text().contains("chaschev"))

            githubLink.get.clickLink()
          }))

          wookie.waitForLocation(new WaitArg("git wookie not logged in")
            .matchByLocation(_.contains("/wookie-view"))
            .whenLoaded((e) => { $("a.button.signin").clickLink() })
          )

          wookie.waitForLocation(new WaitArg("git login")
            .matchByLocation(_.contains("github.com/login"))
            .whenLoaded((e) => {

            wookie.waitForLocation(new WaitArg("wookie logged in")
              .matchByLocation(_.contains("/wookie-view"))
              .whenLoaded((e) => {
              $(".star-button.unstarred:visible").mouseClick()
            }))

            $("#login_field").value(login)
            $("#password").value(password)
              .submit()
          }))

          $("input[maxlength]")
            .value("wookie-view")
            .submit()
        }))
  }
}
