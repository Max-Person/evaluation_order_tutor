package org.swrlapi.example;

import its.model.definition.ClassDef;
import its.model.definition.Domain;
import its.model.definition.EnumValueRef;
import its.model.definition.ObjectDef;
import its.model.definition.loqi.DomainLoqiWriter;
import its.model.definition.rdf.DomainRDFFiller;
import its.model.definition.rdf.RDFUtils;
import its.model.nodes.DecisionTree;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static its.model.definition.build.DomainBuilderUtils.*;


public class ProgrammingLanguageExpressionDomainBuilder {
    
    private static final String DEBUG_DIR = "C:\\Uni\\CompPrehension_mainDir\\ontology_evaluation_order_check\\src\\main\\resources\\decision_trees\\";
    private static final String BASE_TTL_PREF = "http://www.test/test.owl#";
    
    public static void debugDumpLoqi(Domain model, String filename){
        if(!ENABLE_DEBUG_SAVE) return;
        try {
            DomainLoqiWriter.saveDomain(
                model,
                new FileWriter(DEBUG_DIR + filename),
                new HashSet<>()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public record ParsedDomain(Domain domain, List<Integer> errorPos){}
    
    public static ParsedDomain questionToDomainModel(
        Domain domainModel,
        Map<String, DecisionTree> decisionTreeMap,
        Model base,
        boolean includeLastSelection
    ){
        Domain situationDomain = new Domain();
        ParsedDomain parseResult = new ParsedDomain(situationDomain, new ArrayList<>());
        
        Property studentPosProperty = base.getProperty(BASE_TTL_PREF + "student_pos_number");
        List<Resource> selected = base.listSubjectsWithProperty(studentPosProperty)
            .toList().stream()
            .sorted(Comparator.comparing(resource -> resource.getProperty(studentPosProperty).getInt()))
            .toList();
        
        saveModel("base.ttl", base);
        Property indexProperty = base.getProperty(BASE_TTL_PREF + "index");
        List<Resource> baseTokens = base.listSubjectsWithProperty(indexProperty).toList().stream()
            .sorted(Comparator.comparingInt((a) -> a.getProperty(indexProperty).getInt()))
            .collect(Collectors.toList());
        
        Map<Resource, ObjectDef> baseTokensToTokens = new HashMap<>();
        Map<Resource, ObjectDef> baseTokensToElements = new HashMap<>();
        Map<Resource, Resource> complexPairsMap = getComplexPairsMap(baseTokens);
        
        for(Resource baseToken : baseTokens){
            if(baseTokensToTokens.containsKey(baseToken))
                continue;
            
            ParsedClassName parsedClassName = className(baseToken, domainModel);
            String className = parsedClassName.className;
            if(!parsedClassName.isCorrect){
                parseResult.errorPos.add(getIndex(baseToken));
            }
            ObjectDef resElement = newObject(situationDomain, "element_" + baseToken.getLocalName(), className);
            setEnumProperty(resElement,
                "state",
                "state",
                className.equals("operand") ? "evaluated" : "unevaluated"
            );
            if(!"parenthesis".equals(className)) setBoolProperty(resElement, "evaluatesTo", true);
            ObjectDef resToken = resTokenFromBase(baseToken, resElement);
            
            Pair<String, String> loc = getLocalizedName(baseToken, className);
            addLocalizedName(resElement, loc);
            addLocalizedName(resToken, loc);
            
            baseTokensToTokens.put(baseToken, resToken);
            baseTokensToElements.put(baseToken, resElement);
            
            if(complexPairsMap.containsKey(baseToken)){
                Resource otherBaseToken = complexPairsMap.get(baseToken);
                ObjectDef otherResToken = resTokenFromBase(otherBaseToken, resElement);
                Pair<String, String> otherloc = getLocalizedName(otherBaseToken, className);
                addLocalizedName(otherResToken, otherloc);
                
                baseTokensToTokens.put(otherBaseToken, otherResToken);
            }
            
        }
        
        for(Resource baseToken : baseTokens.subList(1, baseTokens.size())){
            int tokenIndex = baseTokens.indexOf(baseToken);
            ObjectDef resToken = baseTokensToTokens.get(baseToken);
            ObjectDef previousResToken = baseTokensToTokens.get(baseTokens.get(tokenIndex - 1));
            addRelationship(previousResToken, "directlyLeftOf", resToken.getName());
        }
        
        situationDomain.addMerge(domainModel);
        situationDomain.validateAndThrow();
        
        ProgrammingLanguageExpressionsSolver solver = new ProgrammingLanguageExpressionsSolver();
        solver.solveTree(situationDomain, decisionTreeMap);
        solver.solveStrict(situationDomain, decisionTreeMap);
        
        
        for (Resource baseToken : selected) {
            if(!includeLastSelection
                && baseToken == selected.get(selected.size() - 1)
                && baseTokensToElements.containsKey(baseToken)
            ){
                //в зависимости от контекста, последний выбранный объект делаем переменной, и не записываем факт его вычисления
                newVariable(situationDomain, "X", baseTokensToElements.get(baseToken).getName());
                newVariable(situationDomain, "X1", baseTokensToTokens.get(baseToken).getName());
                continue;
            }
            
            ObjectDef operator = baseTokensToElements.get(baseToken);
            setEnumProperty(
                operator,
                "state",
                "state", "evaluated"
            );
            situationDomain.getObjects().stream()
                .filter(object -> object.isInstanceOf("operand") &&
                    !object.getRelationshipLinks().listByName("isOperandOf").isEmpty() &&
                    object.getRelationshipLinks().listByName("isOperandOf").stream()
                        .allMatch(link -> link.getObjects().get(0) == operator)
                )
                .forEach(operand -> {
                    setEnumProperty(
                        operand,
                        "state",
                        "state", "used"
                    );
                });
        }
        
        
        situationDomain.validateAndThrow();
        debugDumpLoqi(situationDomain, "out.loqi");
        return parseResult;
    }
    
    private static ObjectDef resTokenFromBase(Resource baseToken, ObjectDef resElement){
        Domain situation = resElement.getDomain();
        ObjectDef token = newObject(situation, "token_" + baseToken.getLocalName(), "token");
        addRelationship(resElement, "has", token.getName());
        addMeta(token, "index", getIndex(baseToken));
        return token;
    }
    
    private static Map<Resource, Resource> getComplexPairsMap(List<Resource> baseTokens){
        Map<Resource, Resource> map = new HashMap<>();
        for(int i = 0; i < baseTokens.size(); i++){
            Resource currentToken = baseTokens.get(i);
            Resource complexEnd = getOtherComplex(baseTokens, i);
            if(complexEnd != null){
                map.put(currentToken, complexEnd);
            }
        }
        return map;
    }
    
    private static Resource getOtherComplex(List<Resource> baseTokens, int index){
        Resource baseToken = baseTokens.get(index);
        int nesting = 0;
        for(int i = index; i < baseTokens.size(); i++){
            Resource currentToken = baseTokens.get(i);
            if(getText(baseToken).equals(getText(currentToken))){
                nesting+=1;
            }
            else if(isComplexEnd(baseToken, currentToken)){
                nesting-=1;
                if(nesting == 0){
                    return currentToken;
                }
            }
        }
        return null;
    }
    
    private static boolean isComplexEnd(Resource begin, Resource end){
        return getText(end).equals(Map.of(
            "[", "]",
            "(", ")",
            "?", ":",
            "if", "else"
        ).get(getText(begin)));
    }
    
    private static int getIndex(Resource baseToken){
        return baseToken.getProperty(baseToken.getModel().getProperty(BASE_TTL_PREF + "index")).getInt() - 1;
    }
    
    private static void addLocalizedName(ObjectDef object, Pair<String, String> localization){
        addMeta(object, "RU", "localizedName", localization.getLeft());
        addMeta(object, "EN", "localizedName", localization.getRight());
    }
    
    private static Pair<String, String> getLocalizedName(Resource baseToken, String classname){
        String ru;
        String en;
        if(classname.equals("parenthesis")){
            ru = "скобки";
            en = "parenthesis";
        }
        else if(classname.equals("operand")){
            ru = "операнд " + getText(baseToken);
            en = "variable " + getText(baseToken);
        }
        else {
            ru = "оператор " + getText(baseToken);
            en = "operator " + getText(baseToken);
        }
        int pos = getIndex(baseToken) + 1;
        ru += " на позиции " + pos;
        en += " at position " + pos;
        return Pair.of(ru, en);
    }
    
    private static String getText(Resource baseToken){
        return baseToken.getProperty(baseToken.getModel().getProperty(BASE_TTL_PREF + "text")).getString();
    }
    
    private record ParsedClassName(String className, boolean isCorrect){
        ParsedClassName(String className){
            this(className, true);
        }
    }
    
    private static ParsedClassName className(Resource baseToken, Domain domainModel){
        String text = getText(baseToken);
        
        List<ClassDef> possibleClasses = domainModel.getClasses().stream()
            .filter(classDef -> classDef.getMetadata().entrySet().stream()
                .anyMatch(metadata -> metadata.getKey().getName().contains("text") && text.equals(metadata.getValue()))
            )
            .toList();
        if(possibleClasses.isEmpty()){
            return new ParsedClassName("operand", text.matches("[a-zA-Z_$][a-zA-Z0-9_$]*"));
        }
        if(possibleClasses.size() == 1){
            return new ParsedClassName(possibleClasses.get(0).getName());
        }
        
        Property precedenceProperty = baseToken.getModel().getProperty(BASE_TTL_PREF + "precedence");
        int tokenPrecedence = baseToken.getProperty(precedenceProperty).getInt();
        return new ParsedClassName(
            possibleClasses.stream()
                .filter(classDef -> Integer.valueOf(tokenPrecedence).equals(classDef.getPropertyValue("precedence")))
                .findFirst()
                .map(ClassDef::getName)
                .orElseThrow()
        );
    }
    
    public static void saveModel(String filename, Model model){
        if(!ENABLE_DEBUG_SAVE) return;
        try {
            OutputStream out = new FileOutputStream(DEBUG_DIR + filename);
            model.write(out, "TTL");
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static final boolean ENABLE_DEBUG_SAVE = false;
}
