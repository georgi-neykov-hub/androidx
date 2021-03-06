/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.appsearch.app.cts;

import static androidx.appsearch.app.util.AppSearchTestUtils.checkIsBatchResultSuccess;
import static androidx.appsearch.app.util.AppSearchTestUtils.checkIsResultSuccess;
import static androidx.appsearch.app.util.AppSearchTestUtils.convertSearchResultsToDocuments;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.appsearch.app.AppSearchEmail;
import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.AppSearchSchema.PropertyConfig;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.GlobalSearchSession;
import androidx.appsearch.app.PutDocumentsRequest;
import androidx.appsearch.app.SearchResult;
import androidx.appsearch.app.SearchResults;
import androidx.appsearch.app.SearchSpec;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.localstorage.LocalStorage;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobalSearchSessionCtsTest {
    private AppSearchSession mDb1;
    private static final String DB_NAME_1 = LocalStorage.DEFAULT_DATABASE_NAME;
    private AppSearchSession mDb2;
    private static final String DB_NAME_2 = "testDb2";

    private GlobalSearchSession mGlobalAppSearchManager;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        mDb1 = checkIsResultSuccess(LocalStorage.createSearchSession(
                new LocalStorage.SearchContext.Builder(context)
                        .setDatabaseName(DB_NAME_1).build()));
        mDb2 = checkIsResultSuccess(LocalStorage.createSearchSession(
                new LocalStorage.SearchContext.Builder(context)
                        .setDatabaseName(DB_NAME_2).build()));

        // Cleanup whatever documents may still exist in these databases. This is needed in
        // addition to tearDown in case a test exited without completing properly.
        cleanup();

        mGlobalAppSearchManager = checkIsResultSuccess(LocalStorage.createGlobalSearchSession(
                new LocalStorage.GlobalSearchContext.Builder(context).build()));
    }

    @After
    public void tearDown() throws Exception {
        // Cleanup whatever documents may still exist in these databases.
        cleanup();
    }

    private void cleanup() throws Exception {
        checkIsResultSuccess(mDb1.setSchema(
                new SetSchemaRequest.Builder().setForceOverride(true).build()));
        checkIsResultSuccess(mDb2.setSchema(
                new SetSchemaRequest.Builder().setForceOverride(true).build()));
    }

    private List<GenericDocument> snapshotResults(String queryExpression, SearchSpec spec)
            throws Exception {
        SearchResults searchResults = mGlobalAppSearchManager.query(queryExpression, spec);
        return convertSearchResultsToDocuments(searchResults);
    }

    /**
     * Asserts that the union of {@code addedDocuments} and {@code beforeDocuments} is exactly
     * equivalent to {@code afterDocuments}. Order doesn't matter.
     *
     * @param beforeDocuments Documents that existed first.
     * @param afterDocuments  The total collection of documents that should exist now.
     * @param addedDocuments  The collection of documents that were expected to be added.
     */
    private void assertAddedBetweenSnapshots(List<? extends GenericDocument> beforeDocuments,
            List<? extends GenericDocument> afterDocuments,
            List<? extends GenericDocument> addedDocuments) throws Exception {
        List<GenericDocument> expectedDocuments = new ArrayList<>(beforeDocuments);
        expectedDocuments.addAll(addedDocuments);
        assertThat(afterDocuments).containsExactlyElementsIn(expectedDocuments);
    }

    @Test
    public void testGlobalQuery_oneInstance() throws Exception {
        // Snapshot what documents may already exist on the device.
        SearchSpec exactSearchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        List<GenericDocument> beforeBodyDocuments = snapshotResults("body", exactSearchSpec);
        List<GenericDocument> beforeBodyEmailDocuments = snapshotResults("body email",
                exactSearchSpec);

        // Schema registration
        checkIsResultSuccess(mDb1.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mDb1.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail).build()));

        // Query for the document
        List<GenericDocument> afterBodyDocuments = snapshotResults("body", exactSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyDocuments, afterBodyDocuments,
                Collections.singletonList(inEmail));

        // Multi-term query
        List<GenericDocument> afterBodyEmailDocuments = snapshotResults("body email",
                exactSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyEmailDocuments, afterBodyEmailDocuments,
                Collections.singletonList(inEmail));
    }

    @Test
    public void testGlobalQuery_twoInstances() throws Exception {
        // Snapshot what documents may already exist on the device.
        SearchSpec exactSearchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        List<GenericDocument> beforeBodyDocuments = snapshotResults("body", exactSearchSpec);

        // Schema registration
        checkIsResultSuccess(mDb1.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA).build()));
        checkIsResultSuccess(mDb2.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a document to instance 1.
        AppSearchEmail inEmail1 =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mDb1.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail1).build()));

        // Index a document to instance 2.
        AppSearchEmail inEmail2 =
                new AppSearchEmail.Builder("uri2")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mDb2.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail2).build()));

        // Query across all instances
        List<GenericDocument> afterBodyDocuments = snapshotResults("body", exactSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyDocuments, afterBodyDocuments,
                ImmutableList.of(inEmail1, inEmail2));
    }

    @Test
    public void testGlobalQuery_getNextPage() throws Exception {
        // Snapshot what documents may already exist on the device.
        SearchSpec exactSearchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        List<GenericDocument> beforeBodyDocuments = snapshotResults("body", exactSearchSpec);

        // Schema registration
        checkIsResultSuccess(mDb1.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));
        List<AppSearchEmail> emailList = new ArrayList<>();
        PutDocumentsRequest.Builder putDocumentsRequestBuilder = new PutDocumentsRequest.Builder();

        // Index 31 documents
        for (int i = 0; i < 31; i++) {
            AppSearchEmail inEmail =
                    new AppSearchEmail.Builder("uri" + i)
                            .setFrom("from@example.com")
                            .setTo("to1@example.com", "to2@example.com")
                            .setSubject("testPut example")
                            .setBody("This is the body of the testPut email")
                            .build();
            emailList.add(inEmail);
            putDocumentsRequestBuilder.addGenericDocument(inEmail);
        }
        checkIsBatchResultSuccess(mDb1.putDocuments(putDocumentsRequestBuilder.build()));

        // Set number of results per page is 7.
        int pageSize = 7;
        SearchResults searchResults = mGlobalAppSearchManager.query("body",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setResultCountPerPage(pageSize)
                        .build());
        List<GenericDocument> documents = new ArrayList<>();

        int pageNumber = 0;
        List<SearchResult> results;

        // keep loading next page until it's empty.
        do {
            results = checkIsResultSuccess(searchResults.getNextPage());
            ++pageNumber;
            for (SearchResult result : results) {
                documents.add(result.getDocument());
            }
        } while (results.size() > 0);

        // check all document presents
        assertAddedBetweenSnapshots(beforeBodyDocuments, documents, emailList);

        int totalDocuments = beforeBodyDocuments.size() + documents.size();

        // +1 for final empty page
        int expectedPages = (int) Math.ceil(totalDocuments * 1.0 / pageSize) + 1;
        assertThat(pageNumber).isEqualTo(expectedPages);
    }

    @Test
    public void testGlobalQuery_acrossTypes() throws Exception {
        // Snapshot what documents may already exist on the device.
        SearchSpec exactSearchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        List<GenericDocument> beforeBodyDocuments = snapshotResults("body", exactSearchSpec);

        SearchSpec exactEmailSearchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .addSchemaType(AppSearchEmail.SCHEMA_TYPE)
                        .build();
        List<GenericDocument> beforeBodyEmailDocuments = snapshotResults("body",
                exactEmailSearchSpec);

        // Schema registration
        AppSearchSchema genericSchema = new AppSearchSchema.Builder("Generic")
                .addProperty(new PropertyConfig.Builder("foo")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .build()
                ).build();

        // db1 has both "Generic" and "builtin:Email"
        checkIsResultSuccess(mDb1.setSchema(new SetSchemaRequest.Builder()
                .addSchema(genericSchema).addSchema(AppSearchEmail.SCHEMA).build()));

        // db2 only has "builtin:Email"
        checkIsResultSuccess(mDb2.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a generic document into db1
        GenericDocument genericDocument = new GenericDocument.Builder<>("uri2", "Generic")
                .setPropertyString("foo", "body").build();
        checkIsBatchResultSuccess(mDb1.putDocuments(
                new PutDocumentsRequest.Builder()
                        .addGenericDocument(genericDocument).build()));

        AppSearchEmail email =
                new AppSearchEmail.Builder("uri1")
                        .setNamespace("namespace")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();

        // Put the email in both databases
        checkIsBatchResultSuccess((mDb1.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(email).build())));
        checkIsBatchResultSuccess(mDb2.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(email).build()));

        // Query for all documents across types
        List<GenericDocument> afterBodyDocuments = snapshotResults("body", exactSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyDocuments, afterBodyDocuments,
                ImmutableList.of(genericDocument, email, email));

        // Query only for email documents
        List<GenericDocument> afterBodyEmailDocuments = snapshotResults("body",
                exactEmailSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyEmailDocuments, afterBodyEmailDocuments,
                ImmutableList.of(email, email));
    }

    @Test
    public void testGlobalQuery_namespaceFilter() throws Exception {
        // Snapshot what documents may already exist on the device.
        SearchSpec exactSearchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        List<GenericDocument> beforeBodyDocuments = snapshotResults("body", exactSearchSpec);

        SearchSpec exactNamespace1SearchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .addNamespace("namespace1")
                        .build();
        List<GenericDocument> beforeBodyNamespace1Documents = snapshotResults("body",
                exactNamespace1SearchSpec);

        // Schema registration
        checkIsResultSuccess(mDb1.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA).build()));
        checkIsResultSuccess(mDb2.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA).build()));

        // Index two documents
        AppSearchEmail document1 =
                new AppSearchEmail.Builder("uri1")
                        .setNamespace("namespace1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mDb1.putDocuments(
                new PutDocumentsRequest.Builder()
                        .addGenericDocument(document1).build()));

        AppSearchEmail document2 =
                new AppSearchEmail.Builder("uri1")
                        .setNamespace("namespace2")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mDb2.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(document2).build()));

        // Query for all namespaces
        List<GenericDocument> afterBodyDocuments = snapshotResults("body", exactSearchSpec);
        assertAddedBetweenSnapshots(beforeBodyDocuments, afterBodyDocuments,
                ImmutableList.of(document1, document2));

        // Query only for "namespace1"
        List<GenericDocument> afterBodyNamespace1Documents = snapshotResults("body",
                exactNamespace1SearchSpec);
        assertAddedBetweenSnapshots(beforeBodyNamespace1Documents, afterBodyNamespace1Documents,
                ImmutableList.of(document1));
    }
}
