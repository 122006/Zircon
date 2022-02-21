package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.util.IncorrectOperationException;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZrAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(ZrAnnotator.class.getName());

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getLanguage() != JavaLanguage.INSTANCE) return;
        if (element instanceof PsiPolyadicExpression && Arrays.stream(element.getChildren()).anyMatch(ZrElementUtil::isJavaStringLiteral)) {
            registerChange2SStringIntentionAction((PsiPolyadicExpression) element, holder);
            return;
        }
        if (ZrElementUtil.isJavaStringLiteral(element)) {
            if (!element.getText().startsWith( "\"" )) {
                registerChange2NormalIntentionAction(element, holder, element.getText());
            }
            return;
        }
        if (element instanceof PsiMethodCallExpressionImpl) {
            if (element.getFirstChild() instanceof PsiReferenceExpression
                    && element.getFirstChild().getText().replace(" ","").endsWith( "String.format" )) {
                registerChangeFromFormatIntentionAction(element, holder);
            }
            return;
        }
//        String.format(Locale.CHINA,"1%02d22",1+ 1,""+"",3);
    }

    private void registerChangeFromFormatIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        final PsiElement child = element.getChildren()[1];
        if (!(child instanceof PsiExpressionList)) return;
        final PsiExpressionList formatExpression = (PsiExpressionList) child;
        final PsiElement[] children = formatExpression.getChildren();
        if (children.length == 0) return;
        final List<PsiElement> collect = Arrays.stream(children).filter(a -> !(a instanceof PsiJavaToken || a instanceof PsiWhiteSpace)).collect(Collectors.toList());
        if (!ZrElementUtil.isJavaStringLiteral(collect.get(0))) {
            collect.remove(0);
        }
        if (!ZrElementUtil.isJavaStringLiteral(collect.get(0))) {
            // error format
            return;
        }

        String text = "f" + collect.get(0).getText();
        collect.remove(0);
        text = text.replace( "%n" , "\\n" ).replace( "$" , "\\$" );
        Matcher m = Pattern.compile( "%.*?[%bBhHsScCdoxXeEfgGaAtT]" ).matcher(text);
        final Iterator<PsiElement> iterator = collect.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        int lastIndex = 0;
        while (m.find()) {
            final int start = m.start();
            stringBuilder.append(text, lastIndex, start);
            String group = m.group(0);
            final String s;
            if (iterator.hasNext()) {
                if ( "%%".equals(group)) {
                    s = "%";
                } else if ( "%n".equals(group)) {
                    s = "\n";
                } else if ( "%s".equals(group)||"%d".equals(group)) {
                    final String replace = iterator.next().getText()
                            .replaceAll( "\\s*([^\"\\s](?:\".*?\")|(?:[^\"\\s])[^\"\\s]*?)\\s*" , "$1" )
                            .replace( "\n" , "" );
                    s = "${" + replace + "}";
                } else {
                    final String replace = iterator.next().getText()
                            .replaceAll( "\\s*([^\"\\s](?:\".*?\")|(?:[^\"\\s])[^\"\\s]*?)\\s*" , "$1" )
                            .replace( "\n" , "" );
                    s = "${" + group + ":" + replace + "}";
                }
            } else {
                s = "${}";
            }
            stringBuilder.append(s);
            lastIndex = m.end();
        }
        stringBuilder.append(text, lastIndex, text.length());
        text = stringBuilder.toString();
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace 'String.format' with 'F-string'" )
                .range(element)
                .tooltip(text)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFix(new Change2FStringQuickFix(text, element))
                .create();
    }

    private void registerChange2NormalIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String text) {
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf( "\"" );
        if (endIndex == -1) {
            LOG.info(element +
                    "字符串前缀无法识别: " + text);
            return;
        }
        if (endIndex == 0) {
            return;
        }
        String prefix = text.substring(0, endIndex).replace( "\\" , "" );
        if (prefix.contains( "\\" )) return;
        Formatter formatter = allFormatters.stream()
                .filter(a -> a.prefix().equals(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            LOG.info( "未识别的字符串前缀: " + text);
            return;
        }
        final ZrStringModel model = formatter.build(text);
        List<StringRange> build = model.getList();
        String printOut = formatter.printOut(build, text);
        if (element.getParent() instanceof PsiExpressionList) {
            if (printOut.startsWith( "(" ) && printOut.endsWith( ")" )) {
                printOut = printOut.substring(1, printOut.length() - 1);
            }
        }
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace with normal string" )
                .range(element)
                .tooltip(printOut)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFix(new Change2NormalStringQuickFix(printOut, element))
                .create();
        if (element.getTextOffset() != 0)
            holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Fold line string code" )
                    .range(element)
                    .tooltip(printOut)
                    .highlightType(ProblemHighlightType.INFORMATION)
                    .withFix(new FoldCodeQuickFix(element, element.getTextOffset()))
                    .create();

    }

    private void registerChange2SStringIntentionAction(@NotNull PsiPolyadicExpression element, @NotNull AnnotationHolder holder) {
        ProblemHighlightType information = ProblemHighlightType.INFORMATION;
        HighlightSeverity severity = HighlightSeverity.INFORMATION;
        StringBuilder printOut = new StringBuilder( "$\"" );
        List<PsiElement> collect = Arrays.stream(element.getChildren())
                .filter(a -> !(a instanceof PsiWhiteSpace
                        || (a instanceof PsiJavaToken && ((PsiJavaToken) a).getTokenType() == JavaTokenType.PLUS)
                        || (Objects.equals(a.getText(), "\"\"" ))))
                .collect(Collectors.toList());
        if (collect.size() == 1) return;
        List<PsiElement> firstCollect = new ArrayList<>();
        for (PsiElement psiElement : collect) {
            if (!ZrElementUtil.isJavaStringLiteral(psiElement)) {
                firstCollect.add(psiElement);
            } else {
                break;
            }
        }
        if (!firstCollect.isEmpty()) {
            printOut.append( "${" );
            printOut.append(firstCollect.stream().map(PsiElement::getText).collect(Collectors.joining( "+" )));
            printOut.append( "}" );
        }
        List<PsiElement> collect1 = collect.stream().skip(firstCollect.size()).collect(Collectors.toList());
        for (int i = 0; i < collect1.size(); i++) {
            PsiElement item = collect1.get(i);
            String itemText = item.getText();
            if (ZrElementUtil.isJavaStringLiteral(item)) {
                if (itemText.startsWith( "\"" )) {
                    printOut.append(itemText, 1, itemText.length() - 1);
                } else {
                    printOut.append(itemText, itemText.indexOf( "\"" ) + 1, itemText.length() - 1);
                }
            } else {
                int appendType = 0;
                if (itemText.matches( "[A-Za-z_\\u4e00-\\u9fa5$]{1}[0-9A-Za-z_\\u4e00-\\u9fa5$().]+" )) {
                    if (i + 1 < collect1.size()) {
                        PsiElement nextItem = collect1.get(i + 1);
                        if (ZrElementUtil.isJavaStringLiteral(nextItem)) {
                            if (nextItem.getText().matches( "\"[0-9A-Za-z_\\u4e00-\\u9fa5$.]+.*" )) {
                                appendType = 1;
                            } else {
                                appendType = 0;
                            }
                        } else {
                            appendType = 1;
                        }
                    } else {
                        appendType = 0;
                    }
                } else {
                    appendType = 1;
                }
                if (item instanceof PsiParenthesizedExpression) {
                    if (itemText.length() <= 2) continue;
                    itemText = itemText.substring(1, itemText.length() - 1).trim();
                }
                final String replace = itemText.replaceAll( "([^\\\\])'" , "$1\\'" )
                        .replaceAll( "([^\\\\])\"" , "$1'" )
                        .replace( "\n" , "" )
                        .replace( "\r" , "" );
                if (appendType == 1) {
                    printOut.append( "${" );
                    printOut.append(replace);
                    printOut.append( "}" );
                } else {
                    printOut.append( "$" );
                    printOut.append(replace);
                }
            }
        }
        printOut.append( "\"" );
//            if( collect.stream().filter(a->(!ZrElementUtil.isJavaStringLiteral(a))).allMatch(a->a.getText().length()<10)){
//                information=ProblemHighlightType.INFORMATION;
//                severity=HighlightSeverity.INFORMATION;
//            }
        String text = printOut.toString();
        holder.newAnnotation(severity, "[ZrString]: Replace '+' with '$-string'" )
                .range(element)
                .tooltip(text)
                .highlightType(information)
                .withFix(new Change2SStringQuickFix(text, element))
                .create();
    }

    private static class Change2NormalStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2NormalStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace with normal string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
//            CodeStyleManager.getInstance(project).reformat(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private static class Change2SStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2SStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace with $-string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private static class Change2FStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2FStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace 'String.format' with F-string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private class FoldCodeQuickFix implements IntentionAction {
        private PsiElement element;
        private int textOffset;

        public FoldCodeQuickFix(@NotNull PsiElement element, int textOffset) {
            this.element = element;
            this.textOffset = textOffset;
        }

        @Override
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Fold line string code";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            if (editor instanceof EditorEx) {
                final FoldingModelEx foldingModel = ((EditorEx) editor).getFoldingModel();
                foldingModel.runBatchFoldingOperation(() -> {
                    final int line = editor.getDocument().getLineNumber(textOffset);
                    FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
                    if (region != null) region.setExpanded(false);
                });
            }
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}

