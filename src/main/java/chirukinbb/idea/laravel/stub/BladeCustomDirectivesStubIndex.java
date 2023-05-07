package chirukinbb.idea.laravel.stub;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import gnu.trove.THashMap;
import chirukinbb.idea.laravel.stub.processor.BladeCustomDirectivesVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class BladeCustomDirectivesStubIndex extends FileBasedIndexExtension<String, Void> {

    public static final ID<String, Void> KEY = ID.create("de.espend.idea.laravel.blade.customs");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new THashMap<>();
            PsiFile psiFile = fileContent.getPsiFile();

            if(!(psiFile instanceof PhpFile)) {
                return map;
            }

            psiFile.acceptChildren(new BladeCustomDirectivesVisitor(hit -> map.put(hit.second, null)));

            return map;
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
    public FileBasedIndex.InputFilter getInputFilter() { return file -> file.getFileType() == PhpFileType.INSTANCE; }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
