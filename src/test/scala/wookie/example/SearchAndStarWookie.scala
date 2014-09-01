package wookie.example

import java.util.Properties

import wookie._
import wookie.view.{WookieView, PageDoneEvent, WaitArg, WhenPageLoaded}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object SearchAndStarWookie {
  var login = ""
  var password = ""

  val defaultPanel = new PanelSupplier {
    override def apply(): WookiePanel = {
      val wookieView = WookieView.newBuilder
        .useJQuery(true)
        .build

      WookiePanel.newBuilder(wookieView).build
    }
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
      new WookieScenario(
        url = Some("http://www.google.com"),
        title = "Star Wookie Page!",
        panel = defaultPanel,
        procedure = (wookiePanel, wookie, $) => {

          // google search result state
          wookie.waitForLocation(new WaitArg("google search results")
            .matchByAddress(_.contains("q="))
            .whenLoaded(new WhenPageLoaded {
            override def apply()(implicit e: PageDoneEvent): Unit = {

              println("results: " + $("h3.r").asResultList())

              //find our link in the results list and click it
              val githubLink = $("h3.r a").asResultList().find(_.text().contains("chaschev"))

              githubLink.get.clickLink()
            }
          }))

          // this matcher is the same as one of the following
          // there is no problem, because matchers are removed when they are hit
          // waits for wookie-view page to load and clicks signin button
          wookie
            .waitForLocation(new WaitArg("git wookie not logged in")
            .matchByAddress(_.contains("/wookie-view"))
            .whenLoaded(new WhenPageLoaded {
              override def apply()(implicit e: PageDoneEvent): Unit = {
                $("a.button.signin").clickLink()
              }
          }))

          // login form
          wookie.waitForLocation(new WaitArg("git login")
            .matchByAddress(_.contains("github.com/login"))
            .whenLoaded(new WhenPageLoaded {
            override def apply()(implicit e: PageDoneEvent): Unit = {

              // github.com/wookie-view, logged in state
              // add this state before submitting the form
              wookie.waitForLocation(new WaitArg("wookie logged in")
                .matchByAddress(_.contains("/wookie-view"))
                .whenLoaded(new WhenPageLoaded {
                  override def apply()(implicit e: PageDoneEvent): Unit = {
                    //click 'star' button
                    val starButton = $(".star-button:visible")

                    if(starButton.text().contains("Star")) {
                      starButton.mouseClick()
                      println("Now the star will shine!")
                    } else {
                      println("Invoke me under my stars!")
                    }
                  }
              }))

              // fill login data and submit the form
              $("#login_field").value(login)
              $("#password").value(password)
                .submit()
            }
          }))

          // submit google request and start the scenario
          $("input[maxlength]")(null)
            .value("wookie-view")
            .submit()
        }))
  }
}
