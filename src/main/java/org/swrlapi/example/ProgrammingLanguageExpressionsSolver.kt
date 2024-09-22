package org.swrlapi.example

import its.model.definition.DomainModel
import its.model.definition.ObjectDef
import its.model.definition.types.EnumValue
import its.model.nodes.DecisionTree
import its.reasoner.LearningSituation
import its.reasoner.nodes.DecisionTreeReasoner._static.solve
import org.swrlapi.example.JsonRequester.debugLog

/**
 * TODO Class Description
 *
 * @author Marat Gumerov
 * @since 20.07.2024
 */
class ProgrammingLanguageExpressionsSolver {

    fun getUnevaluated(domain: DomainModel): List<ObjectDef> {
        return domain.objects.filter {
            it.isInstanceOf("operator")
                    && it.getPropertyValue("state") == EnumValue("state", "unevaluated")
        }.sortedBy { it.getPropertyValue("precedence").toString().toInt()}
    }

    fun solveForX(xObject: ObjectDef, domain: DomainModel, decisionTree: DecisionTree): Boolean {
        val situation = LearningSituation(domain, mutableMapOf("X" to xObject.reference))
        debugLog("solve iter for $xObject")
        val results = decisionTree.solve(situation)
        return results.last().node.value
    }

    private fun solve(domain: DomainModel, decisionTree: DecisionTree, retain: (ObjectDef, ObjectDef) -> Unit) {
        val situationDomain = domain.copy()

        var unevaluated = getUnevaluated(situationDomain)
        while (unevaluated.isNotEmpty()) {
            for (x in unevaluated) {
                solveForX(x, situationDomain, decisionTree)
            }
            val newUnevaluated = getUnevaluated(situationDomain)
            unevaluated = newUnevaluated
        }

        domain.objects.forEach { obj ->
            val objFromSituation = situationDomain.objects.get(obj.name)!!
            retain(obj, objFromSituation)
        }
    }

    fun solveTree(domain: DomainModel, decisionTreeMap: Map<String, DecisionTree>) {
        solve(
            domain,
            decisionTreeMap["no_strict"]!!
        ) { obj, objFromSituation ->
            obj.relationshipLinks.addAll(
                objFromSituation.relationshipLinks.filter { it.relationshipName.contains("OperandOf") }
            )
            objFromSituation.definedPropertyValues.get("evaluatesTo")?.also {
                obj.definedPropertyValues.addOrReplace(it)
            }
        }
    }

    fun solveStrict(domain: DomainModel, decisionTreeMap: Map<String, DecisionTree>) {
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

    fun solveFull(domain: DomainModel, decisionTreeMap: Map<String, DecisionTree>) {
        solve(
            domain,
            decisionTreeMap[""]!!
        ) { obj, objFromSituation ->
            objFromSituation.definedPropertyValues.get("state")?.also {obj.definedPropertyValues.addOrReplace(it)}
        }
    }
}