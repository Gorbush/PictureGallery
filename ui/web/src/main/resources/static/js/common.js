/**
 * Created by sergii_puliaiev on 6/20/17.
 */
var serverTimeZone = new Date().getTimezoneOffset()*60*1000;
var browserTimeZone = new Date().getTimezoneOffset()*60*1000;
function zeroPad(value) {
    var padded = '000'+value;
    return padded.substr(padded.length-2);
}
function serverDate(date) {
    return new Date(date.getTime() - serverTimeZone + browserTimeZone);
}
function clientDate(date) {
    return new Date(date.getTime() + serverTimeZone - browserTimeZone);
}
function formatDate(value) {
    if (!validValue(value)) {
        return value;
    }
    if (/^\d+$/.test(value)) {
        // means this is number only - treat it as milliseconds epoc
        value = new Date(value);
        value = serverDate(value);
        value = formatDateObject(value);
    }
    if (typeof value.millis != "undefined" && value.millis != null) {
        // means this is number only - treat it as milliseconds epoc
        value = new Date(value.millis);
        value = serverDate(value);
        value = formatDateObject(value);
    }
    if (typeof value == "Date" || value.getFullYear) {
        value = formatDateObject(value);
    }
    // fix format from "2014-10-03T21:45:09.007+0000" to "2014-10-03 21:45:09"
    value = value.replace('T', ' ');
    var i = value.indexOf(".");
    if (i != -1) {
        value = value.substring(0, i);
    }
    return value;
}
function formatDateObject(date) {
    return date.getFullYear()+"-"+zeroPad(date.getMonth()+1)+'-'+zeroPad(date.getDate())+' '+
        zeroPad(date.getHours()) + ":" + zeroPad(date.getMinutes()) + ":" + zeroPad(date.getSeconds());
}
function formatToDateDateObject(date) {
    return date.getFullYear()+"-"+zeroPad(date.getMonth()+1)+'-'+zeroPad(date.getDate());
}
var QueryString = function () {
    // This function is anonymous, is executed immediately and
    // the return value is assigned to QueryString!
    var query_string = {};
    var query = window.location.search.substring(1);
    var vars = query.split("&");
    for (var i=0;i<vars.length;i++) {
        var pair = vars[i].split("=");
        // If first entry with this name
        if (typeof query_string[pair[0]] === "undefined") {
            query_string[pair[0]] = decodeURIComponent(pair[1]);
            // If second entry with this name
        } else if (typeof query_string[pair[0]] === "string") {
            var arr = [ query_string[pair[0]],decodeURIComponent(pair[1]) ];
            query_string[pair[0]] = arr;
            // If third or later entry with this name
        } else {
            query_string[pair[0]].push(decodeURIComponent(pair[1]));
        }
    }
    return query_string;
}();

function getTemplateCopy(templateName) {
    var copy = $("div#templates ."+templateName).clone();

    return copy;
}

function validValue(val) {
    return (typeof val != "undefined" && val != null);
}
function fileSizeSI(a,b,c,d,e){
    return (b=Math,c=b.log,d=1e3,e=c(a)/c(d)|0,a/b.pow(d,e)).toFixed(2)
        +' '+(e?'kMGTPEZY'[--e]+'B':'Bytes')
}
function guid() {
    function s4() {
        return Math.floor((1 + Math.random()) * 0x10000)
            .toString(16)
            .substring(1);
    }
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
        s4() + '-' + s4() + s4() + s4();
}

function resize(originalWidth, originalHeight, containerWidth, containerHeight) {
    return {
        width: 500,
        height: 500
    }
}

function lastDayOfMonth(year, month, day) {
    var d = new Date(year, month + 1, 0);
    return formatToDateDateObject(d);
}

var LogContainer = {
    init: function () {
        $(".log-container").on({
            mouseenter: function (event) {
                $("div.log-content", event.target).show();
            },
            mouseleave: function (event) {
                if (event.target.className === "log-content") {
                    $(event.target).hide();
                } else {
                    $("div.log-content", event.target).hide();
                }
            }
        });
    }
};

function moveChildren(source, target) {
    if (!source.childNodes && source.length) {
        // dereference jQuery node
        source = source[0];
    }
    if (!target.childNodes && target.length) {
        // dereference jQuery node
        target = target[0];
    }
    var sourceNodes = source.childNodes;
    var nodesList = [];
    sourceNodes.forEach(function(child) {
        nodesList.push(child);
    });
    var children = [];
    nodesList.forEach(function(child) {
        target.appendChild(child);
        children.push(child);
    });
    return children;
}
function populateTemplate(template, dataObject, target) {
    template = $(template);
    var filledTemplate = template.clone();
    filledTemplate.removeAttr("id");
    if (dataObject) {
        FormHelper.populate(filledTemplate, dataObject);
    }

    if (target) {
        return $(moveChildren(filledTemplate, target));
    }
    return filledTemplate;
}

$(document).ready(function () {
    $('[data-toggle="tooltip"]').tooltip();
});
