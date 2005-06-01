package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OptionGroup;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeStyleIndentAndBracesPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleIndentAndBracesPanel");

  private static final String[] BRACE_PLACEMENT_OPTIONS = new String[]{
    "End of line",
    "Next line if wrapped",
    "Next line",
    "Next line shifted",
    "Next line shifted2"
  };

  private static final int[] BRACE_PLACEMENT_VALUES = new int[] {
    CodeStyleSettings.END_OF_LINE,
    CodeStyleSettings.NEXT_LINE_IF_WRAPPED,
    CodeStyleSettings.NEXT_LINE,
    CodeStyleSettings.NEXT_LINE_SHIFTED,
    CodeStyleSettings.NEXT_LINE_SHIFTED2
  };

  private static final String[] BRACE_FORCE_OPTIONS = new String[]{
    "Do not force",
    "When multiline",
    "Always"
  };

  private static final int[] BRACE_FORCE_VALUES = new int[]{
    CodeStyleSettings.DO_NOT_FORCE,
    CodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CodeStyleSettings.FORCE_BRACES_ALWAYS
  };

  private JComboBox myClassDeclarationCombo = new JComboBox();
  private JComboBox myMethodDeclarationCombo = new JComboBox();
  private JComboBox myOtherCombo = new JComboBox();

  private JCheckBox myCbElseOnNewline;
  private JCheckBox myCbWhileOnNewline;
  private JCheckBox myCbCatchOnNewline;
  private JCheckBox myCbFinallyOnNewline;

  private JCheckBox myCbSpecialElseIfTreatment;
  private JCheckBox myCbIndentCaseFromSwitch;


  private JComboBox myIfForceCombo;
  private JComboBox myForForceCombo;
  private JComboBox myWhileForceCombo;
  private JComboBox myDoWhileForceCombo;

  private JCheckBox myAlignDeclarationParameters;
  private JCheckBox myAlignCallParameters;
  private JCheckBox myAlignExtendsList;
  private JCheckBox myAlignForStatement;
  private JCheckBox myAlignThrowsList;
  private JCheckBox myAlignParenthesizedExpression;
  private JCheckBox myAlignBinaryExpression;
  private JCheckBox myAlignTernaryExpression;
  private JCheckBox myAlignAssignment;
  private JCheckBox myAlignArrayInitializerExpression;

  private Editor myEditor;
  private boolean toUpdatePreview = true;

  private CodeStyleSettings mySettings;

  private JCheckBox myKeepLineBreaks;
  private JCheckBox myKeepCommentAtFirstColumn;
  private JCheckBox myKeepMethodsInOneLine;
  private JCheckBox myKeepSimpleBlocksInOneLine;
  private JCheckBox myKeepControlStatementInOneLine;

  public CodeStyleIndentAndBracesPanel(CodeStyleSettings settings) {
    super(new GridBagLayout());
    mySettings = settings;

    add(createKeepWhenReformatingPanel(),
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));

    add(createBracesPanel(),
        new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));

    add(createAlignmentsPanel(),
        new GridBagConstraints(1, 0, 1, 2, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                               new Insets(0, 4, 0, 4), 0, 0));

    add(createPlaceOnNewLinePanel(),
        new GridBagConstraints(1, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));
    add(createForceBracesPanel(),
        new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                               new Insets(0, 4, 0, 4), 0, 0));

    add(new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(1, 1);
      }
    }, new GridBagConstraints(0, 3, 2, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.NONE,
                              new Insets(0, 0, 0, 0), 0, 0));

    add(createPreviewPanel(),
        new GridBagConstraints(2, 0, 1, 4, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 4), 0, 0));
  }

  private Component createKeepWhenReformatingPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep When Reformatting");

    myKeepLineBreaks = createCheckBox("Line breaks");
    optionGroup.add(myKeepLineBreaks);

    myKeepCommentAtFirstColumn = createCheckBox("Comment at first column");
    optionGroup.add(myKeepCommentAtFirstColumn);

    myKeepMethodsInOneLine = createCheckBox("Simple methods in one line");
    optionGroup.add(myKeepMethodsInOneLine);

    myKeepSimpleBlocksInOneLine = createCheckBox("Simple blocks in one line");
    optionGroup.add(myKeepSimpleBlocksInOneLine);

    myKeepControlStatementInOneLine = createCheckBox("Control statement in one line");
    optionGroup.add(myKeepControlStatementInOneLine);


    return optionGroup.createPanel();

  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private JPanel createBracesPanel() {
    OptionGroup optionGroup = new OptionGroup("Braces Placement");

    myClassDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Class declaration:"), myClassDeclarationCombo);

    myMethodDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Method declaration:"), myMethodDeclarationCombo);

    myOtherCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Other:"), myOtherCombo);

    myCbSpecialElseIfTreatment = createCheckBox("Special \"else if\" treatment");
    optionGroup.add(myCbSpecialElseIfTreatment);

    myCbIndentCaseFromSwitch = createCheckBox("Indent \"case\" from \"switch\"");
    optionGroup.add(myCbIndentCaseFromSwitch);

    return optionGroup.createPanel();
  }

  private JPanel createForceBracesPanel() {
    OptionGroup optionGroup = new OptionGroup("Force Braces");

    myIfForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("if ():"), myIfForceCombo);

    myForForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("for ():"), myForForceCombo);

    myWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("while ():"), myWhileForceCombo);

    myDoWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("do ... while():"), myDoWhileForceCombo);

    return optionGroup.createPanel();
  }

  private JPanel createAlignmentsPanel() {
    OptionGroup optionGroup = new OptionGroup("Align when multiline");

    myAlignDeclarationParameters = createCheckBox("Method parameters");
    optionGroup.add(myAlignDeclarationParameters);

    myAlignCallParameters = createCheckBox("Call arguments");
    optionGroup.add(myAlignCallParameters);

    myAlignExtendsList = createCheckBox("Extends list");
    optionGroup.add(myAlignExtendsList);

    myAlignThrowsList = createCheckBox("Throws list");
    optionGroup.add(myAlignThrowsList);

    myAlignParenthesizedExpression = createCheckBox("Parenthesized expression");
    optionGroup.add(myAlignParenthesizedExpression);

    myAlignBinaryExpression = createCheckBox("Binary operation");
    optionGroup.add(myAlignBinaryExpression);

    myAlignTernaryExpression = createCheckBox("Ternary operation");
    optionGroup.add(myAlignTernaryExpression);

    myAlignAssignment = createCheckBox("Assignments");
    optionGroup.add(myAlignAssignment);

    myAlignForStatement = createCheckBox("For statement");
    optionGroup.add(myAlignForStatement);

    myAlignArrayInitializerExpression = createCheckBox("Array initializer");
    optionGroup.add(myAlignArrayInitializerExpression);

    return optionGroup.createPanel();
  }

  private JPanel createPlaceOnNewLinePanel() {
    OptionGroup optionGroup = new OptionGroup("Place on New Line");

    myCbElseOnNewline = createCheckBox("\"else\" on new line");
    optionGroup.add(myCbElseOnNewline);

    myCbWhileOnNewline = createCheckBox("\"while\" on new line");
    optionGroup.add(myCbWhileOnNewline);

    myCbCatchOnNewline = createCheckBox("\"catch\" on new line");
    optionGroup.add(myCbCatchOnNewline);

    myCbFinallyOnNewline = createCheckBox("\"finally\" on new line");
    optionGroup.add(myCbFinallyOnNewline);

    return optionGroup.createPanel();
  }

  private JCheckBox createCheckBox(String text) {
    JCheckBox checkBox = new JCheckBox(text);
    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });
    return checkBox;
  }

  private JComboBox createForceBracesCombo() {
    JComboBox comboBox = new JComboBox(BRACE_FORCE_OPTIONS);
    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });
    return comboBox;
  }

  private static void setForceBracesComboValue(JComboBox comboBox, int value) {
    for (int i = 0; i < BRACE_FORCE_VALUES.length; i++) {
      int forceValue = BRACE_FORCE_VALUES[i];
      if (forceValue == value) {
        comboBox.setSelectedItem(BRACE_FORCE_OPTIONS[i]);
      }
    }
  }

  private static int getForceBracesValue(JComboBox comboBox) {
    String selected = (String)comboBox.getSelectedItem();
    for (int i = 0; i < BRACE_FORCE_OPTIONS.length; i++) {
      String s = BRACE_FORCE_OPTIONS[i];
      if (s.equals(selected)) {
        return BRACE_FORCE_VALUES[i];
      }
    }
    return 0;
  }

  private JComboBox createBraceStyleCombo() {
    JComboBox comboBox = new JComboBox(BRACE_PLACEMENT_OPTIONS);
    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });
    return comboBox;
  }

  private static void setBraceStyleComboValue(JComboBox comboBox, int value) {
    for( int i = 0; i < BRACE_PLACEMENT_OPTIONS.length; i++ )
      if( BRACE_PLACEMENT_VALUES[i] == value ) {
        comboBox.setSelectedItem(BRACE_PLACEMENT_OPTIONS[i]);
        return;
      }
  }

  private static int getBraceComboValue(JComboBox comboBox) {
    Object item = comboBox.getSelectedItem();
    for( int i = 1; i < BRACE_PLACEMENT_OPTIONS.length; i++ )
      if( BRACE_PLACEMENT_OPTIONS[i].equals(item) )
        return BRACE_PLACEMENT_VALUES[i];
    return BRACE_PLACEMENT_VALUES[0];
  }

  private JPanel createPreviewPanel() {
    myEditor = createEditor();
    JPanel panel = new JPanel(new BorderLayout()) {
      public Dimension getPreferredSize() {
        return new Dimension(350, 0);
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder("Preview"));
    panel.setLayout(new BorderLayout());
    panel.add(myEditor.getComponent(), BorderLayout.CENTER);
    panel.setMinimumSize(new Dimension(350, 0));
    return panel;
  }

  private static Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument("");
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.setHighlighter(HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST));
    return editor;
  }

  private void update() {
    if (!toUpdatePreview) {
      return;
    }
    final String text =
      "public class Foo {\n" +
      "  public int[] X = new int[] { 1, 3, 5\n" +
      "  7, 9, 11};\n" +
      "  public void foo(boolean a, int x,\n" +
      "    int y, int z) {\n" +
      "    label1: do {\n" +
      "      try {\n" +
      "        if(x > 0) {\n" +
      "          int someVariable = a ? \n" +
      "             x : \n" +
      "             y;\n" +
      "        } else if (x < 0) {\n" +
      "          int someVariable = (y +\n" +
      "          z\n" +
      "          );\n" +
      "          someVariable = x = \n" +
      "          x +\n" +
      "          y;\n" +
      "        } else {\n" +
      "          label2:\n" +
      "          for (int i = 0;\n" +
      "               i < 5;\n" +
      "               i++) doSomething(i);\n" +
      "        }\n" +
      "        switch(a) {\n" +
      "          case 0: \n" +
      "           doCase0();\n" +
      "           break;\n" +
      "          default: \n" +
      "           doDefault();\n" +
      "        }\n" +
      "      }\n" +
      "      catch(Exception e) {\n" +
      "        processException(e.getMessage(),\n" +
      "          x + y, z, a);\n" +
      "      }\n" +
      "      finally {\n" +
      "        processFinally();\n" +
      "      }\n" +
      "    }while(true);\n" +
      "\n" +
      "    if (2 < 3) return;\n" +
      "    if (3 < 4)\n" +
      "       return;\n" +
      "    do x++ while (x < 10000);\n" +
      "    while (x < 50000) x++;\n" +
      "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
      "  }\n" +
      "  private class InnerClass implements I1,\n" +
      "  I2 {\n" +
      "    public void bar() throws E1,\n" +
      "     E2 {\n" +
      "    }\n" +
      "  }\n" +
      "}";

    final Project project = ProjectManagerEx.getInstanceEx().getDefaultProject();
    final PsiManager manager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiElementFactory factory = manager.getElementFactory();
        try {
          PsiFile psiFile = factory.createFileFromText("a.java", text);

          CodeStyleSettings savedCodeStyleSettings = mySettings;
          mySettings = (CodeStyleSettings)mySettings.clone();
          apply();
          mySettings.KEEP_LINE_BREAKS = true;

          CodeStyleSettingsManager.getInstance(project).setTemporarySettings(mySettings);
          CodeStyleManager.getInstance(project).reformat(psiFile);
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();

          myEditor.getSettings().setTabSize(mySettings.getTabSize(StdFileTypes.JAVA));
          mySettings = savedCodeStyleSettings;

          Document document = myEditor.getDocument();
          document.replaceString(0, document.getTextLength(), psiFile.getText());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void reset() {
    toUpdatePreview = false;
    myCbElseOnNewline.setSelected(mySettings.ELSE_ON_NEW_LINE);
    myCbWhileOnNewline.setSelected(mySettings.WHILE_ON_NEW_LINE);
    myCbCatchOnNewline.setSelected(mySettings.CATCH_ON_NEW_LINE);
    myCbFinallyOnNewline.setSelected(mySettings.FINALLY_ON_NEW_LINE);

    myCbSpecialElseIfTreatment.setSelected(mySettings.SPECIAL_ELSE_IF_TREATMENT);
    myCbIndentCaseFromSwitch.setSelected(mySettings.INDENT_CASE_FROM_SWITCH);

    setBraceStyleComboValue(myOtherCombo, mySettings.BRACE_STYLE);
    setBraceStyleComboValue(myClassDeclarationCombo, mySettings.CLASS_BRACE_STYLE);
    setBraceStyleComboValue(myMethodDeclarationCombo, mySettings.METHOD_BRACE_STYLE);

    myAlignAssignment.setSelected(mySettings.ALIGN_MULTILINE_ASSIGNMENT);
    myAlignBinaryExpression.setSelected(mySettings.ALIGN_MULTILINE_BINARY_OPERATION);
    myAlignCallParameters.setSelected(mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    myAlignDeclarationParameters.setSelected(mySettings.ALIGN_MULTILINE_PARAMETERS);
    myAlignExtendsList.setSelected(mySettings.ALIGN_MULTILINE_EXTENDS_LIST);
    myAlignForStatement.setSelected(mySettings.ALIGN_MULTILINE_FOR);
    myAlignParenthesizedExpression.setSelected(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    myAlignTernaryExpression.setSelected(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION);
    myAlignThrowsList.setSelected(mySettings.ALIGN_MULTILINE_THROWS_LIST);
    myAlignArrayInitializerExpression.setSelected(mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    setForceBracesComboValue(myForForceCombo, mySettings.FOR_BRACE_FORCE);
    setForceBracesComboValue(myIfForceCombo, mySettings.IF_BRACE_FORCE);
    setForceBracesComboValue(myWhileForceCombo, mySettings.WHILE_BRACE_FORCE);
    setForceBracesComboValue(myDoWhileForceCombo, mySettings.DOWHILE_BRACE_FORCE);

    toUpdatePreview = true;

    myKeepLineBreaks.setSelected(mySettings.KEEP_LINE_BREAKS);
    myKeepCommentAtFirstColumn.setSelected(mySettings.KEEP_FIRST_COLUMN_COMMENT);
    myKeepControlStatementInOneLine.setSelected(mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    myKeepSimpleBlocksInOneLine.setSelected(mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    myKeepMethodsInOneLine.setSelected(mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);


    update();
  }

  public void apply() {
    mySettings.ELSE_ON_NEW_LINE = myCbElseOnNewline.isSelected();
    mySettings.WHILE_ON_NEW_LINE = myCbWhileOnNewline.isSelected();
    mySettings.CATCH_ON_NEW_LINE = myCbCatchOnNewline.isSelected();
    mySettings.FINALLY_ON_NEW_LINE = myCbFinallyOnNewline.isSelected();


    mySettings.SPECIAL_ELSE_IF_TREATMENT = myCbSpecialElseIfTreatment.isSelected();
    mySettings.INDENT_CASE_FROM_SWITCH = myCbIndentCaseFromSwitch.isSelected();

    mySettings.BRACE_STYLE = getBraceComboValue(myOtherCombo);
    mySettings.CLASS_BRACE_STYLE = getBraceComboValue(myClassDeclarationCombo);
    mySettings.METHOD_BRACE_STYLE = getBraceComboValue(myMethodDeclarationCombo);

    mySettings.ALIGN_MULTILINE_ASSIGNMENT = myAlignAssignment.isSelected();
    mySettings.ALIGN_MULTILINE_BINARY_OPERATION = myAlignBinaryExpression.isSelected();
    mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignCallParameters.isSelected();
    mySettings.ALIGN_MULTILINE_PARAMETERS = myAlignDeclarationParameters.isSelected();
    mySettings.ALIGN_MULTILINE_EXTENDS_LIST = myAlignExtendsList.isSelected();
    mySettings.ALIGN_MULTILINE_FOR = myAlignForStatement.isSelected();
    mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = myAlignParenthesizedExpression.isSelected();
    mySettings.ALIGN_MULTILINE_TERNARY_OPERATION = myAlignTernaryExpression.isSelected();
    mySettings.ALIGN_MULTILINE_THROWS_LIST = myAlignThrowsList.isSelected();
    mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = myAlignArrayInitializerExpression.isSelected();
//    mySettings.LABEL_INDENT =

    mySettings.FOR_BRACE_FORCE = getForceBracesValue(myForForceCombo);
    mySettings.IF_BRACE_FORCE = getForceBracesValue(myIfForceCombo);
    mySettings.WHILE_BRACE_FORCE = getForceBracesValue(myWhileForceCombo);
    mySettings.DOWHILE_BRACE_FORCE = getForceBracesValue(myDoWhileForceCombo);

    mySettings.KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    mySettings.KEEP_FIRST_COLUMN_COMMENT = myKeepCommentAtFirstColumn.isSelected();
    mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = myKeepControlStatementInOneLine.isSelected();
    mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = myKeepSimpleBlocksInOneLine.isSelected();
    mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = myKeepSimpleBlocksInOneLine.isSelected();

  }

  public boolean isModified() {
    boolean isModified;
    isModified = isModified(myCbElseOnNewline, mySettings.ELSE_ON_NEW_LINE);
    isModified |= isModified(myCbWhileOnNewline, mySettings.WHILE_ON_NEW_LINE);
    isModified |= isModified(myCbCatchOnNewline, mySettings.CATCH_ON_NEW_LINE);
    isModified |= isModified(myCbFinallyOnNewline, mySettings.FINALLY_ON_NEW_LINE);


    isModified |= isModified(myCbSpecialElseIfTreatment, mySettings.SPECIAL_ELSE_IF_TREATMENT);
    isModified |= isModified(myCbIndentCaseFromSwitch, mySettings.INDENT_CASE_FROM_SWITCH);


    isModified |= mySettings.BRACE_STYLE != getBraceComboValue(myOtherCombo);
    isModified |= mySettings.CLASS_BRACE_STYLE != getBraceComboValue(myClassDeclarationCombo);
    isModified |= mySettings.METHOD_BRACE_STYLE != getBraceComboValue(myMethodDeclarationCombo);

    isModified |= isModified(myAlignAssignment, mySettings.ALIGN_MULTILINE_ASSIGNMENT);
    isModified |= isModified(myAlignBinaryExpression, mySettings.ALIGN_MULTILINE_BINARY_OPERATION);
    isModified |= isModified(myAlignCallParameters, mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    isModified |= isModified(myAlignDeclarationParameters, mySettings.ALIGN_MULTILINE_PARAMETERS);
    isModified |= isModified(myAlignExtendsList, mySettings.ALIGN_MULTILINE_EXTENDS_LIST);
    isModified |= isModified(myAlignForStatement, mySettings.ALIGN_MULTILINE_FOR);
    isModified |= isModified(myAlignParenthesizedExpression, mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    isModified |= isModified(myAlignTernaryExpression, mySettings.ALIGN_MULTILINE_TERNARY_OPERATION);
    isModified |= isModified(myAlignThrowsList, mySettings.ALIGN_MULTILINE_THROWS_LIST);
    isModified |= isModified(myAlignArrayInitializerExpression, mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    isModified |= mySettings.FOR_BRACE_FORCE != getForceBracesValue(myForForceCombo);
    isModified |= mySettings.IF_BRACE_FORCE != getForceBracesValue(myIfForceCombo);
    isModified |= mySettings.WHILE_BRACE_FORCE != getForceBracesValue(myWhileForceCombo);
    isModified |= mySettings.DOWHILE_BRACE_FORCE != getForceBracesValue(myDoWhileForceCombo);

    isModified |= isModified(myKeepLineBreaks, mySettings.KEEP_LINE_BREAKS);
    isModified |= isModified(myKeepCommentAtFirstColumn, mySettings.KEEP_FIRST_COLUMN_COMMENT);
    isModified |= isModified(myKeepControlStatementInOneLine, mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    isModified |= isModified(myKeepSimpleBlocksInOneLine, mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    isModified |= isModified(myKeepMethodsInOneLine, mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);


    return isModified;
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }
}