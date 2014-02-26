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
import javax.annotation.Nullable
import java.util.{Collections, Random}
import java.util.concurrent._
import com.google.common.base.Preconditions
import scala.collection.mutable
import org.apache.commons.lang3.{StringUtils, StringEscapeUtils}
import org.apache.http.{HttpEntity, HttpResponse}
import org.apache.commons.io.{FilenameUtils, IOUtils}
import java.io.{FileOutputStream, File}
import com.google.common.io.{ByteStreams, CountingOutputStream}
import chaschev.io.FileUtils
import chaschev.lang.LangUtils
import com.google.common.collect.{Sets, Maps}
import collection.JavaConverters._
import java.util
import scala.Some
import com.google.common.util.concurrent.SettableFuture

class Builder2 {
  var createWebView: Boolean = true
  var useFirebug: Boolean = true
  var useJQuery: Boolean = false
  var includeJsScript: Option[String] = None
  var includeJsUrls: mutable.MutableList[String] = new mutable.MutableList[String]

  def build: SimpleBrowser2 =
  {
    new SimpleBrowser2(this)
  }

  def createWebView(b: Boolean): Builder2 =
  {
    createWebView = b; this
  }

  def useJQuery(b: Boolean): Builder2 =
  {
    useJQuery = b; this
  }

  def useFirebug(b: Boolean): Builder2 =
  {
    useFirebug = b; this
  }

  def includeJsScript(s: String): Builder2 =
  {
    includeJsScript = Some(s); this
  }

  def addScriptUrls(s: String): Builder2 =
  {
    includeJsUrls += s; this
  }

}

/**
 * Add a script to a header.
 * Click a button.
 * Wait for an element to appear.
 * Check if jQuery is added.
 * Wait for navigation event.
 */
object SimpleBrowser2 {
  final val JQUERY_VERSION = "1.9.1"

  def newBuilder: Builder2 =
  {
    new Builder2()
  }

  final val logger: Logger = LoggerFactory.getLogger(classOf[SimpleBrowser2])
}

class SimpleBrowser2(builder: Builder2) extends Pane {
  private val random: Random = new Random

  protected val webView: Option[WebView] = if (builder.createWebView) Some(new WebView) else None
  protected val webEngine: WebEngine = if (webView == None) new WebEngine() else webView.get.getEngine
  protected val useFirebug = builder.useFirebug
  protected val useJQuery = builder.useJQuery
  protected val includeJsScript: Option[String] = builder.includeJsScript
  protected val includeJsUrls = builder.includeJsUrls

  protected val history = mutable.MutableList[String]()
  protected val navigationPredicates:util.Set[NavigationRecord] = Sets.newConcurrentHashSet()

  protected val scheduler = Executors.newScheduledThreadPool(1)

  case class JSHandler(handler: Option[() => Unit], eventId: Int, latch: CountDownLatch) {
//    private val scala = Sets.newConcurrentHashSet().asScala
//    private val map: ConcurrentHashMap[NavigationRecord, Boolean] =

  }

  case class NavigationRecord(dueAt:Long, predicate: ((String, String) => Boolean), future:SettableFuture[Boolean]){
    def isDue = dueAt < System.currentTimeMillis()
  }

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

  def getWebView: WebView =
  {
    webView.get
  }

  def getEngine: WebEngine = webEngine

  def load(location: String): SimpleBrowser2 = load(location, None)

  def jsReady(eventId: Int, from: String)
  {
    SimpleBrowser2.logger.info(s"event $eventId arrived from $from")

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
  }

  /**
   *
   * @param predicate(newLocation, oldLocation)
   */
  def waitForLocation(predicate: ((String, String) => Boolean), timeoutMs: Int, handler: Option[()=>Unit]) : Boolean = {
    val future:SettableFuture[Boolean] = SettableFuture.create()

    navigationPredicates.add(new NavigationRecord(System.currentTimeMillis() + timeoutMs, predicate, future))

    if (handler.isDefined) {
      future.addListener(new Runnable {
        override def run(): Unit =
        {
          handler.get.apply()
        }
      }, scheduler)

      true
    }else{
      future.get(timeoutMs, TimeUnit.MILLISECONDS)
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
        insertJQuery(eventId, SimpleBrowser2.JQUERY_VERSION)
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

  def load(location: String, r: Runnable): SimpleBrowser2 =
  {
    load(location, Some(() => {
      r.run()
    }))
  }

  def load(location: String, onLoad: Option[() => Unit]): SimpleBrowser2 =
  {
    SimpleBrowser2.logger.info("navigating to {}", location)

    webEngine.setOnAlert(new EventHandler[WebEvent[String]] {
      def handle(webEvent: WebEvent[String])
      {
        LoggerFactory.getLogger("wk-alert").info(webEvent.getData)
      }
    })

    val eventId: Int = random.nextInt

    val urls = includeJsUrls.clone()

    if (useJQuery && !useFirebug) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${SimpleBrowser2.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 0 // 1 is for clicks.js

    latchCount += urls.length // for all the urls

    //    if(includeJsScript.isDefined) latchCount += 1   // for user's JS

    val latch = new CountDownLatch(latchCount)

    val handler = new JSHandler(onLoad, eventId, latch)

    webEngine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
      def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, t1: Worker.State)
      {
        if (t1 eq Worker.State.SUCCEEDED) {
          if (jsHandlers.putIfAbsent(eventId, handler) != null) {
            return
          }

          if (onLoad != null) {
            SimpleBrowser2.logger.info(s"registered event: $eventId for location $location, $t, $t1")

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

          val clicksJs = io.Source.fromInputStream(getClass.getResourceAsStream("/fx/clicks.js")).mkString

          insertJS(eventId, new JSScript(Array.empty, Some(clicksJs)))
        }
      }
    })

    webEngine.locationProperty().addListener(new ChangeListener[String] {
      def changed(observableValue: ObservableValue[_ <: String], oldLoc: String, newLoc: String)
      {
        SimpleBrowser2.logger.info(s"location changed to $newLoc")

        history += newLoc

        val it = navigationPredicates.iterator()

        while(it.hasNext){
          val r = it.next()

          val isDue = r.isDue

          if(isDue) {
            r.future.set(false)
            it.remove()
          }else{
            val matches = r.predicate.apply(newLoc, oldLoc)

            if(matches){
              r.future.set(true)
              it.remove()
            }
          }
        }
      }
    })

    webEngine.load(location)

    this
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

    jsWindow.setMember("simpleBrowser", SimpleBrowser2.this)

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

  def $(jQuery: String)
  {
    Platform.runLater(new Runnable {
      override def run(): Unit = {
        val s = s"printJQuery($jQuery)"

        println(s"executing $s")

        getEngine.executeScript(s)
      }
    })
  }
}