package fx

import chaschev.util.Exceptions
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker
import javafx.event.EventHandler
import javafx.scene.layout.Pane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebEvent
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.{Random}
import java.util.concurrent._
import scala.collection.mutable
import org.apache.commons.lang3.{StringUtils, StringEscapeUtils}
import com.google.common.collect.{Sets, Maps}
import java.util
import scala.Some
import com.google.common.util.concurrent.SettableFuture

class WookieBuilder {
  var createWebView: Boolean = true
  var useFirebug: Boolean = true
  var useJQuery: Boolean = false
  var includeJsScript: Option[String] = None
  var includeJsUrls: mutable.MutableList[String] = new mutable.MutableList[String]

  def build: WookieView =
  {
    new WookieView(this)
  }

  def createWebView(b: Boolean): WookieBuilder =
  {
    createWebView = b; this
  }

  def useJQuery(b: Boolean): WookieBuilder =
  {
    useJQuery = b; this
  }

  def useFirebug(b: Boolean): WookieBuilder =
  {
    useFirebug = b; this
  }

  def includeJsScript(s: String): WookieBuilder =
  {
    includeJsScript = Some(s); this
  }

  def addScriptUrls(s: String): WookieBuilder =
  {
    includeJsUrls += s; this
  }

}

class DirectJQueryWrapper(jQueryObject:JSObject, wookie:WookieView) extends JQueryWrapper(null, wookie){
  val jQueryObj:JSObject = jQueryObject

  override def text(): String = jQueryObj.call("text", Array.empty).asInstanceOf
}

class JQueryWrapper(selector:String, wookie:WookieView){
  val escapedSelector = StringEscapeUtils.escapeEcmaScript(selector)

  def attrs():List[String] = {
    interact(s"jQueryAttrs('$escapedSelector')").asInstanceOf
  }

  def attr(name:String):String = {
    interact(s"jQuery('$escapedSelector').attr('$name')").asInstanceOf[String]
  }

  def attr(name:String, value:String):JQueryWrapper = {
    interact(s"jQuery('$escapedSelector').attr('$name', '$value')")
    this
  }

  def value(value:String):JQueryWrapper = {
    interact(s"jQuery('$escapedSelector').val('$value')")
    this
  }

  def click():JQueryWrapper = {
    interact(s"$$clickIt('$escapedSelector')")
    this
  }

  def submit():JQueryWrapper = {
    interact(s"submitEnclosingForm('$escapedSelector')")
    this
  }

  def pressKey(code:Int):JQueryWrapper = {
    interact(s"pressKey('$escapedSelector', $code)")
    this
  }

  def pressEnter():JQueryWrapper = {
    pressKey(13)
    this
  }

  def html():String = {
    interact(s"jQuery_text('$escapedSelector', true)".toString).asInstanceOf[String]
//    return "keke"
//    "keke"
  }

  def text():String = {
    interact(s"jQuery_text('$escapedSelector', false)").asInstanceOf[String]
  }

  def interact(script:String, timeoutMs:Long = 5000):AnyRef = {
    WookieView.logger.debug(s"interact: $script")

    val url = wookie.getHistory.last
    val eventId = scala.util.Random.nextInt()

    val latch = new CountDownLatch(1)

    var r:AnyRef = null

    wookie.includeStuffOnPage(eventId, url, Some(() => {
      WookieView.logger.debug(s"executing $script")
      r = wookie.getEngine.executeScript(script)
      latch.countDown()
    }))

    r = wookie.getEngine.executeScript(script)
    latch.countDown()

    val started = System.currentTimeMillis()
    var expired = false

    while(!expired && !latch.await(1000, TimeUnit.MILLISECONDS) ){
      if(System.currentTimeMillis() - started > timeoutMs) expired = true
    }

    if(expired == true){
      throw new TimeoutException(s"JS was not executed in ${timeoutMs}ms")
    }

    WookieView.logger.trace(s"left interact: $script")

    r
  }

  def html(s:String):JQueryWrapper = {

    this
  }

