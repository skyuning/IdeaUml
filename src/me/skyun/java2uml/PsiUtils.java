package me.skyun.java2uml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class PsiUtils {

    public static PsiJavaFile getPsiJavaFile(Project project, String filename) {
        VirtualFile vf = project.getBaseDir().findFileByRelativePath(filename);
        PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(vf);
        return psiJavaFile;
    }

    public static PsiMethod getContainingMethod(PsiStatement statement) {
        PsiElement element = statement;
        while (true) {
            element = element.getParent();
            assert element != null;
            if (element instanceof PsiMethod)
                return (PsiMethod) element;
        }
    }

    public static <T extends PsiElement> T getContainingParent(
        PsiElement child, Class<T> parentType, PsiElement topElement) {

        PsiElement element = child;
        PsiElement result = null;
        while (element != topElement) {
            element = element.getParent();
            assert element != null;
            if (parentType.isAssignableFrom(element.getClass()))
                result = element;
        }
        return (T) result;
    }

    public static <T extends PsiElement> T findPsiElement(PsiElement source, Class<T> targetClass) {
        List<T> result = findPsiElements(source, targetClass);
        if (!result.isEmpty())
            return result.get(0);
        else
            return null;
    }

    static <T extends PsiElement> List<T> findPsiElements(PsiElement source, Class<T> targetClass) {
        return findPsiElements(source, targetClass, false, false);
    }

    public static <T extends PsiElement> List<T> findPsiElements(
        PsiElement source, Class<T> targetClass, boolean recurse) {
        return findPsiElements(source, targetClass, recurse, false);
    }

    public static <T extends PsiElement> List<T> findPsiElements(
        PsiElement source, Class<T> targetClass, boolean recurse, boolean stopOnFind) {

        ArrayList<T> resultElements = new ArrayList<T>();
        for (PsiElement childElement : source.getChildren()) {
            if (targetClass.isAssignableFrom(childElement.getClass())) {
                resultElements.add((T) childElement);
                if (stopOnFind)
                    continue;
            }
            if (recurse)
                resultElements.addAll(findPsiElements(childElement, targetClass, true, stopOnFind));
        }
        return resultElements;
    }
}
