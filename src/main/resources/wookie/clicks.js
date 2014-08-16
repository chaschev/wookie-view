var $clickIt = function(sel){
    clickIt(jQuery(sel))
};

var clickIt = function($el){
  var el = $el[0];
  var etype = 'click';

  clickDom(el, etype);
};

var clickItem = function(operationFn, $sel){
    alert("about to click: " + jQuery_text(operationFn, $sel, true));

    clickIt(operationFn($sel));
};

var submitEnclosingForm = function(operationFn, sel){
    operationFn(sel).closest('form').submit();
};

var pressKey = function(sel, code){
    var p = {which: code, keyCode: code};

    jQuery(sel).trigger(jQuery.Event("keydown", p));
    jQuery(sel).trigger(jQuery.Event("keyup", p));
    jQuery(sel).trigger(jQuery.Event("keypress", p));
//    alert("triggered " + JSON.stringify(p));
};

var clickDom = function(el, etype){
  if (el.fireEvent) {
    el.fireEvent('on' + etype);
  } else {
    var evObj = document.createEvent('Events');

    evObj.initEvent(etype, true, false);

    el.dispatchEvent(evObj);
  }
};

var printJQuery = function($sel){
    var r = $($sel);
    alert("found " + r.length + " results for " + $sel + ": " + r.html());

    r.each(function(index, el){
        alert(el.outerHTML);
    });
};

var printJQuery2 = function(r){
    alert("found " + r.length + " results : " + r.html());

    r.each(function(index, el){
        alert(el.outerHTML);
    });
};

var newArrayFn = function(i){
    return function($sel){
        return $($($sel)[i]);
    };
};

var arrayFn = function($sel){
    return $($(sel)[i]);
};

var directFn = function($sel){
    return window.__javaToJS;
};

var newFindFn = function($rootSel, $findSel){
    return function($sel){
        return $($rootSel).find($findSel).find($sel);
    };
};

var jQueryAggregate = function(operationFn, $sel, initialValue, aggregator){
    var r = operationFn($sel);

    var result = initialValue

    r.each(function(index, el){
        result = aggregator(result, index, el);
    });

    return result;
};

var jQueryFind = function(operationFn, $sel, $findSel){
    return operationFn($sel).find($findSel);
};

var jQuerySetValue = function(operationFn, $sel, name, value){
    return operationFn($sel).val(value);
};

var jQueryGetAttr = function(operationFn, $sel, name){
    return operationFn($sel).attr(name);
};

var jQuerySetAttr = function(operationFn, $sel, name, value){
    return operationFn($sel).attr(name, value);
};

var jQuery_asResultArray = function(operationFn, $sel){
    var res = [];

    jQueryAggregate(operationFn, $sel, res, function(r, i, el){
        r.push(jQuery(el));
        return r;
    });

    return res;
};

var jQueryAttrs = function(operationFn, $sel){
    var r = operationFn($sel);

    if(r.length == 0) return [];

    var nodes=[], values=[];
    var el =  r[0];

    for (var attr, i = 0, attrs=el.attributes, l=attrs.length; i<l; i++){
        attr = attrs.item(i);
        nodes.push(attr.nodeName);
        values.push(attr.nodeValue);
    }

    return nodes;
};

//var jQuery_asResultArray = function($sel){
//
//};

var jQuery_text = function(operationFn, $sel, isHtml){
    return jQueryAggregate(operationFn, $sel, '', function(r, i, el){
        if(isHtml) {
            return r + el.outerHTML + "\n";
        } else {
            return r + el.innerText + "\n";
        }
    });
};