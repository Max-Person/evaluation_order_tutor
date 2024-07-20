package org.swrlapi.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import its.model.DomainSolvingModel;
import org.vstu.compprehension.models.businesslogic.Question;
import org.vstu.compprehension.models.businesslogic.domains.Domain;
import org.vstu.compprehension.models.businesslogic.domains.ProgrammingLanguageExpressionDomain;
import org.vstu.compprehension.models.entities.AnswerObjectEntity;
import org.vstu.compprehension.models.entities.ViolationEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static org.swrlapi.example.OntologyUtil.*;

class MessageToken {
    String text;
    Integer check_order;
    String status;
    String additional_info;
    Boolean enabled;
}

class Message {
    List<MessageToken> expression;
    List<MessageToken> answers;
    List<OntologyUtil.Error> errors;
    String lang;
    String task_lang;
    String action;
    String type;
    String text;
}

public class JsonRequester {
    // {"expression":[{"text":"a"},{"text":"["},{"text":"i"},{"text":"+"},{"text":"1"},{"text":"]"},{"text":"["},{"text":"j"},{"text":"]"}],"errors":[],"lang":"en", "task_lang":"cpp"}
    // {"expression":[{"text":"a"},{"text":"?"},{"text":"("},{"text":"b"},{"text":","},{"text":"c"},{"text":")"},{"text":":"},{"text":"f"},{"text":"("},{"text":"b"},{"text":","},{"text":"c"},{"text":")"}],"errors":[],"lang":"en", "task_lang":"cpp"}
    // {"expression":[{"text":"a"},{"text":"."},{"text":"b"},{"text":"("},{"text":"c"},{"text":"+"},{"text":"1"},{"text":","},{"text":"d"},{"text":")"}],"errors":[],"lang":"en", "task_lang":"cpp"}
    // {"expression":[{"text":"a","check_order":1000,"enabled":false},{"text":".","check_order":1000,"enabled":true},{"text":"b","check_order":1000,"enabled":false},{"text":"(","check_order":1,"status":"wrong","enabled":true},{"text":"c","check_order":1000,"enabled":false},{"text":"+","check_order":1000,"enabled":true},{"text":"1","check_order":1000,"enabled":false},{"text":",","check_order":1000,"enabled":false},{"text":"d","check_order":1000,"enabled":false},{"text":")","check_order":1000,"enabled":false}],"errors":[{"parts":[{"text":"operator + at pos 6","type":"operator","index":6},{"text":"should be evaluated before","type":"plain_text"},{"text":"parenthesis ( at pos 4,","type":"operator","index":4},{"text":"because","type":"plain_text"},{"text":"function arguments","type":"term"},{"text":"are evaluated before","type":"plain_text"},{"text":"function call","type":"term"}]}],"lang":"en","task_lang":"cpp","action":"get_supplement","type":"main"}
    // {"expression":[{"text":"a","check_order":1000,"enabled":false},{"text":".","check_order":1000,"enabled":true},{"text":"b","check_order":1000,"enabled":false},{"text":"(","check_order":1000,"enabled":true},{"text":"c","check_order":1000,"enabled":false},{"text":"+","check_order":1,"status":"wrong","enabled":true},{"text":"1","check_order":1000,"enabled":false},{"text":"*","check_order":1000,"enabled":true},{"text":"d","check_order":1000,"enabled":false},{"text":")","check_order":1000,"enabled":false}],"errors":[{"parts":[{"text":"operator * at pos 8","type":"operator","index":8},{"text":"should be evaluated before","type":"plain_text"},{"text":"operator + at pos 6,","type":"operator","index":6},{"text":"because","type":"plain_text"},{"text":"operator *","type":"operator","index":8},{"text":"has higher","type":"plain_text"},{"text":"precedence","type":"term"}],"type":"error_base_higher_precedence_right"}],"lang":"en","task_lang":"cpp","action":"get_supplement","type":"main"}
    // {"expression":[{"text":"a","check_order":1000,"enabled":false},{"text":".","check_order":1000,"enabled":true},{"text":"b","check_order":1000,"enabled":false},{"text":"(","check_order":1000,"enabled":true},{"text":"c","check_order":1000,"enabled":false},{"text":"+","check_order":1,"enabled":true},{"text":"1","check_order":1000,"enabled":false},{"text":"*","check_order":1000,"enabled":true},{"text":"d","check_order":1000,"enabled":false},{"text":")","check_order":1000,"enabled":false}],"answers":[{"text":"precedence of operator c at pos 5","status":"correct","additional_info":"select_highest_precedence_left_operator","enabled":true},{"text":"associativity of operator c at pos 5","status":"wrong","additional_info":"select_precedence_or_associativity_left_influence","enabled":true},{"text":"precedence of operator 1 at pos 7","status":"correct","additional_info":"select_highest_precedence_right_operator","enabled":true},{"text":"associativity of operator 1 at pos 7","status":"wrong","additional_info":"select_precedence_or_associativity_right_influence","enabled":true}],"errors":[{"parts":[], "type":"select_precedence_or_associativity_right_influence"}],"lang":"en","task_lang":"cpp","action":"get_supplement","type":"supplementary","text":"What prevents evaluation of operator + at pos 6 ?"}
    // {"expression":[{"text":"a","check_order":1000,"enabled":false},{"text":".","check_order":1000,"enabled":true},{"text":"b","check_order":1000,"enabled":false},{"text":"(","check_order":1000,"enabled":true},{"text":"c","check_order":1000,"enabled":false},{"text":"+","check_order":1,"enabled":true},{"text":"1","check_order":1000,"enabled":false},{"text":"*","check_order":1000,"enabled":true},{"text":"d","check_order":1000,"enabled":false},{"text":")","check_order":1000,"enabled":false}],"answers":[{"text":"precedence","status":"correct","additional_info":"select_highest_precedence_right_operator","enabled":true},{"text":"associativity","status":"wrong","additional_info":"error_select_precedence_or_associativity_left","enabled":true}],"errors":[{"parts":[], "type":"select_highest_precedence_right_operator"}],"lang":"en","task_lang":"cpp","action":"get_supplement","type":"supplementary","text":"What influences evaluation order at first?"}
    // {"expression":[{"text":"a"},{"text":"<"},{"text":"b"},{"text":"*"},{"text":"c"},{"text":"*"},{"text":"d"},{"text":"+"},{"text":"e"},{"text":"*"},{"text":"f"}],"errors":[],"lang":"en", "task_lang":"cpp", "action":"next_step"}
    
    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        JsonRequester requester = new JsonRequester();
        while (true){
            System.out.print("\nexpecting input JSON: ");
            String jsonString = reader.readLine();
            System.out.println(requester.response(jsonString));
        }
    }

    public String response(String request){
        Message message;
        try {
            message = new Gson().fromJson(
                request,
                Message.class
            );
        } catch (java.lang.Exception xcp) {
            message = new Message();
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(getResponse(message));
    }
    
    private Message getResponse(Message message) {
        prepareMessageDefaults(message);
        
        if (message.action.equals("supported_languages")){
            return getSupportedLanguagesResponse(message);
        } else if (message.action.equals("supported_task_languages")) {
            return getSupportedProgrammingLanguagesResponse(message);
        }

        if (message.action.equals("get_supplement")) {
            return getSupplementaryResponse(message);
        }

        message.errors = new ArrayList<>();
        message.type = "main";
        parse(message);

        if (message.action.equals("find_errors")) {
            return getFindErrorsResponse(message);
        } else if (message.action.equals("next_step")) {
            return getNextStepResponse(message);
        }

        return null;
    }
    
    private void prepareMessageDefaults(Message message){
        if (message == null) {
            message = new Message();
        }
        if (message.expression == null) {
            message.expression = new ArrayList<>();
        }
        if (message.lang == null) {
            message.lang = "en";
        }
        if (message.task_lang == null) {
            message.task_lang = "cpp";
        }
        if (message.action == null) {
            message.action = "find_errors";
        }
        if (message.type == null) {
            message.type = "main";
        }
        
        for (MessageToken token : message.expression) {
            if (token.status != null && token.status.equals("wrong")) {
                token.status = null;
            }
            if (token.check_order == null) {
                token.check_order = 1000;
            }
            if (token.text == null) {
                token.text = "";
            }
        }
    }
    
    private void setMessageTokens(Message message, String... tokens){
        Arrays.stream(tokens).forEach(tokenStr -> {
            MessageToken token = new MessageToken();
            token.text = tokenStr;
            message.expression.add(token);
        });
    }
    
    private Message getSupportedLanguagesResponse(Message message){
        setMessageTokens(message, "en", "ru");
        return message;
    }
    
    private Message getSupportedProgrammingLanguagesResponse(Message message){
        setMessageTokens(message, "cpp", "cs", "python");
        return message;
    }
    
    private String getProgrammingLanguage(Message message){
        if (message.task_lang.equals("cpp")) {
            return  "C++";
        } else if (message.task_lang.equals("cs")) {
            return  "C#";
        } else {
            return  "Python";
        }
    }
    
    private OntologyHelper getOntologyHelper(Message message){
        return new OntologyHelper(
            createExpressionFromMessage(message),
            getProgrammingLanguage(message)
        );
    }
    
    private void parse(Message message){
        OntologyHelper helper = getOntologyHelper(message);
        
        Set<Integer> validTokenPositions = getGoodPositions(helper);
        Set<Integer> operandsPos = getOperandPositions(helper);
        
        for (int index = 0; index < message.expression.size(); index++) {
            int pos = index + 1;
            MessageToken token = message.expression.get(index);
            token.enabled = !operandsPos.contains(index);
            if (!validTokenPositions.contains(index)) {
                token.status = "wrong";
                OntologyUtil.Error result = new OntologyUtil.Error();
                if (message.lang.equals("ru")) {
                    result.add(new ErrorPart(
                        "Токен на позиции " + pos,
                        "operator",
                        pos
                    )).add(new ErrorPart(
                        "не распознан, возможно не все части разделены пробелами или оператор не поддержан"
                    ));
                } else {
                    result.add(new ErrorPart(
                        "Token at pos " + pos,
                        "operator",
                        pos
                    )).add(new ErrorPart(
                        "is not correct, try to separate all parts with spaces or operator is not supported"
                    ));
                }
                message.errors.add(result);
            }
        }
    }
    
    private Expression createExpressionFromMessage(Message message){
        return new Expression(
            message.expression.stream().map(token -> {
                Term term = new Term(token.text);
                term.setStudentPos(token.check_order);
                return term;
            }).toList()
        );
    }
    
    
    private static final String DOMAIN_MODEL_LOCATION = "decision_trees/";
    private final DomainSolvingModel domainSolvingModel = new DomainSolvingModel(
        this.getClass().getClassLoader().getResource(DOMAIN_MODEL_LOCATION),
        DomainSolvingModel.BuildMethod.LOQI
    );
    
    private Message getFindErrorsResponse(Message message){
        OntologyHelper helper = getOntologyHelper(message);
        its.model.definition.Domain tagDomain = domainSolvingModel.getTagsData()
            .get(getProgrammingLanguage(message).toLowerCase())
            .copy();
        tagDomain.addMerge(domainSolvingModel.getDomain());
        its.model.definition.Domain situationDomain
            = ProgrammingLanguageExpressionRDFTransformer.questionToDomainModel(tagDomain, helper.getModel());
        situationDomain.addMerge(tagDomain);
        situationDomain.validateAndThrow();
        Set<StudentError> errors = GetErrors(helper, false);
        for (StudentError error : errors) {
            OntologyUtil.Error text = getErrorDescription(error, helper, message.lang);
            for (ViolationEntity violation : helper.judgeQuestion().violations) {
                if (helper.needSupplementaryQuestion(violation)) {
                    text.type = violation.getLawName();
                    break;
                }
            }
            
            message.errors.add(text);
            message.expression.get(error.getErrorPos() - 1).status = "wrong";
        }
        
        if (errors.isEmpty()) {
            for (MessageToken token : message.expression) {
                if (token.check_order != 1000 && token.check_order != 0) {
                    token.enabled = false;
                    token.status = "correct";
                }
            }
        }
        
        return message;
    }
    
    private Message getNextStepResponse(Message message){
        Domain.CorrectAnswer correctAnswer = new ProgrammingLanguageExpressionDomain()
            .getAnyNextCorrectAnswer(getOntologyHelper(message).getQuestion(), message.lang);
        
        if (correctAnswer == null) {
            return message;
        }
        
        for (MessageToken token : message.expression) {
            if (token.check_order != 1000 && token.check_order != 0) {
                token.enabled = false;
                token.status = "correct";
            }
        }
        
        int correctPosition = Integer.parseInt(correctAnswer.answer.getDomainInfo().substring("op__0__".length())) - 1;
        message.expression.get(correctPosition).enabled = false;
        message.expression.get(correctPosition).status = "suggested";
        
        int last_check_order = 0;
        for (MessageToken token : message.expression) {
            if (token.check_order != 1000) {
                last_check_order = Math.max(last_check_order, token.check_order);
            }
        }
        message.expression.get(correctPosition).check_order = last_check_order + 1;
        
        OntologyUtil.Error error = new OntologyUtil.Error();
        OntologyUtil.ErrorPart errorPart = new ErrorPart(correctAnswer.explanation.getText());
        message.errors.add(error.add(errorPart));
        
        return message;
    }
    
    private Message getSupplementaryResponse(Message message){
        message.type = "supplementary";
        if (message.errors != null && !message.errors.isEmpty()) {
            message.answers = new ArrayList<>();
            String lawName = message.errors.get(0).type;
            OntologyHelper helper = new OntologyHelper(
                createExpressionFromMessage(message),
                getProgrammingLanguage(message),
                message.lang,
                lawName
            );
            Question q = helper.getQuestion();
            
            if (q == null) {
                message.type = "no_supplementary";
                return message;
            }
            
            message.text = q.getQuestionText().getText();
            for (AnswerObjectEntity answer : q.getAnswerObjects()) {
                MessageToken token = new MessageToken();
                token.text = answer.getHyperText();
                token.enabled = true;
                Domain.InterpretSentenceResult res = helper.judgeSupplementaryQuestion(answer);
                
                token.status = res.isAnswerCorrect ? "correct" : "wrong";
                token.additional_info = res.violations.get(0).getLawName();
                message.answers.add(token);
            }
            message.errors = new ArrayList<>();
        }
        return message;
    }
}
