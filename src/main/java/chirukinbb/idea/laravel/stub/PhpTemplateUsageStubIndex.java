package chirukinbb.idea.laravel.stub;

import chirukinbb.idea.laravel.LaravelProjectComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.ClassReference;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.stubs.indexes.PhpConstantNameIndex;
import gnu.trove.THashMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class PhpTemplateUsageStubIndex extends FileBasedIndexExtension<String, Void> {

    public static final ID<String, Void> KEY = ID.create("de.espend.idea.laravel.template");
    private static int MAX_FILE_BYTE_SIZE = 2097152;


    public static Set<String> RENDER_METHODS = new HashSet<String>() {{
        add("make");
        add("of");
    }};

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return new DataIndexer<String, Void, FileContent>() {
            @NotNull
            @Override
            public Map<String, Void> map(@NotNull FileContent inputData) {
                final Map<String, Void> map = new THashMap<>();

                PsiFile psiFile = inputData.getPsiFile();
                if(!LaravelProjectComponent.isEnabledForIndex(psiFile.getProject())) {
                    return map;
                }

                if(!(inputData.getPsiFile() instanceof PhpFile) && isValidForIndex(inputData)) {
                    return map;
                }

                psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {

                        if(element instanceof MethodReference) {
                            visitMethodReference((MethodReference) element);
                        }

                        if(element instanceof FunctionReference) {
                            visitFunctionReference((FunctionReference) element);
                        }

                        super.visitElement(element);
                    }

                    public void visitFunctionReference(FunctionReference functionReference) {

                        if(!"view".equals(functionReference.getName())) {
                            return;
                        }

                        PsiElement[] parameters = functionReference.getParameters();

                        if(parameters.length < 1 || !(parameters[0] instanceof StringLiteralExpression)) {
                            return;
                        }

                        String contents = ((StringLiteralExpression) parameters[0]).getContents();
                        if(StringUtils.isBlank(contents)) {
                            return;
                        }

                        // replace done by Laravel before rendering
                        map.put(contents.replace(".", "/").toLowerCase(), null);
                    }

                    public void visitMethodReference(MethodReference methodReference) {

                        String methodName = methodReference.getName();
                        if(!RENDER_METHODS.contains(methodName)) {
                            return;
                        }

                        PsiElement classReference = methodReference.getFirstChild();
                        if(!(classReference instanceof ClassReference)) {
                            return;
                        }

                        if(!"View".equals(((ClassReference) classReference).getName())) {
                            return;
                        }

                        PsiElement[] parameters = methodReference.getParameters();
                        if(parameters.length == 0 || !(parameters[0] instanceof StringLiteralExpression)) {
                            return;
                        }

                        String contents = ((StringLiteralExpression) parameters[0]).getContents();
                        if(StringUtils.isBlank(contents)) {
                            return;
                        }

                        // replace done by Laravel before rendering
                        map.put(contents.replace(".", "/").toLowerCase(), null);
                    }

                });

                return map;
            }
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return PhpConstantNameIndex.PHP_INPUT_FILTER;
    }


    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public static boolean isValidForIndex(FileContent inputData) {
        return inputData.getFile().getLength() < MAX_FILE_BYTE_SIZE;
    }

}
