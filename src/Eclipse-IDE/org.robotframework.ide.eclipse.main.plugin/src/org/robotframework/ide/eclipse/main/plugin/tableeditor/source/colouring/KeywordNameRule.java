/*
* Copyright 2016 Nokia Solutions and Networks
* Licensed under the Apache License, Version 2.0,
* see license.txt file for details.
*/
package org.robotframework.ide.eclipse.main.plugin.tableeditor.source.colouring;

import java.util.List;
import java.util.Optional;

import org.eclipse.jface.text.rules.IToken;
import org.rf.ide.core.testdata.model.table.exec.descs.VariableExtractor;
import org.rf.ide.core.testdata.model.table.exec.descs.ast.mapping.NonEnvironmentDeclarationMapper;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;

public class KeywordNameRule extends VariableUsageRule {

    public KeywordNameRule(final IToken nameToken, final IToken embeddedVariablesToken) {
        super(embeddedVariablesToken, nameToken);
    }

    @Override
    public Optional<PositionedTextToken> evaluate(final IRobotLineElement token, final int offsetInToken,
            final List<RobotLine> context) {
        final IRobotTokenType type = token.getTypes().get(0);

        if (type == RobotTokenType.KEYWORD_NAME) {
            final Optional<PositionedTextToken> evaluated = super.evaluate(token, offsetInToken, context);
            if (evaluated.isPresent()) {
                return evaluated;
            }

            return Optional.of(new PositionedTextToken(nonVarToken, token.getStartOffset(), token.getText().length()));
        }
        return Optional.empty();
    }

    @Override
    protected VariableExtractor createVariableExtractor() {
        return new VariableExtractor(new NonEnvironmentDeclarationMapper());
    }
}
