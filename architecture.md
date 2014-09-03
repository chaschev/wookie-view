# Architecture
## Basic Classes

*WaitArg* –  The input containing callbacks to handle the change of the browser state. The 'change of a browser state' is also descibed here.

*Wookie.waitForLocation* – creates a NavigationRecord instance and puts it into an array to scan after periods of time. See notes on the size of this array (must be <= 1)
*NavigationRecord* - a record stored in an array. Contains WaitArg and a promise to NavigationDoneEvent  (may contain eventId??)
*PageDoneEvent* - when browser finishes loading a page (ok, timeout, nok).
*NavigationMatcher*
*WookieView.scanNavRecords* - a method to scan records to find matching page-done handlers & check timeouts
*WookieView.includeStuffOnPage* – a method which includes jQuery and other scripts once the page is done (including timeout situation).
*WookieView.registerScanningTimer* - runs a timer to scan records for timeouts.

## Important notes

* If the page is not yet ready, clicking a button (JS interacton in code) won't work.
* (todo?) As there is only one page being loaded at the moment of time, it makes sense to maintain a single NavigationRecord in the wookie.navigationRecords
* So eventually in the majority of the scenarios (all?) the interaction can be done in a single thread like:

```scala
open('www.google.com')

// WookieScenarioLock

// open will start loading the page and lock current thread
// WhenLoaded will unlock the execution, store PageLoaded event into the context so that $ will not require it

$("input[maxlength]")
    .timeout("2s")
    .value("wookie-view")
    .submit()
    
// submit is different here as it should lock current thread. So maybe lock/unlock should be added to all page loading

//...

$('.download').clickLink()
download()
    .matchByAddress((s) => s.contains('download.oracle.com'))
    .awaitWithLock()
```