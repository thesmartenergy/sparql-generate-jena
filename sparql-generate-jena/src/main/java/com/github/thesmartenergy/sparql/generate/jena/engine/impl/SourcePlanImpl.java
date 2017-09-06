/*
 * Copyright 2016 The Apache Software Foundation.
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
package com.github.thesmartenergy.sparql.generate.jena.engine.impl;

import com.github.thesmartenergy.sparql.generate.jena.SPARQLGenerateException;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.log4j.Logger;
import java.util.Objects;
import com.github.thesmartenergy.sparql.generate.jena.engine.SourcePlan;
import java.io.IOException;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.riot.system.stream.StreamManager;

/**
 * Executes a <code>{@code SOURCE <node> ACCEPT <mime> AS <var>}</code> clause.
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
public class SourcePlanImpl extends PlanBase implements SourcePlan {

    /**
     * The logger.
     */
    private static final Logger LOG
            = Logger.getLogger(SourcePlanImpl.class.getName());

    /**
     * The source node. A uri or a variable.
     */
    private final Node node;

    /**
     * The accept node. A uri or a variable.
     */
    private final Node accept;

    /**
     * The bound variable.
     */
    private final Var var;

    /**
     * type mapper.
     */
    final static TypeMapper tm = TypeMapper.getInstance();

    /**
     * The generation plan of a <code>{@code SOURCE <node> ACCEPT <mime> AS
     * <var>}</code> clause.
     *
     * @param node0 The IRI or the Variable node where a GET must be operated.
     * Must not be null.
     * @param accept0 The IRI or the Variable node that represent the accepted
     * Internet Media Type. May be null.
     * @param var0 The variable to bound the potentially retrieved document.
     * Must not be null.
     */
    public SourcePlanImpl(
            final Node node0,
            final Node accept0,
            final Var var0) {
        Objects.requireNonNull(node0, "Node must not be null");
        Objects.requireNonNull(var0, "Var must not be null");
        if (!node0.isURI() && !node0.isVariable()) {
            throw new IllegalArgumentException("Source node must be a IRI or a"
                    + " Variable. got " + node0);
        }
        this.node = node0;
        this.accept = accept0;
        this.var = var0;
    }

    /**
     * {@inheritDoc}
     */
    final public void exec(
            final List<Var> variables,
            final List<BindingHashMapOverwrite> values) {
        boolean added = variables.add(var);
        if (!added) {
            throw new SPARQLGenerateException("Variable " + var + " is already"
                    + " bound !");
        }
        ensureNotEmpty(variables, values);
        values.replaceAll((BindingHashMapOverwrite value) -> {
            String literal = null;
            String datatypeURI = null;

            // generate the source URI.
            final String sourceUri = getActualSource(value);
            String acceptHeader = getAcceptHeader(value);

            // try with accept header.
            if (acceptHeader != null) {
                String acceptURI = "accept:" + acceptHeader + ":" + sourceUri;
                TypedInputStream stream = StreamManager.get().open(acceptURI);
                if (stream != null) {
                    try {
                        literal = IOUtils.toString(stream.getInputStream(), "UTF-8");
                        if (stream.getMediaType() != null && stream.getMediaType().getContentType() != null) {
                            datatypeURI = "http://www.iana.org/assignments/media-types/" + stream.getMediaType().getContentType();
                        } else {
                            datatypeURI = "http://www.w3.org/2001/XMLSchema#string";
                        }
                        RDFDatatype dt = tm.getSafeTypeByName(datatypeURI);
                        final Node n = NodeFactory.createLiteral(literal, dt);
                        return new BindingHashMapOverwrite(value, var, n);
                    } catch (IOException ex) {
                    }
                }
                LOG.warn("not found with streamManager: " + acceptURI);
            }

            // try without.
            TypedInputStream stream = StreamManager.get().open(sourceUri);
            if (stream != null) {
                try {
                    literal = IOUtils.toString(stream.getInputStream(), "UTF-8");
                    if (stream.getMediaType() != null && stream.getMediaType().getContentType() != null) {
                        datatypeURI = "http://www.iana.org/assignments/media-types/" + stream.getMediaType().getContentType();
                    } else {
                        datatypeURI = "http://www.w3.org/2001/XMLSchema#string";
                    }
                    RDFDatatype dt = tm.getSafeTypeByName(datatypeURI);
                    final Node n = NodeFactory.createLiteral(literal, dt);
                    return new BindingHashMapOverwrite(value, var, n);
                } catch (IOException ex) {
                }
                LOG.warn("not found with streamManager: " + sourceUri);

            }

            return new BindingHashMapOverwrite(value, var, null);
        });
    }

    /**
     *
     * @param binding -
     * @return the actual URI that represents the location of the query to be
     * fetched.
     */
    private String getActualSource(
            final BindingHashMapOverwrite binding) {
        if (node.isVariable()) {
            Node actualSource = binding.get((Var) node);
            if (actualSource == null) {
                return null;
            }
            if (!actualSource.isURI()) {
                throw new SPARQLGenerateException("Variable " + node.getName()
                        + " must be bound to a IRI that represents the location"
                        + " of the query to be fetched.");
            }
            return actualSource.getURI();
        } else {
            if (!node.isURI()) {
                throw new SPARQLGenerateException("The source must be a IRI"
                        + " that represents the location of the query to be"
                        + " fetched. Got " + node.getURI());
            }
            return node.getURI();
        }
    }

    /**
     * returns the accept header computed from ACCEPT clause.
     *
     * @param binding -
     * @return the actual accept header to use.
     */
    private String getAcceptHeader(
            final BindingHashMapOverwrite binding) {
        Node actualAccept = accept;
        if (accept == null) {
            return "*/*";
        }
        if (accept.isVariable()) {
            actualAccept = binding.get((Var) accept);
            if (accept == null) {
                return "*/*";
            }
        }
        if (!actualAccept.isURI()) {
            throw new SPARQLGenerateException("Variable " + node.getName()
                    + " must be bound to a IRI that represents the internet"
                    + " media type of the source to be fetched. For"
                    + " instance, <http://www.iana.org/assignments/media-types/application/xml>.");
        }
        if (!actualAccept.getURI().startsWith("http://www.iana.org/assignments/media-types/")) {
            throw new SPARQLGenerateException("Variable " + node.getName()
                    + " must be bound to a IANA MIME URN (RFC to be"
                    + " written). For instance,"
                    + " <http://www.iana.org/assignments/media-types/application/xml>.");
        }
        return actualAccept.getURI();

    }

}
