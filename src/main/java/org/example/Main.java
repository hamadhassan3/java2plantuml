package org.example;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    /**
     * The sub-folders in the application to be parsed.
     */
    private static final String[] subFolders = {
            "controllers",
            "models/repositories",
            "services"
    };

    /**
     * The associations between classes are stored in this set while parsing
     * the code. This set can be used after all classes have been parsed.
     */
    private static final Set<String> associations = new HashSet<>();

    /**
     * The UML code is stored in this string builder while parsing the code.
     */
    private static final StringBuilder plantUMLCode = new StringBuilder();

    /**
     * Processes the compilation unit (i.e. Class/Interface) and generates the
     * UML code for the compilation unit.
     *
     * @param compilationUnit The Class/Interface to be converted to Plant UML.
     */
    private static void processCompilationUnit(CompilationUnit compilationUnit) {

        // Processing one Class/Interface at a time
        new ClassRelationshipGenerator().visit(compilationUnit, null);
    }

    /**
     * Generates the UML code for a Class/Interface.
     */
    private static class ClassRelationshipGenerator extends VoidVisitorAdapter<Void> {

        /**
         * Visits the Class/Interface and generates the UML code for it.
         *
         * @param classDeclaration The Class/Interface to be converted to Plant UML.
         * @param arg              The argument to be passed to the visitor.
         */
        @Override
        public void visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {

            // Getting the name of the Interface/Class.
            String elementName = classDeclaration.getNameAsString();

            // The list of specialized types for the generic class/interface.
            List<String> specializedTypes = new ArrayList<>();

            // The list of extended types for the class/interface.
            StringBuilder extensionsBuilder = new StringBuilder();

            // The prefix to be added to the name of the class/interface. This
            // is helpful when there are conflicting names in the UML.
            StringBuilder namePrefix = new StringBuilder();

            if (!classDeclaration.getExtendedTypes().isEmpty()) {

                // Getting the extended types for the class/interface.
                for (int i = 0; i < classDeclaration.getExtendedTypes().size(); i++) {
                    String extendedTypeName = classDeclaration.getExtendedTypes().get(i).toString();

                    String regex = "(\\w+)\\s*<(.+)>";

                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(extendedTypeName);

                    // Checking if the class/interface is generic.
                    if (matcher.matches()) {

                        String genericType = matcher.group(2).split(",")[0];

                        // Adding the specialized type to the list.
                        specializedTypes.add("class \""
                                + matcher.group(1) + "<" + genericType + ">\""
                                + " as " + matcher.group(1) + "_" + genericType
                                + " { }");

                        extendedTypeName = matcher.group(1) + "_" + genericType;

                        namePrefix.append(genericType).append("_");
                    }

                    // Adding the extended types to the list.
                    extensionsBuilder.append(extendedTypeName);
                    if (i < classDeclaration.getExtendedTypes().size() - 1) {
                        extensionsBuilder.append(", ");
                    }
                }
            }

            // Deciding if the element is an interface or class
            plantUMLCode.append(classDeclaration.isInterface() ? "interface " : "class ").append(namePrefix).append(elementName);

            // Checking if the class/interface is extending other classes.
            if (!classDeclaration.getExtendedTypes().isEmpty()) {
                plantUMLCode.append(" extends ");
            }

            plantUMLCode.append(extensionsBuilder);

            if (!classDeclaration.getImplementedTypes().isEmpty()) {
                plantUMLCode.append(" implements ");

                // Getting the implemented types for the class/interface.
                for (int i = 0; i < classDeclaration.getImplementedTypes().size(); i++) {
                    plantUMLCode.append(classDeclaration.getImplementedTypes().get(i));
                    if (i < classDeclaration.getImplementedTypes().size() - 1) {
                        plantUMLCode.append(", ");
                    }
                }
            }

            plantUMLCode.append(" {\n");

            // Getting the fields for the class/interface.
            classDeclaration.getFields().forEach(field -> field.getVariables().forEach(variable -> {
                String fieldName = variable.getNameAsString();
                Type fieldType = field.getElementType();
                String fieldTypeString = fieldType.toString();

                // Print field
                plantUMLCode.append("\t").append("+ ").append(fieldName).append(": ").append(fieldTypeString).append("\n");

                if (fieldType.isClassOrInterfaceType()) {
                    String referencedTypeName =
                            fieldType.getElementType()
                                    .asString();
                    referencedTypeName = extractType(referencedTypeName);
                    if (!shouldIgnoreType(referencedTypeName)) {
                        String association = elementName + " - " + extractMultiplicity(referencedTypeName) + referencedTypeName;
                        associations.add(association);
                    }
                }
            }));

            // Getting the methods for the class/interface.
            classDeclaration.getMethods().forEach(method -> plantUMLCode.append("\t").append("+ ")
                    .append(isMethodStatic(method) ? "{static} " : "")
                    .append(method.getDeclarationAsString(false, false, false))
                    .append("\n"));

            plantUMLCode.append("}\n");

            for (String specializedType : specializedTypes) {
                plantUMLCode.append(specializedType).append("\n");
            }

            super.visit(classDeclaration, arg);
        }
    }

    /**
     * This method checks if the method is static or not.
     *
     * @param method The method to be checked.
     * @return True if the method is static, false otherwise.
     */
    private static boolean isMethodStatic(MethodDeclaration method) {
        return method.getModifiers().stream().anyMatch(
                (modifier -> modifier.toString().trim()
                        .equals("static")));
    }

    /**
     * Checks if the type should be ignored. Default types like String, Float,
     * etc. are ignored during generation of associations between classes.
     * 
     * @param typeName The name of the type to be checked.
     * @return True if the type should be ignored, false otherwise.
     */
    private static boolean shouldIgnoreType(String typeName) {
        return typeName.equals("String")
                || typeName.equals("Integer")
                || typeName.equals("Long")
                || typeName.equals("Double")
                || typeName.equals("Float")
                || typeName.equals("Character")
                || typeName.equals("Object")
                || typeName.equals("Boolean")
                || typeName.equals("Byte")
                || typeName.equals("Short")
                || typeName.equals("T");
    }

    /**
     * Extracts the multiplicity from the type name. For example, if the type
     * name is List<String>, then the multiplicity is extracted as "*".
     * 
     * @param typeName The name of the type from which the multiplicity is to be
     *                 extracted.
     * @return The multiplicity extracted from the type name.
     */
    private static String extractMultiplicity(String typeName) {
        String regex = "(\\w+)\\s*<(.+)>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(typeName);

        if (matcher.matches()) {
            return " \"*\" ";
        }
        else{
            return "";
        }
    }

    /**
     * Extracts the type from the type name. For example, if the type name is
     * List<String>, then the type is extracted as String.
     * 
     * @param typeName The name of the type from which the type is to be
     *                 extracted.
     * @return The type extracted from the type name.
     */
    private static String extractType(String typeName) {
        String regex = "(\\w+)\\s*<(.+)>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(typeName);

        if (matcher.matches()) {
            if (matcher.group(2).split(",").length > 1){
                return matcher.group(2).split(",")[1];
            }
            else{
                return matcher.group(2);
            }
        }
        else{
            return typeName;
        }
    }

    /**
     * Parses the directory and generates the Plant UML code for the classes
     * and interfaces in the directory. Each sub-folder in the directory is
     * parsed separately.
     * 
     * @param directoryPath The path of the directory to be parsed.
     */
    public static void parseDirectory(String directoryPath) {

        Path projectRoot = Paths.get(directoryPath);
        for (String root : subFolders) {

            // The path of the sub-folder to be parsed.
            Path sourceRootPath = projectRoot.resolve(root);
            SourceRoot sourceRoot = new SourceRoot(sourceRootPath);

            // The list of all the compilation units in the directory.
            List<ParseResult<CompilationUnit>> parseResults;

            try {
                // Parse the directory and extract the compilation units.
                parseResults = sourceRoot.tryToParse();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            parseResults.forEach(result -> {
                if (result.isSuccessful()) {

                    // A single file was parsed, process it.
                    result.getResult().ifPresent(Main::processCompilationUnit);
                } else {

                    // Failed to parse the file.
                    System.err.println(
                            "Failed to parse file: " + result.getProblems());
                }
            });
        }
    }

    public static void main(String[] args) {

        // Extracting source directory path from the command line arguments.
        if (args.length != 1) {
            System.out.println("Usage: ./javaCodeDirectoryParser <directory_path>");
            System.exit(1);
        }

        String directoryPath = args[0];

        // Converting the code at the source into plantuml code.
        parseDirectory(directoryPath);

        // Specify the output file path
        String outputPath = "output.puml";

        try (FileWriter writer = new FileWriter(outputPath)) {
            // Write PlantUML code to the file
            writer.write("@startuml\n");

            writer.write(plantUMLCode.toString());

            for (String association : associations) {
                writer.write(association + "\n");
            }

            writer.write("@enduml\n");

            System.out.println("PlantUML code has been written to: " + outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Error writing PlantUML code to file", e);
        }
    }
}
