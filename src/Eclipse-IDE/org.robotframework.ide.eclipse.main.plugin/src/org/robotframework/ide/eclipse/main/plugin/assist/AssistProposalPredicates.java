/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.assist;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Optional;

import org.rf.ide.core.libraries.LibrarySpecification;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordCall;

public class AssistProposalPredicates {

    public static <T> AssistProposalPredicate<T> alwaysTrue() {
        return any -> true;
    }

    public static <T> AssistProposalPredicate<T> alwaysFalse() {
        return any -> false;
    }

    public static AssistProposalPredicate<String> globalVariablePredicate(final RobotElement element) {
        return globalVarName -> {

            if (newHashSet("${TEST_NAME}", "@{TEST_TAGS}", "${TEST_DOCUMENTATION}").contains(globalVarName)) {
                // those are only available inside test case
                return element instanceof RobotCase || element.getParent() instanceof RobotCase;

            } else if (newHashSet("${SUITE_STATUS}", "${SUITE_MESSAGE}").contains(globalVarName)) {
                // those are only available for suite teardown
                return element instanceof RobotKeywordCall
                        && ((RobotKeywordCall) element).getLinkedElement().getModelType() == ModelType.SUITE_TEARDOWN;

            } else if (newHashSet("${TEST_STATUS}", "${TEST_MESSAGE}").contains(globalVarName)) {
                // those are only available for test teardown
                return element instanceof RobotKeywordCall && (((RobotKeywordCall) element).getLinkedElement()
                        .getModelType() == ModelType.TEST_CASE_TEARDOWN
                        || ((RobotKeywordCall) element).getLinkedElement()
                                .getModelType() == ModelType.SUITE_TEST_TEARDOWN);

            } else if (newHashSet("${KEYWORD_STATUS}", "${KEYWORD_MESSAGE}").contains(globalVarName)) {
                // those are only available for keyword teardown
                return element instanceof RobotKeywordCall && ((RobotKeywordCall) element).getLinkedElement()
                        .getModelType() == ModelType.USER_KEYWORD_TEARDOWN;
            }
            return true;
        };
    }

    public static AssistProposalPredicate<LibrarySpecification> reservedLibraryPredicate() {
        return spec -> spec.getDescriptor().isReferencedLibrary() || !spec.getName().equalsIgnoreCase("reserved");
    }

    public static AssistProposalPredicate<String> gherkinReservedWordsPredicate(final int cellIndex) {
        return reservedWord -> cellIndex == 1 && GherkinReservedWordProposals.GHERKIN_ELEMENTS.contains(reservedWord);
    }

    public static AssistProposalPredicate<String> forLoopReservedWordsPredicate(final int cellIndex,
            final Optional<RobotToken> firstTokenInLine) {
        return reservedWord -> {
            if (ForLoopReservedWordsProposals.FOR_LOOP_1.equals(reservedWord)
                    || ForLoopReservedWordsProposals.NEW_FOR_LOOP_LITERALS.contains(reservedWord)) {
                // we're in 2nd cell
                return cellIndex == 1;

            } else {
                // line starts with :FOR and we're in at least 4th cell
                return cellIndex >= 3 && firstTokenInLine.isPresent()
                        && firstTokenInLine.get().getTypes().contains(RobotTokenType.FOR_TOKEN);
            }
        };
    }

    public static AssistProposalPredicate<String> withNamePredicate(final int cellIndex) {
        return withName -> cellIndex >= 2 && RedWithNameProposals.WITH_NAME.equals(withName);
    }
}
