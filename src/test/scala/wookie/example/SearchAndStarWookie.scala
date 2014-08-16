package wookie.example

import wookie._
import wookie.WookieScenario
import java.util.Properties

import wookie.view.{WookieView, WaitArg}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object SearchAndStarWookie {
  var login = ""
  var password = ""

  val defaultPanel = () => {
    val wookieView = WookieView.newBuilder.useJQuery(true).build

    WookiePanel.newBuilder(wookieView).build
  }

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    val props = new Properties()

    props.load(getClass.getResourceAsStream("/auth.properties"))

    login = props.getProperty("git.login")
    password = props.getProperty("git.password")

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

          // this matcher is the same as one of the following
          // there is no problem, because matchers are removed when they are hit
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
                  val starButton = $(".star-button:visible")

                  if(starButton.text().contains("Star")) {
                    starButton.mouseClick()
                  }
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
