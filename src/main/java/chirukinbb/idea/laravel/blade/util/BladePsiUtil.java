package chirukinbb.idea.laravel.blade.util;

import chirukinbb.idea.laravel.blade.BladePattern;
import chirukinbb.idea.laravel.util.PsiElementUtils;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.php.blade.psi.BladePsiDirective;
import com.jetbrains.php.blade.psi.BladePsiDirectiveParameter;
import com.jetbrains.php.blade.psi.BladeTokenTypes;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class BladePsiUtil {

    public static boolean isDirectiveWithInstance(@NotNull PsiElement psiElement, @NotNull String clazz, @NotNull String method) {
        PsiElement stringLiteral = psiElement.getParent();
        if(stringLiteral instanceof StringLiteralExpression) {
            PsiElement parameterList = stringLiteral.getParent();
            if(parameterList instanceof ParameterList) {
                PsiElement methodReference = parameterList.getParent();
                if(methodReference instanceof MethodReference && method.equals(((MethodReference) methodReference).getName())) {
                    String text = methodReference.getText();
                    int i = text.indexOf(":");
                    // method resolve dont work; extract class name from text "Foo::method(..."
                    return i > 0 && StringUtils.stripStart(text.substring(0, i), "\\").equals(clazz);
                }
            }
        }

        return false;
    }

    @Nullable
    public static String getSection(PsiElement psiSection) {

        for(PsiElement psiElement : PsiElementUtils.getChildrenFix(psiSection)) {

            if(psiElement.getNode().getElementType() == BladeTokenTypes.DIRECTIVE_PARAMETER_CONTENT) {

                String content = PsiElementUtils.trimQuote(psiElement.getText());
                if(StringUtils.isNotBlank(content)) {
                    return content;
                }

                return null;
            }
        }

        return null;

    }

    /**
     * Checks for registered directive on language injection
     *
     * "@foobar('bar')"
     */
    public static boolean isDirective(@NotNull PsiElement psiElement, @NotNull IElementType... elementType) {
        PsiLanguageInjectionHost host = InjectedLanguageManager
            .getInstance(psiElement.getProject())
            .getInjectionHost(psiElement);

        return
            host instanceof BladePsiDirectiveParameter &&
            ArrayUtils.contains(elementType, ((BladePsiDirectiveParameter) host).getDirectiveElementType());
    }

    /**
     * Extract template attribute of @each directory eg index 0 and 3
     */
    @NotNull
    public static List<String> getEachDirectiveTemplateParameter(@NotNull BladePsiDirectiveParameter parameter) {
        String text = parameter.getText();
        if(StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }

        List<String> parameters = extractParameters(text);

        // first and fourth item is our template attribute
        List<String> templateVariables = new ArrayList<>();
        for (Integer integer : Arrays.asList(0, 3)) {
            if(parameters.size() < integer + 1) {
                return templateVariables;
            }

            String content = PsiElementUtils.trimQuote(parameters.get(integer));
            if(StringUtils.isBlank(content)) {
                continue;
            }

            templateVariables.add(content);
        }

        return templateVariables;
    }

    /**
     * Extract parameter from directive parameters
     *
     * @param text "foo", "foobar"
     */
    @NotNull
    public static List<String> extractParameters(@NotNull String text) {
        String content = StringUtils.stripStart(StringUtils.stripEnd(text, ")"), "(");

        Matcher matcher = Pattern.compile("([^,]+\\(.+?\\))|([^,]+)").matcher(content);
        List<String> parameters = new ArrayList<>();

        while(matcher.find()) {
            parameters.add(StringUtil.trim(matcher.group(0)));
        }

        return parameters;
    }

    /**
     * {{ $slot }}
     * {{ $title or 'Laravel News' }}
     */
    private static void visitPrintBlockVariables(@NotNull PsiFile psiFile, @NotNull Consumer<Pair<PsiElement, String>> consumer) {
        psiFile.acceptChildren(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if(!BladePattern.getTextBlockContentVariablePattern().accepts(element)) {
                    super.visitElement(element);
                    return;
                }

                String text = element.getText();

                Matcher matcher = Pattern.compile("\\s*\\$([\\w-]+)\\s*").matcher(text);
                while(matcher.find()) {
                    consumer.accept(Pair.create(element, StringUtil.trim(matcher.group(1))));
                }

                super.visitElement(element);
            }
        });
    }

    /**
     * Collects variables for print blocks
     *
     * {{ $slot }}
     * {{ $title or 'Laravel News' }}
     */
    public static Collection<String> collectPrintBlockVariables(@NotNull PsiFile psiFile) {
        Collection<String> variables = new ArrayList<>();

        visitPrintBlockVariables(psiFile, pair ->
            variables.add(pair.getSecond())
        );

        return variables;
    }

    /**
     * Collect targets for print blocks with variables
     *
     * {{ $slot }}
     * {{ $title or 'Laravel News' }}
     */
    public static Collection<PsiElement> collectPrintBlockVariableTargets(@NotNull PsiFile psiFile, @NotNull String variable) {
        Collection<PsiElement> variables = new ArrayList<>();

        visitPrintBlockVariables(psiFile, pair -> {
            if(variable.equals(pair.getSecond())) {
                variables.add(pair.getFirst());
            }
        });

        return variables;
    }

    /**
     * Crazy shit to find component directive for a given slot
     * Blade Plugin do not provide a nested tree!
     *
     * "@component('layouts.app')"
     *  "@slot('title')"
     *      Home Page
     *  "@endslot"
     * "@endcomponent"
     */
    @Nullable
    public static String findComponentForSlotScope(@NotNull PsiElement psiDirectiveParameter) {
        PsiElement bladeHost;
        if(psiDirectiveParameter.getNode().getElementType() == BladeTokenTypes.DIRECTIVE_PARAMETER_CONTENT) {
            bladeHost = psiDirectiveParameter.getParent();
        } else {
            bladeHost = InjectedLanguageManager
                .getInstance(psiDirectiveParameter.getProject())
                .getInjectionHost(psiDirectiveParameter);
        }

        if(!(bladeHost instanceof BladePsiDirectiveParameter)) {
            return null;
        }

        return findComponentForSlotScope((BladePsiDirectiveParameter) bladeHost);
    }

    /**
     * Crazy shit to find component directive for a given slot
     * Blade Plugin do not provide a nested tree!
     *
     * "@component('layouts.app')"
     *  "@slot('title')"
     *      Home Page
     *  "@endslot"
     * "@endcomponent"
     */
    @Nullable
    public static String findComponentForSlotScope(@NotNull BladePsiDirectiveParameter psiDirectiveParameter) {
        PsiElement bladeDirective = psiDirectiveParameter.getParent();
        if(!(bladeDirective instanceof BladePsiDirective)) {
            return null;
        }

        String parameter = null;
        for (PsiElement prev = bladeDirective.getPrevSibling(); prev != null; prev = prev.getPrevSibling()) {
            if (prev instanceof BladePsiDirective && "@component".equals(((BladePsiDirective) prev).getName())) {
                parameter = getDirectiveParameter((BladePsiDirective) prev);
                break;
            }
        }

        return parameter;
    }

    /**
     * Extract parameter from Blade directive
     *
     * "@component('layouts.app')" => "layouts.app"
     */
    @Nullable
    public static String getDirectiveParameter(@NotNull BladePsiDirective directiveParameter) {
        String parameterText = getDirectiveParameterContent(directiveParameter);
        if(parameterText != null) {
            return BladeTemplateUtil.getParameterFromParameterDirective(parameterText);
        }

        return null;
    }

    /**
     * Extract parameter content from Blade directive
     *
     * "@component('layouts.app', 'foo')" => "'layouts.app', 'foo'"
     */
    @Nullable
    public static String getDirectiveParameterContent(@NotNull BladePsiDirective directiveParameter) {
        BladePsiDirectiveParameter parameter = directiveParameter.getParameter();
        if(parameter != null) {
            String parameterText = parameter.getParameterText();
            if(parameterText != null) {
                return parameterText;
            }
        }

        return null;
    }

    /**
     * Extract parameter from Blade directive
     *
     * "@component('layouts.app', "foobar")" => "layouts.app"
     */
    @NotNull
    public static List<String> getDirectiveParameters(@NotNull BladePsiDirective directiveParameter) {
        BladePsiDirectiveParameter parameter = directiveParameter.getParameter();
        if(parameter != null) {
            String parameterText = parameter.getParameterText();
            if(parameterText != null) {
                return extractParameters(parameterText);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Visit all Blade include directives
     *
     * "@include", "@includeIf", "@includeWhen", "@includeFirst"
     */
    public static void visitIncludes(@NotNull PsiFile bladeFile, @NotNull Consumer<Pair<String, PsiElement>> consumer) {
        bladeFile.acceptChildren(new TemplateIncludeElementWalkingVisitor(consumer));
    }

    private static class TemplateIncludeElementWalkingVisitor extends PsiRecursiveElementWalkingVisitor {
        @NotNull
        private final Consumer<Pair<String, PsiElement>> consumer;

        private TemplateIncludeElementWalkingVisitor(@NotNull Consumer<Pair<String, PsiElement>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void visitElement(PsiElement element) {
            if((element instanceof BladePsiDirective)) {
                IElementType elementType = ((BladePsiDirective) element).getDirectiveElementType();

                if(elementType == BladeTokenTypes.INCLUDE_DIRECTIVE || elementType == BladeTokenTypes.COMPONENT_DIRECTIVE) {
                    // @include('foobar.include')
                    // @component('inc.alert')

                    for (String parameter : getDirectiveParameters(((BladePsiDirective) element))) {
                        String parameterTrim = PsiElementUtils.trimQuote(parameter);
                        if(StringUtils.isNotBlank(parameterTrim)) {
                            consumer.accept(Pair.create(parameterTrim, element));
                        }
                    }
                } else if(elementType == BladeTokenTypes.INCLUDE_IF_DIRECTIVE) {
                    // @includeIf( 'foobar.includeIf' , ['some' => 'data'])

                    List<String> directiveParameters = getDirectiveParameters(((BladePsiDirective) element));
                    if(directiveParameters.size() > 0) {
                        String parameterTrim = PsiElementUtils.trimQuote(directiveParameters.get(0));
                        if(StringUtils.isNotBlank(parameterTrim)) {
                            consumer.accept(Pair.create(parameterTrim, element));
                        }
                    }
                } else if(elementType == BladeTokenTypes.INCLUDE_WHEN_DIRECTIVE) {
                    // @includeWhen($boolean, 'foobar.includeWhen', ['some' => 'data'])

                    List<String> directiveParameters = getDirectiveParameters(((BladePsiDirective) element));
                    if(directiveParameters.size() > 1) {
                        String parameterTrim = PsiElementUtils.trimQuote(directiveParameters.get(1));
                        if(StringUtils.isNotBlank(parameterTrim)) {
                            consumer.accept(Pair.create(parameterTrim, element));
                        }
                    }
                } else if(elementType == BladeTokenTypes.INCLUDE_FIRST_DIRECTIVE) {
                    // @includeFirst(['foobar.includeFirst', 'foobar.includeFirst2'], ['some' => 'data'])

                    String parameter = getDirectiveParameterContent(((BladePsiDirective) element));
                    if(parameter != null) {
                        // find first array

                        Matcher matcher = Pattern.compile("\\s*\\[([^\\[]+)]").matcher(parameter);
                        if(matcher.find()) {
                            // split array value

                            Matcher matcher2 = Pattern.compile("['|\"]([^'\"]*)['|\"]").matcher(matcher.group(0));
                            while(matcher2.find()) {
                                String trim = PsiElementUtils.trimQuote(StringUtil.trim(matcher2.group(0)));
                                if(StringUtils.isNotBlank(trim)) {
                                    consumer.accept(Pair.create(trim, element));
                                }
                            }
                        }
                     }
                }
            }

            super.visitElement(element);
        }
    }
}
