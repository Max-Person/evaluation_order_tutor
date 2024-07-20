package org.swrlapi.example;

import its.model.definition.ClassDef;
import its.model.definition.Domain;
import its.model.definition.EnumValueRef;
import its.model.definition.ObjectDef;
import its.model.definition.loqi.DomainLoqiWriter;
import its.model.definition.rdf.DomainRDFFiller;
import its.model.definition.rdf.RDFUtils;
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
    
    private static void debugDumpLoqi(Domain model, String filename){
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
    
    
    public static Domain questionToDomainModel(
        Domain domainModel,
        Model base
    ){
        Domain situationDomain = new Domain();
        
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
            
            String className = className(baseToken, domainModel);
            ObjectDef resElement = newObject(situationDomain, "element_" + baseToken.getLocalName(), className);
            setEnumProperty(resElement,
                "state",
                "state",
                className.equals("operand") ? "evaluated" : "unevaluated"
            );
            ObjectDef resToken = resTokenFromBase(baseToken, resElement);
            
            Pair<String, String> loc = getLocalizedName(baseToken, baseTokens.indexOf(baseToken)+1, domainModel);
            addLocalizedName(resElement, loc);
            addLocalizedName(resToken, loc);
            
            baseTokensToTokens.put(baseToken, resToken);
            baseTokensToElements.put(baseToken, resElement);
            
            if(complexPairsMap.containsKey(baseToken)){
                Resource otherBaseToken = complexPairsMap.get(baseToken);
                ObjectDef otherResToken = resTokenFromBase(otherBaseToken, resElement);
                Pair<String, String> otherloc = getLocalizedName(otherBaseToken, baseTokens.indexOf(otherBaseToken)+1, domainModel);
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
        
        if(!selected.isEmpty()) {
            for (Resource baseToken : selected.subList(0, selected.size() - 1)) {
                setEnumProperty(
                    baseTokensToElements.get(baseToken),
                    "state",
                    "state", "evaluated"
                );
                baseToken.listProperties(base.getProperty(BASE_TTL_PREF + "has_operand")).toList().stream()
                    .map(s -> s.getObject().asResource())
                    .filter(resource -> !resource.equals(complexPairsMap.get(baseToken)))
                    .map(baseTokensToElements::get)
                    .forEach(operand -> {
                        setEnumProperty(
                            operand,
                            "state",
                            "state", "used"
                        );
                    });
            }
            Resource currentlyChosenRes = selected.get(selected.size() - 1);
            if(baseTokensToElements.containsKey(currentlyChosenRes)){
                newVariable(situationDomain, "X", baseTokensToElements.get(currentlyChosenRes).getName());
                newVariable(situationDomain, "X1", baseTokensToTokens.get(currentlyChosenRes).getName());
            }
        }
        
        debugDumpLoqi(situationDomain, "out.loqi");
        situationDomain.addMerge(domainModel);
        situationDomain.validateAndThrow();
        return situationDomain;
    }
    
    private static ObjectDef resTokenFromBase(Resource baseToken, ObjectDef resElement){
        Domain situation = resElement.getDomain();
        ObjectDef token = newObject(situation, "token_" + baseToken.getLocalName(), "token");
        addRelationship(resElement, "has", token.getName());
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
        return baseToken.getProperty(baseToken.getModel().getProperty(BASE_TTL_PREF + "index")).getInt();
    }
    
    private static void addLocalizedName(ObjectDef object, Pair<String, String> localization){
        addMeta(object, "RU", "localizedName", localization.getLeft());
        addMeta(object, "EN", "localizedName", localization.getRight());
    }
    
    private static Pair<String, String> getLocalizedName(Resource baseToken, int index, Domain domainModel){
        String classname = className(baseToken, domainModel);
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
        ru += " на позиции " + index;
        en += " at position " + index;
        return Pair.of(ru, en);
    }
    
    private static String getText(Resource baseToken){
        return baseToken.getProperty(baseToken.getModel().getProperty(BASE_TTL_PREF + "text")).getString();
    }
    
    private static String className(Resource baseToken, Domain domainModel){
        String text = getText(baseToken);
        Property hasOperandProperty = baseToken.getModel().getProperty(BASE_TTL_PREF + "has_operand");
        int operandCount = baseToken.listProperties(hasOperandProperty).toList().size();
        boolean isPrefix = operandCount == 1
            && getIndex(baseToken) < getIndex(baseToken.getProperty(hasOperandProperty).getResource());
        
        List<ClassDef> possibleClasses = domainModel.getClasses().stream()
            .filter(classDef -> classDef.getMetadata().entrySet().stream()
                .anyMatch(metadata -> metadata.getKey().getName().contains("text") && text.equals(metadata.getValue()))
            )
            .toList();
        if(possibleClasses.isEmpty() && text.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")){
            return "operand";
        }
        if(possibleClasses.size() == 1){
            return possibleClasses.get(0).getName();
        }
        return possibleClasses.stream()
            .filter(classDef ->
                new EnumValueRef("arity", "binary").equals(classDef.getPropertyValue("arity"))
                    == (operandCount == 2)
                && new EnumValueRef("place", "prefix").equals(classDef.getPropertyValue("place"))
                    == isPrefix
            )
            .findFirst().orElseThrow().getName();
    }
    
    public static void saveModel(String filename, Model model){
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
}
