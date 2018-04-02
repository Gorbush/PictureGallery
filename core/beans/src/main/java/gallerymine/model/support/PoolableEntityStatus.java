package gallerymine.model.support;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sergii_puliaiev on 6/22/17.
 */
@Data
public class PoolableEntityStatus {

    private final String name;

    public static Map<Class, Map<String, PoolableEntityStatus>> values = new HashMap<>();

    public static PoolableEntityStatus ENUM = new PoolableEntityStatus();

    protected PoolableEntityStatus() {
        name = null;
    }

    protected PoolableEntityStatus(String name) {
        this.name = name;
//        getValues().put(name, this);
    }

    public void add(String name) {
        getValues().put(name, new PoolableEntityStatus(name));
    }

    public Map<String, PoolableEntityStatus> getValues() {
        Map<String, PoolableEntityStatus> result = values.get(PoolableEntityStatus.class);
        if (result == null) {
            result = new HashMap<>();
            values.put(PoolableEntityStatus.class, result);
        }
        return result;
    }

    public static String QUEUED="QUEUED";
    public static String AWAITING="AWAITING";
    public static String IN_PROCESS="IN_PROCESS";
    public static String AWAITING_SUB="AWAITING_SUB";
    public static String FAILED="FAILED";
    public static String DONE="DONE";

    {
        add("QUEUED");
        add("AWAITING");
        add("IN_PROCESS");
        add("AWAITING_SUB");
        add("FAILED");
        add("DONE");
    }

    @Override
    public String toString() {
        return name;
    }
}
