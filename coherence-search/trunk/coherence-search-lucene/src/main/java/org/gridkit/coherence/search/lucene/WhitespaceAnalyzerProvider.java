/**
 * Copyright 2010 Grid Dynamics Consulting Services, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gridkit.coherence.search.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;

import java.io.Serializable;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
class WhitespaceAnalyzerProvider implements LuceneAnalyzerProvider, Serializable {

	private static final long serialVersionUID = 20100720L;

	int x;
	
	public Analyzer getAnalyzer() {
		return new WhitespaceAnalyzer();
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return true;
	}	
}
