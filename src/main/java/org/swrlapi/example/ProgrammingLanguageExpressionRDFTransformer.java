package org.swrlapi.example;

import its.model.definition.ClassDef;
import its.model.definition.Domain;
import its.model.definition.EnumValueRef;
import its.model.definition.loqi.DomainLoqiWriter;
import its.model.definition.rdf.DomainRDFFiller;
import its.model.definition.rdf.RDFUtils;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.vstu.compprehension.models.businesslogic.domains.ProgrammingLanguageExpressionDomain;
import org.vstu.compprehension.models.entities.BackendFactEntity;
import org.vstu.compprehension.models.entities.QuestionEntity;
import org.vstu.compprehension.models.entities.ResponseEntity;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


public class ProgrammingLanguageExpressionRDFTransformer {
    
    private static final String DEBUG_DIR = "C:\\Uni\\CompPrehension_mainDir\\ontology_evaluation_order_check\\src\\main\\resources\\decision_trees\\";
    private static final String BASE_TTL_PREF = "http://www.test/test.owl#";
    
    private static void debugDumpLoqi(its.model.definition.Domain model, String filename){
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
    
    public static its.model.definition.Domain questionToDomainModel(
        Domain commonDomainModel,
        Model base
    ){
        Model m = questionToModel(commonDomainModel, base);
        its.model.definition.Domain situationModel = commonDomainModel.getDomain().copy();
        DomainRDFFiller.fillDomain(
            situationModel,
            m,
            Collections.singleton(DomainRDFFiller.Option.NARY_RELATIONSHIPS_OLD_COMPAT),
            null
        );
        situationModel.validateAndThrowInvalid();
        
        val dumpModel = situationModel.copy();
        dumpModel.subtract(commonDomainModel);
        debugDumpLoqi(dumpModel, "out.loqi");
        return situationModel;
    }
    
    public static Model questionToModel(
        Domain commonDomainModel,
        Model base
    ){
        Property studentPosProperty = base.getProperty(BASE_TTL_PREF + "student_pos_number");
        List<Resource> selected = base.listSubjectsWithProperty(studentPosProperty)
            .toList().stream()
            .sorted(Comparator.comparing(resource -> resource.getProperty(studentPosProperty).getInt()))
            .collect(Collectors.toList());
        
        saveModel("base.ttl", base);
        Model res = ModelFactory.createDefaultModel();
        res.setNsPrefix("", RDFUtils.POAS_PREF);
        Property indexProperty = base.getProperty(BASE_TTL_PREF + "index");
        Property typeProperty = res.getProperty(RDFUtils.RDF_PREF + "type");
        Property leftOfProperty = res.getProperty(RDFUtils.POAS_PREF + "directlyLeftOf");
        Property stateProperty = res.getProperty(RDFUtils.POAS_PREF + "state");
        Property varProperty = res.getProperty(RDFUtils.POAS_PREF + "var...");
        Property ruProperty = res.getProperty(RDFUtils.POAS_PREF + "RU_localizedName");
        Property enProperty = res.getProperty(RDFUtils.POAS_PREF + "EN_localizedName");
        List<Resource> baseTokens = base.listSubjectsWithProperty(indexProperty).toList().stream()
            .sorted(Comparator.comparingInt((a) -> a.getProperty(indexProperty).getInt()))
            .collect(Collectors.toList());
        Map<Resource, Resource> baseTokensToTokens = new HashMap<>();
        Map<Resource, Resource> baseTokensToElements = new HashMap<>();
        Map<Resource, Resource> complexPairsMap = getComplexPairsMap(baseTokens);
        for(Resource baseToken : baseTokens){
            if(baseTokensToTokens.containsKey(baseToken))
                continue;
            Resource resElement = getResource(res,"element_" + baseToken.getLocalName());
            resElement.addProperty(typeProperty, getClassResource(baseToken, res, commonDomainModel));
            resElement.addProperty(
                stateProperty,
                getResource(res, className(baseToken, commonDomainModel).equals("operand")? "evaluated" : "unevaluated")
            );
            Resource resToken = resTokenFromBase(baseToken, resElement);
            
            Pair<String, String> loc = getLocalizedName(baseToken, baseTokens.indexOf(baseToken)+1, commonDomainModel);
            resElement.addProperty(ruProperty, loc.getLeft());
            resElement.addProperty(enProperty, loc.getRight());
            resToken.addProperty(ruProperty, loc.getLeft());
            resToken.addProperty(enProperty, loc.getRight());
            
            baseTokensToTokens.put(baseToken, resToken);
            baseTokensToElements.put(baseToken, resElement);
            
            if(complexPairsMap.containsKey(baseToken)){
                Resource otherBaseToken = complexPairsMap.get(baseToken);
                Resource otherResToken = resTokenFromBase(otherBaseToken, resElement);
                Pair<String, String> otherloc = getLocalizedName(otherBaseToken, baseTokens.indexOf(otherBaseToken)+1, commonDomainModel);
                otherResToken.addProperty(ruProperty, otherloc.getLeft());
                otherResToken.addProperty(enProperty, otherloc.getRight());
                
                baseTokensToTokens.put(otherBaseToken, otherResToken);
            }
            
        }
        
        for(Resource baseToken : baseTokens.subList(1, baseTokens.size())){
            int tokenIndex = baseTokens.indexOf(baseToken);
            Resource resToken = baseTokensToTokens.get(baseToken);
            Resource previousResToken = baseTokensToTokens.get(baseTokens.get(tokenIndex - 1));
            previousResToken.addProperty(leftOfProperty, resToken);
        }
        
        if(!selected.isEmpty()) {
            for (Resource baseToken : selected.subList(0, selected.size() - 1)) {
                baseTokensToElements.get(baseToken).removeAll(stateProperty);
                baseTokensToElements.get(baseToken).addProperty(stateProperty, getResource(res, "evaluated"));
                baseToken.listProperties(base.getProperty(BASE_TTL_PREF + "has_operand")).toList().stream()
                    .map(s -> s.getObject().asResource())
                    .filter(resource -> !resource.equals(complexPairsMap.get(baseToken)))
                    .map(baseTokensToElements::get)
                    .forEach(operand -> {
                        operand.removeAll(stateProperty);
                        operand.addProperty(stateProperty, getResource(res, "used"));
                    });
            }
            Resource currentlyChosenRes = selected.get(selected.size() - 1);
            if(baseTokensToElements.containsKey(currentlyChosenRes)){
                baseTokensToElements.get(currentlyChosenRes).addProperty(varProperty, "X");
                baseTokensToTokens.get(currentlyChosenRes).addProperty(varProperty, "X1");
            }
        }
        saveModel("res.ttl", res);
        return res;
    }
    
    private static Resource resTokenFromBase(Resource baseToken, Resource resElement){
        Model res = resElement.getModel();
        Property typeProperty = res.getProperty(RDFUtils.RDF_PREF + "type");
//        Property belongsProperty = res.getProperty(RDFUtils.POAS_PREF + "belongsTo");
        Property hasProperty = res.getProperty(RDFUtils.POAS_PREF + "has");
        
        Resource resToken = getResource(res, "token_" + baseToken.getLocalName());
        resToken.addProperty(typeProperty, getResource(res,"token"));
//        resToken.addProperty(belongsProperty, resElement);
        resElement.addProperty(hasProperty, resToken);
        return resToken;
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
//        Property arityProperty = baseToken.getModel().getProperty(BASE_TTL_PREF + "arity");
//        Property placeProperty = baseToken.getModel().getProperty(BASE_TTL_PREF + "prefix_postfix");
        
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
    
    private static Resource getClassResource(Resource baseToken, Model res, Domain domainModel){
        return getResource(res, className(baseToken, domainModel));
    }
    
    private static Resource getResource(Model model, String localName){
        return model.getResource(RDFUtils.POAS_PREF + localName);
    }
    
    public static void saveModel(String filename, Model model){
//        if(true) return;
//        log.info("saving {}", filename);
        OutputStream out = null;
        try {
            out = new FileOutputStream(DEBUG_DIR + filename);
            model.write(out, "TTL");
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
