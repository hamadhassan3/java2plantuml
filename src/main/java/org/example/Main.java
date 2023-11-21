package org.example;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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

    private static Set<String> associations = new HashSet<>();
    private static StringBuilder plantUMLCode = new StringBuilder();

    private static void processCompilationUnit(CompilationUnit compilationUnit) {
        new ClassRelationshipGenerator().visit(compilationUnit, null);
    }

    private static class ClassRelationshipGenerator extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classDeclaration, Void arg) {
            String name = classDeclaration.getNameAsString();

            plantUMLCode.append(classDeclaration.isInterface() ? "interface " : "class ").append(name);

            List<String> specializedTypes = new ArrayList<>();

            if (!classDeclaration.getExtendedTypes().isEmpty()) {
                plantUMLCode.append(" extends ");
                for (int i = 0; i < classDeclaration.getExtendedTypes().size(); i++) {
                    String extendedTypeName = classDeclaration.getExtendedTypes().get(i).toString();

                    String regex = "(\\w+)\\s*<(.+)>";

                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(extendedTypeName);

                    if (matcher.matches()) {

                        String genericType = matcher.group(2).split(",")[0];

                        specializedTypes.add("class \""
                                + matcher.group(1) + "<" + genericType + ">\""
                                + " as " + matcher.group(1) + "_" + genericType
                                + " { }");

                        extendedTypeName = matcher.group(1) + "_" + genericType;
                    }

                    plantUMLCode.append(extendedTypeName);
                    if (i < classDeclaration.getExtendedTypes().size() - 1) {
                        plantUMLCode.append(", ");
                    }
                }
            }
            if (!classDeclaration.getImplementedTypes().isEmpty()) {
                plantUMLCode.append(" implements ");
                for (int i = 0; i < classDeclaration.getImplementedTypes().size(); i++) {
                    plantUMLCode.append(classDeclaration.getImplementedTypes().get(i));
                    if (i < classDeclaration.getImplementedTypes().size() - 1) {
                        plantUMLCode.append(", ");
                    }
                }
            }

            plantUMLCode.append(" {\n");

            classDeclaration.getFields().forEach(field -> {
                field.getVariables().forEach(variable -> {
                    String fieldName = variable.getNameAsString();
                    Type fieldType = field.getElementType();
                    String fieldTypeString = fieldType.toString();

                    // Print field
                    plantUMLCode.append("\t").append("+ ").append(fieldName).append(": ").append(fieldTypeString).append("\n");

                    if (fieldType.isClassOrInterfaceType()) {
                        String referencedTypeName =
                                fieldType.getElementType()
                                        .asString();
                        if (!shouldIgnoreType(referencedTypeName)) {
                            String association = name + " - " + extractType(referencedTypeName);
                            associations.add(association);
                        }
                    }
                });
            });

            classDeclaration.getMethods().forEach(method -> {
                    plantUMLCode.append("\t").append("+ ").append(method.getDeclarationAsString(false, false, false)).append("\n");
            });

            plantUMLCode.append("}\n");

            for (String specializedType : specializedTypes) {
                plantUMLCode.append(specializedType).append("\n");
            }

            super.visit(classDeclaration, arg);
        }
    }

    private static boolean shouldIgnoreType(String typeName) {
        return typeName.equals("String")
                || typeName.equals("Integer")
                || typeName.equals("Long")
                || typeName.equals("Double")
                || typeName.equals("Float")
                || typeName.equals("Character")
                || typeName.equals("Object");
    }

    private static String extractType(String typeName) {
        String regex = "(\\w+)\\s*<(.+)>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(typeName);

        if (matcher.matches()) {
            if (matcher.group(2).split(",").length > 1){
                return " \"*\" " + matcher.group(2).split(",")[1];
            }
            else{
                return " \"*\" " + matcher.group(2);
            }
        }
        else{
            return typeName;
        }
    }

    public static void parseDirectory(String directoryPath) {

        String[] roots = {
                "models/repositories",
                "services",
                "controllers"

        };
        Path projectRoot = Paths.get(directoryPath);
        for (String root : roots) {

            Path sourceRootPath = projectRoot.resolve(root);
            SourceRoot sourceRoot = new SourceRoot(sourceRootPath);

            List<ParseResult<CompilationUnit>> parseResults = null;
            try {
                parseResults = sourceRoot.tryToParse();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            parseResults.forEach(result -> {
                if (result.isSuccessful()) {
                    CompilationUnit compilationUnit =
                            result.getResult().orElse(null);
                    if (compilationUnit != null) {
                        // Process the CompilationUnit as needed
                        processCompilationUnit(compilationUnit);
                    }
                } else {
                    System.err.println(
                            "Failed to parse file: " + result.getProblems());
                }
            });
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: JavaCodeDirectoryParser <directory_path>");
            System.exit(1);
        }

        String directoryPath = args[0];

        parseDirectory(directoryPath);

        // Specify the output file path
        String outputPath = "output.puml";

        try (FileWriter writer = new FileWriter(outputPath)) {
            // Write PlantUML code to the file
            writer.write("@startuml\n");

            writer.write(plantUMLCode.toString());

//            for (String association : associations) {
//                writer.write(association + "\n");
//            }

            writer.write("@enduml\n");

            System.out.println("PlantUML code has been written to: " + outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Error writing PlantUML code to file", e);
        }
    }
}
