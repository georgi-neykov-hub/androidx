/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.room.solver

import COMMON
import androidx.paging.DataSource
import androidx.paging.PagingSource
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.XTestInvocation
import androidx.room.compiler.processing.util.runProcessorTest
import androidx.room.ext.GuavaUtilConcurrentTypeNames
import androidx.room.ext.L
import androidx.room.ext.LifecyclesTypeNames
import androidx.room.ext.PagingTypeNames
import androidx.room.ext.ReactiveStreamsTypeNames
import androidx.room.ext.RoomTypeNames.STRING_UTIL
import androidx.room.ext.RxJava2TypeNames
import androidx.room.ext.RxJava3TypeNames
import androidx.room.ext.T
import androidx.room.parser.SQLTypeAffinity
import androidx.room.processor.Context
import androidx.room.processor.ProcessorErrors
import androidx.room.solver.binderprovider.DataSourceFactoryQueryResultBinderProvider
import androidx.room.solver.binderprovider.DataSourceQueryResultBinderProvider
import androidx.room.solver.binderprovider.LiveDataQueryResultBinderProvider
import androidx.room.solver.binderprovider.PagingSourceQueryResultBinderProvider
import androidx.room.solver.binderprovider.RxQueryResultBinderProvider
import androidx.room.solver.shortcut.binderprovider.GuavaListenableFutureDeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.GuavaListenableFutureInsertMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.RxCallableDeleteOrUpdateMethodBinderProvider
import androidx.room.solver.shortcut.binderprovider.RxCallableInsertMethodBinderProvider
import androidx.room.solver.types.CompositeAdapter
import androidx.room.solver.types.EnumColumnTypeAdapter
import androidx.room.solver.types.TypeConverter
import androidx.room.testing.context
import com.squareup.javapoet.TypeName
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import testCodeGenScope
import toSources

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@RunWith(JUnit4::class)
class TypeAdapterStoreTest {
    companion object {
        fun tmp(index: Int) = CodeGenScope._tmpVar(index)
    }

    @Test
    fun testDirect() {
        runProcessorTest { invocation ->
            val store = TypeAdapterStore.create(Context(invocation.processingEnv))
            val primitiveType = invocation.processingEnv.requireType(TypeName.INT)
            val adapter = store.findColumnTypeAdapter(primitiveType, null, false)
            assertThat(adapter, notNullValue())
        }
    }

    @Test
    fun testJavaLangBoolean() {
        runProcessorTest { invocation ->
            val store = TypeAdapterStore.create(
                Context(invocation.processingEnv)
            )
            val boolean = invocation
                .processingEnv
                .requireType("java.lang.Boolean")
                .makeNullable()
            val adapter = store.findColumnTypeAdapter(boolean, null, false)
            assertThat(adapter, notNullValue())
            assertThat(adapter, instanceOf(CompositeAdapter::class.java))
            val composite = adapter as CompositeAdapter
            assertThat(
                composite.intoStatementConverter?.from?.typeName,
                `is`(TypeName.BOOLEAN.box())
            )
            assertThat(
                composite.columnTypeAdapter.out.typeName,
                `is`(TypeName.INT.box())
            )
        }
    }

    @Test
    fun testJavaLangEnumCompilesWithoutError() {
        val enumSrc = Source.java(
            "foo.bar.Fruit",
            """ package foo.bar;
                import androidx.room.*;
                enum Fruit {
                    APPLE,
                    BANANA,
                    STRAWBERRY}
                """.trimMargin()
        )
        runProcessorTest(
            sources = listOf(enumSrc)
        ) { invocation ->
            val store = TypeAdapterStore.create(Context(invocation.processingEnv))
            val enum = invocation
                .processingEnv
                .requireType("foo.bar.Fruit")
            val adapter = store.findColumnTypeAdapter(enum, null, false)
            assertThat(adapter, notNullValue())
            assertThat(adapter, instanceOf(EnumColumnTypeAdapter::class.java))
        }
    }

