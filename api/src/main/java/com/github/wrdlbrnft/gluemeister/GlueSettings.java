package com.github.wrdlbrnft.gluemeister;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */
public @interface GlueSettings {
    CacheSetting cache() default CacheSetting.SINGLETON;
    ResolveSetting[] resolve() default {};
    String key() default "";
}
