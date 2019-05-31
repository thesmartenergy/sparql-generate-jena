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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.sparql.ARQInternalErrorException;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;

/**
 * Support for a iterator function of two arguments.
 */
public abstract class IteratorStreamFunctionBase5 extends IteratorStreamFunctionBase {

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkBuild(ExprList args) {
        if (args.size() != 5) {
            throw new QueryBuildException("Selector '"
                    + this.getClass().getName() + "' takes 5 arguments");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final CompletableFuture<Void> exec(List<NodeValue> args, Function<List<List<NodeValue>>, CompletableFuture<Void>> nodeValuesStream) {
        if (args == null) {
            throw new ARQInternalErrorException(this.getClass().getName()
                    + ": Null args list");
        }
        if (args.size() != 5) {
            throw new ExprEvalException(this.getClass().getName()
                    + ": Wrong number of arguments: Wanted 5, got "
                    + args.size());
        }
        NodeValue v1 = args.get(0);
        NodeValue v2 = args.get(1);
        NodeValue v3 = args.get(2);
        NodeValue v4 = args.get(3);
        NodeValue v5 = args.get(4);
        return exec(v1, v2, v3, v4, v5, nodeValuesStream);
    }

    /**
     * {@inheritDoc}
     */
    public abstract CompletableFuture<Void> exec(NodeValue v1, NodeValue v2, NodeValue v3, NodeValue v4, NodeValue v5, Function<List<List<NodeValue>>, CompletableFuture<Void>> nodeValuesStream);
}