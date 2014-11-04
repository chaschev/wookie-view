wookie-view
===========

WookieView is made to simplify WebView usage and replace Selenium in some of the tasks, maybe. With WookieView you can easily automatize web browsing operations like searching with Google Search, visiting your Facebook page with your login and password, crawling through a forum and downloading files. WookieView is pure JDK, it uses a few Java libraries and can be seen as a lightweight version of Selenium.

## Features

* Concise syntax
* JQuery support
* Embedding scripts into existing pages (i.e. jQuery, Firebug, your manual JS scripts)
* File downloads
* Scala & Java 8 support
* Simple plain script and event-driven API

## How to open this project in IDE

To open a project in your IDE, you just import it as a Maven project.

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

## Questions, etc

Just msg me or mail me, I will respond as quickly as I can.

## Maven artifacts

I will publish this project to Maven Central if gets enough attention.