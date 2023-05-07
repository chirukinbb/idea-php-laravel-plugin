package chirukinbb.idea.laravel.view;

import chirukinbb.idea.laravel.LaravelIcons;
import chirukinbb.idea.laravel.LaravelProjectComponent;
import chirukinbb.idea.laravel.blade.BladePattern;
import chirukinbb.idea.laravel.blade.util.BladePsiUtil;
import chirukinbb.idea.laravel.blade.util.BladeTemplateUtil;
import chirukinbb.idea.laravel.util.PsiElementUtils;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.blade.psi.BladeTokenTypes;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import fr.adrienbrault.idea.symfony2plugin.util.ParameterBag;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class ViewReferences implements GotoCompletionRegistrar {

    private static MethodMatcher.CallToSignature[] VIEWS = new MethodMatcher.CallToSignature[] {
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "make"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "exists"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "alias"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "name"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "of"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "renderEach"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "callComposer"),
        new MethodMatcher.CallToSignature("\\Illuminate\\View\\Factory", "callCreator"),

        new MethodMatcher.CallToSignature("\\Illuminate\\Mail\\Mailer", "send"),
        new MethodMatcher.CallToSignature("\\Illuminate\\Mail\\Mailer", "plain"),
        new MethodMatcher.CallToSignature("\\Illuminate\\Mail\\Mailer", "queue"),
    };

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        /*
         * view('caret');
         * Factory::make('caret');
         */
        registrar.register(PlatformPatterns.psiElement().withParent(StringLiteralExpression.class).withLanguage(PhpLanguage.INSTANCE), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return null;
            }

            if(!PsiElementUtils.isFunctionReference(stringLiteral, "view", 0) &&
                MethodMatcher.getMatchedSignatureWithDepth(stringLiteral, VIEWS) == null) {
                return null;
            }

            return new ViewDirectiveCompletionProvider(stringLiteral);
        });

        /*
         * @each('view.name', $jobs, 'job')
         * @each('view.name', $jobs, 'job', 'view.empty')
         */
        registrar.register(BladePattern.getParameterDirectiveForElementType(BladeTokenTypes.EACH_DIRECTIVE), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return null;
            }

            ParameterBag parameterBag = PhpElementsUtil.getCurrentParameterIndex(stringLiteral);
            if(parameterBag == null || (parameterBag.getIndex() != 0 && parameterBag.getIndex() != 3)) {
                return null;
            }

            return new ViewDirectiveCompletionProvider(stringLiteral);
        });

        /*
         * @includeIf('view.name')
         * @component('view.name')
         */
        registrar.register(BladePattern.getParameterDirectiveForElementType(BladeTokenTypes.INCLUDE_IF_DIRECTIVE, BladeTokenTypes.COMPONENT_DIRECTIVE), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return null;
            }

            ParameterBag parameterBag = PhpElementsUtil.getCurrentParameterIndex(stringLiteral);
            if(parameterBag == null || parameterBag.getIndex() != 0) {
                return null;
            }

            return new ViewDirectiveCompletionProvider(stringLiteral);
        });

        /*
         * @includeWhen($boolean, 'view.name', ['some' => 'data'])
         */
        registrar.register(BladePattern.getParameterDirectiveForElementType(BladeTokenTypes.INCLUDE_WHEN_DIRECTIVE), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return null;
            }

            ParameterBag parameterBag = PhpElementsUtil.getCurrentParameterIndex(stringLiteral);
            if(parameterBag == null || parameterBag.getIndex() != 1) {
                return null;
            }

            return new ViewDirectiveCompletionProvider(stringLiteral);
        });

        /*
         * @includeFirst(['custom-template', 'default-template'])
         */
        registrar.register(BladePattern.getArrayParameterDirectiveForElementType(BladeTokenTypes.INCLUDE_FIRST_DIRECTIVE), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return null;
            }

            return new ViewDirectiveCompletionProvider(stringLiteral);
        });

        /*
         * @slot('title')
         */
        registrar.register(BladePattern.getParameterDirectiveForElementType(BladeTokenTypes.SLOT_DIRECTIVE), psiElement -> {
            if (psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            return new MyBladeSlotDirectiveCompletionProvider(psiElement);
        });
    }

    /**
     * Directory view parameter content references
     *
     * "@component("foobar.bar")"
     */
    private static class ViewDirectiveCompletionProvider extends GotoCompletionProvider {
        private ViewDirectiveCompletionProvider(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {

            final Collection<LookupElement> lookupElements = new ArrayList<>();

            ViewCollector.visitFile(getProject(), (virtualFile, name) ->
                lookupElements.add(LookupElementBuilder.create(name).withIcon(virtualFile.getFileType().getIcon()))
            );

            // @TODO: no filesystem access in test; fake item
            if(ApplicationManager.getApplication().isUnitTestMode()) {
                lookupElements.add(LookupElementBuilder.create("test_view"));
            }

            return lookupElements;
        }

        @NotNull
        @Override
        public Collection<? extends PsiElement> getPsiTargets(@NotNull PsiElement psiElement, int offset, @NotNull Editor editor) {
            PsiElement stringLiteral = psiElement.getParent();
            if(!(stringLiteral instanceof StringLiteralExpression)) {
                return Collections.emptyList();
            }

            String contents = ((StringLiteralExpression) stringLiteral).getContents();
            if(StringUtils.isBlank(contents)) {
                return Collections.emptyList();
            }

            // select position of click event
            int caretOffset = offset - psiElement.getTextRange().getStartOffset();

            Collection<PsiElement> targets = new ArrayList<>(PsiElementUtils.convertVirtualFilesToPsiFiles(
                getProject(),
                BladeTemplateUtil.resolveTemplate(getProject(), contents, caretOffset)
            ));

            // @TODO: no filesystem access in test; fake item
            if("test_view".equals(contents) && ApplicationManager.getApplication().isUnitTestMode()) {
                targets.add(PsiManager.getInstance(getProject()).findDirectory(getProject().getBaseDir()));
            }

            return targets;
        }
    }

    /**
     * Navigation and completion
     *
     * "@slot('title')"
     */
    private static class MyBladeSlotDirectiveCompletionProvider extends GotoCompletionProvider {
        private final PsiElement psiElement;

        MyBladeSlotDirectiveCompletionProvider(PsiElement psiElement) {
            super(psiElement);
            this.psiElement = psiElement;
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            String component = BladePsiUtil.findComponentForSlotScope(psiElement);
            if(component == null) {
                return Collections.emptyList();
            }

            Collection<String> slots = new HashSet<>();

            for (VirtualFile virtualFile : BladeTemplateUtil.resolveTemplateName(getProject(), component)) {
                PsiFile file = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if(file != null) {
                    slots.addAll(BladePsiUtil.collectPrintBlockVariables(file));
                }
            }

            return slots.stream()
                .map((Function<String, LookupElement>) s ->
                    LookupElementBuilder.create(s).withIcon(LaravelIcons.LARAVEL).withTypeText(component, true)
                )
                .collect(Collectors.toList());
        }

        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            List<String> strings = BladePsiUtil.extractParameters(element.getText());
            if(strings.size() < 1) {
                return Collections.emptyList();
            }

            String variable = PsiElementUtils.trimQuote(strings.get(0));
            if(StringUtils.isBlank(variable)) {
                return Collections.emptyList();
            }

            String component = BladePsiUtil.findComponentForSlotScope(psiElement);
            if(component == null) {
                return Collections.emptyList();
            }

            Collection<PsiElement> psiElements = new ArrayList<>();

            for (VirtualFile virtualFile : BladeTemplateUtil.resolveTemplateName(getProject(), component)) {
                PsiFile file = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if(file != null) {
                    psiElements.addAll(BladePsiUtil.collectPrintBlockVariableTargets(file, variable));
                }
            }

            return psiElements;
        }
    }
}