  def append(s:String):JQueryWrapper = {
    this
  }

  def asResultList():List[JQueryWrapper] = {
    throw new UnsupportedOperationException
  }

  override def toString: String = html()
}

/**
 * (ok) Add a script to a header.
 * (ok) Click a button.
 * (ok) Wait for an element to appear.
 * (ok) Check if jQuery is added.
 * (ok) Wait for navigation event.
 */
object WookieView {
  final val JQUERY_VERSION = "1.9.1"

  def newBuilder: WookieBuilder =
  {
    new WookieBuilder()
  }

  final val logger: Logger = LoggerFactory.getLogger(classOf[WookieView])
}

abstract class NavigationEvent {
  def ok() : Boolean
}

class NokNavigationEvent extends NavigationEvent{
  override def ok() = false
}

case class OkNavigationEvent(newLocation:String, oldLocation:String) extends NavigationEvent{
  override def ok() = true
}

case class NavigationRecord(dueAt:Long, predicate: ((String, String) => Boolean), future:SettableFuture[NavigationEvent]){
  def isDue = dueAt != -1 && dueAt < System.currentTimeMillis()
}

class WookieView(builder: WookieBuilder) extends Pane {
  private val random: Random = new Random

  protected val webView: Option[WebView] = if (builder.createWebView) Some(new WebView) else None
  protected val webEngine: WebEngine = if (webView == None) new WebEngine() else webView.get.getEngine
  protected val useFirebug = builder.useFirebug
  protected val useJQuery = builder.useJQuery
  protected val includeJsScript: Option[String] = builder.includeJsScript
  protected val includeJsUrls = builder.includeJsUrls

  protected val history = mutable.MutableList[String]()
  protected val navigationPredicates:util.Set[NavigationRecord] = Sets.newConcurrentHashSet()

  protected val scheduler = Executors.newScheduledThreadPool(4)

  case class JSHandler(handler: Option[() => Unit], eventId: Int, latch: CountDownLatch) {
//    private val scala = Sets.newConcurrentHashSet().asScala
//    private val map: ConcurrentHashMap[NavigationRecord, Boolean] =

  }

  def getHistory = {history}

  private final val jsHandlers = new ConcurrentHashMap[Integer, JSHandler]

  {
    if (webView.isDefined) {
      getChildren.add(webView.get)
      webView.get.prefWidthProperty.bind(widthProperty)
      webView.get.prefHeightProperty.bind(heightProperty)
    }

    scheduler.schedule(new Runnable {
      override def run(): Unit = {

      }
    }, 50, TimeUnit.MILLISECONDS)
  }

  def getWebView: Option[WebView] = webView

  def getEngine: WebEngine = webEngine

  def load(location: String): WookieView = load(location, None)

  def jsReady(eventId: Int, from: String)
  {
    WookieView.logger.info(s"event $eventId arrived from $from")

    val jsHandler = jsHandlers.get(eventId)

    if (jsHandler != null) {
      jsHandler.latch.countDown()

      if (jsHandler.latch.getCount == 0) {
        this.synchronized {
          if (jsHandler.latch.getCount == 0) {
            if (jsHandler.handler.isDefined) jsHandler.handler.get()
          }
        }
      }
    }

    WookieView.logger.debug(s"leaving from jsReady, eventId: $eventId")
  }

  /**
   * Waits for a location to change.
   *
   * @param predicate(newLocation, oldLocation)
   */
  def waitForLocation(predicate: ((String, String) => Boolean), timeoutMs: Int, handler: Option[(NavigationEvent)=>Unit]) : Boolean = {
    val future:SettableFuture[NavigationEvent] = SettableFuture.create()

    navigationPredicates.add(new NavigationRecord(if(timeoutMs <=0 ) -1 else System.currentTimeMillis() + timeoutMs, predicate, future))

    if (handler.isDefined) {
      future.addListener(new Runnable {
        override def run(): Unit =
        {
          handler.get.apply(future.get())
        }
      }, scheduler)

      true
    }else{
      future.get(timeoutMs, TimeUnit.MILLISECONDS).ok()
    }
  }

