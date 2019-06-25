/*
 * Copyright 2016 Ecole des Mines de Saint-Etienne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.emse.ci.sparqlext.iterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.sparql.ARQInternalErrorException;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base implementation of the {@link IteratorFunction} interface.
 */
public abstract class IteratorStreamFunctionBase implements IteratorFunction {

    private static final Logger LOG = LoggerFactory.getLogger(IteratorStreamFunctionBase.class);

    /**
     * The list of argument expressions.
     */
    protected ExprList arguments = null;
    /**
     * The function environment.
     */
    private FunctionEnv env;

    /**
     * Build a iterator function execution with the given arguments, and operate
     * a check of the build.
     *
     * @param args -
     * @throws QueryBuildException if the iterator function cannot be executed
     * with the given arguments.
     */
    @Override
    public final void build(ExprList args) {
        LOG.info("Building the iterator " + System.identityHashCode(this));
        arguments = args;
        checkBuild(args);
    }

    /**
     * Partially checks if the iterator function can be executed with the given
     * arguments.
     *
     * @param args -
     * @throws QueryBuildException if the iterator function cannot be executed
     * with the given arguments.
     */
    public abstract void checkBuild(ExprList args);

    @Override
    public final CompletableFuture<Void> exec(
            final Binding binding,
            final ExprList args,
            final FunctionEnv env,
            final Function<List<List<NodeValue>>, CompletableFuture<Void>> collectionListNodeValue) {
        ExecutionControl control = new ExecutionControl();        

        this.env = env;
        if (args == null) {
            throw new ARQInternalErrorException("IteratorFunctionBase:"
                    + " Null args list");
        }

        List<NodeValue> evalArgs = new ArrayList<>();
        for (Expr e : args) {
            try {
                NodeValue x = e.eval(binding, env);
                evalArgs.add(x);
            } catch (ExprEvalException ex) {
                LOG.trace("Cannot evaluate node " + e + ": " + ex.getMessage());
                evalArgs.add(null);
            }
        }
        try {
            exec(evalArgs, (listNodeValues) -> {
                control.registerFuture(collectionListNodeValue.apply(listNodeValues));
            }, control);
        } catch (ExprEvalException ex) {
            control.getReturnedFuture().completeExceptionally(ex);
        }
        return control.getReturnedFuture();
    }

    /**
     * Return the Context object for this execution.
     *
     * @return -
     */
    public final Context getContext() {
        return (Context) env.getContext();
    }

    /**
     * IteratorFunction call to a list of evaluated argument values.
     *
     * @param args -
     * @param collectionListNodeValue - where to emit new future collections of
     * @param control - the control on the execution. Method complete() needs to
     * be called when the iterator completed.
     * lists of values
     */
    public abstract void exec(List<NodeValue> args, Consumer<List<List<NodeValue>>> collectionListNodeValue, ExecutionControl control);

}
