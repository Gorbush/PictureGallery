package gallerymine.backend.utils;

/**
 * Reg Exp helper to convert file path or name wildcards to regexps
 * Created by sergii_puliaiev on 7/5/17.
 */
public class RegExpHelper {

    public static boolean isMask(String data) {
        return data.contains("*") || data.contains("?") || data.startsWith("~");
    }

    public static String convertToRegExp(String wildcard) {
        if (wildcard.startsWith("~")) {
            // means this is complete regexp - not a wildcard
            return wildcard.substring(1);
        }
        StringBuilder s = new StringBuilder(wildcard.length()+10);
        s.append('^');
        boolean prevWasStar = false;
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch(c) {
                case '*':
                    if (prevWasStar) {
                        s.append(".*");
                    } else {
                        s.append("[^/]*");
                    }
                    break;
                case '?':
                    s.append(".");
                    break;
                // escape special regexp-characters
                case '(': case ')': case '[': case ']': case '$':
                case '^': case '.': case '{': case '}': case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
            prevWasStar = c == '*';
        }
        s.append('$');
        return(s.toString());
    }

}
