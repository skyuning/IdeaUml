package me.skyun.java2uml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
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

    public static PsiMethod[] findLocalMethods(PsiStatement statement) {
        ArrayList<PsiMethod> localMethods = new ArrayList<PsiMethod>();
        for (PsiMethodCallExpression call : PsiUtils.findPsiElements(statement, PsiMethodCallExpression.class, true)) {
            if (getLocalMethod(call) != null)
                localMethods.add(PsiUtils.getLocalMethod(call));
        }
        return localMethods.toArray(new PsiMethod[0]);
    }

    private static PsiMethod getLocalMethod(PsiMethodCallExpression call) {
        PsiReferenceExpression reference = PsiUtils.findPsiElement(call, PsiReferenceExpression.class);
        if (PsiUtils.findPsiElement(reference, PsiReferenceExpression.class) != null)
            // if reference has reference, the reference is not local reference
            return null;
        PsiIdentifier identifier = PsiUtils.findPsiElement(reference, PsiIdentifier.class);
        PsiClass psiClass = ((PsiJavaFile) call.getContainingFile()).getClasses()[0];
        PsiMethod[] localMethods = psiClass.findMethodsByName(identifier.getText(), false);
        if (localMethods == null || localMethods.length == 0)
            return null;
        return localMethods[0];
    }

    public static PsiMethod findPsiMethod(PsiClass psiClass, String methodName) {
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        if (methods != null && methods.length > 0)
            return methods[0];
        else
            return null;
    }

    public static <T extends PsiElement> T findPsiElement(PsiElement source, Class<T> targetClass) {
        List<T> result = findPsiElements(source, targetClass);
        if (!result.isEmpty())
            return result.get(0);
        else
            return null;
    }

    static <T extends PsiElement> List<T> findPsiElements(PsiElement source, Class<T> targetClass) {
        return findPsiElements(source, targetClass, false);
    }

    public static <T extends PsiElement> List<T> findPsiElements(PsiElement source, Class<T> targetClass, boolean recurse) {
        ArrayList<T> resultElements = new ArrayList<T>();
        for (PsiElement childElement : source.getChildren()) {
            if (targetClass.isAssignableFrom(childElement.getClass()))
                resultElements.add((T) childElement);
            if (recurse)
                resultElements.addAll(findPsiElements(childElement, targetClass, true));
        }
        return resultElements;
    }
}
