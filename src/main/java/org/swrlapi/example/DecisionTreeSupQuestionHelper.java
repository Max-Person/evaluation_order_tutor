package org.swrlapi.example;

import its.model.definition.DomainModel;
import its.model.nodes.DecisionTree;
import its.questions.gen.QuestioningSituation;
import its.questions.gen.formulations.Localization;
import its.questions.gen.states.*;
import its.questions.gen.strategies.FullBranchStrategy;
import its.questions.gen.strategies.QuestionAutomata;
import kotlin.Pair;

import java.util.ArrayList;

/**
 * TODO Class Description
 *
 * @author Marat Gumerov
 * @since 22.07.2024
 */
public class DecisionTreeSupQuestionHelper {
    public DecisionTreeSupQuestionHelper(
        DecisionTree decisionTree
    ) {
        this.supplementaryAutomata = FullBranchStrategy.INSTANCE.buildAndFinalize(
            decisionTree.getMainBranch(), new EndQuestionState()
        );
    }
    
    private final QuestionAutomata supplementaryAutomata;
    
    
    //DT = Decision Tree
    void makeSupplementaryQuestion(Message message, DomainModel situationDomain) {
        
        //создать ситуацию, описывающую контекст задания вспомогательных вопросов
        QuestioningSituation situation = makeQuestioningSituation(message, situationDomain);
        
        //получить состояние автомата вопросов, к которому перешли на последнем шаге
        QuestionState state = message.supplementaryInfo != null
            ? supplementaryAutomata.get(message.supplementaryInfo.nextQuestionStateId)
            : supplementaryAutomata.getInitState();
        
        
        //Получить начальный результат
        QuestionStateResult res = message.answers == null || message.answers.isEmpty()
            ? state.getQuestion(situation)
            : state.proceedWithAnswer(situation, message.answers.stream().map(answer -> answer.id).toList());
        
        //очистка
        message.explanations = null;
        message.answers = null;
        
        //Пока есть переход - пишем о нем в массив объяснений (если есть объяснение)
        while(res instanceof QuestionStateChange change){
            if(change.getExplanation() != null){
                writeExplanationToMessage(message, change.getExplanation());
            }
            
            state = change.getNextState();
            
            if(state == null || getActualState(state) instanceof EndQuestionState){
                res = null;
            } else {
                res = state.getQuestion(situation);
            }
        }
        
        //Пишем вопрос в Message
        if(res instanceof Question question){
            writeQuestionToMessage(message, question);
        }
        
        writeToSupplementaryInfoToMessage(message, situation, state, res);
    }
    
    private QuestioningSituation makeQuestioningSituation(Message message, DomainModel situationDomain) {
        if(message.supplementaryInfo == null){
            return new QuestioningSituation(situationDomain, message.lang.toUpperCase());
        }
        return new QuestioningSituation(
            situationDomain,
            message.supplementaryInfo.decisionTreeVariables,
            message.supplementaryInfo.discussedVariables,
            message.supplementaryInfo.givenAnswers,
            message.supplementaryInfo.assumedResults,
            message.lang.toUpperCase()
        );
    }
    
    private void writeExplanationToMessage(Message message, Explanation explanation){
        if(message.explanations == null){
            message.explanations = new ArrayList<>();
        }
        ExplanationInfo explanationInfo = new ExplanationInfo();
        explanationInfo.text = explanation.getText();
        explanationInfo.type = explanation.getType().toString();
        message.explanations.add(explanationInfo);
    }
    
    private void writeQuestionToMessage(Message message, Question question){
        QuestionInfo questionInfo = new QuestionInfo();
        questionInfo.text = question.getText();
        questionInfo.type = question.getType().toString();
        questionInfo.answerOptions = question.getOptions().stream().map(AnswerInfo::fromPair).toList();
        if(question.isAggregation()){
            questionInfo.matchOptions = AggregationQuestionState.aggregationMatching(
                Localization.getLocalizations().get(message.lang.toUpperCase())
            )
                .entrySet().stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                .map(AnswerInfo::fromPair)
                .toList();
        }
        message.questionInfo = questionInfo;
    }
    
    private void writeToSupplementaryInfoToMessage(
        Message message,
        QuestioningSituation situation,
        QuestionState currentState,
        QuestionStateResult currentStateResult
    ){
        if(message.supplementaryInfo == null){
            message.supplementaryInfo = new SupplementaryInfo();
        }
        SupplementaryInfo supplementaryInfo = message.supplementaryInfo;
        supplementaryInfo.decisionTreeVariables = situation.getDecisionTreeVariables();
        supplementaryInfo.discussedVariables = situation.getDiscussedVariables();
        supplementaryInfo.givenAnswers = situation.getGivenAnswers();
        supplementaryInfo.assumedResults = situation.getAssumedResults();
        supplementaryInfo.nextQuestionStateId =
            currentStateResult instanceof QuestionStateChange change
                ? change.getNextState() != null ? change.getNextState().getId() : 0
                : currentState.getId();
    }
    
    private QuestionState getActualState(QuestionState state){
        return state instanceof RedirectQuestionState redirect
            ? redirect.redirectsTo()
            : state;
    }
    
}
