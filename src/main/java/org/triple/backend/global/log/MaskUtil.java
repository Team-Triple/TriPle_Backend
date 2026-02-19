package org.triple.backend.global.log;

public class MaskUtil {
    public static String maskId(Long id) {
        if(id == null) return "null";
        String idStr = String.valueOf(id);
        if(idStr.length() <= 2) return "**";
        return idStr.charAt(0) + "*".repeat(idStr.length() - 2)
                + idStr.substring(idStr.length() - 1);
    }

    public static String maskString(String value) {
        if (value == null) return "null";
        if (value.isBlank()) return "(blank)";

        int len = value.length();
        if (len <= 2) return "*".repeat(len);
        if (len <= 8) {
            return value.charAt(0) + "*".repeat(len - 2) + value.charAt(len - 1);
        }
        return value.substring(0, 4) + "*".repeat(len - 8) + value.substring(len - 4);
    }
}
