/*
 * Copyright 2018 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.libraries;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.io.Files;

public class LibrarySpecificationReaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testReadingIncorrectFile() throws Exception {
        assertThat(LibrarySpecificationReader.readSpecification(null)).isNotPresent();
        assertThat(LibrarySpecificationReader.readSpecification(temporaryFolder.newFolder("folder"))).isNotPresent();
        assertThat(LibrarySpecificationReader.readSpecification(new File("not_existing_file"))).isNotPresent();
        assertThat(LibrarySpecificationReader.readSpecification(temporaryFolder.newFile("file"))).isNotPresent();
    }

    @Test
    public void testReadingCorrectFile() throws Exception {
        final File file = temporaryFolder.newFile("libspec");
        final String content = "<keywordspec name=\"TestLib\" format=\"ROBOT\">"
                + "<version>1.0</version><scope>global</scope>"
                + "<doc>Documentation for test library ``TestLib``.</doc>"
                + "<kw name=\"Some Keyword\"><arguments><arg>a</arg><arg>b</arg></arguments><doc></doc></kw>"
                + "</keywordspec>";
        Files.write(content.getBytes(), file);
        assertThat(LibrarySpecificationReader.readSpecification(file)).hasValueSatisfying(spec -> {
            assertThat(spec.getConstructor()).isNull();
            assertThat(spec.getDescriptor()).isNull();
            assertThat(spec.isModified()).isFalse();
            assertThat(spec.getName()).isEqualTo("TestLib");
            assertThat(spec.getFormat()).isEqualTo("ROBOT");
            assertThat(spec.getVersion()).isEqualTo("1.0");
            assertThat(spec.getScope()).isEqualTo("global");
            assertThat(spec.getDocumentation()).isEqualTo("Documentation for test library ``TestLib``.");
            assertThat(spec.getKeywordNames()).containsExactly("Some Keyword");
        });
    }

}
