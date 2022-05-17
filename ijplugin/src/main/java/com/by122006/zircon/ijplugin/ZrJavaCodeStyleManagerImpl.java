package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.jsp.JspSpiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.impl.source.resolve.ResolveClassUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.NotNullList;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.tools.ant.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZrJavaCodeStyleManagerImpl extends JavaCodeStyleManagerImpl {
    private static final Logger LOG = Logger.getInstance(ZrJavaCodeStyleManagerImpl.class);

    public ZrJavaCodeStyleManagerImpl(Project project) {
        super(project);
    }

    @Override
    public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
        try {
            return prepareOptimizeImportsResult(file, __ -> true);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            LOG.error(e);
            LOG.error( "不支持的idea版本" );
            return super.prepareOptimizeImportsResult(file);
        }
    }

    @NotNull
    // returns list of (name, isImportStatic) pairs
    private static Collection<Pair<String, Boolean>> collectNamesToImport(@NotNull PsiJavaFile file, List<? super PsiElement> comments) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Set<Pair<String, Boolean>> names = new THashSet<>();

        final JspFile jspFile = JspPsiUtil.getJspFile(file);
        collectNamesToImport(names, comments, file, jspFile);
        if (jspFile != null) {
            try {
                 Class<?> aClass;
                try {
                    aClass = Class.forName("com.intellij.jsp.JspSpiUtil");
                } catch (ClassNotFoundException e) {
                    aClass = Class.forName("com.intellij.psi.jsp.JspSpiUtil");
                }
                final Method getIncludingFiles = aClass.getDeclaredMethod("getIncludingFiles", JspFile.class);
                final PsiFile[] invoke = (PsiFile[]) getIncludingFiles.invoke(null, jspFile);
                final Method getIncludedFiles = aClass.getDeclaredMethod("getIncludedFiles", JspFile.class);
                final PsiFile[] invoke2 = (PsiFile[]) getIncludedFiles.invoke(null, jspFile);
                PsiFile[] files = ArrayUtil.mergeArrays(invoke, invoke2);
                for (PsiFile includingFile : files) {
                    final PsiFile javaRoot = includingFile.getViewProvider().getPsi(JavaLanguage.INSTANCE);
                    if (javaRoot instanceof PsiJavaFile && file != javaRoot) {
                        collectNamesToImport(names, comments, (PsiJavaFile) javaRoot, jspFile);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        final Method addUnresolvedImportNames = ImportHelper.class.getDeclaredMethod( "addUnresolvedImportNames", Set.class, PsiJavaFile.class);
        addUnresolvedImportNames.setAccessible(true);
        addUnresolvedImportNames.invoke(null, names, file);
        return names;
    }

    /**
     * @param filter pretend some references do not exist so the corresponding imports may be deleted
     */
    @Nullable( "null means no need to replace the import list because they are the same" )
    public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file, @NotNull Predicate<? super Pair<String, Boolean>> filter) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        ImportHelper importHelper = new ImportHelper(JavaCodeStyleSettings.getInstance(file));
        JavaCodeStyleSettings mySettings = ReflectUtil.getField(importHelper, "mySettings" );
        PsiImportList oldList = file.getImportList();
        if (oldList == null) return null;

        // Java parser works in a way that comments may be included to the import list, e.g.:
        //     import a;
        //     /* comment */
        //     import b;
        // We want to preserve those comments then.
        List<PsiElement> nonImports = new NotNullList<>();
        // Note: this array may contain "<packageOrClassName>.*" for unresolved imports!
        final Collection<Pair<String, Boolean>> invoke = collectNamesToImport(file, nonImports);
        List<Pair<String, Boolean>> names =
                invoke
                        .stream()
                        .filter(filter)
                        .sorted(Comparator.comparing(o -> o.getFirst()))
                        .collect(Collectors.toList());

        List<Pair<String, Boolean>> resultList = ImportHelper.sortItemsAccordingToSettings(names, mySettings);

        final Map<String, Boolean> classesOrPackagesToImportOnDemand = new THashMap<>();
        ImportHelper.collectOnDemandImports(resultList, mySettings, classesOrPackagesToImportOnDemand);

        MultiMap<String, String> conflictingMemberNames = new MultiMap<>();
        for (Pair<String, Boolean> pair : resultList) {
            if (pair.second) {
                conflictingMemberNames.putValue(StringUtil.getShortName(pair.first), StringUtil.getPackageName(pair.first));
            }
        }

        for (String methodName : conflictingMemberNames.keySet()) {
            Collection<String> collection = conflictingMemberNames.get(methodName);
            if (!classesOrPackagesToImportOnDemand.keySet().containsAll(collection)) {
                for (String name : collection) {
                    classesOrPackagesToImportOnDemand.remove(name);
                }
            }
        }

        final Method findSingleImports = ImportHelper.class.getDeclaredMethod( "findSingleImports", PsiJavaFile.class, Collection.class, Set.class);
        findSingleImports.setAccessible(true);
        final Set<String> classesToUseSingle = (Set<String>) findSingleImports.invoke(null, file, resultList, classesOrPackagesToImportOnDemand.keySet());
        Set<String> toReimport = new THashSet<>();
        final Method calcClassesConflictingViaOnDemandImports = ImportHelper.class.getDeclaredMethod( "calcClassesConflictingViaOnDemandImports", PsiJavaFile.class, Map.class, GlobalSearchScope.class, Set.class);
        calcClassesConflictingViaOnDemandImports.setAccessible(true);
        calcClassesConflictingViaOnDemandImports.invoke(null, file, classesOrPackagesToImportOnDemand, file.getResolveScope(), toReimport);
        classesToUseSingle.addAll(toReimport);
        try {

            final Method buildImportListText = ImportHelper.class.getDeclaredMethod( "buildImportListText", List.class, Set.class, Set.class);
            buildImportListText.setAccessible(true);
            final StringBuilder text = (StringBuilder) buildImportListText.invoke(null, resultList, classesOrPackagesToImportOnDemand.keySet(), classesToUseSingle);
            for (PsiElement nonImport : nonImports) {
                text.append( "\n" ).append(nonImport.getText());
            }
            String ext = JavaFileType.INSTANCE.getDefaultExtension();
            PsiFileFactory factory = PsiFileFactory.getInstance(file.getProject());
            final PsiJavaFile dummyFile = (PsiJavaFile) factory.createFileFromText( "_Dummy_." + ext, JavaFileType.INSTANCE, text);
            CodeStyle.reformatWithFileContext(dummyFile, file);

            PsiImportList newImportList = dummyFile.getImportList();
            assert newImportList != null : dummyFile.getText();
            if (oldList.isReplaceEquivalent(newImportList)) return null;
            return newImportList;
        } catch (IncorrectOperationException e) {
            LOG.error(e);
            return null;
        }
    }

    private static void collectNamesToImport(@NotNull final Set<? super Pair<String, Boolean>> names,
                                             @NotNull List<? super PsiElement> comments,
                                             @NotNull final PsiJavaFile file,
                                             PsiFile context) {
        String packageName = file.getPackageName();

        final List<PsiFile> roots = file.getViewProvider().getAllFiles();
        for (PsiElement root : roots) {
            addNamesToImport(names, comments, root, packageName, context);
        }
    }

    private static void addNamesToImport(@NotNull Set<? super Pair<String, Boolean>> names,
                                         @NotNull List<? super PsiElement> comments,
                                         @NotNull PsiElement scope,
                                         @NotNull String thisPackageName,
                                         PsiFile context) {
        if (scope instanceof PsiImportList) return;

        final LinkedList<PsiElement> stack = new LinkedList<>();
        stack.add(scope);
        while (!stack.isEmpty()) {
            final PsiElement child = stack.removeFirst();
            if (child instanceof PsiImportList) {
                for (PsiElement element = child.getFirstChild(); element != null; element = element.getNextSibling()) {
                    ASTNode node = element.getNode();
                    if (node == null) {
                        continue;
                    }
                    IElementType elementType = node.getElementType();
                    if (!ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(elementType) &&
                            !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(elementType)) {
                        comments.add(element);
                    }
                }
                continue;
            }
            if (child instanceof PsiLiteralExpression) {
                final Formatter formatter = ZrUtil.checkPsiLiteralExpression((PsiLiteralExpression)child);
                if (formatter == null) continue;
                final ZrStringModel build = formatter.build(child.getText());
                final PsiElement[] psiElements = build.getList().stream().filter(a -> a.codeStyle == 1)
                        .map(a -> JavaPsiFacade
                                .getElementFactory(child.getProject())
                                .createExpressionFromText(a.stringVal.trim(), child)).toArray(PsiElement[]::new);
                ContainerUtil.addAll(stack,psiElements);
            } else
                ContainerUtil.addAll(stack, child.getChildren());

            for (final PsiReference reference : child.getReferences()) {
                JavaResolveResult resolveResult = HighlightVisitorImpl.resolveJavaReference(reference);
                if (resolveResult == null) continue;

                PsiJavaCodeReferenceElement referenceElement = null;
                if (reference instanceof PsiJavaReference) {
                    final PsiJavaReference javaReference = (PsiJavaReference) reference;
                    if (javaReference instanceof JavaClassReference && ((JavaClassReference) javaReference).getContextReference() != null)
                        continue;
                    referenceElement = null;
                    if (reference instanceof PsiJavaCodeReferenceElement) {
                        referenceElement = (PsiJavaCodeReferenceElement) child;
                        if (referenceElement.getQualifier() != null) {
                            continue;
                        }
                        if (reference instanceof PsiJavaCodeReferenceElementImpl
                                && ((PsiJavaCodeReferenceElementImpl) reference).getKindEnum(((PsiJavaCodeReferenceElementImpl) reference).getContainingFile()) == PsiJavaCodeReferenceElementImpl.Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
                            continue;
                        }
                    }
                }

                PsiElement refElement = resolveResult.getElement();

                PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
                if (!(currentFileResolveScope instanceof PsiImportStatementBase) && refElement != null) continue;
                if (context != null &&
                        refElement != null &&
                        (!currentFileResolveScope.isValid() ||
                                currentFileResolveScope instanceof JspxImportStatement &&
                                        context != ((JspxImportStatement) currentFileResolveScope).getDeclarationFile())) {
                    continue;
                }

                if (refElement == null && referenceElement != null) {
                    refElement = ResolveClassUtil.resolveClass(referenceElement, referenceElement.getContainingFile()); // might be uncomplete code
                }
                if (refElement == null) continue;

                if (referenceElement != null) {
                    if (currentFileResolveScope instanceof PsiImportStaticStatement) {
                        PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement) currentFileResolveScope;
                        String name = importStaticStatement.getImportReference().getCanonicalText();
                        if (importStaticStatement.isOnDemand()) {
                            String refName = referenceElement.getReferenceName();
                            if (refName != null) name = name + "." + refName;
                        }
                        names.add(Pair.create(name, Boolean.TRUE));
                        continue;
                    }
                }

                if (refElement instanceof PsiClass) {
                    String qName = ((PsiClass) refElement).getQualifiedName();
                    if (qName == null || hasPackage(qName, thisPackageName)) continue;
                    names.add(Pair.create(qName, Boolean.FALSE));
                }
            }
        }
    }

    static boolean hasPackage(@NotNull String className, @NotNull String packageName) {
        if (!className.startsWith(packageName)) return false;
        if (className.length() == packageName.length()) return false;
        if (!packageName.isEmpty() && className.charAt(packageName.length()) != '.') return false;
        return className.indexOf('.', packageName.length() + 1) < 0;
    }
}
