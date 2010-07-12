package com.griddynamics.gridkit.coherence.index.lucene;

import com.tangosol.util.BinaryEntry;
import com.tangosol.util.MapIndex;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.ExternalizableHelper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

/**
 * @author Alexander Solovyov
 */

public class LuceneMapIndex implements MapIndex {
    public static final String KEY = "key";
    public static final String VALUE = "value";

    private static final String INDEX_KEY = "value-key";    

    private final ValueExtractor extractor;

    private RAMDirectory directory = new RAMDirectory();
    private Analyzer analyzer = new WhitespaceAnalyzer();
    private IndexSearcher indexSearcher;

    public LuceneMapIndex(ValueExtractor extractor) {
        this.extractor = extractor;
    }

    public ValueExtractor getValueExtractor() {
        return extractor;
    }

    public boolean isOrdered() {
        return false;
    }

    public boolean isPartial() {
        return false;
    }

    public Map getIndexContents() {
        throw new UnsupportedOperationException();
    }

    public Object get(Object o) {
        return NO_VALUE;
    }

    public Comparator getComparator() {
        return null;
    }

    public void insert(Map.Entry entry) {
        String value = (String) extractor.extract(entry.getValue());
        
        if (value != null) {
            Document doc = new Document();

            doc.add(new Field(INDEX_KEY, value, Field.Store.NO, Field.Index.NOT_ANALYZED));
            doc.add(new Field(VALUE, value, Field.Store.YES, Field.Index.ANALYZED));

            doc.add(new Field(KEY, ExternalizableHelper.toByteArray(getEntryKey(entry)), Field.Store.YES));

            try {
                IndexWriter writer = getIndexWriter();
                writer.addDocument(doc);
                writer.close();
                indexSearcher = null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void update(Map.Entry entry) {
        delete(entry);
        insert(entry);
    }

    public void delete(Map.Entry entry) {
        String value = (String) extractor.extract(entry.getValue());
        if (value != null) {
            try {
                IndexReader reader = IndexReader.open(directory);
                reader.deleteDocuments(new Term(INDEX_KEY, value));
                reader.close();
                indexSearcher = null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public RAMDirectory getDirectory() {
        return directory;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public IndexSearcher getIndexSearcher() {
        if (indexSearcher == null) {
            try {
                IndexWriter writer = getIndexWriter();
                writer.optimize();
                writer.close();
                indexSearcher = new IndexSearcher(directory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return indexSearcher;
    }

    private IndexWriter getIndexWriter() throws IOException {
        return new IndexWriter(directory, analyzer, IndexWriter.MaxFieldLength.UNLIMITED);
    }

    private Object getEntryKey(Map.Entry entry) {
        if (entry instanceof BinaryEntry) {
            return ((BinaryEntry)entry).getBinaryKey();
        }
        else {
            return entry.getKey();
        }
    }
}
