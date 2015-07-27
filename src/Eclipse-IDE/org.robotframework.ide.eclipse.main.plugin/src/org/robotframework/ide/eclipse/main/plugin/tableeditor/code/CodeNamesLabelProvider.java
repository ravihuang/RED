package org.robotframework.ide.eclipse.main.plugin.tableeditor.code;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.Stylers;
import org.eclipse.jface.viewers.Stylers.DisposeNeededStyler;
import org.eclipse.jface.viewers.StylersDisposingLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.robotframework.ide.eclipse.main.plugin.RobotImages;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotDefinitionSetting;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordCall;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordDefinition;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.ElementAddingToken;

import com.google.common.base.Joiner;

class CodeNamesLabelProvider extends StylersDisposingLabelProvider {

    @Override
    public StyledString getStyledText(final Object element) {
        if (element instanceof RobotKeywordDefinition) {
            final DisposeNeededStyler styler = addDisposeNeededStyler(Stylers.withFontStyle(SWT.BOLD));
            return new StyledString(((RobotKeywordDefinition) element).getName(), styler);
        } else if (element instanceof RobotCase) {
            final DisposeNeededStyler styler = addDisposeNeededStyler(Stylers.withFontStyle(SWT.BOLD));
            return new StyledString(((RobotCase) element).getName(), styler);
        } else if (element instanceof RobotKeywordCall) {
            return new StyledString(((RobotKeywordCall) element).getName());
        } else if (element instanceof ElementAddingToken) {
            return ((ElementAddingToken) element).getStyledText();
        }
        return null;
    }

    @Override
    public Image getImage(final Object element) {
        if (element instanceof RobotKeywordDefinition) {
            return RobotImages.getUserKeywordImage().createImage();
        } else if (element instanceof RobotCase) {
            return RobotImages.getTestCaseImage().createImage();
        } else if (element instanceof ElementAddingToken) {
            return ((ElementAddingToken) element).getImage();
        }
        return null;
    }

    @Override
    public String getToolTipText(final Object element) {
        if (element instanceof RobotKeywordDefinition) {
            final RobotKeywordDefinition def = (RobotKeywordDefinition) element;

            final String arguments = getArguments(def);
            final String returnValue = getReturnValue(def);
            final String documentation = getDocumentation(def);
            
            return def.getName() + "\nArguments:\n  " + arguments + "\nReturns:\n  " + returnValue + documentation;

        } else if (element instanceof RobotElement) {
            return ((RobotElement) element).getName();
        }
        return null;
    }

    private String getDocumentation(final RobotKeywordDefinition def) {
        final RobotDefinitionSetting docSetting = def.getDocumentationSetting();
        if (docSetting == null) {
            return "";
        } else {
            return "\n\n" + docSetting.getArguments().get(0);
        }
    }

    private String getReturnValue(final RobotKeywordDefinition def) {
        final RobotDefinitionSetting returnValueSetting = def.getReturnValueSetting();
        if (returnValueSetting == null) {
            return "<none>";
        } else {
            return Joiner.on(' ').join(returnValueSetting.getArguments());
        }
    }

    private String getArguments(final RobotKeywordDefinition def) {
        final RobotDefinitionSetting argumentsSetting = def.getArgumentsSetting();
        if (argumentsSetting == null) {
            return "<none>";
        } else {
            return Joiner.on("\n  ").join(argumentsSetting.getArguments());
        }
    }

    @Override
    public Image getToolTipImage(final Object element) {
        if (element instanceof RobotElement) {
            return RobotImages.getTooltipImage().createImage();
        }
        return null;
    }
}
