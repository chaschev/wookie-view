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
$("input[maxlength]")
    .value("wookie-view")
    .submit();
```

#### Click 'Sign In' button at GitHub

```java
wookie
    .waitForLocation(new WaitArg("git wookie not logged in")
    .matchByAddress((s) -> s.contains("/wookie-view"))
    .whenLoaded(e -> {
        $.apply("a.button.signin", e).clickLink();
    }))
```

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
// Wait for Google Search results page to load
wookie
    .waitForLocation(new WaitArg("google search results")
    .matchByAddress((s) -> s.contains("q="))
    .whenLoaded(e -> {
        System.out.println("results: " + $.apply("h3.r", e).asResultList());

        // find our link in the results list and click it
        // in Scala the selector would be shorter: $("h3.r a")
        JQueryWrapper githubLink = $.apply("h3.r a", e).asResultListJava()
            .stream()
            .filter((j) -> j.text().contains("chaschev"))
            .findFirst().get();

        githubLink.clickLink();
    }));
  
// Search Google
$.apply("input[maxlength]", null)
    .value("wookie-view")
    .submit();
```

#### Download a file (Scala)

```scala
wookie.waitForDownloadToStart(
  new LocationMatcher(loc =>
    loc.contains("download.oracle.com") && loc.contains("?")
  )
).andThen({ case result =>
  logger.info(s"download done: $result")
})
```