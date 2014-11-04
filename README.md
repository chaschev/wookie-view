wookie-view
===========

WookieView allows you to automatize web browsing – i.e. to search with Google Search, visit you Facebook, download files. For people who are familiar with Selenium it is can a lightweight alternative to Selenium. It has very few external dependencies and uses WebView from JDK 8 to render pages.

## Features:

* Concise syntax
* JQuery support
* Embedding scripts into existing pages (i.e. jQuery, Firebug, your manual JS scripts)
* File downloads
* Scala & Java 8 support

## How to open this project in IDE

To open a project in your IDE, you might import it as a Maven project.

Maven artifacts are coming if the project gets popular.

## Code Examples

Code examples can be found in an [example folder](https://github.com/chaschev/wookie-view/tree/master/src/test/scala/wookie/example). Some few examples from there:

#### Search Google

```java
load("http://www.google.com")

$("input[maxlength]")
  .value("wookie-view")
  .submit() // execution is blocked here during submit()

println("results: " + $("h3.r").asResultList)
```

#### Click 'Sign In' button at GitHub

```java
$("a.button.signin").followLink()
```

#### Submit a form

// fill login data at the login page
$("#login_field").value(login)
$("#password").value(password)
  .submit()

#### Configuring the WookieView

```java
WookieView wookieView = WookieView.newBuilder
    .useFirebug(false)
    .useJQuery(true)
    .createWebView(!DownloadJDK.miniMode)  // WookieView can be hidden
    .includeJsScript(io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/downloadJDK.js")).mkString)
    .build()
```

#### Follow a link after a Google Search query
   
```java
load("http://www.google.com")

$("input[maxlength]")
  .value("wookie-view")
  .submit()

System.out.println("results: " + $("h3.r").asResultList)

// get the list of result links with CSS selector,
// find a link in it and follow it
$("h3.r a").asResultList().find(_.text().contains("chaschev")).get.followLink()
```

#### Download a file (Scala)

```scala
addDownloadHook(new LocationMatcher(loc =>
    loc.contains("download.oracle.com") && loc.contains("?")
))
downloadLink.get.followLink()
```