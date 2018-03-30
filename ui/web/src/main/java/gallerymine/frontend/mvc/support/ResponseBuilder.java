package gallerymine.frontend.mvc.support;

import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sergii_puliaiev on 6/14/17.
 */
public class ResponseBuilder {

    Map<String, Object> map = new HashMap<>();

    public ResponseBuilder put(String name, Object value) {
        map.put(name, value);
        return this;
    }

    public Map<String, Object> build() {
        return map;
    }

    public Map<String, Object> buildOk() {
        put("status", "200");
        return map;
    }

    public Map<String, Object> buildWarn() {
        put("status", "399");
        return map;
    }

    public Map<String, Object> buildError(String message) {
        put("status", "505");
        put("error", message);
        return map;
    }

    public Map<String, Object> buildError(String message, Exception e) {
        put("status", "505");
        put("message", message);
        put("exception", e.getClass().getName());
        return map;
    }

    public ResponseBuilder addMessage(String message, Object... params) {
        List<String> messageList;
        if (map.get("message") == null) {
            messageList = new ArrayList<>();
            map.put("message", messageList);
        } else {
            if (map.get("message") instanceof String) {
                messageList = new ArrayList<>();
                messageList.add((String) map.get("message"));
                map.put("message", messageList);
            } else {
                messageList = (List<String>)map.get("message");
            }
        }
        if (params != null) {
            message = String.format(message, params);
        }
        messageList.add(message);
        return this;
    }

    public static ResponseBuilder responseOk() {
        ResponseBuilder builder = new ResponseBuilder();
        builder.put("status", "200");
        return builder;
    }

    public static ResponseBuilder responseWarn() {
        ResponseBuilder builder = new ResponseBuilder();
        builder.put("status", "399");
        return builder;
    }

    public static ResponseBuilder responseError(String message) {
        ResponseBuilder builder = new ResponseBuilder();
        builder.put("error", message);
        builder.put("status", "505");
        return builder;
    }

    public static ResponseBuilder responseError(String message, Exception e) {
        ResponseBuilder builder = new ResponseBuilder();
        builder.put("message", message);
        builder.put("status", "505");
        builder.put("exception", e.getClass().getName());
        return builder;
    }

    public ModelAndView buildModel(String view) {
        ModelAndView model = new ModelAndView(view, build());
        return model;
    }
}
