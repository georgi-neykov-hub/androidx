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

// TODO(b/160821157): Replace FocusState with FocusState2.isFocused
@file:Suppress("DEPRECATION")

package androidx.compose.foundation

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.savedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.testutils.assertShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.isFocused
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AmbientTextInputService
import androidx.compose.ui.platform.AmbientTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.CommitTextEditOp
import androidx.compose.ui.text.input.EditOperation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextFieldValue.Companion.Saver
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.length
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class TextFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private val Tag = "textField"

    @Test
    fun textField_focusInSemantics() {
        val inputService = mock<TextInputService>()

        var isFocused = false
        rule.setContent {
            val state = remember { mutableStateOf("") }
            Providers(
                AmbientTextInputService provides inputService
            ) {
                BasicTextField(
                    value = state.value,
                    modifier = Modifier.fillMaxSize().onFocusChanged { isFocused = it.isFocused },
                    onValueChange = { state.value = it }
                )
            }
        }

        rule.onNode(hasSetTextAction()).performClick()

        rule.runOnIdle {
            assertThat(isFocused).isTrue()
        }
    }

    @Composable
    private fun TextFieldApp() {
        val state = remember { mutableStateOf("") }
        BasicTextField(
            value = state.value,
            modifier = Modifier.fillMaxSize(),
            onValueChange = {
                state.value = it
            }
        )
    }

    @Test
    fun textField_commitTexts() {
        val textInputService = mock<TextInputService>()
        val inputSessionToken = 10 // any positive number is fine.

        whenever(textInputService.startInput(any(), any(), any(), any()))
            .thenReturn(inputSessionToken)

        rule.setContent {
            Providers(
                AmbientTextInputService provides textInputService
            ) {
                TextFieldApp()
            }
        }

        rule.onNode(hasSetTextAction()).performClick()

        var onEditCommandCallback: ((List<EditOperation>) -> Unit)? = null
        rule.runOnIdle {
            // Verify startInput is called and capture the callback.
            val onEditCommandCaptor = argumentCaptor<(List<EditOperation>) -> Unit>()
            verify(textInputService, times(1)).startInput(
                value = any(),
                imeOptions = any(),
                onEditCommand = onEditCommandCaptor.capture(),
                onImeActionPerformed = any()
            )
            assertThat(onEditCommandCaptor.allValues.size).isEqualTo(1)
            onEditCommandCallback = onEditCommandCaptor.firstValue
            assertThat(onEditCommandCallback).isNotNull()
        }

        // Performs input events "1", "a", "2", "b", "3". Only numbers should remain.
        arrayOf(
            listOf(CommitTextEditOp("1", 1)),
            listOf(CommitTextEditOp("a", 1)),
            listOf(CommitTextEditOp("2", 1)),
            listOf(CommitTextEditOp("b", 1)),
            listOf(CommitTextEditOp("3", 1))
        ).forEach {
            // TODO: This should work only with runOnUiThread. But it seems that these events are
            // not buffered and chaining multiple of them before composition happens makes them to
            // get lost.
            rule.runOnIdle { onEditCommandCallback!!.invoke(it) }
        }

        rule.runOnIdle {
            val stateCaptor = argumentCaptor<TextFieldValue>()
            verify(textInputService, atLeastOnce())
                .onStateUpdated(eq(inputSessionToken), any(), stateCaptor.capture())

            // Don't care about the intermediate state update. It should eventually be "1a2b3".
            assertThat(stateCaptor.lastValue.text).isEqualTo("1a2b3")
        }
    }

    @Composable
    private fun OnlyDigitsApp() {
        val state = remember { mutableStateOf("") }
        BasicTextField(
            value = state.value,
            modifier = Modifier.fillMaxSize(),
            onValueChange = {
                if (it.all { it.isDigit() }) {
                    state.value = it
                }
            }
        )
    }

    @Test
    fun textField_commitTexts_state_may_not_set() {
        val textInputService = mock<TextInputService>()
        val inputSessionToken = 10 // any positive number is fine.

        whenever(textInputService.startInput(any(), any(), any(), any()))
            .thenReturn(inputSessionToken)

        rule.setContent {
            Providers(
                AmbientTextInputService provides textInputService
            ) {
                OnlyDigitsApp()
            }
        }

        rule.onNode(hasSetTextAction()).performClick()

        var onEditCommandCallback: ((List<EditOperation>) -> Unit)? = null
        rule.runOnIdle {
            // Verify startInput is called and capture the callback.
            val onEditCommandCaptor = argumentCaptor<(List<EditOperation>) -> Unit>()
            verify(textInputService, times(1)).startInput(
                value = any(),
                imeOptions = any(),
                onEditCommand = onEditCommandCaptor.capture(),
                onImeActionPerformed = any()
            )
            assertThat(onEditCommandCaptor.allValues.size).isEqualTo(1)
            onEditCommandCallback = onEditCommandCaptor.firstValue
            assertThat(onEditCommandCallback).isNotNull()
        }

        // Performs input events "1", "a", "2", "b", "3". Only numbers should remain.
        arrayOf(
            listOf(CommitTextEditOp("1", 1)),
            listOf(CommitTextEditOp("a", 1)),
            listOf(CommitTextEditOp("2", 1)),
            listOf(CommitTextEditOp("b", 1)),
            listOf(CommitTextEditOp("3", 1))
        ).forEach {
            // TODO: This should work only with runOnUiThread. But it seems that these events are
            // not buffered and chaining multiple of them before composition happens makes them to
            // get lost.
            rule.runOnIdle { onEditCommandCallback!!.invoke(it) }
        }

        rule.runOnIdle {
            val stateCaptor = argumentCaptor<TextFieldValue>()
            verify(textInputService, atLeastOnce())
                .onStateUpdated(eq(inputSessionToken), any(), stateCaptor.capture())

            // Don't care about the intermediate state update. It should eventually be "123" since
            // the rejects if the incoming model contains alphabets.
            assertThat(stateCaptor.lastValue.text).isEqualTo("123")
        }
    }

    @Test
    fun textField_onTextLayoutCallback() {
        val textInputService = mock<TextInputService>()
        val inputSessionToken = 10 // any positive number is fine.

        whenever(textInputService.startInput(any(), any(), any(), any()))
            .thenReturn(inputSessionToken)

        val onTextLayout: (TextLayoutResult) -> Unit = mock()
        rule.setContent {
            Providers(
                AmbientTextInputService provides textInputService
            ) {
                val state = remember { mutableStateOf("") }
                BasicTextField(
                    value = state.value,
                    modifier = Modifier.fillMaxSize(),
                    onValueChange = {
                        state.value = it
                    },
                    onTextLayout = onTextLayout
                )
            }
        }

        rule.onNode(hasSetTextAction()).performClick()

        var onEditCommandCallback: ((List<EditOperation>) -> Unit)? = null
        rule.runOnIdle {
            // Verify startInput is called and capture the callback.
            val onEditCommandCaptor = argumentCaptor<(List<EditOperation>) -> Unit>()
            verify(textInputService, times(1)).startInput(
                value = any(),
                imeOptions = any(),
                onEditCommand = onEditCommandCaptor.capture(),
                onImeActionPerformed = any()
            )
            assertThat(onEditCommandCaptor.allValues.size).isEqualTo(1)
            onEditCommandCallback = onEditCommandCaptor.firstValue
            assertThat(onEditCommandCallback).isNotNull()
        }

        // Performs input events "1", "2", "3".
        arrayOf(
            listOf(CommitTextEditOp("1", 1)),
            listOf(CommitTextEditOp("2", 1)),
            listOf(CommitTextEditOp("3", 1))
        ).forEach {
            // TODO: This should work only with runOnUiThread. But it seems that these events are
            // not buffered and chaining multiple of them before composition happens makes them to
            // get lost.
            rule.runOnIdle { onEditCommandCallback!!.invoke(it) }
        }

        rule.runOnIdle {
            val layoutCaptor = argumentCaptor<TextLayoutResult>()
            verify(onTextLayout, atLeastOnce()).invoke(layoutCaptor.capture())

            // Don't care about the intermediate state update. It should eventually be "123"
            assertThat(layoutCaptor.lastValue.layoutInput.text.text).isEqualTo("123")
        }
    }

    @Test
    fun textFieldInRow_fixedElementIsVisible() {
        val parentSize = 300.dp
        val boxSize = 50.dp
        var size: Int? = null
        rule.setContent {
            Box(Modifier.preferredSize(parentSize)) {
                Row {
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned {
                                size = it.size.width
                            }
                    )
                    Box(Modifier.preferredSize(boxSize))
                }
            }
        }

        with(rule.density) {
            assertThat(size).isEqualTo(parentSize.toIntPx() - boxSize.toIntPx())
        }
    }

    @Test
    fun textFieldValue_saverRestoresState() {
        var state: MutableState<TextFieldValue>? = null

        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            state = savedInstanceState(saver = Saver) { TextFieldValue() }
        }

        rule.runOnIdle {
            state!!.value = TextFieldValue("test", TextRange(1, 2))

            // we null it to ensure recomposition happened
            state = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertThat(state!!.value).isEqualTo(
                TextFieldValue("test", TextRange(1, 2))
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun textFieldNotFocused_cursorNotRendered() {
        rule.setContent {
            BasicTextField(
                value = "",
                onValueChange = {},
                textStyle = TextStyle(color = Color.White),
                modifier = Modifier.preferredSize(10.dp, 20.dp).background(color = Color.White),
                cursorColor = Color.Blue
            )
        }

        rule.onNode(hasSetTextAction())
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = RectangleShape,
                shapeColor = Color.White,
                backgroundColor = Color.White,
                shapeOverlapPixelCount = 0.0f
            )
    }

    @Test
    fun defaultSemantics() {
        rule.setContent {
            BasicTextField(
                modifier = Modifier.testTag("textField"),
                value = "",
                onValueChange = {}
            )
        }

        rule.onNodeWithTag("textField")
            .assertTextEquals("")
            .assertHasClickAction()
            .assert(hasSetTextAction())
            .assert(hasImeAction(ImeAction.Unspecified))
            .assert(isNotFocused())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange,
                    TextRange.Zero
                )
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))

        val textLayoutResults = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("textField")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayoutResults) }
        assert(textLayoutResults.size == 1) { "TextLayoutResult is null" }
    }

    @Test
    fun semantics_clickAction() {
        rule.setContent {
            var value by remember { mutableStateOf("") }
            BasicTextField(
                modifier = Modifier.testTag("textField"),
                value = value,
                onValueChange = { value = it }
            )
        }

        rule.onNodeWithTag("textField")
            .assert(isNotFocused())
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("textField")
            .assert(isFocused())
    }

    @Test
    fun semantics_setTextSetSelectionActions() {
        rule.setContent {
            var value by remember { mutableStateOf("") }
            BasicTextField(
                modifier = Modifier.testTag(Tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        val hello = AnnotatedString("Hello")
        rule.onNodeWithTag("textField")
            .assertTextEquals("")
            .performSemanticsAction(SemanticsActions.SetText) { it(hello) }
        rule.onNodeWithTag("textField")
            .assertTextEquals(hello.text)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange,
                    TextRange(hello.length)
                )
            )

        rule.onNodeWithTag("textField")
            .performSemanticsAction(SemanticsActions.SetSelection) { it(1, 3, true) }
        rule.onNodeWithTag("textField")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange,
                    TextRange(1, 3)
                )
            )
    }

    @Test
    fun setImeAction_isReflectedInSemantics() {
        rule.setContent {
            BasicTextField(
                value = "",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onValueChange = {}
            )
        }

        rule.onNode(hasSetTextAction())
            .assert(hasImeAction(ImeAction.Search))
    }

    @Test
    fun semantics_copyTextAction() {
        val text = "Hello World"
        var value by mutableStateOf(TextFieldValue(text, TextRange(0, 5)))

        rule.setContent {
            BasicTextField(
                modifier = Modifier.testTag(Tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        rule.onNodeWithTag(Tag)
            .performSemanticsAction(SemanticsActions.CopyText) { it() }

        rule.runOnIdle {
            assertThat(value.selection).isEqualTo(TextRange(5, 5))
        }
    }

    @Test
    fun semantics_pasteTextAction() {
        val text = "Hello World"
        var value by mutableStateOf(TextFieldValue(text, TextRange(0, 6)))

        rule.setContent {
            BasicTextField(
                modifier = Modifier.testTag(Tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        // copy text to the clipboard
        rule.onNodeWithTag(Tag)
            .performSemanticsAction(SemanticsActions.CopyText) { it() }
        rule.runOnIdle {
            assertThat(value.selection.collapsed).isTrue()
            assertThat(value.selection.start).isEqualTo(6)
        }

        // paste text from the clipboard
        rule.onNodeWithTag(Tag)
            .performSemanticsAction(SemanticsActions.PasteText) { it() }
        rule.runOnIdle {
            assertThat(value.text).isEqualTo("Hello Hello World")
        }
    }

    @Test
    fun semantics_cutTextAction() {
        val text = "Hello World"
        var value by mutableStateOf(TextFieldValue(text, TextRange(0, 6)))

        rule.setContent {
            BasicTextField(
                modifier = Modifier.testTag(Tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        rule.onNodeWithTag(Tag)
            .performSemanticsAction(SemanticsActions.CutText) { it() }

        rule.runOnIdle {
            assertThat(value.text).isEqualTo("World")
            assertThat(value.selection).isEqualTo(TextRange(0, 0))
        }
    }

    @LargeTest
    @Test
    fun semantics_longClick() {
        val text = "Hello World"
        var value by mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        var toolbar: TextToolbar? = null

        rule.setContent {
            toolbar = AmbientTextToolbar.current
            BasicTextField(
                modifier = Modifier.testTag(Tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        rule.runOnIdle {
            assertThat(toolbar?.status).isEqualTo(TextToolbarStatus.Hidden)
        }

        rule.onNodeWithTag(Tag)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }

        rule.runOnIdle {
            assertThat(toolbar?.status).isEqualTo(TextToolbarStatus.Shown)
        }
    }

    @Test
    fun stringOverrideTextField_canDeleteLastSymbol() {
        var lastSeenText = ""
        rule.setContent {
            var text by remember { mutableStateOf("") }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    lastSeenText = it
                },
                modifier = Modifier.testTag(Tag)
            )
        }

        rule.onNodeWithTag(Tag)
            .performTextInput("A")

        rule.runOnIdle {
            assertThat(lastSeenText).isEqualTo("A")
        }

        rule.onNodeWithTag(Tag)
            .performTextClearance(true)

        rule.runOnIdle {
            assertThat(lastSeenText).isEqualTo("")
        }
    }
}
