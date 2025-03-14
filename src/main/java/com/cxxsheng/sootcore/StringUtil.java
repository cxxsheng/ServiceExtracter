package com.cxxsheng.sootcore;

import java.util.List;
import java.util.Set;

public class StringUtil {

    public static boolean listContains(List<String> hitStrings, List target) {
        for (Object o : target) {
            if (hitStrings.contains(o.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean listContains(Set<String> hitStrings, List target) {
        for (Object o : target) {
            if (hitStrings.contains(o.toString())) {
                return true;
            }
        }
        return false;
    }

}
