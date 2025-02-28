package chirukinbb.idea.laravel.translation;

import chirukinbb.idea.laravel.LaravelProjectComponent;
import chirukinbb.idea.laravel.stub.processor.CollectProjectUniqueKeys;
import chirukinbb.idea.laravel.util.ArrayReturnPsiRecursiveVisitor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import chirukinbb.idea.laravel.LaravelIcons;
import chirukinbb.idea.laravel.LaravelSettings;
import chirukinbb.idea.laravel.blade.util.BladePsiUtil;
import chirukinbb.idea.laravel.stub.TranslationKeyStubIndex;
import chirukinbb.idea.laravel.translation.utils.TranslationUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class TranslationReferences implements GotoCompletionLanguageRegistrar {

    private static MethodMatcher.CallToSignature[] TRANSLATION_KEY = new MethodMatcher.CallToSignature[] {
        new MethodMatcher.CallToSignature("\\Illuminate\\Translation\\Translator", "get"),
        new MethodMatcher.CallToSignature("\\Illuminate\\Translation\\Translator", "has"),
        new MethodMatcher.CallToSignature("\\Illuminate\\Translation\\Translator", "choice"),
        new MethodMatcher.CallToSignature("\\Illuminate\\Translation\\Translator", "transChoice"),
    };

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement(), psiElement -> {
            if(psiElement == null || !LaravelProjectComponent.isEnabled(psiElement)) {
                return null;
            }

            PsiElement parent = psiElement.getParent();
            if(parent != null && (
                MethodMatcher.getMatchedSignatureWithDepth(parent, TRANSLATION_KEY) != null || PhpElementsUtil.isFunctionReference(parent, 0, "trans", "__", "trans_choice")
            )) {
                return new TranslationKey(parent);
            }

            // for blade @lang directive
            if(BladePsiUtil.isDirectiveWithInstance(psiElement, "Illuminate\\Support\\Facades\\Lang", "get")) {
                return new TranslationKey(psiElement);
            }

            return null;
        });
    }

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    public static class TranslationKey extends GotoCompletionProvider {

        public TranslationKey(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {

            final Collection<LookupElement> lookupElements = new ArrayList<>();

            CollectProjectUniqueKeys ymlProjectProcessor = new CollectProjectUniqueKeys(getProject(), TranslationKeyStubIndex.KEY);
            FileBasedIndex.getInstance().processAllKeys(TranslationKeyStubIndex.KEY, ymlProjectProcessor, getProject());
            for(String key: ymlProjectProcessor.getResult()) {
                lookupElements.add(LookupElementBuilder.create(key).withIcon(LaravelIcons.TRANSLATION));
            }

            return lookupElements;
        }

        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {

            final String contents = element.getContents();
            if(StringUtils.isBlank(contents)) {
                return Collections.emptyList();
            }

            final String priorityTemplate = "/" + LaravelSettings.getInstance(element.getProject()).getMainLanguage() + "/";

            final Set<PsiElement> priorityTargets = new LinkedHashSet<>();
            final Set<PsiElement> targets = new LinkedHashSet<>();

            FileBasedIndex.getInstance().getFilesWithKey(TranslationKeyStubIndex.KEY, Collections.singleton(contents), virtualFile -> {
                PsiFile psiFileTarget = PsiManager.getInstance(getProject()).findFile(virtualFile);
                if(psiFileTarget == null) {
                    return true;
                }

                String namespace = TranslationUtil.getNamespaceFromFilePath(virtualFile.getPath());
                if(namespace == null) {
                    return true;
                }

                psiFileTarget.acceptChildren(new ArrayReturnPsiRecursiveVisitor(namespace, (key, psiKey, isRootElement) -> {
                    if(!isRootElement && key.equalsIgnoreCase(contents)) {
                        if(virtualFile.getPath().contains(priorityTemplate)) {
                            priorityTargets.add(psiKey);
                        } else {
                            targets.add(psiKey);
                        }
                    }
                }));

                return true;
            }, GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(getProject()), PhpFileType.INSTANCE));

            priorityTargets.addAll(targets);
            return priorityTargets;
        }

    }

}