  def waitFor($predicate: String, timeoutMs: Int): Boolean =
  {
    val startedAt = System.currentTimeMillis
    val eventId = random.nextInt
    val latch = new CountDownLatch(1)

    jsHandlers.put(eventId, new JSHandler(None, eventId, latch))

    Platform.runLater(new Runnable {
      def run()
      {
        insertJQuery(eventId, WookieView.JQUERY_VERSION)
      }
    })

    try {
      if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        return false
      }

      val periodMs: Int = timeoutMs / 20 + 2

      while (true) {
        val time = System.currentTimeMillis
        if (time - startedAt > timeoutMs) {
          return false
        }
        val scriptLatch: CountDownLatch = new CountDownLatch(1)

        var result = false

        Platform.runLater(new Runnable {
          def run()
          {
            result = webEngine.executeScript($predicate).asInstanceOf[Boolean]
            scriptLatch.countDown
          }
        })

        scriptLatch.await(periodMs, TimeUnit.MILLISECONDS)

        if (result) return true

        Thread.sleep(periodMs)
      }

      true
    }
    catch {
      case e: InterruptedException => throw Exceptions.runtime(e)
    }
  }

  def load(location: String, r: Runnable): WookieView =
  {
    load(location, Some(() => {
      r.run()
    }))
  }

  def load(location: String, onLoad: Option[() => Unit]): WookieView =
  {
    WookieView.logger.info("navigating to {}", location)

    webEngine.setOnAlert(new EventHandler[WebEvent[String]] {
      def handle(webEvent: WebEvent[String])
      {
        LoggerFactory.getLogger("wk-alert").info(webEvent.getData)
      }
    })

    val eventId: Int = random.nextInt

    webEngine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
      def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, t1: Worker.State)
      {
        if (t1 eq Worker.State.SUCCEEDED) {
          WookieView.logger.info(s"page ready: $location")
          includeStuffOnPage(eventId, location, onLoad)
        }
      }
    })

    webEngine.locationProperty().addListener(new ChangeListener[String] {
      def changed(observableValue: ObservableValue[_ <: String], oldLoc: String, newLoc: String)
      {
        WookieView.logger.info(s"location changed to $newLoc")

        history += newLoc

        val it = navigationPredicates.iterator()

        while(it.hasNext){
          val r = it.next()

          val isDue = r.isDue

          if(isDue) {
            r.future.set(new NokNavigationEvent)
            it.remove()
          }else{
            val matches = r.predicate.apply(newLoc, oldLoc)

            if(matches){
              r.future.set(new OkNavigationEvent(newLoc, oldLoc))
              it.remove()
            }
          }
        }
      }
    })

    webEngine.load(location)

    this
  }

  def includeStuffOnPage(eventId: Int, location: String, onLoad: Option[() => Unit]): Boolean =
  {
    try {
      val s = webEngine.executeScript(s"'' + window.__wookiePageInitialized").asInstanceOf[String]
      if (s == "true") {

        WookieView.logger.debug(s"wookie page $location already initialized")

        if (onLoad.isDefined) onLoad.get.apply()

        return true
      }
    } catch {
      case e:Exception => WookieView.logger.debug("exception", e)
    }

    WookieView.logger.info(s"initializing $location")

    val urls = includeJsUrls.clone()

    if (useJQuery && !useFirebug) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${WookieView.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 0 // 1 is for clicks.js

    latchCount += urls.length // for all the urls

    //    if(includeJsScript.isDefined) latchCount += 1   // for user's JS

    val latch = new CountDownLatch(latchCount)

    val handler = new JSHandler(onLoad, eventId, latch)

    if (jsHandlers.putIfAbsent(eventId, handler) != null) {
      return true
    }

    if (onLoad.isDefined) {
      WookieView.logger.info(s"registered event: $eventId for location $location")

      jsHandlers.put(eventId, handler)
    }

    if (useFirebug) {
      webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}")
    }

    if (urls.nonEmpty) {
      insertJS(eventId, new JSScript(urls.toArray, None))
    }

    if (includeJsScript.isDefined) {
      insertJS(eventId, new JSScript(Array.empty, includeJsScript))
    }

    initClicksJs(eventId)

    getEngine.executeScript("window.__wookiePageInitialized = true;")

    false
  }

  def initClicksJs(eventId: Int)
  {
    val clicksJs = io.Source.fromInputStream(getClass.getResourceAsStream("/fx/clicks.js")).mkString

    insertJS(eventId, new JSScript(Array.empty, Some(clicksJs)))
  }

  case class JSScript(urls: Array[String], text: Option[String]) {
    {
      if (!(!urls.isEmpty || text.isDefined)) {
        throw new RuntimeException("url or text must be present")
      }
    }
  }

  protected def insertJQuery(eventId: Int, version: String)
  {
    if (webEngine.executeScript(s"typeof jQuery == 'undefined'").asInstanceOf[Boolean]) {
      insertJS(eventId, new JSScript(Array[String](s"https://ajax.googleapis.com/ajax/libs/jquery/$version/jquery.min.js"), None))
    } else {
      jsReady(eventId, "jQuery already loaded")
    }
  }

  protected def insertJS(eventId: Int, jsScript: JSScript)
  {
    val jsWindow: JSObject = webEngine.executeScript("window").asInstanceOf[JSObject]

    jsWindow.setMember("simpleBrowser", WookieView.this)

    val isUrlsMode = jsScript.text.isEmpty

    val script = if (isUrlsMode) {
      s"" +
        s"" +
        s"function __loadScripts(array){\n" +
        s"  for(var i = 0; i < array.length; i++){\n" +
        s"    var text = array[i];\n" +
        s"    script = document.createElement('script');\n\n" +
        s"    \n" +
        s"    script.onload = function() {\n" +
        s"      try{\n      " +
        s"        window.simpleBrowser.jsReady($eventId, 'url ' + text);\n" +
        s"      } catch(e){\n" +
        s"        alert(e);\n" +
        s"      }\n" +
        s"    };\n" +
        s"    " +
        s"    var head = document.getElementsByTagName('head')[0];\n\n" +
        s"    " +
        s"    script.type = 'text/javascript';\n" +
        s"    script.src = text;\n\n" +
        s"    head.appendChild(script);\n" +
        s"  }\n" +
        s"}\n" +
        s"__loadScripts([" + jsScript.urls.map("'" + _ + "'").mkString(", ") + s"])"
    } else {
      val text = StringEscapeUtils.escapeEcmaScript(jsScript.text.get)

      s"" +
        s"  script = document.createElement('script');\n\n" +
        s"  \n" +
        s"  script.onload = function() {\n" +
        s"      try{\n      " +
        s"        window.simpleBrowser.jsReady($eventId, 'script');\n" +
        s"      } catch(e){\n" +
        s"        alert(e);\n" +
        s"      }\n" +
        s"  };\n" +
        s"" +
        s"  var head = document.getElementsByTagName('head')[0];\n\n" +
        s"" +
        s"  script.type = 'text/javascript';\n" +
        s"  script.text = '$text';\n" +
        s"  head.appendChild(script);\n"
    }

//    print(s"\n---\n$script\n")

    webEngine.executeScript(script)
  }

  def getHTML: String = webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML").asInstanceOf[String]

  def click(jQuery: String)
  {
    Platform.runLater(new Runnable {
      override def run(): Unit =
      {
        val s = s"$$clickIt($jQuery)"

        println(s"executing $s")

        getEngine.executeScript(s)
      }
    })
  }

  /**
   * Todo: change into a wrapper object with methods: html(), text(), attr()
   * @param jQuerySelector
   * @return
   */
  def $(jQuerySelector: String):JQueryWrapper =
  {
    new JQueryWrapper(jQuerySelector, this)
  }
}