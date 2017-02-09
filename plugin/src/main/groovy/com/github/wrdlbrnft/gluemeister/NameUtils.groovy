package com.github.wrdlbrnft.gluemeister
/**
 *  Created with IntelliJ IDEA<br>
 *  User: Xaver<br>
 *  Date: 28/01/2017
 */
class NameUtils {

    static String toUpperCase(String name) {
        if (name == null) {
            return null
        }

        if (name.isEmpty()) {
            return name
        }

        return name.substring(0, 1).toUpperCase() + name.substring(1)
    }

    static String createVariantTaskName(String prefix, variant) {
        return prefix + toUpperCase(variant.name)
    }
}
