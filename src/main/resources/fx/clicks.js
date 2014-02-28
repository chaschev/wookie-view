var $clickIt = function(sel){
    clickIt(jQuery(sel))
}

var clickIt = function($el){
  var el = $el[0];
  var etype = 'click';

  clickDom(el, etype);
}

var submitEnclosingForm = function(sel){
    jQuery(sel).closest('form').submit();
}

var pressKey = function(sel, code){
    var p = {which: code, keyCode: code};

    jQuery(sel).trigger(jQuery.Event("keydown", p));
    jQuery(sel).trigger(jQuery.Event("keyup", p));
    jQuery(sel).trigger(jQuery.Event("keypress", p));
//    alert("triggered " + JSON.stringify(p));
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

var jQueryAggregate = function($sel, initialValue, aggregator){
    var r = $($sel);

    var result = initialValue

    r.each(function(index, el){
        result = aggregator(result, index, el);
    });

    return result;
}

var jQueryChildren = function($sel){
    jQueryAggregate($sel, [], function(r, i, el){
        r.push(el);r;
    });
};

var jQueryAttrs = function($sel){
    var r = $($sel);

    if(r.length == 0) return [];

    var nodes=[], values=[];
    var el =  r[0];

    for (var attr, i=0, attrs=el.attributes, l=attrs.length; i<l; i++){
        attr = attrs.item(i)
        nodes.push(attr.nodeName);
        values.push(attr.nodeValue);
    }

    return nodes;
};

var jQuery_text = function($sel, isHtml){
    return jQueryAggregate($sel, '', function(r, i, el){
        if(isHtml) {
            return r + el.outerHTML + "\n";
        } else {
            return r + el.innerText + "\n";
        }
    });
}