/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.tableeditor.cases;

import org.eclipse.nebula.widgets.nattable.data.IRowDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.IConfigLabelAccumulator;
import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCodeHoldingElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotDefinitionSetting;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordCall;
import org.robotframework.ide.eclipse.main.plugin.model.RobotTask;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.TableConfigurationLabels;

public class CasesElementsLabelAccumulator implements IConfigLabelAccumulator {

    public static final String CASE_CONFIG_LABEL = "CASE";

    public static final String CASE_WITH_TEMPLATE_CONFIG_LABEL = "TEMPLATED_CASE";

    public static final String CASE_SETTING_CONFIG_LABEL = "CASE_SETTING";

    public static final String CASE_CALL_CONFIG_LABEL = "CASE_CALL";

    private final IRowDataProvider<Object> dataProvider;

    public CasesElementsLabelAccumulator(final IRowDataProvider<Object> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public void accumulateConfigLabels(final LabelStack configLabels, final int columnPosition, final int rowPosition) {
        final Object rowObject = dataProvider.getRowObject(rowPosition);

        if (columnPosition == 0) {
            if (rowObject instanceof RobotDefinitionSetting) {
                configLabels.addLabel(CASE_SETTING_CONFIG_LABEL);

            } else if (rowObject instanceof RobotKeywordCall) {
                configLabels.addLabel(CASE_CALL_CONFIG_LABEL);

                final AModelElement<?> linkedElement = ((RobotKeywordCall) rowObject).getLinkedElement();
                final boolean isForLoopContinuation = linkedElement.getElementTokens()
                        .stream()
                        .findFirst()
                        .filter(token -> token.getTypes().contains(RobotTokenType.FOR_WITH_END_CONTINUATION))
                        .isPresent();
                if (isForLoopContinuation) {
                    configLabels.addLabelOnTop(TableConfigurationLabels.CELL_NOT_EDITABLE_LABEL);
                }

            } else if (rowObject instanceof RobotCodeHoldingElement<?>) {
                final RobotCodeHoldingElement<?> holder = (RobotCodeHoldingElement<?>) rowObject;
                if (holder.getTemplateInUse().isPresent()) {
                    configLabels.addLabel(CASE_WITH_TEMPLATE_CONFIG_LABEL);
                } else {
                    configLabels.addLabel(CASE_CONFIG_LABEL);
                }
            }
        } else if (columnPosition > 0) {
            if (columnPosition > 1 && rowObject instanceof RobotDefinitionSetting
                    && ((RobotDefinitionSetting) rowObject).isDocumentation()) {

                configLabels.addLabelOnTop(TableConfigurationLabels.CELL_NOT_EDITABLE_LABEL);

            } else if (rowObject instanceof RobotCase || rowObject instanceof RobotTask) {
                configLabels.addLabelOnTop(TableConfigurationLabels.CELL_NOT_EDITABLE_LABEL);
            }
        }
    }
}
