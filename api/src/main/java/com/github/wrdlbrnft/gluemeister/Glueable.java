package com.github.wrdlbrnft.gluemeister;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.CLASS)
public @interface Glueable {
    String key() default "";
    boolean enabled() default true;
}
