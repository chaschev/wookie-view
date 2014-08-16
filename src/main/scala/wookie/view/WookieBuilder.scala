package wookie.view

import scala.collection.mutable

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookieBuilder {
  var createWebView: Boolean = true
  var useFirebug: Boolean = false
  var useJQuery: Boolean = false
  var includeJsScript: Option[String] = None
  var includeJsUrls: mutable.MutableList[String] = new mutable.MutableList[String]

  def build: WookieView =
  {
    new WookieView(this)
  }

  def createWebView(b: Boolean): WookieBuilder = { createWebView = b; this }
  def useJQuery(b: Boolean): WookieBuilder = { useJQuery = b; this }
  def useFirebug(b: Boolean): WookieBuilder = { useFirebug = b; this }
  def includeJsScript(s: String): WookieBuilder = { includeJsScript = Some(s); this }
  def addScriptUrls(s: String): WookieBuilder = { includeJsUrls += s; this }
}

