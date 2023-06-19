/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.languages.hcl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.SemanticAnalyzer;
import org.netbeans.modules.languages.hcl.ast.HCLAttribute;
import org.netbeans.modules.languages.hcl.ast.HCLBlock;
import org.netbeans.modules.languages.hcl.ast.HCLCollection;
import org.netbeans.modules.languages.hcl.ast.HCLDocument;
import org.netbeans.modules.languages.hcl.ast.HCLElement;
import org.netbeans.modules.languages.hcl.ast.HCLExpression;
import org.netbeans.modules.languages.hcl.ast.HCLFunction;
import org.netbeans.modules.languages.hcl.ast.HCLIdentifier;
import org.netbeans.modules.languages.hcl.ast.HCLVariable;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;

/**
 *
 * @author lkishalmi
 */
public class HCLSemanticAnalyzer extends SemanticAnalyzer<HCLParserResult> {
    private volatile boolean cancelled;
    private Map<OffsetRange, Set<ColoringAttributes>> highlights = Collections.emptyMap();
    
    @Override
    public Map<OffsetRange, Set<ColoringAttributes>> getHighlights() {
        return highlights;
    }

    protected final synchronized boolean isCancelled() {
        return cancelled;
    }

    protected final synchronized void resume() {
        cancelled = false;
    }

    @Override
    public final void run(HCLParserResult result, SchedulerEvent event) {
        resume();

        Highlighter h = createHighlighter(result);
        result.getDocument().accept(h);
        highlights = h.work;
    }

    protected Highlighter createHighlighter(HCLParserResult result) {
        return new DefaultHighlighter(result.getReferences());
    }
    
    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
    
    protected abstract class Highlighter extends HCLElement.BAEVisitor {
        protected final Map<OffsetRange, Set<ColoringAttributes>> work = new HashMap<>();

        protected final SourceRef refs;
        protected Highlighter(SourceRef refs) {
            this.refs = refs;
        }

        @Override
        public final boolean visit(HCLElement e) {
            if (isCancelled()) {
                return true;
            }
            return super.visit(e);
        }
        
        protected final void mark(HCLElement e, Set<ColoringAttributes> attrs) {
            refs.getOffsetRange(e).ifPresent((range) -> work.put(range, attrs));
        }
    }
    
    protected class DefaultHighlighter extends Highlighter {

        public DefaultHighlighter(SourceRef refs) {
            super(refs);
        }
        
        @Override
        protected boolean visitBlock(HCLBlock block) {
            if (block.getParent() instanceof HCLDocument) {
                List<HCLIdentifier> decl = block.getDeclaration();
                HCLIdentifier type = decl.get(0);

                mark(type, ColoringAttributes.CLASS_SET);
                if (decl.size() > 1) {
                    for (int i = 1; i < decl.size(); i++) {
                        HCLIdentifier id = decl.get(i);
                        mark(id, ColoringAttributes.CONSTRUCTOR_SET);
                    }
                }
            } else {
                //TODO: Handle nested Blocks...
            }
            return false;
        }

        @Override
        protected boolean visitAttribute(HCLAttribute attr) {
            mark(attr.getName(), ColoringAttributes.FIELD_SET);
            return false;
        }

        @Override
        protected boolean visitExpression(HCLExpression expr) {
            if (expr instanceof HCLFunction) {
                HCLFunction func =  (HCLFunction) expr;
                mark(func.getName(), ColoringAttributes.CONSTRUCTOR_SET);
            }

            if (expr instanceof HCLCollection.Object) {
                HCLCollection.Object obj = (HCLCollection.Object) expr;
                for (HCLExpression key : obj.getKeys()) {
                    if (key instanceof HCLVariable) {
                        mark(key, ColoringAttributes.FIELD_SET);
                    }
                }
            }
            return false;
        }
        
    }
}
