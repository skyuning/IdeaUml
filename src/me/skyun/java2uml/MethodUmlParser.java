package me.skyun.java2uml;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class MethodUmlParser extends UmlParser {

    private static String NORMAL_STATEMENT = "-d-> %s\n";
    private static String REFERENCE_NAME = "%s#%d: %s -> %s";
    private static String REFERENCE = "\"%s\" -r-> %s\n";
    private static String REFERENCE2 = "\"%s\" -d-> %s\n";

    private PsiMethod mPsiMethod;

    public MethodUmlParser(PsiMethod psiMethod) {
        super(psiMethod);
        mPsiMethod = psiMethod;
    }

    @Override
    public String parse() {
        if (mPsiMethod.getBody().getStatements().length > 0) {
            String uml = "\"" + getFullMethodName(mPsiMethod) + "\"";
            uml += getCodeBlockUML();
            uml += getReferenceUml();
            uml += getInnerClassUml();
            return uml;
        } else
            return "";
    }

    private String getCodeBlockUML() {
        String blockUml = "";
        for (PsiStatement statement : mPsiMethod.getBody().getStatements()) {
            if (statement instanceof PsiIfStatement)
                blockUml += getIfUml((PsiIfStatement) statement);
            else
                blockUml += String.format(NORMAL_STATEMENT, statement.getText().split("\n")[0]);
        }
        blockUml += "\n";
        return blockUml;
    }

    private String getInnerClassUml() {
        String innerClassUml = "";
        List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(mPsiMethod.getBody(), PsiAnonymousClass.class, true);
        for (int i = 0; i < anonyClasses.size(); i++)
            innerClassUml += new ClassUmlParser(anonyClasses.get(i), "" + mPsiMethod.getName() + "#" + i).parse();
        return innerClassUml;
    }

    private String getReferenceUml() {
        PsiCodeBlock codeBlock = mPsiMethod.getBody();
        String referenceUml = "";
        for (int i = 0; i < codeBlock.getStatements().length; i++) {
            PsiStatement statement = codeBlock.getStatements()[i];
            // if statement
            if (statement instanceof PsiIfStatement)
                continue;

            // normal statement, get its reference method uml
            for (PsiReferenceExpression reference : PsiUtils.findPsiElements(statement, PsiReferenceExpression.class, true)) {
                if (!(reference.resolve() instanceof PsiMethod))
                    continue;
                PsiMethod refMethod = (PsiMethod) reference.resolve();
                String refName = getReferenceText(i, statement, refMethod);
                referenceUml += String.format(REFERENCE, getStatementText(statement, false), refName + "<< Begin >>");
                referenceUml += String.format(REFERENCE2, refName, getFullMethodName(refMethod));
            }
        }
        return referenceUml + "\n";
    }

    private static String getStatementText(PsiStatement statement) {
        return getStatementText(statement, true);
    }

    private static String getStatementText(PsiStatement statement, boolean endline) {
        PsiStatement _statement = (PsiStatement) statement.copy();
        List<PsiAnonymousClass> innerClasses = PsiUtils.findPsiElements(_statement, PsiAnonymousClass.class, true);
        for (PsiAnonymousClass innerClass : innerClasses)
            innerClass.deleteChildRange(PsiUtils.findJavaToken(innerClass, "{"), PsiUtils.findJavaToken(innerClass, "}"));
        String uml = _statement.getText().replace("\n", "\\n");
        if (endline)
            uml += "\n";
        return uml;
    }

    private static String getReferenceText(int statementNo, PsiStatement statement, PsiMethod refMethod) {
        String callerMethodName = getFullMethodName(PsiUtils.getContainingMethod(statement));
        String statementText = getStatementText(statement, false);
        String refMethodName = getFullMethodName(refMethod);
        return String.format(REFERENCE_NAME, callerMethodName, statementNo, statementText, refMethodName);
    }

    private static String getFullMethodName(PsiMethod method) {
        return method.getContainingClass().getName() + "::" + method.getName() + "()";
    }

    private static String getIfUml(PsiIfStatement ifStatement) {
        String ifUml = "if " + ifStatement.getCondition().getText() + "\n";

        if (ifStatement.getThenBranch() instanceof PsiExpressionStatement) {
            ifUml += "  --> [yes] " + getStatementText(ifStatement.getThenBranch());
        } else if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement thenBlock = (PsiBlockStatement) ifStatement.getThenBranch();
            PsiStatement[] thenStatements = thenBlock.getCodeBlock().getStatements();
            ifUml += "  --> [yes] " + getStatementText(thenStatements[0]);
            for (int i = 1; i < thenStatements.length; i++) {
                ifUml += "  --> " + getStatementText(thenStatements[i]);
            }
        }

        ifUml += "else\n";

        if (ifStatement.getElseBranch() instanceof PsiExpressionStatement) {
            ifUml += "  --> [no] " + ifStatement.getElseBranch().getText().split("\n")[0] + "\n";
        } else if (ifStatement.getElseBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement elseBlock = (PsiBlockStatement) ifStatement.getElseBranch();
            PsiStatement[] elseStatements = elseBlock.getCodeBlock().getStatements();
            ifUml += "  --> [no] " + elseStatements[0].getText().split("\n")[0] + "\n";
            for (int i = 1; i < elseStatements.length; i++) {
                ifUml += "  --> " + elseStatements[i].getText().split("\n")[0] + "\n";
            }
        }

        ifUml += "endif\n\n";

        return ifUml;
    }
}
