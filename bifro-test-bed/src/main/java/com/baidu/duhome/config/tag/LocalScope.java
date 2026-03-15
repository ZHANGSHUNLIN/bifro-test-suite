package com.baidu.duhome.config.tag;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * 标记vertx的本地级别行为
 */
@Documented
@Retention(SOURCE)
public @interface LocalScope {
}
