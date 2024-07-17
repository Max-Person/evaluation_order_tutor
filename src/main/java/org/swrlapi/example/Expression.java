package org.swrlapi.example;

import java.util.ArrayList;
import java.util.List;

public class Expression {
    List<Term> terms;
    
    public Expression(List<Term> terms){
        this.terms = terms;
    }

    public List<Term> getTerms() {
        return terms;
    }

    public List<String> getTokens() {
        List<String> tokens = new ArrayList<>();
        for (Term t : terms) {
            tokens.add(t.Text);
        }
        return tokens;
    }

    public int size() {
        return terms.size();
    }
    
    public static Expression ofTokens(List<String> expression) {
        return new Expression(expression.stream().map(Term::new).toList());
    }

}
