/*
 * Copyright 2018 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.libraries;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.rf.ide.core.environment.RobotRuntimeEnvironment;

public final class Documentation {

    private final DocFormat format;

    private final String rawDocumentation;

    private final Collection<String> localSymbols;

    public Documentation(final DocFormat format, final String rawDocumentation) {
        this(format, rawDocumentation, Collections.emptySet());
    }

    public Documentation(final DocFormat format, final String rawDocumentation, final Collection<String> localSymbols) {
        this.format = format;
        this.rawDocumentation = rawDocumentation;
        this.localSymbols = localSymbols;
    }

    public DocFormat getFormat() {
        return format;
    }

    public String getRawDocumentation() {
        return rawDocumentation;
    }

    public Collection<String> getLocalSymbols() {
        return localSymbols;
    }

    public String provideFormattedDocumentation(final RobotRuntimeEnvironment env) {
        return env.createHtmlDoc(rawDocumentation, format);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj != null && obj.getClass() == Documentation.class) {
            final Documentation that = (Documentation) obj;
            return Objects.equals(this.format, that.format)
                    && Objects.equals(this.rawDocumentation, that.rawDocumentation);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, rawDocumentation);
    }

    public static enum DocFormat {
        ROBOT,
        HTML,
        REST,
        TEXT
    }
}