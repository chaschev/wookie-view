package fx

import javafx.application.Application
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import scala.runtime.BoxedUnit


object WookieSandboxApp {
  def main(args: Array[String])
  {
    Application.launch(classOf[WookieSandboxApp], args: _*)
  }
}

class WookieSandboxApp extends Application {
  def start(stage: Stage)
  {
    try
      val initialUrl = "http://www.google.com"
      val wookie = WookieView.newBuilder
        .useFirebug(false)
        .useJQuery(true)
        .build

      val location = new TextField(initialUrl)
      val codeArea = new TextArea()
      val go = new Button("Go")

      val goAction = new EventHandler[ActionEvent] {
        def handle(arg0: ActionEvent)
        {
          wookie.load(location.getText)
        }
      }

      go.setOnAction(goAction)

      val menuItem = new MenuItem("Go!")

      menuItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN))
      menuItem.setOnAction(goAction)

      wookie.getEngine.locationProperty.addListener(new ChangeListener[String] {
        def changed(observableValue: ObservableValue[_ <: String], s: String, newLoc: String)
        {
          System.out.println("location changed to: " + newLoc)
        }
      })

      val toolbar = new HBox
      toolbar.getChildren.addAll(location, go)
      toolbar.setFillHeight(true)

      val menu = new Menu("File")
      menu.getItems.addAll(menuItem)

      val menuBar = new MenuBar
      menuBar.getMenus.add(menu)

      val vBox = new VBox()

      vBox.getChildren.addAll(menuBar, toolbar, codeArea, wookie)
      vBox.setFillWidth(true)

      val scene = new Scene(vBox)
      stage.setScene(scene)
      stage.setWidth(1024)
      stage.setHeight(768)
      stage.show

      VBox.setVgrow(wookie, Priority.ALWAYS)

      wookie.load(initialUrl, Some((e: NavigationEvent) => {
        System.out.println("-----")
        System.out.println(wookie.$("input").html)
        System.out.println("-----")
        System.out.println(wookie.$("div").find("div").html)
        System.out.println("-----")
        System.out.println(wookie.$("div").text)
        System.out.println("-----")
        System.out.println(wookie.$("a").attr("href"))
        System.out.println("-----")
        System.out.println("text:" + wookie.$("input[maxlength]").html)

        wookie.waitForLocation(new NavArg().matchByPredicate((event, arg) => {
          event.newLoc.contains("q=")
        }).handler((e) => {
          System.out.println("h3s: " + wookie.$("h3").html)
          System.out.println("results: " + wookie.$("h3.r").asResultList)
        }))

        wookie.$("input[maxlength]").value("bear java deployment").submit

        return BoxedUnit.UNIT
      }))

    catch {
      case e: Exception =>
        e.printStackTrace
    }
  }
}
