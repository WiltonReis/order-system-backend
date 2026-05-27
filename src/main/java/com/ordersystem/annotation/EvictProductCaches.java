package com.ordersystem.annotation;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Caching(evict = {
        @CacheEvict(value = "products", allEntries = true),
        @CacheEvict(value = "products-paged", allEntries = true)
})
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EvictProductCaches {
}
