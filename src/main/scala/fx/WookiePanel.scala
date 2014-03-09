package fx

import javafx.scene.layout.{HBox, Priority, VBox, Pane}
import javafx.scene.control.{Button, TextArea, TextField}
import javafx.event.{ActionEvent, EventHandler}
import javafx.beans.value.{ObservableValue, ChangeListener}
import javafx.scene.Scene
import javafx.scene.web.{WebEngine, WebEvent, WebErrorEvent}
import java.util.Date
import java.text.SimpleDateFormat
import java.lang
import javafx.scene.input.{KeyCode, KeyEvent}
import WookiePanel.JS_INVITATION

object WookiePanel{
  final val JS_INVITATION = "Enter JavaScript here, i.e.: alert( $('div:last').html() )"


  def newBuilder(wookie:WookieView):WookiePanelBuilder = {
    new WookiePanelBuilder(wookie)
  }
}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookiePanel(builder:WookiePanelBuilder) extends VBox with WookiePanelFields[WookiePanel]{
  val self = this

  val location = new TextField(builder.startAtPage.getOrElse(""))
  val jsArea = new TextArea()
  val logArea = new TextArea()

  val prevButton = new Button("<")
  val nextButton = new Button(">")

  val jsButton = new Button("JS")
  val go = new Button("Go")

  val wookie:WookieView = builder.wookie
  val engine:WebEngine = builder.wookie.getEngine

  val goAction = new EventHandler[ActionEvent] {
    def handle(arg0: ActionEvent)
    {
      builder.wookie.load(location.getText)
    }
  }

  {
    jsArea.setPrefRowCount(2)
    jsArea.appendText(JS_INVITATION)

    jsArea.focusedProperty().addListener(new ChangeListener[lang.Boolean] {
      def changed(observableValue: ObservableValue[_ <: lang.Boolean], s: lang.Boolean, newValue: lang.Boolean)
      {
        if(jsArea.getText.equals(JS_INVITATION)) jsArea.setText("")
        jsArea.positionCaret(1)
        jsArea.positionCaret(0)
      }
    })

    def runJS()  {
      val r = engine.executeScript(jsArea.getText)

      if(r != "undefined") log(s"exec result: $r")
    }

    jsButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        runJS()
      }
    })

    prevButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        engine.getHistory.go(-1)
      }
    })

    nextButton.setOnAction(new EventHandler[ActionEvent] {
      def handle(e:ActionEvent){
        engine.getHistory.go(+1)
      }
    })

    jsArea.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler[KeyEvent] {
      def handle(e:KeyEvent){
        if (e.getCode.equals(KeyCode.ENTER) && e.isControlDown) { // CTRL + ENTER
          runJS()
        }
      }
    })

    HBox.setHgrow(location, Priority.ALWAYS)

    go.setOnAction(goAction)

    if (builder.showDebugPanes) {
//      val engine:WebEngine = builder.wookie.getEngine

      engine.locationProperty.addListener(new ChangeListener[String] {
        def changed(observableValue: ObservableValue[_ <: String], s: String, newLoc: String)
        {
          log(s"location changed to: $newLoc")

          location.setText(newLoc)
        }
      })

      engine.setOnError(new EventHandler[WebErrorEvent] {
        def handle(webEvent: WebErrorEvent)
        {
          WookieView.logOnAlert(webEvent.getMessage)
        }
      })
    }

    val toolbar = new HBox
    toolbar.getChildren.addAll(prevButton, nextButton, jsButton, location, go)
    toolbar.setFillHeight(true)

    val vBox = this

    vBox.getChildren.add(toolbar)

    if(builder.showDebugPanes) vBox.getChildren.add(jsArea)

    vBox.getChildren.add(builder.wookie)

    if(builder.showNavigation) vBox.getChildren.add(logArea)

    vBox.setFillWidth(true)

    VBox.setVgrow(builder.wookie, Priority.ALWAYS)
  }

  def log(s:String) = {
    val sWithEOL = if(s.endsWith("\n")) s else s + "\n"

    logArea.appendText(s"$nowForLog $sWithEOL")

    WookieView.logger.info(s)
  }

  def nowForLog: String =
  {
    new SimpleDateFormat("hh:mm:ss.SSS").format(new Date())
  }
}

trait WookiePanelFields[SELF]{
  var startAtPage:Option[String] = None
  var showNavigation:Boolean = true
  var showDebugPanes:Boolean = true

  def showNavigation(b: Boolean): SELF = { showNavigation = b; self()}
  def showDebugPanes(b: Boolean): SELF = { showDebugPanes = b; self()}
  def startAtPage(url:String): SELF = { startAtPage = Some(url); self()}

  def self():SELF
}

class WookiePanelBuilder(_wookie:WookieView) extends WookiePanelFields[WookiePanelBuilder]{
  val self = this

  val wookie = _wookie

  def build: WookiePanel =
  {
    new WookiePanel(this)
  }
}
