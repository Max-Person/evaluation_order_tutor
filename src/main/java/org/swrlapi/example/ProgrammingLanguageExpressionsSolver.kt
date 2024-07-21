package org.swrlapi.example

import its.model.DomainSolvingModel
import its.model.definition.Domain
import its.model.definition.ObjectDef
import its.model.definition.types.EnumValue
import its.model.nodes.DecisionTree
import its.reasoner.LearningSituation
import its.reasoner.nodes.DecisionTreeReasoner._static.solve

/**
 * TODO Class Description
 *
 * @author Marat Gumerov
 * @since 20.07.2024
 */
class ProgrammingLanguageExpressionsSolver {

    private fun solve(domain: Domain, decisionTree: DecisionTree, retain: (ObjectDef, ObjectDef) -> Unit) {
        val situationDomain = domain.copy()
        val situation = LearningSituation(situationDomain)

        fun getUnevaluated(domain: Domain): List<ObjectDef> {
            return domain.objects.filter {
                it.isInstanceOf("operator")
                        && it.getPropertyValue("state") == EnumValue("state", "unevaluated")
            }.sortedBy { it.getPropertyValue("precedence").toString().toInt()}
        }

        var unevaluated = getUnevaluated(situationDomain)
        while (unevaluated.isNotEmpty()) {
            for (x in unevaluated) {
                situation.decisionTreeVariables.clear()
                situation.decisionTreeVariables["X"] = x.reference
                println("solve iter for $x")
                decisionTree.solve(situation)
            }
            val newUnevaluated = getUnevaluated(situationDomain)
            unevaluated = newUnevaluated
        }

        domain.objects.forEach { obj ->
            val objFromSituation = situationDomain.objects.get(obj.name)!!
            retain(obj, objFromSituation)
        }
    }

    fun solveTree(domain: Domain, decisionTreeMap: Map<String, DecisionTree> ) {
        solve(
            domain,
            decisionTreeMap["no_strict"]!!
        ) { obj, objFromSituation ->
            obj.relationshipLinks.addAll(
                objFromSituation.relationshipLinks.filter { it.relationshipName.contains("OperandOf") }
            )
        }
    }

    fun solveStrict(domain: Domain, decisionTreeMap: Map<String, DecisionTree>) {
        solve(
            domain,
            decisionTreeMap[""]!!
        ) { obj, objFromSituation ->
            objFromSituation.definedPropertyValues.get("state")?.also {
                if(it.value is EnumValue && (it.value as EnumValue).valueName == "omitted"){
                    obj.definedPropertyValues.addOrReplace(it)
                }
            }
        }
    }

    fun solveFull(domain: Domain, decisionTreeMap: Map<String, DecisionTree>) {
        solve(
            domain,
            decisionTreeMap[""]!!
        ) { obj, objFromSituation ->
            objFromSituation.definedPropertyValues.get("state")?.also {obj.definedPropertyValues.addOrReplace(it)}
        }
    }
}