/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.i18n;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * WebFlux filter that sets the locale from the Accept-Language request header.
 * Defaults to Simplified Chinese if the header is absent.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocaleFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String acceptLanguage = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE);
        Locale locale = StringUtils.hasText(acceptLanguage)
                ? Locale.forLanguageTag(acceptLanguage.split(",")[0].trim())
                : Locale.SIMPLIFIED_CHINESE;
        LocaleContextHolder.setLocale(locale);
        return chain.filter(exchange).doFinally(s -> LocaleContextHolder.resetLocaleContext());
    }
}
