package viewplugin.views;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.IDocumentElementRequestor;
import org.eclipse.jdt.internal.core.ResolvedBinaryType;
import org.eclipse.jdt.internal.core.ResolvedSourceType;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceRefElement;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.ViewPart;

public class SampleView extends ViewPart {

    // private Composite mParent;
    private Browser mBrowser;
    private IReusableEditor mEditor;
    private IJavaProject mProject;

    public SampleView() {
    }

    public void createPartControl(final Composite parent) {
        // mParent = parent;
        mBrowser = new Browser(parent, SWT.NONE);
        mBrowser.setUrl("file:///Users/linyun/Documents/workspace/HelloWorldPlugin/svg/B.svg");
        mBrowser.addLocationListener(new LocationListener() {
            @Override
            public void changing(LocationEvent event) {
                if (!event.location.startsWith("file:///"))
                    return;

                mBrowser.stop();
                if (mProject == null)
                    return;

                String pathStr = event.location.replace("file:///TTT", "");
                IPath path = Path.fromPortableString(pathStr);
                IFile file = mProject.getProject().getFile(path);
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                FileEditorInput newInput = new FileEditorInput(file);
                page.reuseEditor(mEditor, newInput);
                try {
                    refreshUml(newInput);
                } catch (JavaModelException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void changed(LocationEvent event) {
            }
        });

        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        page.addSelectionListener(new ISelectionListener() {
            @SuppressWarnings("restriction")
            @Override
            public void selectionChanged(final IWorkbenchPart part, ISelection selection) {
                if (!(part instanceof CompilationUnitEditor))
                    return;

                if (part instanceof IReusableEditor)
                    mEditor = (IReusableEditor) part;

                try {
                    refreshUml(((EditorPart) part).getEditorInput());
                } catch (JavaModelException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void refreshUml(IEditorInput input) throws JavaModelException {
        if (!(input instanceof IFileEditorInput))
            return;
        IJavaElement javaFile = JavaCore.create(((IFileEditorInput) input).getFile());

        if (mProject == null)
            mProject = javaFile.getJavaProject();

        if (!(javaFile instanceof ICompilationUnit))
            return;
        ICompilationUnit compilationUnit = (ICompilationUnit) javaFile;

        String uml = getCompilationUnitUml(compilationUnit);
        String svg = getSvgString(uml);
        mBrowser.setText(svg);
    }

    private String getCompilationUnitUml(ICompilationUnit compilationUnit) throws JavaModelException {
        String uml = "";
        for (IType type : compilationUnit.getAllTypes())
            uml += getTypeUml(type);
        uml = String.format("@startuml\n%s@enduml", uml);
        IClassFile classFile;
        return uml;
    }

    private String getTypeUml(IType type) throws JavaModelException {
        String methodsUml = "";
        for (IMethod method : type.getMethods())
            methodsUml += getMethodUml(method);

        String typeUml = "";
        typeUml += String.format("class %s {\n%s}\n", type.getElementName(), methodsUml);

        typeUml += getSuperTypeUml(type);
        return typeUml;
    }

    private String getSuperTypeUml(IType type) throws JavaModelException {
        SourceRefElement superClass = (SourceRefElement) type.newSupertypeHierarchy(null).getSuperclass(type);
        if (superClass == null)
            return "";

        String uml = "";
        uml += String.format("class %s [[%s]] {\n}\n", superClass.getElementName(), superClass.getPath()
                .toPortableString());
        uml += String.format("%s -u-|> %s\n", type.getElementName(), superClass.getElementName());
        return uml;
    }

    private String getMethodUml(IMethod method) throws JavaModelException {
        String methodUml = String.format("%s %s %s(%s)\n", getMethodModifier(method),
                Signature.getSignatureSimpleName(method.getReturnType()), method.getElementName(),
                getMethodParamUml(method));
        return methodUml;
    }

    private String getMethodModifier(IMethod method) {
        try {
            switch (method.getFlags()) {
            case Flags.AccPublic:
                return "+";
            case Flags.AccProtected:
                return "#";
            case Flags.AccPrivate:
                return "-";
            default:
                return "";
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getMethodParamUml(IMethod method) throws JavaModelException {
        if (method.getNumberOfParameters() == 0)
            return "";

        String[] paramTypes = method.getParameterTypes();
        String[] paramNames = method.getParameterNames();
        String paramUml = Signature.getSignatureSimpleName(paramTypes[0]) + " " + paramNames[0];
        for (int i = 1; i < paramTypes.length; i++)
            paramUml += ", " + Signature.getSignatureSimpleName(paramTypes[i]) + " " + paramNames[i];
        return paramUml;
    }

    private String getSvgString(String uml) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            SourceStringReader reader = new SourceStringReader(uml);
            List<BlockUml> blocks = reader.getBlocks();
            int imageСounter = 0;
            for (BlockUml block : blocks) {
                Diagram diagram = block.getDiagram();
                int pages = diagram.getNbImages();
                for (int page = 0; page < pages; ++page) {
                    reader.generateImage(baos, imageСounter++, new FileFormatOption(FileFormat.SVG));
                    baos.close();
                }
            }
            return new String(baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void setFocus() {
        mBrowser.setFocus();
    }
}