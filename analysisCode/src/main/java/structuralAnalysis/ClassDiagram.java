package structuralAnalysis;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neilwalkinshaw on 24/10/2017.
 */
public class ClassDiagram {

    /**
     * Given a package name and a directory returns all classes within that directory
     * @param directory
     * @param pkgname
     * @return Classes within Directory with package name
     */
    public static List<Class<?>> processDirectory(File directory, String pkgname) {

        ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
        String prefix = pkgname+".";
        if(pkgname.equals(""))
            prefix = "";

        // Get the list of the files contained in the package
        String[] files = directory.list();
        for (int i = 0; i < files.length; i++) {
            String fileName = files[i];
            String className = null;

            // we are only interested in .class files
            if (fileName.endsWith(".class")) {
                // removes the .class extension
                className = prefix+fileName.substring(0, fileName.length() - 6);
            }


            if (className != null) {
                classes.add(loadClass(className));
            }

            //If the file is a directory recursively class this method.
            File subdir = new File(directory, fileName);
            if (subdir.isDirectory()) {

                classes.addAll(processDirectory(subdir, prefix + fileName));
            }
        }
        return classes;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Unexpected ClassNotFoundException loading class '" + className + "'");
        }
    }



    public ClassDiagram(String root){
        File dir = new File(root);
        List<Class<?>> classes = processDirectory(dir,"");
        //Insert your lab code here.
    }


}