    @Test
    fun testVia1TypeAdapter() {
        runProcessorTest { invocation ->
            val store = TypeAdapterStore.create(Context(invocation.processingEnv))
            val booleanType = invocation.processingEnv.requireType(TypeName.BOOLEAN)
            val adapter = store.findColumnTypeAdapter(booleanType, null, false)
            assertThat(adapter, notNullValue())
            assertThat(adapter, instanceOf(CompositeAdapter::class.java))
            val bindScope = testCodeGenScope()
            adapter!!.bindToStmt("stmt", "41", "fooVar", bindScope)
            assertThat(
                bindScope.generate().toString().trim(),
                `is`(
                    """
                    final int ${tmp(0)};
                    ${tmp(0)} = fooVar ? 1 : 0;
                    stmt.bindLong(41, ${tmp(0)});
                    """.trimIndent()
                )
            )

            val cursorScope = testCodeGenScope()
            adapter.readFromCursor("res", "curs", "7", cursorScope)
            assertThat(
                cursorScope.generate().toString().trim(),
                `is`(
                    """
                    final int ${tmp(0)};
                    ${tmp(0)} = curs.getInt(7);
                    res = ${tmp(0)} != 0;
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun testVia2TypeAdapters() {
        val point = Source.java(
            "foo.bar.Point",
            """
            package foo.bar;
            import androidx.room.*;
            @Entity
            public class Point {
                public int x, y;
                public Point(int x, int y) {
                    this.x = x;
                    this.y = y;
                }
                public static Point fromBoolean(boolean val) {
                    return val ? new Point(1, 1) : new Point(0, 0);
                }
                public static boolean toBoolean(Point point) {
                    return point.x > 0;
                }
            }
            """
        )
        runProcessorTest(
            sources = listOf(point)
        ) { invocation ->
            val store = TypeAdapterStore.create(
                Context(invocation.processingEnv),
                pointTypeConverters(invocation.processingEnv)
            )
            val pointType = invocation.processingEnv.requireType("foo.bar.Point")
            val adapter = store.findColumnTypeAdapter(pointType, null, false)
            assertThat(adapter, notNullValue())
            assertThat(adapter, instanceOf(CompositeAdapter::class.java))

            val bindScope = testCodeGenScope()
            adapter!!.bindToStmt("stmt", "41", "fooVar", bindScope)
            assertThat(
                bindScope.generate().toString().trim(),
                `is`(
                    """
                    final int ${tmp(0)};
                    final boolean ${tmp(1)};
                    ${tmp(1)} = foo.bar.Point.toBoolean(fooVar);
                    ${tmp(0)} = ${tmp(1)} ? 1 : 0;
                    stmt.bindLong(41, ${tmp(0)});
                    """.trimIndent()
                )
            )

            val cursorScope = testCodeGenScope()
            adapter.readFromCursor("res", "curs", "11", cursorScope).toString()
            assertThat(
                cursorScope.generate().toString().trim(),
                `is`(
                    """
                    final int ${tmp(0)};
                    ${tmp(0)} = curs.getInt(11);
                    final boolean ${tmp(1)};
                    ${tmp(1)} = ${tmp(0)} != 0;
                    res = foo.bar.Point.fromBoolean(${tmp(1)});
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun testDate() {
        runProcessorTest { invocation ->
            val store = TypeAdapterStore.create(
                invocation.context,
                dateTypeConverters(invocation.processingEnv)
            )
            val tDate = invocation.processingEnv.requireType("java.util.Date")
            val adapter = store.findCursorValueReader(tDate, SQLTypeAffinity.INTEGER)
            assertThat(adapter, notNullValue())
            assertThat(adapter?.typeMirror(), `is`(tDate))
            val bindScope = testCodeGenScope()
            adapter!!.readFromCursor("outDate", "curs", "0", bindScope)
            assertThat(
                bindScope.generate().toString().trim(),
                `is`(
                    """
                final java.lang.Long _tmp;
                if (curs.isNull(0)) {
                  _tmp = null;
                } else {
                  _tmp = curs.getLong(0);
                }
                // convert Long to Date;
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun testIntList() {
        runProcessorTest { invocation ->
            val binders = createIntListToStringBinders(invocation)
            val store = TypeAdapterStore.create(
                Context(invocation.processingEnv), binders[0],
                binders[1]
            )

            val adapter = store.findColumnTypeAdapter(binders[0].from, null, false)
            assertThat(adapter, notNullValue())

            val bindScope = testCodeGenScope()
            adapter!!.bindToStmt("stmt", "41", "fooVar", bindScope)
            assertThat(
                bindScope.generate().toString().trim(),
                `is`(
                    """
                final java.lang.String ${tmp(0)};
                ${tmp(0)} = androidx.room.util.StringUtil.joinIntoString(fooVar);
                if (${tmp(0)} == null) {
                  stmt.bindNull(41);
                } else {
                  stmt.bindString(41, ${tmp(0)});
                }
                    """.trimIndent()
                )
            )

            val converter = store.findTypeConverter(
                binders[0].from,
                invocation.context.COMMON_TYPES.STRING
            )
            assertThat(converter, notNullValue())
            assertThat(store.reverse(converter!!), `is`(binders[1]))
        }
    }

    @Test
    fun testOneWayConversion() {
        runProcessorTest { invocation ->
            val binders = createIntListToStringBinders(invocation)
            val store = TypeAdapterStore.create(Context(invocation.processingEnv), binders[0])
            val adapter = store.findColumnTypeAdapter(binders[0].from, null, false)
            assertThat(adapter, nullValue())

            val stmtBinder = store.findStatementValueBinder(binders[0].from, null)
            assertThat(stmtBinder, notNullValue())

            val converter = store.findTypeConverter(
                binders[0].from,
                invocation.context.COMMON_TYPES.STRING
            )
            assertThat(converter, notNullValue())
            assertThat(store.reverse(converter!!), nullValue())
        }
    }

    @Test
    fun testMissingRx2Room() {
        @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
        runProcessorTest(
            sources = listOf(COMMON.PUBLISHER, COMMON.RX2_FLOWABLE).toSources()
        ) { invocation ->
            val publisherElement = invocation.processingEnv
                .requireTypeElement(ReactiveStreamsTypeNames.PUBLISHER)
            assertThat(publisherElement, notNullValue())
            assertThat(
                RxQueryResultBinderProvider.getAll(invocation.context).any {
                    it.matches(publisherElement.type)
                },
                `is`(true)
            )
            invocation.assertCompilationResult {
                hasError(ProcessorErrors.MISSING_ROOM_RXJAVA2_ARTIFACT)
            }
        }
    }

    @Test
    fun testMissingRx3Room() {
        @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
        runProcessorTest(
            sources = listOf(COMMON.PUBLISHER, COMMON.RX3_FLOWABLE).toSources()
        ) { invocation ->
            val publisherElement = invocation.processingEnv
                .requireTypeElement(ReactiveStreamsTypeNames.PUBLISHER)
            assertThat(publisherElement, notNullValue())
            assertThat(
                RxQueryResultBinderProvider.getAll(invocation.context).any {
                    it.matches(publisherElement.type)
                },
                `is`(true)
            )
            invocation.assertCompilationResult {
                hasError(ProcessorErrors.MISSING_ROOM_RXJAVA3_ARTIFACT)
            }
        }
    }

    @Test
    fun testFindPublisher() {
        listOf(
            COMMON.RX2_FLOWABLE to COMMON.RX2_ROOM,
            COMMON.RX3_FLOWABLE to COMMON.RX3_ROOM
        ).forEach { (rxTypeSrc, rxRoomSrc) ->
            @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
            runProcessorTest(
                sources = listOf(COMMON.PUBLISHER, rxTypeSrc, rxRoomSrc).toSources()
            ) { invocation ->
                val publisher = invocation.processingEnv
                    .requireTypeElement(ReactiveStreamsTypeNames.PUBLISHER)
                assertThat(publisher, notNullValue())
                assertThat(
                    RxQueryResultBinderProvider.getAll(invocation.context).any {
                        it.matches(publisher.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindFlowable() {
        listOf(
            Triple(COMMON.RX2_FLOWABLE, COMMON.RX2_ROOM, RxJava2TypeNames.FLOWABLE),
            Triple(COMMON.RX3_FLOWABLE, COMMON.RX3_ROOM, RxJava3TypeNames.FLOWABLE)
        ).forEach { (rxTypeSrc, rxRoomSrc, rxTypeClassName) ->
            @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
            runProcessorTest(
                sources = listOf(COMMON.PUBLISHER, rxTypeSrc, rxRoomSrc).toSources()
            ) { invocation ->
                val flowable = invocation.processingEnv.requireTypeElement(rxTypeClassName)
                assertThat(
                    RxQueryResultBinderProvider.getAll(invocation.context).any {
                        it.matches(flowable.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindObservable() {
        listOf(
            Triple(COMMON.RX2_OBSERVABLE, COMMON.RX2_ROOM, RxJava2TypeNames.OBSERVABLE),
            Triple(COMMON.RX3_OBSERVABLE, COMMON.RX3_ROOM, RxJava3TypeNames.OBSERVABLE)
        ).forEach { (rxTypeSrc, rxRoomSrc, rxTypeClassName) ->
            @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
            runProcessorTest(
                sources = listOf(rxTypeSrc, rxRoomSrc).toSources()
            ) { invocation ->
                val observable = invocation.processingEnv.requireTypeElement(rxTypeClassName)
                assertThat(observable, notNullValue())
                assertThat(
                    RxQueryResultBinderProvider.getAll(invocation.context).any {
                        it.matches(observable.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindInsertSingle() {
        listOf(
            Triple(COMMON.RX2_SINGLE, COMMON.RX2_ROOM, RxJava2TypeNames.SINGLE),
            Triple(COMMON.RX3_SINGLE, COMMON.RX3_ROOM, RxJava3TypeNames.SINGLE)
        ).forEach { (rxTypeSrc, _, rxTypeClassName) ->
            @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
            runProcessorTest(sources = listOf(rxTypeSrc).toSources()) { invocation ->
                val single = invocation.processingEnv.requireTypeElement(rxTypeClassName)
                assertThat(single, notNullValue())
                assertThat(
                    RxCallableInsertMethodBinderProvider.getAll(invocation.context).any {
                        it.matches(single.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindInsertMaybe() {
        listOf(
            Triple(COMMON.RX2_MAYBE, COMMON.RX2_ROOM, RxJava2TypeNames.MAYBE),
            Triple(COMMON.RX3_MAYBE, COMMON.RX3_ROOM, RxJava3TypeNames.MAYBE)
        ).forEach { (rxTypeSrc, _, rxTypeClassName) ->
            runProcessorTest(sources = listOf(rxTypeSrc).toSources()) { invocation ->
                val maybe = invocation.processingEnv.requireTypeElement(rxTypeClassName)
                assertThat(
                    RxCallableInsertMethodBinderProvider.getAll(invocation.context).any {
                        it.matches(maybe.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindInsertCompletable() {
        listOf(
            Triple(COMMON.RX2_COMPLETABLE, COMMON.RX2_ROOM, RxJava2TypeNames.COMPLETABLE),
            Triple(COMMON.RX3_COMPLETABLE, COMMON.RX3_ROOM, RxJava3TypeNames.COMPLETABLE)
        ).forEach { (rxTypeSrc, _, rxTypeClassName) ->
            runProcessorTest(sources = listOf(rxTypeSrc).toSources()) { invocation ->
                val completable = invocation.processingEnv.requireTypeElement(rxTypeClassName)
                assertThat(
                    RxCallableInsertMethodBinderProvider.getAll(invocation.context).any {
                        it.matches(completable.type)
                    },
                    `is`(true)
                )
            }
        }
    }

    @Test
    fun testFindInsertListenableFuture() {
        runProcessorTest(sources = listOf(COMMON.LISTENABLE_FUTURE).toSources()) {
            invocation ->
            val future = invocation.processingEnv
                .requireTypeElement(GuavaUtilConcurrentTypeNames.LISTENABLE_FUTURE)
            assertThat(
                GuavaListenableFutureInsertMethodBinderProvider(invocation.context).matches(
                    future.type
                ),
                `is`(true)
            )
        }
    }

    @Test
    fun testFindDeleteOrUpdateSingle() {
        runProcessorTest(sources = listOf(COMMON.RX2_SINGLE).toSources()) { invocation ->
            val single = invocation.processingEnv.requireTypeElement(RxJava2TypeNames.SINGLE)
            assertThat(single, notNullValue())
            assertThat(
                RxCallableDeleteOrUpdateMethodBinderProvider.getAll(invocation.context).any {
                    it.matches(single.type)
                },
                `is`(true)
            )
        }
    }

    @Test
    fun testFindDeleteOrUpdateMaybe() {
        runProcessorTest(sources = listOf(COMMON.RX2_MAYBE).toSources()) {
            invocation ->
            val maybe = invocation.processingEnv.requireTypeElement(RxJava2TypeNames.MAYBE)
            assertThat(maybe, notNullValue())
            assertThat(
                RxCallableDeleteOrUpdateMethodBinderProvider.getAll(invocation.context).any {
                    it.matches(maybe.type)
                },
                `is`(true)
            )
        }
    }

    @Test
    fun testFindDeleteOrUpdateCompletable() {
        runProcessorTest(sources = listOf(COMMON.RX2_COMPLETABLE).toSources()) {
            invocation ->
            val completable = invocation.processingEnv
                .requireTypeElement(RxJava2TypeNames.COMPLETABLE)
            assertThat(completable, notNullValue())
            assertThat(
                RxCallableDeleteOrUpdateMethodBinderProvider.getAll(invocation.context).any {
                    it.matches(completable.type)
                },
                `is`(true)
            )
        }
    }

    @Test
    fun testFindDeleteOrUpdateListenableFuture() {
        runProcessorTest(
            sources = listOf(COMMON.LISTENABLE_FUTURE).toSources()
        ) { invocation ->
            val future = invocation.processingEnv
                .requireTypeElement(GuavaUtilConcurrentTypeNames.LISTENABLE_FUTURE)
            assertThat(future, notNullValue())
            assertThat(
                GuavaListenableFutureDeleteOrUpdateMethodBinderProvider(invocation.context)
                    .matches(future.type),
                `is`(true)
            )
        }
    }

    @Test
    fun testFindLiveData() {
        runProcessorTest(
            sources = listOf(COMMON.COMPUTABLE_LIVE_DATA, COMMON.LIVE_DATA).toSources()
        ) { invocation ->
            val liveData = invocation.processingEnv
                .requireTypeElement(LifecyclesTypeNames.LIVE_DATA)
            assertThat(liveData, notNullValue())
            assertThat(
                LiveDataQueryResultBinderProvider(invocation.context).matches(
                    liveData.type
                ),
                `is`(true)
            )
        }
    }

    @Test
    fun findPagingSourceIntKey() {
        runProcessorTest { invocation ->
            val pagingSourceElement = invocation.processingEnv
                .requireTypeElement(PagingSource::class)
            val intType = invocation.processingEnv.requireType(Integer::class)
            val pagingSourceIntIntType = invocation.processingEnv
                .getDeclaredType(pagingSourceElement, intType, intType)

            assertThat(pagingSourceIntIntType, notNullValue())
            assertThat(
                PagingSourceQueryResultBinderProvider(invocation.context)
                    .matches(pagingSourceIntIntType),
                `is`(true)
            )
        }
    }

    @Test
    fun findPagingSourceStringKey() {
        runProcessorTest { invocation ->
            val pagingSourceElement = invocation.processingEnv
                .requireTypeElement(PagingSource::class)
            val stringType = invocation.processingEnv.requireType(String::class)
            val pagingSourceIntIntType = invocation.processingEnv
                .getDeclaredType(pagingSourceElement, stringType, stringType)

            assertThat(pagingSourceIntIntType, notNullValue())
            assertThat(
                PagingSourceQueryResultBinderProvider(invocation.context)
                    .matches(pagingSourceIntIntType),
                `is`(true)
            )
            invocation.assertCompilationResult {
                hasError(ProcessorErrors.PAGING_SPECIFY_PAGING_SOURCE_TYPE)
            }
        }
    }

    @Test
    fun findDataSource() {
        runProcessorTest {
            invocation ->
            val dataSource = invocation.processingEnv.requireTypeElement(DataSource::class)
            assertThat(dataSource, notNullValue())
            assertThat(
                DataSourceQueryResultBinderProvider(invocation.context).matches(
                    dataSource.type
                ),
                `is`(true)
            )
            invocation.assertCompilationResult {
                hasError(ProcessorErrors.PAGING_SPECIFY_DATA_SOURCE_TYPE)
            }
        }
    }

    @Test
    fun findPositionalDataSource() {
        runProcessorTest {
            invocation ->
            @Suppress("DEPRECATION")
            val dataSource = invocation.processingEnv
                .requireTypeElement(androidx.paging.PositionalDataSource::class)
            assertThat(dataSource, notNullValue())
            assertThat(
                DataSourceQueryResultBinderProvider(invocation.context).matches(
                    dataSource.type
                ),
                `is`(true)
            )
        }
    }

    @Test
    fun findDataSourceFactory() {
        runProcessorTest(sources = listOf(COMMON.DATA_SOURCE_FACTORY).toSources()) {
            invocation ->
            val pagedListProvider = invocation.processingEnv
                .requireTypeElement(PagingTypeNames.DATA_SOURCE_FACTORY)
            assertThat(pagedListProvider, notNullValue())
            assertThat(
                DataSourceFactoryQueryResultBinderProvider(invocation.context).matches(
                    pagedListProvider.type
                ),
                `is`(true)
            )
        }
    }

    private fun createIntListToStringBinders(invocation: XTestInvocation): List<TypeConverter> {
        val intType = invocation.processingEnv.requireType(Integer::class)
        val listElement = invocation.processingEnv.requireTypeElement(java.util.List::class)
        val listOfInts = invocation.processingEnv.getDeclaredType(listElement, intType)
        val intListConverter = object : TypeConverter(
            listOfInts,
            invocation.context.COMMON_TYPES.STRING
        ) {
            override fun convert(
                inputVarName: String,
                outputVarName: String,
                scope: CodeGenScope
            ) {
                scope.builder().apply {
                    addStatement(
                        "$L = $T.joinIntoString($L)", outputVarName, STRING_UTIL,
                        inputVarName
                    )
                }
            }
        }

        val stringToIntListConverter = object : TypeConverter(
            invocation.context.COMMON_TYPES.STRING, listOfInts
        ) {
            override fun convert(
                inputVarName: String,
                outputVarName: String,
                scope: CodeGenScope
            ) {
                scope.builder().apply {
                    addStatement(
                        "$L = $T.splitToIntList($L)", outputVarName, STRING_UTIL,
                        inputVarName
                    )
                }
            }
        }
        return listOf(intListConverter, stringToIntListConverter)
    }

    fun pointTypeConverters(env: XProcessingEnv): List<TypeConverter> {
        val tPoint = env.requireType("foo.bar.Point")
        val tBoolean = env.requireType(TypeName.BOOLEAN)
        return listOf(
            object : TypeConverter(tPoint, tBoolean) {
                override fun convert(
                    inputVarName: String,
                    outputVarName: String,
                    scope: CodeGenScope
                ) {
                    scope.builder().apply {
                        addStatement(
                            "$L = $T.toBoolean($L)", outputVarName, from.typeName,
                            inputVarName
                        )
                    }
                }
            },
            object : TypeConverter(tBoolean, tPoint) {
                override fun convert(
                    inputVarName: String,
                    outputVarName: String,
                    scope: CodeGenScope
                ) {
                    scope.builder().apply {
                        addStatement(
                            "$L = $T.fromBoolean($L)", outputVarName, tPoint.typeName,
                            inputVarName
                        )
                    }
                }
            }
        )
    }

    fun dateTypeConverters(env: XProcessingEnv): List<TypeConverter> {
        val tDate = env.requireType("java.util.Date").makeNullable()
        val tLong = env.requireType("java.lang.Long").makeNullable()
        return listOf(
            object : TypeConverter(tDate, tLong) {
                override fun convert(
                    inputVarName: String,
                    outputVarName: String,
                    scope: CodeGenScope
                ) {
                    scope.builder().apply {
                        addStatement("// convert Date to Long")
                    }
                }
            },
            object : TypeConverter(tLong, tDate) {
                override fun convert(
                    inputVarName: String,
                    outputVarName: String,
                    scope: CodeGenScope
                ) {
                    scope.builder().apply {
                        addStatement("// convert Long to Date")
                    }
                }
            }
        )
    }
}
