var $clickIt = function(sel){
    clickIt($(sel))
}

var clickIt = function($el){
  var el = $el[0];
  var etype = 'click';

  clickDom(el, etype);
}

var clickDom = function(el, etype){
  if (el.fireEvent) {
    el.fireEvent('on' + etype);
  } else {

    var evObj = document.createEvent('Events');

    evObj.initEvent(etype, true, false);

    el.dispatchEvent(evObj);
  }
}

var printJQuery = function($sel){
    var r = $($sel);
    alert("found " + r.length + " results for " + $sel + ": " + r.html());

    r.each(function(index, el){
        alert(el.outerHTML);
    });
}