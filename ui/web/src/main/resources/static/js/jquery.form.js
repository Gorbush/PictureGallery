/**
 * Form helper fo jQuery:
 * - parsing from JSON
 * - populating page elements with some pre-formatting for dates and arrays
 * - saving into JSON
 *
 * @author Sergii Puliaiev
 * @url https://github.com/Gorbush
 * */
var FormHelper = {
    version: "1.0.0"
};

FormHelper.parseForm = function($form){
    var serialized = $form.serializeArray({
        checkboxesAsBools: true
    });
    var s;
    var data = {};
    for(s in serialized){
        var name = serialized[s]['name'];
        var value = serialized[s]['value'];
        value = convertValue($form, name, value);
        populateValueIntoObject(data, name, value);
    }
    return data;
};
function populateValueIntoObject(data, name, value) {
    name = name.trim();
    var indexBraceStart = name.indexOf("[");
    var indexDotStart = name.indexOf(".");
    if (indexDotStart == -1 && indexBraceStart == -1) {
        data[name] = value;
        return data;
    }
    if (indexBraceStart >-1 && (indexDotStart == -1 || indexBraceStart < indexDotStart)) {
        // we have dot first
        var indexedProperty = name.substr(0, indexBraceStart);
        var indexEnd = name.indexOf("]")-indexBraceStart-1;
        var object;
        var index =  name.substr(indexBraceStart+1, indexEnd).trim();
        if (index.match(/^'.*'$/)) {
            index = index.replace(/^'(.*)'/,'$1');
        }
        if (index.match(/^".*"$/)) {
            index = index.replace(/^"(.*)"/,'$1');
        }
        var isArray = !isNaN(parseInt(index));
        if (!data[indexedProperty]) {
            if (isArray) {
                data[indexedProperty] = [];
                var newName = name.substr(indexBraceStart+indexEnd+2);
                if (newName == "") {
                    data[indexedProperty].push(value);
                } else {
                    newName = newName.substr(1);
                    var newData = {};
                    data[indexedProperty].push(newData);
                    var res = populateValueIntoObject(newData, newName, value);
                }
            } else {
                data[indexedProperty] = {};
                object = data[indexedProperty];
                var newName = name.substr(indexBraceStart+indexEnd+2);
                if (newName == "") {
                    object[index] = value;
                } else {
                    var newData = object[index];
                    populateValueIntoObject(newData, newName, value);
                }
            }
        } else {
            if (isArray) {
                var newName = name.substr(indexBraceStart+indexEnd+2);
                if (newName == "") {
                    data[indexedProperty].push(value);
                } else {
                    newName = newName.substr(1);
                    var newData;
                    while(!validValue(newData = data[indexedProperty][index])) {
                        data[indexedProperty].push({});
                    }
                    var res = populateValueIntoObject(newData, newName, value);
                }
            } else {
                object = data[indexedProperty];
                var newName = name.substr(indexBraceStart+indexEnd+2);
                if (newName == "") {
                    object[index] = value;
                } else {
                    var newData = object[index];
                    populateValueIntoObject(newData, newName, value);
                }
            }
        }
        return data;
    }
    if (indexDotStart != -1) {
        // we have dot first
        var indexedProperty = name.substr(0, indexDotStart);
        var isArray = !isNaN(parseInt(indexedProperty));
        if (!data[indexedProperty]) {
            if (isArray) {
                data[indexedProperty] = [];
                data[indexedProperty].push({});
            }else{
                data[indexedProperty] = {};
            }
        }
        var newName = name.substr(indexDotStart+1);
        if (newName == "") {
            if (isArray) {
                data[indexedProperty].push(value);
            } else {
                data[indexedProperty] = value;
            }
        } else {
            var newData = data[indexedProperty];
            data[indexedProperty] = populateValueIntoObject(newData, newName, value);
        }
        return data;
    }
}
FormHelper.parseFormToJSON = function($form){
    var serialized = $form.serializeArray({
        checkboxesAsBools: true
    });
    var s;
    var data = {};
    for(s in serialized){
        var name = serialized[s]['name'];
        var value = serialized[s]['value'];
        value = convertValue($form, name, value);
        populateValueIntoObject(data, name, value);
    }
    return data;
};
function convertValue (form, name, value) {
    var component = $(form).find("[name = '"+name.replace(/'/g,'"')+"']");
    var converter = $(component).attr("formConverter");
    if (validValue(converter) && converter == "ToLong") {
        value = clientDate(new Date(value)).getTime();
    }
    return value;
}
FormHelper.serializeForm = function($form){
    return JSON.stringify(this.parseForm($form), null, 2);
};

FormHelper.populate = function (rootElement, dataObject, onCellRenderFunction) {
        rootElement.data("populatedBy", dataObject);
        rootElement.find("[name]").each( function() {
            var cellElement =  $(this);

            var preservedCell = cellElement.attr("preserve");
            if (preservedCell) {
                return;
            }

            var fieldName = cellElement.attr("name");
            if (typeof fieldName == "undefined") {
                fieldName = cellElement.attr("class");
            }
            if (typeof fieldName == "undefined" || fieldName == "") {
                return;
            }
            var content = getFieldValue(dataObject, fieldName);
            var fieldClass = cellElement.attr("class");
            var formatting = cellElement.attr("data-format");

            content = fixContent(fieldName, content, fieldClass, cellElement[0].tagName, formatting);

            if (/^(input|select|textarea)$/i.test(cellElement[0].tagName)){
                if (cellElement[0].type == "checkbox") {
                    cellElement.prop('checked', content == "true" || content == true);
                } else {
                    cellElement.val(content);
                }
            } else {
                cellElement.text(content);
            }

            if (onCellRenderFunction) {
                onCellRenderFunction(rowObject, rowElement, cellElement, fieldName, content);
            }
        });
    };

FormHelper.validate = function($form){
	// var rangeParser = /(int|float)([\[\(])(\d*):(\d*)([\)\]])/g;
	var urlParser = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
	var dateParser = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
	var alphaNumericParser = /^[0-9a-zA-Z,._]*$/;
    var alphaNumericParserAndSpace = /^[0-9a-zA-Z,._ ]*$/;

    var everythingIsValid = true;
    try {
        $.each($("input[data-validation]:visible", $form), function (i, item) {
            // create the list item
            var elem = $(item);
            var valueRules = elem.attr("data-validation");
            // var format = elem.attr("data-validation-format");
            var pattern = elem.attr("data-validation-pattern");
            var message = elem.attr("data-validation-error-msg");
            var value = elem.val();
            var empty = value == "";
            var failed = false;

            if (valueRules.contains("required")) {
                failed = failed || empty;
            }
            if (valueRules.contains("match")) {
                // match with pattern
                var regexp = new RegExp(pattern);

                failed = failed || !regexp.test(value);
            }
            if (valueRules.contains("date")) {
                failed = failed || !dateParser.test(value);
            }
            if (valueRules.contains("url")) {
                failed = failed || !urlParser.test(value);
            }

            if (valueRules.contains("length")) {
                var len = elem.attr("data-validation-length");
                var min, max;
                if (len.contains("-")) {
                    // range
                    min = len.substr(0, len.indexOf("-"));
                    max = len.substr(len.indexOf("-") + 1);
                    failed = failed || (value.length < min);
                    failed = failed || (value.length > max);
                }
                if (len.contains("<")) {
                    max = len.substr(len.indexOf("<") + 1);
                    failed = failed || (value.length > max);
                }
                if (len.contains(">")) {
                    min = len.substr(len.indexOf(">") + 1);
                    failed = failed || (value.length < min);
                }
            }
            if (valueRules.contains("alphanumeric")) {
                failed = failed || !alphaNumericParser.test(value);
            }
            if (valueRules.contains("alphaNumericSpace")) {
                failed = failed || !alphaNumericParserAndSpace.test(value);
            }
            if (!empty && (!valueRules.contains("list")) && (valueRules.contains("int") || valueRules.contains("float"))) {
                failed = failed || !$.isNumeric(value);
                if (!failed) {
                    var number;
                    if (valueRules.contains("int")) {
                        number = parseInt(value);
                    }
                    if (valueRules.contains("float")) {
                        number = parseFloat(value);
                    }
                    var match = /(int|float)([\[\(])(\d*):(\d*)([\)\]])/g.exec(valueRules);
                    //var match = rangeParser.exec(valueRules);
                    if (match) {
                        var min, max;
                        // range
                        min = match[3];
                        if (min) {
                            if (match[2] == "(") {
                                failed = failed || (number <= min);
                            } else { // means [ or nothing
                                failed = failed || (number < min);
                            }
                        }
                        max = match[4];
                        if (max) {
                            if (match[5] == ")") {
                                failed = failed || (number >= max);
                            } else { // means ] or nothing
                                failed = failed || (number > max);
                            }
                        }
                    }
                }
            }
            if (!empty && valueRules.contains("list")) {
                var array = convertStringToArray(value);
                for(var index = 0;index < array.length; index++){
                    var val = array[index];
                    if (valueRules.contains("int") || valueRules.contains("float")) {
                        failed = failed || !$.isNumeric(val);
                    }
                    if (failed) {
                        break;
                    }
                }
            }
            if (failed) {
                everythingIsValid = false;
                ErrorsPane.addFieldMessage(elem, message);
            }
        });
    } catch (err) {
        everythingIsValid = false;
        ErrorsPane.addFieldMessage(null, "Validation failed with error: "+err);
    }
    return everythingIsValid;
};

function fixContent(fieldName, fieldValue, fieldClass, tagName, formatting) {
    if (!validValue(fieldValue)) {
        fieldValue = "";
    }
    if (!validValue(fieldClass)) {
        fieldClass = "";
    }
    if (!validValue(fieldName)) {
        fieldName = "";
    }
    if (fieldName.toLowerCase().endsWith("date") || fieldName.toLowerCase().endsWith("time") || fieldClass.indexOf("date") >= 0) {
        if (/^\d+$/.test(fieldValue)) {
            // means this is number only - treat it as milliseconds epoc
            fieldValue = new Date(fieldValue);
            fieldValue = serverDate(fieldValue);
            fieldValue = formatDate(fieldValue);
        }
        if (typeof fieldValue.millis != "undefined" && fieldValue.millis != null) {
            // means this is number only - treat it as milliseconds epoc
            fieldValue = new Date(fieldValue.millis);
            fieldValue = serverDate(fieldValue);
            fieldValue = formatDate(fieldValue);
        }
        // fix format from "2014-10-03T21:45:09.007+0000" to "2014-10-03 21:45:09"
        fieldValue = fieldValue.replace('T', ' ');
        var i = fieldValue.indexOf(".");
        if (i != -1) {
            fieldValue = fieldValue.substring(0, i);
        }
    }
    if (fieldValue.length && (formatting == "pre" || tagName == "PRE")) {
        var newVal = "";
        for(var j=0;j < fieldValue.length;j++) {
            newVal = newVal + fieldValue[j] + "\n";
        }
        fieldValue = newVal;
    }
    if (fieldClass.indexOf("simple_file_name") >= 0) {
        var lastSlash = fieldValue.lastIndexOf("/");
        if (lastSlash > -1) {
            fieldValue = fieldValue.substring(lastSlash+1);
        }
    }
    if (fieldClass.indexOf("file_size") >= 0) {
        fieldValue = fileSizeSI(fieldValue);
    }
    return fieldValue;
}
function getFieldValue(object, fieldPath) {
    if (!validValue(object)) {getField
        return null;
    }
    if (typeof fieldPath == "undefined" || fieldPath == null || fieldPath == "") {
        return null;
    }
    if (fieldPath.indexOf(".") != -1) {
        var path = fieldPath.split(".");
        var obj = object;
        var ind = 0;
        while (ind < path.length) {
            var field = path[ind];
            if (field.indexOf("[") != -1) {
                var indexStart = field.indexOf("[");
                var indexedProperty =  field.substr(0, indexStart);
                obj = obj[indexedProperty];
                var index =  field.substr(indexStart+1, field.indexOf("]")-indexStart-1);
                obj = obj[index];
            } else {
                if (field != "") {
                    obj = obj[field];
                }
            }
            if (typeof obj == "undefined" || obj == null) {
                return null;
            }
            ind ++;
        }
        return obj;
    } else {
        if (fieldPath.indexOf("[") != -1) {
            var indexStart = fieldPath.indexOf("[");
            var indexedProperty =  fieldPath.substr(0, indexStart);
            var subObject = object[indexedProperty];
            if (!validValue(subObject)) {
                object = subObject;
            } else {
                var index =  fieldPath.substr(indexStart+1, fieldPath.indexOf("]")-indexStart-1).trim();
                if (index.match(/^'.*'$/)) {
                    index = index.replace(/^'(.*)'/,'$1');
                }
                if (index.match(/^".*"$/)) {
                    index = index.replace(/^"(.*)"/,'$1');
                }
                object = subObject[index];
            }
        } else {
            object = object[fieldPath];
        }
        return object;
    }
}


$( document ).ready(function() {
    (function ($) {
        $.fn.serialize = function (options) {
            return $.param(this.serializeArray(options));
        };

        $.fn.serializeArray = function (options) {
            var o = $.extend({
                checkboxesAsBools: false
            }, options || {});

            var rselectTextarea = /select|textarea/i;
            var rinput = /text|hidden|password|search|/i;
            var rinputNode = /input/i;

            return this.map(function () {
                return this.elements ? $.makeArray(this.elements) : this;
            })
                .filter(function () {
                    return this.name && !this.disabled &&
                        (this.checked
                            || (o.checkboxesAsBools && this.type === 'checkbox')
                            || rselectTextarea.test(this.nodeName)
                            || (rinputNode.test(this.nodeName))); // && rinput.test(this.type)
                })
                .map(function (i, elem) {
                    var val = $(this).val();
                    return val == null ?
                        null :
                        $.isArray(val) ?
                            $.map(val, function (val, i) {
                                return { name: elem.name, value: val };
                            }) :
                            {
                                name: elem.name,
                                value: (o.checkboxesAsBools && this.type === 'checkbox') ? //moar ternaries!
                                    (this.checked ? 'true' : 'false') :
                                    val
                            };
                }).get();
        };

    })(jQuery);
});
