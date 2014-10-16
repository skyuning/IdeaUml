package me.skyun.java2uml;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;

import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class CodeBlockUmlParser extends UmlParser {

    private static String NORMAL_STATEMENT = "-d-> %s\n";
    private static String REFERENCE_NAME = "%s()\\n[%s:%d: %s]";
    private static String REFERENCE = "\"%s\" -r-> %s\n";

    private PsiClass mContainingClass;
    private PsiCodeBlock mPsiCodeBlock;
    private String mBlockName;

    public CodeBlockUmlParser(PsiClass containingClass, PsiCodeBlock psiCodeBlock, String blockName) {
        super(psiCodeBlock);
        mContainingClass = containingClass;
        mPsiCodeBlock = psiCodeBlock;
        mBlockName = blockName;
    }

    @Override
    public String parse() {
        return getCodeBlockUML(mPsiCodeBlock, mBlockName);
    }

    private String getCodeBlockUML(PsiCodeBlock codeBlock, String blockName) {
        String blockUml = "";
        if (codeBlock.getStatements().length > 0)
            blockUml += String.format("\"%s::%s\"", mContainingClass.getName(), blockName);
        for (PsiStatement statement : codeBlock.getStatements()) {
            if (statement instanceof PsiIfStatement)
                blockUml += getIfUml((PsiIfStatement) statement);
            else
                blockUml += String.format(NORMAL_STATEMENT, statement.getText().split("\n")[0]);
        }
        blockUml += "\n";

        String referenceUml = getBlockReferenceUml(codeBlock, blockName);

        String anonymousClassUml = "";
        List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(codeBlock, PsiAnonymousClass.class, true);
        for (int i = 0; i < anonyClasses.size(); i++) {
            anonymousClassUml += new ClassUmlParser(anonyClasses.get(i), "" + blockName + "#" + i).parse();
        }

        return blockUml + referenceUml + anonymousClassUml;
    }

    private static String getBlockReferenceUml(PsiCodeBlock codeBlock, String blockName) {
        String referenceUml = "";
        for (int i = 0; i < codeBlock.getStatements().length; i++) {
            PsiStatement statement = codeBlock.getStatements()[i];
            // if statement
            if (statement instanceof PsiIfStatement)
                continue;

            // normal statement, get its reference method uml
            for (PsiMethod refMethod : PsiUtils.findLocalMethods(statement)) {
                String refName = getReferenceName(refMethod, blockName, i, statement);
                referenceUml += String.format(REFERENCE, getStatementName(statement, false), refName + "<< Begin >>");
                referenceUml += String.format(REFERENCE, refName, getRefMethodName(refMethod));
            }
        }
        return referenceUml + "\n";
    }

    private static String getStatementName(PsiStatement statement) {
        return getStatementName(statement, true);
    }

    private static String getStatementName(PsiStatement statement, boolean endline) {
        String uml = statement.getText().split("\n")[0];
        if (endline)
            uml += "\n";
        return uml;
    }

    private static String getReferenceName(PsiMethod refMethod, String caller, int statementNo, PsiStatement statement) {
        return String.format(REFERENCE_NAME, refMethod.getName(), caller, statementNo, getStatementName(statement, false));
    }

    private static String getRefMethodName(PsiMethod refMethod) {
        return refMethod.getContainingClass().getName() + "::" + refMethod.getName() + "()";
    }

    private static String getIfUml(PsiIfStatement ifStatement) {
        String ifUml = "if " + ifStatement.getCondition().getText() + "\n";

        if (ifStatement.getThenBranch() instanceof PsiExpressionStatement) {
            ifUml += "  --> [yes] " + getStatementName(ifStatement.getThenBranch());
        } else if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement thenBlock = (PsiBlockStatement) ifStatement.getThenBranch();
            PsiStatement[] thenStatements = thenBlock.getCodeBlock().getStatements();
            ifUml += "  --> [yes] " + getStatementName(thenStatements[0]);
            for (int i = 1; i < thenStatements.length; i++) {
                ifUml += "  --> " + getStatementName(thenStatements[i]);
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
